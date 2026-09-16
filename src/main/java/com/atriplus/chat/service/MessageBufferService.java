package com.atriplus.chat.service;

import com.atriplus.chat.client.AgentClient;
import com.atriplus.chat.client.dto.AgentChatRequest;
import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.dto.BufferedMessage;
import com.atriplus.chat.client.dto.ChatMessageItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;



/**
 * 消息缓冲区服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageBufferService {

    /**
     * 消息刷新执行器
     */
    private static final long DEBOUNCE_MILLIS = 5000;
    /**
     * 最大批量大小
     */
    private static final int MAX_BATCH_SIZE = 5;
    /**
     * 消息缓冲区redis前缀
     */
    private static final String BUFFER_PREFIX = "atriplus:chat:buffer:";
    /**
     * 消息缓冲区过期时间
     */
    private static final String DUE_KEY = "atriplus:chat:buffer:due";

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final AgentClient agentClient;

    private final ReplyEventPublisher replyEventPublisher;

    private final TaskExecutor messageFlushExecutor;

    /**
     * 添加消息脚本
     */
    private static final DefaultRedisScript<Long> ADD_MESSAGE_SCRIPT =
            new DefaultRedisScript<>("""
                    redis.call('RPUSH',KEYS[1],ARGV[1])
                    redis.call('ZADD',KEYS[2],ARGV[2],ARGV[3])
                    return redis.call('LLEN',KEYS[1])
                    """,
                    Long.class
            );
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> FLUSH_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local force = ARGV[3]
                    local now = tonumber(ARGV[4])

                    if force == 'false' then
                        local dueTime = redis.call('ZSCORE', KEYS[2], ARGV[1])
                        if not dueTime or tonumber(dueTime) > now then
                            return {}
                        end
                    end

                    local maxCount = tonumber(ARGV[2])
                    local messages = redis.call('LRANGE', KEYS[1], 0, maxCount - 1)

                    if #messages == 0 then
                        redis.call('ZREM', KEYS[2], ARGV[1])
                        return {}
                    end

                    redis.call('LTRIM', KEYS[1], maxCount, -1)

                    local remaining = redis.call('LLEN', KEYS[1])
                    if remaining == 0 then
                        redis.call('ZREM', KEYS[2], ARGV[1])
                    end

                    return messages
                    """,
                    List.class
            );

    /**
     * 新消息入口
     */
    public void addMessage(String conversationId, BufferedMessage message){
        String bufferKey = BUFFER_PREFIX + conversationId;
        String messageJson;
        // 序列化消息
        try {
            messageJson = objectMapper.writeValueAsString(message);
        }catch (JsonProcessingException e) {
            throw new RuntimeException("序列化消息失败", e);
        }
        long dueTime = System.currentTimeMillis() + DEBOUNCE_MILLIS;
        Long size = redisTemplate.execute(ADD_MESSAGE_SCRIPT, List.of(bufferKey, DUE_KEY), messageJson, String.valueOf(dueTime),conversationId);
        log.info("添加消息到缓冲区，当前缓冲区大小：{}", size);

        // 达到5条消息后刷新缓冲区
        if(size != null && size >= MAX_BATCH_SIZE){
            triggerFlush(conversationId,true);
        }

    }

    /**
     * 刷新过期消息
     */
    @Scheduled(fixedDelay = 1000)
    public void flushExpiredBuffers() {
        long now = System.currentTimeMillis();
        // 从过期时间集合中获取过期消息的对话ID
        Set<String> conversationIds = redisTemplate.opsForZSet().rangeByScore(DUE_KEY,0,now,0,100);

        if (conversationIds == null ||conversationIds.isEmpty()){
            return;
        }
        // 刷新过期消息
        for(String conversationId : conversationIds){
            triggerFlush(conversationId,false);
        }
    }

    /**
     * 把真正的 Agent 调用
     * 放到独立线程池。
     */
    private void triggerFlush(String conversationId, boolean force) {
        messageFlushExecutor.execute(() -> flush(conversationId, force));
    }

    /**
     * 刷新消息
     */
    @SuppressWarnings("unchecked")
    private void flush(String conversationId, boolean force) {
        String bufferKey = BUFFER_PREFIX + conversationId;

        List<String> messageJsonList = (List<String>) redisTemplate.execute(
                FLUSH_SCRIPT,
                List.of(bufferKey, DUE_KEY),
                conversationId,
                String.valueOf(MAX_BATCH_SIZE),
                String.valueOf(force),
                String.valueOf(System.currentTimeMillis())
        );

        if (messageJsonList == null || messageJsonList.isEmpty()) {
            return;
        }

        List<BufferedMessage> messages = messageJsonList.stream()
                .map(this::parseMessage)
                .toList();

        log.info("刷新消息，对话ID：{}，消息数量：{}", conversationId, messages.size());

        AgentChatRequest request = new AgentChatRequest(
                conversationId,
                messages.stream()
                        .map(message -> new ChatMessageItem(
                                message.eventId(),
                                message.senderName(),
                                message.text()))
                        .toList()
        );

        try {
            AgentChatResponse response = agentClient.chat(request);
            log.info("shouldReply:{}, reply:{}", response.shouldReply(), response.reply());
            // Agent 返回后发布原始回复协议，Python 决定是否真正发送 QQ。
            replyEventPublisher.publish(conversationId, response);
        } catch (Exception e) {
            log.error("调用代理聊天接口或发布回复失败，conversationId={}", conversationId, e);
        }
    }

    /**
     * 解析消息
     * @param json
     * @return
     */
    private BufferedMessage parseMessage(String json) {
        try {
            return objectMapper.readValue(json, BufferedMessage.class
            );

        } catch (JsonProcessingException e) {
            throw new RuntimeException("BufferedMessage JSON 解析失败", e);
        }
    }




}
