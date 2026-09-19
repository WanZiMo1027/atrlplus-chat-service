package com.atriplus.chat.service;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.config.RabbitMqConfig;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.QQReplyEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 把 Agent 结果发布给 Python；不更改原消息缓冲与 Agent 调用流程。 */
@Service
@Profile("rabbitmq")
@RequiredArgsConstructor
@Slf4j
public class ReplyEventPublisher implements ReplyGateway {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void send(String conversationId, ChatTarget target, AgentChatResponse response) {
        publish(conversationId, response);
    }

    public void publish(String conversationId, AgentChatResponse response) {
        QQReplyEvent event = new QQReplyEvent(conversationId, response.shouldReply(), response.reply());
        try {
            // 显式序列化 UTF-8 JSON，避免影响现有 RabbitTemplate 的转换器设置。
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding("UTF-8");
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            Message message = new Message(objectMapper.writeValueAsBytes(event), properties);
            rabbitTemplate.send(RabbitMqConfig.REPLY_EXCHANGE, RabbitMqConfig.REPLY_ROUTING_KEY, message);
            log.info("REPLY EVENT PUBLISHED: conversationId={}, shouldReply={}",
                    conversationId, response.shouldReply());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Reply event JSON serialization failed", e);
        }
    }
}
