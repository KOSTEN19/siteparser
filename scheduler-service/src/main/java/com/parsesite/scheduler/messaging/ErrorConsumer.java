package com.parsesite.scheduler.messaging;

import com.parsesite.common.QueueNames;
import com.parsesite.scheduler.service.ScheduleService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ErrorConsumer {
    private final ScheduleService scheduleService;

    public ErrorConsumer(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @RabbitListener(queues = QueueNames.ERROR_QUEUE)
    public void handleError(String url) {
        scheduleService.registerError(url);
    }
}
