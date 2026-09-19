package com.atriplus.chat.dto;

/** 由不同消息适配器转换得到的内部统一消息。 */
public record IncomingChatMessage(
        String eventId,
        String conversationId,
        String senderName,
        String text,
        ChatTarget target
) {
}
