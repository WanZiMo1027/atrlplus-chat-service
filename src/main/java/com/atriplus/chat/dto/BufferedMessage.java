package com.atriplus.chat.dto;

/**
 * 缓冲区消息
 * @param eventId 事件ID
 * @param senderName 发送者名称
 * @param text 消息文本
 */
public record BufferedMessage(String eventId,String senderName,String text) {
}
