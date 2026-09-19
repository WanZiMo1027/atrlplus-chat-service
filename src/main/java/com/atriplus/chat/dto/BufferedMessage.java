package com.atriplus.chat.dto;

/**
 * 缓冲区消息
 * @param eventId 事件ID
 * @param senderName 发送者名称
 * @param text 消息文本
 * @param target 回复目标；旧 RabbitMQ 消息没有该字段时允许为空
 */
public record BufferedMessage(String eventId, String senderName, String text, ChatTarget target) {
}
