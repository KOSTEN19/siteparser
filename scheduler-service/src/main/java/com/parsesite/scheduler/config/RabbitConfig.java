package com.parsesite.scheduler.config;

import com.parsesite.common.QueueNames;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    public Queue crawlQueue() {
        return new Queue(QueueNames.CRAWL_QUEUE, true);
    }

    @Bean
    public Queue errorQueue() {
        return new Queue(QueueNames.ERROR_QUEUE, true);
    }
}
