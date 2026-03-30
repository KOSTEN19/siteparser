package com.parsesite.scheduler.service;

import com.parsesite.common.QueueNames;
import com.parsesite.common.dto.CrawlTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String rabbitHost;
    private final int rabbitPort;
    private final String rabbitUsername;
    private final String rabbitPassword;
    private final List<String> seeds = Arrays.asList(
            "https://www.securityvision.ru/news/"
    );

    public ScheduleService(
            @Value("${spring.rabbitmq.host:rabbitmq}") String rabbitHost,
            @Value("${spring.rabbitmq.port:5672}") int rabbitPort,
            @Value("${spring.rabbitmq.username:guest}") String rabbitUsername,
            @Value("${spring.rabbitmq.password:guest}") String rabbitPassword) {
        this.rabbitHost = rabbitHost;
        this.rabbitPort = rabbitPort;
        this.rabbitUsername = rabbitUsername;
        this.rabbitPassword = rabbitPassword;
    }

    @PostConstruct
    public void initQueues() {
        try {
            withChannel(new ChannelCallback() {
                @Override
                public void execute(Channel channel) throws Exception {
                    channel.queueDeclare(QueueNames.TASKS_QUEUE, true, false, false, null);
                    channel.queueDeclare(QueueNames.RESULTS_QUEUE, true, false, false, null);
                }
            });
        } catch (Exception ignored) {
        }
    }

    @Scheduled(initialDelay = 8000, fixedDelay = 60000)
    public void scheduleSeedCrawls() {
        final String seedId = UUID.randomUUID().toString();
        try {
            withChannel(new ChannelCallback() {
                @Override
                public void execute(Channel channel) throws Exception {
                    channel.queueDeclare(QueueNames.TASKS_QUEUE, true, false, false, null);
                    for (String seed : seeds) {
                        CrawlTask task = new CrawlTask(seed, "seed", seedId, 0);
                        byte[] body = objectMapper.writeValueAsBytes(task);
                        channel.basicPublish("", QueueNames.TASKS_QUEUE,
                                new AMQP.BasicProperties.Builder().deliveryMode(2).build(), body);
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void withChannel(ChannelCallback callback) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setPort(rabbitPort);
        factory.setUsername(rabbitUsername);
        factory.setPassword(rabbitPassword);
        Connection connection = null;
        Channel channel = null;
        try {
            connection = factory.newConnection();
            channel = connection.createChannel();
            callback.execute(channel);
        } finally {
            if (channel != null) {
                channel.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }

    private interface ChannelCallback {
        void execute(Channel channel) throws Exception;
    }
}
