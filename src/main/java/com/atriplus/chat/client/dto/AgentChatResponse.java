package com.atriplus.chat.client.dto;


public record AgentChatResponse(
        boolean shouldReply,
        String reply
) {
}
