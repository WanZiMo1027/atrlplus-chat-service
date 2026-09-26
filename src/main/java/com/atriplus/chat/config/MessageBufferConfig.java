package com.atriplus.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 消息缓冲区配置
 */
@Configuration
public class MessageBufferConfig {

    /**
     * 消息刷新执行器
     */
    @Bean("messageFlushExecutor")
    public TaskExecutor messageFlushExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);

        executor.setThreadNamePrefix("message-flush-");

        executor.initialize();

        return executor;
    }

    @Bean("mediaDescriptionExecutor")
    @Profile("snowluma")
    public TaskExecutor mediaDescriptionExecutor() {
        // 动画表情识别单独排队，不占用消息批次刷新线程。
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("media-description-");
        executor.initialize();
        return executor;
    }
}
