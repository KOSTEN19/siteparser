package com.parsesite.crawler.controller;

import com.parsesite.crawler.service.CrawlerWorker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class RabbitDebugController {
    private final CrawlerWorker crawlerWorker;

    public RabbitDebugController(CrawlerWorker crawlerWorker) {
        this.crawlerWorker = crawlerWorker;
    }

    @GetMapping("/debug/basic-get")
    public Map<String, Object> basicGetOne() throws Exception {
        Map<String, Object> message = crawlerWorker.debugBasicGetOne();
        if (message == null) {
            return Collections.singletonMap("message", "queue is empty");
        }
        return message;
    }
}
