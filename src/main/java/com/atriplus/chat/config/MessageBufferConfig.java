package com.atriplus.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * 消息缓冲区配置
 */
@Configuration
@EnableConfigurationProperties(MessageBufferProperties.class)
public class MessageBufferConfig {

    /**
     * 时间源可在测试中替换，防抖和八秒间隔不需要真实等待。
     */
    @Bean("messageBufferClock")
    public Clock messageBufferClock() {
        return Clock.systemUTC();
    }

    /**
     * 锁续租独立执行，不能被慢模型或 QQ 发送占满刷新线程后阻塞。
     */
    @Bean(name = "messageBufferLeaseScheduler", destroyMethod = "shutdownNow")
    public ScheduledExecutorService messageBufferLeaseScheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(2, Thread.ofPlatform()
                .name("message-buffer-lease-", 0).daemon(true).factory());
        // 一轮结束即释放已取消续租任务的引用，避免短回复大量积累定时任务。
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

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
