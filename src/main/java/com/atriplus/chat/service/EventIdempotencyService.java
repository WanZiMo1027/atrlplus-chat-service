package com.atriplus.chat.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 事件幂等性服务
 */
@Service
public class EventIdempotencyService {
    // 事件处理状态前缀
    private static final String PREFIX = "atriplus:mq:processed:";
    //事件处理锁
    private static final String LOCK_PREFIX = "atriplus:mq:lock:";
    // 事件处理状态过期时间
    private static final Duration TTL = Duration.ofDays(7);
    // 锁过期时间
    private static final Duration LOCK_TTL = Duration.ofDays(1);

    @Autowired
    private  StringRedisTemplate redisTemplate;

    /**
     * 检查事件是否已处理
     * @param eventId 事件ID
     * @return 是否已处理
     */
    public boolean isProcessed(String eventId) {
        Boolean exists = redisTemplate.hasKey(PREFIX + eventId);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 尝试开始处理事件
     * @param eventId 事件ID
     * @return 是否成功获取锁
     */
    public boolean tryStartProcessing(String eventId) {
        return redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + eventId, "1", LOCK_TTL);
    }

    /**
     * 标记事件为已处理
     * @param eventId 事件ID
     */
    public void markProcessed(String eventId) {
        redisTemplate.opsForValue().set(PREFIX + eventId, "1", TTL);
    }

    /**
     * 释放事件处理锁
     * @param eventId 事件ID
     */
    public void releaseProcessingLock(String eventId) {
        redisTemplate.delete(LOCK_PREFIX + eventId);
    }
}