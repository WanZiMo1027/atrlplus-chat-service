package com.atriplus.chat.client.dto;

import org.springframework.lang.Nullable;

public record AgentChatResponse(
        boolean shouldReply,
        String reply,
        @Nullable String meme
) {
}
