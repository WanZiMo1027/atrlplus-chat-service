package com.atriplus.chat.dto;

import com.atriplus.chat.client.dto.OneBotMessageSnapshot;

/** 由不同消息适配器转换得到的内部统一消息。
 * eventId 事件ID
 * conversationId 会话ID
 * senderName 发送者名称
 * text 本体消息
 * target 目标
 * snapshot 一机器人消息快照
 */
public record IncomingChatMessage(
        String eventId,
        String conversationId,
        String senderName,
        String text,
        ChatTarget target,
        OneBotMessageSnapshot snapshot
) {
}
