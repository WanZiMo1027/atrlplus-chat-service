package com.atriplus.chat.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentChatResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsMissingOrNullOptionalFields() throws Exception {
        AgentChatResponse missing = objectMapper.readValue(
                "{\"shouldReply\":true,\"replies\":[\"你好\"]}",
                AgentChatResponse.class
        );
        AgentChatResponse explicitNull = objectMapper.readValue(
                "{\"shouldReply\":true,\"replies\":[\"你好\"],\"replyToEventId\":null,\"meme\":null}",
                AgentChatResponse.class
        );

        assertEquals(List.of("你好"), missing.replies());
        assertNull(missing.replyToEventId());
        assertNull(missing.meme());
        assertEquals(List.of("你好"), explicitNull.replies());
        assertNull(explicitNull.replyToEventId());
        assertNull(explicitNull.meme());
    }

    @Test
    void deserializesReplyTargetAndSemanticMemeTag() throws Exception {
        AgentChatResponse response = objectMapper.readValue(
                "{\"shouldReply\":true,\"replies\":[\"我知道了\"],\"replyToEventId\":\"123456:100001\",\"meme\":\"开心\"}",
                AgentChatResponse.class
        );

        assertEquals(List.of("我知道了"), response.replies());
        assertEquals("123456:100001", response.replyToEventId());
        assertEquals("开心", response.meme());
    }
}
