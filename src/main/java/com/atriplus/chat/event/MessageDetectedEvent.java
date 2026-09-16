package com.atriplus.chat.event;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 消息检测事件
 * 用于表示消息被检测到的事件
 * @param schemaVersion 消息事件的 schema 版本
 * @param eventId 事件 ID
 * @param eventType 事件类型
 * @param source 事件来源
 * @param payload 事件负载
 */
public record MessageDetectedEvent(
        Integer schemaVersion,
        String eventId,
        String eventType,
        String source,
        JsonNode payload
) {
}