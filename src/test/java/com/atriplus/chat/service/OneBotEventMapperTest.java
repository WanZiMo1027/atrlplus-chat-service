package com.atriplus.chat.service;

import com.atriplus.chat.dto.ChatType;
import com.atriplus.chat.event.onebot.OneBotEvent;
import com.atriplus.chat.event.onebot.OneBotMessageSegment;
import com.atriplus.chat.event.onebot.OneBotSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OneBotEventMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OneBotEventMapper mapper = new OneBotEventMapper();

    @Test
    void mapsGroupMessageToStableConversationAndTarget() throws Exception {
        OneBotEvent event = new OneBotEvent(
                "message",
                "group",
                10001L,
                objectMapper.readTree("123"),
                20002L,
                30003L,
                List.of(new OneBotMessageSegment("text", objectMapper.readTree("{\"text\":\"你好\"}"))),
                "你好",
                new OneBotSender(20002L, "昵称", "群名片")
        );

        var message = mapper.map(event).orElseThrow();

        assertEquals("10001:123", message.eventId());
        assertEquals("qq:10001:group:30003", message.conversationId());
        assertEquals("群名片", message.senderName());
        assertEquals("你好", message.text());
        assertEquals(ChatType.GROUP, message.target().type());
        assertEquals(30003L, message.target().targetId());
    }

    @Test
    void mapsPrivateMessageAndFallsBackToRawText() throws Exception {
        OneBotEvent event = new OneBotEvent(
                "message",
                "private",
                10001L,
                objectMapper.readTree("124"),
                20002L,
                null,
                List.of(),
                "私聊内容",
                new OneBotSender(20002L, "昵称", "")
        );

        var message = mapper.map(event).orElseThrow();

        assertEquals("qq:10001:private:20002", message.conversationId());
        assertEquals(ChatType.PRIVATE, message.target().type());
        assertEquals("私聊内容", message.text());
    }

    @Test
    void ignoresSelfMessage() throws Exception {
        OneBotEvent event = new OneBotEvent(
                "message",
                "private",
                10001L,
                objectMapper.readTree("125"),
                10001L,
                null,
                List.of(),
                "不应处理",
                null
        );

        assertTrue(mapper.map(event).isEmpty());
    }
}
