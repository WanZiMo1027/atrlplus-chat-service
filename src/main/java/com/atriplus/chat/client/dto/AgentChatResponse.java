package com.atriplus.chat.client.dto;

import org.springframework.lang.Nullable;

import java.util.List;
/**
 * 智能体聊天响应
 * @param shouldReply 是否需要回复
 * @param replies 回复文本列表
 * @param replyToEventId 回复的消息ID
 * @param meme meme 回复图片 URL
 */
public record AgentChatResponse(
        boolean shouldReply,
        List<String> replies,
        @Nullable String replyToEventId,
        @Nullable String meme
) {
}
