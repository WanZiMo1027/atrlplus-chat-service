package com.atriplus.chat.client.dto;

import java.util.List;

/**
 * 智能体聊天请求
 * 用于表示智能体聊天请求的参数
 * @param conversationId 对话 ID
 * @param messages 消息列表`
 */
public record AgentChatRequest(
        String conversationId,
        List<ChatMessageItem> messages
) {
}