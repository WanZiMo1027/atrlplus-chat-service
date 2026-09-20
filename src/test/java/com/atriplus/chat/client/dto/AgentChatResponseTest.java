package com.atriplus.chat.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentChatResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsMissingOrNullMemeForBackwardCompatibility() throws Exception {
        AgentChatResponse missing = objectMapper.readValue(
                "{\"shouldReply\":true,\"reply\":\"你好\"}",
                AgentChatResponse.class
        );
        AgentChatResponse explicitNull = objectMapper.readValue(
                "{\"shouldReply\":true,\"reply\":\"你好\",\"meme\":null}",
                AgentChatResponse.class
        );

        assertNull(missing.meme());
        assertNull(explicitNull.meme());
    }

    @Test
    void deserializesSemanticMemeTag() throws Exception {
        AgentChatResponse response = objectMapper.readValue(
                "{\"shouldReply\":true,\"reply\":null,\"meme\":\"开心\"}",
                AgentChatResponse.class
        );

        assertEquals("开心", response.meme());
    }
}
