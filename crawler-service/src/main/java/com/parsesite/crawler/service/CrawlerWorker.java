package com.parsesite.crawler.service;

import com.parsesite.common.QueueNames;
import com.parsesite.common.dto.CrawlTask;
import com.parsesite.common.dto.IndexTask;
import com.parsesite.common.entity.RawDocument;
import com.parsesite.crawler.repository.RawDocumentRepository;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

@Service
public class CrawlerWorker {
    private final RawDocumentRepository rawDocumentRepository;
    private final RabbitTemplate rabbitTemplate;

    public CrawlerWorker(RawDocumentRepository rawDocumentRepository, RabbitTemplate rabbitTemplate) {
        this.rawDocumentRepository = rawDocumentRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = QueueNames.CRAWL_QUEUE)
    public void crawl(CrawlTask task) {
        try {
            Optional<RawDocument> previous = rawDocumentRepository.findTopByUrlOrderByFetchedAtDesc(task.getUrl());
            Connection connection = Jsoup.connect(task.getUrl())
                    .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
                    .timeout(10000);
            if (previous.isPresent()) {
                if (previous.get().getEtag() != null) {
                    connection.header("If-None-Match", previous.get().getEtag());
                }
                if (previous.get().getLastModified() != null) {
                    connection.header("If-Modified-Since", previous.get().getLastModified());
                }
            }

            Connection.Response response = connection.execute();
            if (response.statusCode() == 304) {
                return;
            }
            String body = response.body();
            RawDocument rawDocument = new RawDocument();
            rawDocument.setUrl(task.getUrl());
            rawDocument.setContent(body);
            rawDocument.setContentType(response.contentType());
            rawDocument.setCharset(response.charset());
            rawDocument.setEtag(response.header("ETag"));
            rawDocument.setLastModified(response.header("Last-Modified"));
            rawDocument.setFetchedAt(Instant.now());
            rawDocument.setContentHash(sha256(body));
            RawDocument saved = rawDocumentRepository.save(rawDocument);
            rabbitTemplate.convertAndSend(QueueNames.INDEX_QUEUE, new IndexTask(saved.getId(), saved.getUrl()));
        } catch (Exception ex) {
            rabbitTemplate.convertAndSend(QueueNames.ERROR_QUEUE, task.getUrl());
        }
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
}
