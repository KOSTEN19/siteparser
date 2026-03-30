package com.parsesite.scheduler.service;

import com.parsesite.common.QueueNames;
import com.parsesite.common.dto.CrawlTask;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScheduleService {
    private final RabbitTemplate rabbitTemplate;
    private final ConcurrentHashMap<String, Integer> retryCounter = new ConcurrentHashMap<String, Integer>();
    private final List<String> seeds = Arrays.asList(
            "https://www.securityvision.ru/news/"
    );

    public ScheduleService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(initialDelay = 8000, fixedDelay = 60000)
    public void scheduleSeedCrawls() {
        for (String seed : seeds) {
            rabbitTemplate.convertAndSend(QueueNames.CRAWL_QUEUE, new CrawlTask(seed, 1, "seed"));
        }
    }

    public void registerError(String url) {
        int current = retryCounter.getOrDefault(url, 0);
        if (current >= 3) {
            return;
        }
        retryCounter.put(url, current + 1);
        long backoffMs = (long) Math.pow(2, current) * 3000L;
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        rabbitTemplate.convertAndSend(QueueNames.CRAWL_QUEUE, new CrawlTask(url, 1, "retry"));
    }
}
