package com.atriplus.chat.service;

import com.atriplus.chat.dto.BufferedMessage;
import com.atriplus.chat.dto.IncomingChatMessage;
import com.atriplus.chat.event.MessageDetectedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 消息接收服务
 */
@Service
public class MessageIngestService {

    private static final Logger log = LoggerFactory.getLogger(MessageIngestService.class);

    @Autowired
    private MessageBufferService messageBufferService;

    /**
     * 处理消息
     * @param event 消息检测事件
     */
    public void ingest(MessageDetectedEvent event) {

        log.info("开始处理消息 eventId={}, type={}, source={}", event.eventId(), event.eventType(), event.source());

        JsonNode payload = event.payload();

        String senderName = payload.path("senderName").asText();

        StringBuilder textBuilder = new StringBuilder();
        JsonNode textNode = payload.path("text");

        if (textNode.isArray()) {
            for (JsonNode node : textNode) {
                if (!textBuilder.isEmpty()) {
                    textBuilder.append("\n");
                }
                textBuilder.append(node.asText());
            }
        } else if (textNode.isTextual()) {
            textBuilder.append(textNode.asText());
        }

        String text = textBuilder.toString();

        // 直接使用群名作为 conversationId
        String conversationId = payload.path("conversationName").asText("").trim();

        if (conversationId.isBlank()) {
            conversationId = "unknown";
        }

        ingest(new IncomingChatMessage(event.eventId(), conversationId, senderName, text, null));

        log.info("消息已进入缓冲区 eventId={}, senderName={}, text={}", event.eventId(), senderName, text);
    }

    /**
     * 接收适配器已经标准化的消息。
     */
    public void ingest(IncomingChatMessage event) {
        // 主要流程：将统一消息持久化到 Redis 缓冲，后续由定时任务批量调用 Agent。
        BufferedMessage message = new BufferedMessage(
                event.eventId(),
                event.senderName(),
                event.text(),
                event.target()
        );
        messageBufferService.addMessage(event.conversationId(), message);
    }
}
