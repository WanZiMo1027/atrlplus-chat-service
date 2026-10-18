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
        List<ChatMessageItem> messages,
        Boolean replyEnabled
) {
    public AgentChatRequest {
        // 旧调用方未提供该字段时，继续允许 Agent 正常决策。
        replyEnabled = !Boolean.FALSE.equals(replyEnabled);
    }

    public AgentChatRequest(String conversationId, List<ChatMessageItem> messages) {
        this(conversationId, messages, true);
    }
}
