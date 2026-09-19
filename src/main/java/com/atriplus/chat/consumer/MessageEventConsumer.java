package com.atriplus.chat.consumer;
import com.atriplus.chat.config.RabbitMqConfig;
import com.atriplus.chat.event.MessageDetectedEvent;
import com.atriplus.chat.service.EventIdempotencyService;
import com.atriplus.chat.service.MessageIngestService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.internal.constraintvalidators.bv.AssertTrueValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.io.IOException;

/*
 * 消息事件消费者
 */
@Component
@Profile("rabbitmq")
public class MessageEventConsumer {

    /**
     * 日志记录器
     */
    public static final Logger log = LoggerFactory.getLogger(MessageEventConsumer.class);

    /**
     * 最大重试次数
     */
    public static final int MAX_RETRY = 3;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageIngestService messageIngestService;

    @Autowired
    private EventIdempotencyService eventIdempotencyService;

    /**
     * 消息事件消费者
     */
    @RabbitListener(queues = RabbitMqConfig.MESSAGE_QUEUE)
    public void receive(String body, Message message, Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        MessageDetectedEvent event;

        try {
            event = objectMapper.readValue(body, MessageDetectedEvent.class);
        } catch (Exception e) {
            log.error("消息解析失败，进入DLQ", e);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        String eventId = event.eventId();
        // 检查事件是否已处理
        if (eventIdempotencyService.isProcessed(eventId)) {
            log.info("检测到重复消息，直接ACK eventId={}", eventId);
            channel.basicAck(deliveryTag, false);
            return;
        }
        // 抢处理权
        boolean acquired = eventIdempotencyService.tryStartProcessing(eventId);
        if (!acquired) {
            log.info("eventId 正在被其他消费者处理，重新入队 eventId={}", eventId);
            channel.basicNack(deliveryTag, false, true);
            return;
        }
        try {
            for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
                try {
                    log.info("开始消费 eventId={}, 第 {} 次尝试", eventId, attempt);
                    messageIngestService.ingest(event);
                    // 标记处理完成
                    eventIdempotencyService
                            .markProcessed(eventId);
                    // ACK
                    channel.basicAck(deliveryTag,false);
                    log.info("消息处理完成并ACK eventId={}", eventId);
                    return;
                } catch (Exception e) {
                    // 记录警告日志
                    log.warn("消息处理失败 eventId={}, attempt={}", eventId, attempt, e);
                    if (attempt == 3) {
                        // 释放锁
                        eventIdempotencyService.releaseProcessingLock(eventId);
                        // 不重新进入正常队列, 交给 DLX
                        channel.basicNack(deliveryTag, false, false);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            eventIdempotencyService.releaseProcessingLock(eventId);
            throw e;
        }
    }
}
