package com.parsesite.indexer.service;

import com.parsesite.common.QueueNames;
import com.parsesite.common.dto.AnalyzeTask;
import com.parsesite.common.dto.IndexTask;
import com.parsesite.common.entity.RawDocument;
import com.parsesite.indexer.repository.RawDocumentRepository;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IndexWorker {
    private final RawDocumentRepository repository;
    private final RestHighLevelClient esClient;
    private final RabbitTemplate rabbitTemplate;

    public IndexWorker(RawDocumentRepository repository, RestHighLevelClient esClient, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.esClient = esClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = QueueNames.INDEX_QUEUE)
    public void index(IndexTask task) throws Exception {
        Optional<RawDocument> rawOpt = repository.findById(task.getRawDocumentId());
        if (!rawOpt.isPresent()) {
            return;
        }
        RawDocument raw = rawOpt.get();
        String text = cleanText(raw.getUrl(), raw.getContent());
        String language = detectLanguage(text);
        List<String> entities = extractEntities(text);

        String id = String.valueOf(raw.getId());
        String payload = "{"
                + "\"url\":\"" + escape(raw.getUrl()) + "\","
                + "\"content\":\"" + escape(text) + "\","
                + "\"language\":\"" + language + "\","
                + "\"entities\":" + toJsonArray(entities) + ","
                + "\"contentHash\":\"" + raw.getContentHash() + "\""
                + "}";

        IndexRequest request = new IndexRequest("documents").id(id).source(payload, XContentType.JSON);
        esClient.index(request, RequestOptions.DEFAULT);

        rabbitTemplate.convertAndSend(QueueNames.ANALYZE_QUEUE, new AnalyzeTask(id, text));
    }

    private String cleanText(String url, String html) {
        Document doc = Jsoup.parse(html, url);
        String host = "";
        String path = "";
        try {
            URI uri = URI.create(url);
            host = uri.getHost() == null ? "" : uri.getHost();
            path = uri.getPath() == null ? "" : uri.getPath();
        } catch (Exception ignored) {
        }

        if (host.contains("securityvision.ru")) {
            if (path.equals("/news") || path.equals("/news/")) {
                return extractSecurityVisionNewsFeedText(doc);
            }
            // For article pages remove global site chrome and forms.
            doc.select("header, footer, nav, script, style, noscript, form, aside, .cookie, .cookies, .menu").remove();
            Element main = doc.selectFirst("main, article, .news-detail, .content, .post-content");
            if (main != null) {
                return normalize(main.text());
            }
        }
        return normalize(doc.text());
    }

    private String extractSecurityVisionNewsFeedText(Document doc) {
        doc.select("script, style, noscript").remove();
        StringBuilder sb = new StringBuilder();
        sb.append("Новости Security Vision. ");

        // Prefer card/title level headings from the feed.
        List<Element> titles = doc.select("h3, h4, h5");
        int titleCount = 0;
        for (Element t : titles) {
            String title = normalize(t.text());
            if (title.length() < 20) {
                continue;
            }
            if (title.toLowerCase(Locale.ROOT).contains("security orchestration tools")
                    || title.toLowerCase(Locale.ROOT).contains("governance, risk management")
                    || title.toLowerCase(Locale.ROOT).contains("security data analysis")) {
                continue;
            }
            sb.append(" Заголовок: ").append(title).append(". ");
            titleCount++;
            if (titleCount >= 40) {
                break;
            }
        }

        // Keep dates that usually belong to news cards.
        Pattern datePattern = Pattern.compile("\\b\\d{2}\\.\\d{2}\\.\\d{4}\\b");
        Matcher matcher = datePattern.matcher(doc.text());
        int dateCount = 0;
        while (matcher.find() && dateCount < 80) {
            sb.append(" Дата: ").append(matcher.group()).append(". ");
            dateCount++;
        }

        // Keep top-level thematic tags from "Материалы по тегам".
        Element tagsHeader = doc.selectFirst("h2:matchesOwn((?i)Материалы по тегам)");
        if (tagsHeader != null) {
            Element tagsParent = tagsHeader.parent();
            if (tagsParent != null) {
                int tags = 0;
                for (Element a : tagsParent.select("a[href]")) {
                    String tag = normalize(a.text());
                    if (tag.isEmpty() || tag.length() > 80) {
                        continue;
                    }
                    sb.append(" Тег: ").append(tag).append(". ");
                    tags++;
                    if (tags >= 60) {
                        break;
                    }
                }
            }
        }
        return normalize(sb.toString());
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private String detectLanguage(String text) {
        int cyr = 0;
        int lat = 0;
        for (char c : text.toCharArray()) {
            if (c >= 'А' && c <= 'я') cyr++;
            if ((c >= 'A' && c <= 'z')) lat++;
        }
        return cyr > lat ? "ru" : "en";
    }

    private List<String> extractEntities(String text) {
        Pattern p = Pattern.compile("\\b([A-ZА-Я][a-zа-я]{2,})\\b");
        Matcher m = p.matcher(text);
        Set<String> set = new LinkedHashSet<String>();
        while (m.find() && set.size() < 20) {
            set.add(m.group(1));
        }
        return new ArrayList<String>(set);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(values.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
