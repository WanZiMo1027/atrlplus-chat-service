package com.atriplus.chat;

import com.atriplus.chat.consumer.MessageEventConsumer;
import com.atriplus.chat.service.ai.ReplyGateway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ChatServiceApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        // 默认 SnowLuma 模式不得创建 RabbitMQ 运行组件。
        assertTrue(context.getBeansOfType(RabbitTemplate.class).isEmpty());
        assertTrue(context.getBeansOfType(MessageEventConsumer.class).isEmpty());
        assertEquals(1, context.getBeansOfType(ReplyGateway.class).size());
    }
}
