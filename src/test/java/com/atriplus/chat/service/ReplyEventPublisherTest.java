package com.atriplus.chat.service;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.config.RabbitMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReplyEventPublisherTest {
    @Test
    void publishesUtf8ReplyProtocolWithConversationAndPersistentJson() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        ReplyEventPublisher publisher = new ReplyEventPublisher(template, mapper);
        publisher.publish("qq-default", new AgentChatResponse(true, "你好，世界"));
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(template).send(eq(RabbitMqConfig.REPLY_EXCHANGE), eq(RabbitMqConfig.REPLY_ROUTING_KEY), message.capture());
        var json = mapper.readTree(message.getValue().getBody());
        assertEquals(3, json.size());
        assertEquals("qq-default", json.get("conversationId").asText());
        assertTrue(json.get("shouldReply").asBoolean());
        assertEquals("你好，世界", json.get("reply").asText());
        assertEquals("application/json", message.getValue().getMessageProperties().getContentType());
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getValue().getMessageProperties().getDeliveryMode());
    }

    @Test
    void preservesNoReplyDecision() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        new ReplyEventPublisher(template, mapper).publish("qq-default", new AgentChatResponse(false, null));
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(template).send(eq(RabbitMqConfig.REPLY_EXCHANGE), eq(RabbitMqConfig.REPLY_ROUTING_KEY), message.capture());
        var json = mapper.readTree(message.getValue().getBody());
        assertFalse(json.get("shouldReply").asBoolean());
        assertTrue(json.get("reply").isNull());
    }

    @Test
    void brokerFailureIsPropagatedToFlushErrorHandler() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(template).send(anyString(), anyString(), any(Message.class));
        assertThrows(IllegalStateException.class, () -> new ReplyEventPublisher(template, new ObjectMapper())
                .publish("qq-default", new AgentChatResponse(true, "你好")));
    }
}
