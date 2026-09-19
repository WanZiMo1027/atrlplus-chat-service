package com.atriplus.chat.config;


import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("rabbitmq")
public class RabbitMqConfig {

    // 回复协议集中定义；与 Python config.py 的 direct / durable 配置一致。
    public static final String REPLY_EXCHANGE = "atriplus.qq.reply.exchange";
    public static final String REPLY_QUEUE = "atriplus.qq.reply.queue";
    public static final String REPLY_ROUTING_KEY = "qq.reply";

    @Bean
    public DirectExchange replyExchange() {
        return new DirectExchange(REPLY_EXCHANGE, true, false);
    }

    @Bean
    public Queue replyQueue() {
        return QueueBuilder.durable(REPLY_QUEUE).build();
    }

    @Bean
    public Binding replyBinding(@Qualifier("replyQueue") Queue queue,
                                @Qualifier("replyExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(REPLY_ROUTING_KEY);
    }

    // 消息队列名称
    public static final String MESSAGE_QUEUE = "atriplus.qq.message.queue";
    // DLX队列名称
    public static final String DLX_QUEUE = "atriplus.qq.dlx.queue";
    // DLQ队列名称
    public static final String DLQ_QUEUE = "atriplus.qq.dl.queue";
    // DLQ路由键
    public static final String DLQ_ROUTING_KEY = "qq.message.dead";

    // DLQ交换机
    @Bean
    public DirectExchange deadLetterExchange(){
        return ExchangeBuilder
                .directExchange(DLX_QUEUE)
                .durable(true)
                .build();
    }

    // 死信队列
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder
                .durable(DLQ_QUEUE)
                .build();
    }

    // DLQ绑定
    @Bean
    public Binding deadLetterBinding(@Qualifier("deadLetterQueue") Queue deadLetterQueue, @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DLQ_ROUTING_KEY);
    }

    // 正常消息队列
    @Bean
    public Queue messageQueue() {
        return QueueBuilder
                .durable(MESSAGE_QUEUE)  // 持久化队列
                .deadLetterExchange(DLX_QUEUE)  // DLX交换机
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)  // DLQ路由键
                .build();
    }
}
