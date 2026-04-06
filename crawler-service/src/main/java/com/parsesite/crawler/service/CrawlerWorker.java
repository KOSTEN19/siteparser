package com.parsesite.crawler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parsesite.common.QueueNames;
import com.parsesite.common.dto.CrawlTask;
import com.parsesite.common.dto.PublicationResult;
import com.parsesite.common.entity.RawDocument;
import com.parsesite.crawler.repository.RawDocumentRepository;
import com.rabbitmq.client.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CrawlerWorker implements DisposableBean {
    private final RawDocumentRepository rawDocumentRepository;
    private final String rabbitHost;
    private final int rabbitPort;
    private final String rabbitUsername;
    private final String rabbitPassword;
    private final String outputDir;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentLinkedQueue<DeliveryTask> taskQueue = new ConcurrentLinkedQueue<DeliveryTask>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService workerPool = Executors.newFixedThreadPool(4);
    private com.rabbitmq.client.Connection rabbitConnection;
    private Channel consumeChannel;
    private Channel publishChannel;

    public CrawlerWorker(
            RawDocumentRepository rawDocumentRepository,
            @Value("${spring.rabbitmq.host:rabbitmq}") String rabbitHost,
            @Value("${spring.rabbitmq.port:5672}") int rabbitPort,
            @Value("${spring.rabbitmq.username:guest}") String rabbitUsername,
            @Value("${spring.rabbitmq.password:guest}") String rabbitPassword,
            @Value("${crawler.output-dir:/app/output}") String outputDir) {
        this.rawDocumentRepository = rawDocumentRepository;
        this.rabbitHost = rabbitHost;
        this.rabbitPort = rabbitPort;
        this.rabbitUsername = rabbitUsername;
        this.rabbitPassword = rabbitPassword;
        this.outputDir = outputDir;
    }

    @PostConstruct
    public void start() throws Exception {
        Files.createDirectories(Paths.get(outputDir));
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setPort(rabbitPort);
        factory.setUsername(rabbitUsername);
        factory.setPassword(rabbitPassword);
        rabbitConnection = factory.newConnection();
        consumeChannel = rabbitConnection.createChannel();
        publishChannel = rabbitConnection.createChannel();
        consumeChannel.basicQos(20);
        consumeChannel.queueDeclare(QueueNames.TASKS_QUEUE, true, false, false, null);
        publishChannel.queueDeclare(QueueNames.TASKS_QUEUE, true, false, false, null);
        publishChannel.queueDeclare(QueueNames.RESULTS_QUEUE, true, false, false, null);
        publishChannel.queueDeclare(QueueNames.ERROR_QUEUE, true, false, false, null);

        consumeChannel.basicConsume(QueueNames.TASKS_QUEUE, false, new DefaultConsumer(consumeChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                taskQueue.offer(new DeliveryTask(envelope.getDeliveryTag(), body));
            }
        });

        for (int i = 0; i < 4; i++) {
            workerPool.submit(new Runnable() {
                @Override
                public void run() {
                    while (running.get()) {
                        DeliveryTask deliveryTask = taskQueue.poll();
                        if (deliveryTask == null) {
                            try {
                                Thread.sleep(30);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                        processDelivery(deliveryTask);
                    }
                }
            });
        }
    }

    public Map<String, Object> debugBasicGetOne() throws Exception {
        Channel getChannel = rabbitConnection.createChannel();
        try {
            GetResponse response = getChannel.basicGet(QueueNames.TASKS_QUEUE, false);
            if (response == null) {
                return null;
            }
            CrawlTask task = objectMapper.readValue(response.getBody(), CrawlTask.class);
            getChannel.basicAck(response.getEnvelope().getDeliveryTag(), false);
            return objectMapper.convertValue(task, Map.class);
        } finally {
            getChannel.close();
        }
    }

    private void processDelivery(DeliveryTask deliveryTask) {
        try {
            CrawlTask task = objectMapper.readValue(deliveryTask.body, CrawlTask.class);
            if ("seed".equals(task.getKind())) {
                processSeedTask(task);
            } else {
                processArticleTask(task);
            }
            synchronized (consumeChannel) {
                consumeChannel.basicAck(deliveryTask.deliveryTag, false);
            }
        } catch (Exception ex) {
            try {
                synchronized (consumeChannel) {
                    consumeChannel.basicAck(deliveryTask.deliveryTag, false);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void processSeedTask(CrawlTask task) throws Exception {
        org.jsoup.Connection.Response response = Jsoup.connect(task.getUrl())
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
                .timeout(10000)
                .ignoreHttpErrors(true)
                .execute();
        if (response.statusCode() >= 400) {
            publishError(task.getUrl(), response.statusCode(), "Seed fetch failed");
            return;
        }
        Document doc = response.parse();
        Set<String> links = new HashSet<String>();
        for (Element a : doc.select("h3 a[href], h4 a[href], h5 a[href], .news a[href], .news-item a[href], article a[href]")) {
            String href = a.absUrl("href");
            String normalized = normalizeUrl(href);
            if (isLikelyNewsArticleUrl(normalized)) {
                links.add(normalized);
            }
        }
        for (String link : links) {
            CrawlTask articleTask = new CrawlTask(link, "article", task.getSeedId(), 0);
            publishTask(articleTask);
        }
    }

    private void processArticleTask(CrawlTask task) throws Exception {
        org.jsoup.Connection.Response response = Jsoup.connect(task.getUrl())
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
                .timeout(12000)
                .ignoreHttpErrors(true)
                .execute();
        if (response.statusCode() >= 400) {
            publishError(task.getUrl(), response.statusCode(), "Article fetch failed");
            return;
        }
        Document doc = response.parse();
        String title = firstText(doc, "h1", "h2", "meta[property=og:title]");
        String publishedAt = extractPublishedAt(doc);
        String author = extractAuthor(doc);
        String text = extractText(doc);
        String documentId = sha256(publishedAt + "|" + task.getUrl());

        if (rawDocumentRepository.existsByDocumentId(documentId)) {
            return;
        }

        RawDocument rawDocument = new RawDocument();
        rawDocument.setUrl(task.getUrl());
        rawDocument.setDocumentId(documentId);
        rawDocument.setTitle(title);
        rawDocument.setAuthor(author);
        rawDocument.setPublishedAt(publishedAt);
        rawDocument.setContent(response.body());
        rawDocument.setContentType(response.contentType());
        rawDocument.setCharset(response.charset());
        rawDocument.setEtag(response.header("ETag"));
        rawDocument.setLastModified(response.header("Last-Modified"));
        rawDocument.setFetchedAt(Instant.now());
        rawDocument.setContentHash(sha256(response.body()));
        rawDocumentRepository.save(rawDocument);

        PublicationResult result = new PublicationResult();
        result.setId(documentId);
        result.setTitle(title);
        result.setPublishedAt(publishedAt);
        result.setAuthor(author);
        result.setUrl(task.getUrl());
        result.setText(text);
        result.setSource("securityvision");
        result.setStatusCode(response.statusCode());
        writeJson(result);
        publishResult(result);
    }

    private void publishTask(CrawlTask task) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(task);
        synchronized (publishChannel) {
            publishChannel.basicPublish("", QueueNames.TASKS_QUEUE,
                    new AMQP.BasicProperties.Builder().deliveryMode(2).build(), body);
        }
    }

    private void publishResult(PublicationResult result) throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(result);
        synchronized (publishChannel) {
            publishChannel.basicPublish("", QueueNames.RESULTS_QUEUE,
                    new AMQP.BasicProperties.Builder().deliveryMode(2).build(), body);
        }
    }

    private void publishError(String url, int statusCode, String error) throws Exception {
        PublicationResult result = new PublicationResult();
        result.setId(sha256(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + "|" + url));
        result.setUrl(url);
        result.setSource("securityvision");
        result.setStatusCode(statusCode);
        result.setError(error);
        writeJson(result);
        byte[] body = objectMapper.writeValueAsBytes(result);
        synchronized (publishChannel) {
            publishChannel.basicPublish("", QueueNames.ERROR_QUEUE,
                    new AMQP.BasicProperties.Builder().deliveryMode(2).build(), body);
        }
    }

    private String firstText(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element e = doc.selectFirst(selector);
            if (e == null) {
                continue;
            }
            if ("meta[property=og:title]".equals(selector)) {
                String content = e.attr("content");
                if (content != null && !content.trim().isEmpty()) {
                    return content.trim();
                }
            }
            String text = e.text();
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }
        return "";
    }

    private String extractPublishedAt(Document doc) {
        Document scoped = doc.clone();
        removeNoiseSections(scoped);

        String[] selectors = new String[]{
                "meta[property=article:published_time]",
                "meta[name=pubdate]",
                "meta[name=date]",
                "time[datetime]",
                "[itemprop=datePublished]",
                ".news-date",
                ".date"
        };
        for (String selector : selectors) {
            Element e = scoped.selectFirst(selector);
            if (e == null) {
                continue;
            }
            String value = "time[datetime]".equals(selector) || "[itemprop=datePublished]".equals(selector)
                    ? e.attr("datetime").trim()
                    : e.attr("content").trim();
            if (value.isEmpty()) {
                value = e.text().trim();
            }
            if (!value.isEmpty()) {
                return value;
            }
        }

        Element main = scoped.selectFirst("article, .news-detail, .content, main");
        String text = main != null ? main.text() : scoped.text();
        Matcher matcher = Pattern.compile("\\b\\d{2}\\.\\d{2}\\.\\d{4}\\b").matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private String extractAuthor(Document doc) {
        Element metaAuthor = doc.selectFirst("meta[name=author]");
        if (metaAuthor != null && !metaAuthor.attr("content").trim().isEmpty()) {
            return metaAuthor.attr("content").trim();
        }
        Element authorText = doc.selectFirst("*:matchesOwn((?i)Автор|author)");
        if (authorText != null) {
            String candidate = authorText.text().trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return "unknown";
    }

    private String extractText(Document doc) {
        Document working = doc.clone();
        removeNoiseSections(working);

        Element body = working.selectFirst("article, .news-detail, .content, .post-content, main");
        if (body == null) {
            body = working.body();
        }
        body.select("script, style, noscript, header, footer, nav, form, aside, .cookie, .cookies, .menu, .breadcrumbs").remove();
        return body.text().replaceAll("\\s+", " ").trim();
    }

    private void removeNoiseSections(Document doc) {
        doc.select("script, style, noscript, header, footer, nav, form, aside").remove();
        for (Element title : doc.select("h1, h2, h3, h4, h5")) {
            String t = title.text().toLowerCase();
            if (t.contains("похожие новости") || t.contains("похожие статьи")
                    || t.contains("рекомендуем") || t.contains("материалы по тегам")) {
                Element parent = title.parent();
                if (parent != null) {
                    parent.remove();
                } else {
                    title.remove();
                }
            }
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String clean = url.trim();
        int hashIdx = clean.indexOf('#');
        if (hashIdx >= 0) {
            clean = clean.substring(0, hashIdx);
        }
        int qIdx = clean.indexOf('?');
        if (qIdx >= 0) {
            clean = clean.substring(0, qIdx);
        }
        return clean;
    }

    private boolean isLikelyNewsArticleUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        if (!url.startsWith("https://www.securityvision.ru/news/")) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if ("/news".equals(path) || "/news/".equals(path)) {
                return false;
            }
            if (!path.startsWith("/news/")) {
                return false;
            }
            String suffix = path.substring("/news/".length());
            if (suffix.isEmpty()) {
                return false;
            }
            if (suffix.endsWith("/")) {
                suffix = suffix.substring(0, suffix.length() - 1);
            }
            if (suffix.isEmpty() || suffix.contains("/")) {
                return false;
            }
            String s = suffix.toLowerCase();
            return !(s.equals("news") || s.startsWith("page") || s.contains("tag")
                    || s.contains("feed") || s.contains("rss") || s.contains("category"));
        } catch (Exception ex) {
            return false;
        }
    }

    private void writeJson(PublicationResult result) throws IOException {
        Path target = Paths.get(outputDir, result.getId() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), result);
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public void destroy() throws Exception {
        running.set(false);
        workerPool.shutdownNow();
        if (consumeChannel != null) {
            consumeChannel.close();
        }
        if (publishChannel != null) {
            publishChannel.close();
        }
        if (rabbitConnection != null) {
            rabbitConnection.close();
        }
    }

    private static class DeliveryTask {
        private final long deliveryTag;
        private final byte[] body;

        private DeliveryTask(long deliveryTag, byte[] body) {
            this.deliveryTag = deliveryTag;
            this.body = body;
        }
    }
}
