package com.atriplus.chat.service.ai;

import com.atriplus.chat.client.dto.OneBotMessageSnapshot;
import com.atriplus.chat.dto.BufferedMessage;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import com.atriplus.chat.event.onebot.OneBotMessageSegment;
import com.atriplus.chat.service.NickNameAndQQNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OneBotMessageDescriptionServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SnowLumaMessageLookupService lookup = mock(SnowLumaMessageLookupService.class);
    private final NickNameAndQQNumberService nickNameService = mock(NickNameAndQQNumberService.class);
    private final OneBotMessageDescriptionService describer =
            new OneBotMessageDescriptionService(lookup, objectMapper, nickNameService);

    @Test
    void describesAtOnlyEventWhenLookupFails() throws Exception {
        when(lookup.getMessage("500827705")).thenReturn(Optional.empty());
        when(nickNameService.getNickName("3763604733")).thenReturn(Optional.empty());
        BufferedMessage message = buffered("500827705", List.of(
                segment("at", "{\"qq\":\"3763604733\"}"),
                segment("text", "{\"text\":\" \"}")));

        String description = describer.describeMessage(message);

        assertTrue(description.contains("@QQ 3763604733"));
        assertTrue(description.contains("引用：无"));
        assertTrue(description.contains("正文：无"));
        assertTrue(description.indexOf("@QQ") < description.indexOf("空白文字"));
    }

    @Test
    void replacesAtQqNumberWithBusinessNickName() throws Exception {
        when(lookup.getMessage("500827705")).thenReturn(Optional.empty());
        when(nickNameService.getNickName("3763604733")).thenReturn(Optional.of("大肥鱼"));

        String description = describer.describeMessage(buffered("500827705", List.of(
                segment("at", "{\"qq\":\"3763604733\"}"))));

        assertTrue(description.contains("@大肥鱼（QQ 3763604733）"));
    }

    @Test
    void expandsQuotedMessageOnceAndKeepsSegmentOrder() throws Exception {
        JsonNode current = objectMapper.readTree("""
                {"message_id":-1661821917,"message":[
                  {"type":"reply","data":{"id":"-1037619435"}},
                  {"type":"at","data":{"qq":"3763604733"}},
                  {"type":"text","data":{"text":" 1"}}]}
                """);
        JsonNode quoted = objectMapper.readTree("""
                {"message_id":-1037619435,"user_id":3763604733,
                 "sender":{"nickname":"大肥鱼"},"message":[
                  {"type":"reply","data":{"id":"999"}},
                  {"type":"text","data":{"text":"暴力炸金"}}]}
                """);
        when(lookup.getMessage("-1661821917")).thenReturn(Optional.of(current));
        when(lookup.getMessage("-1037619435")).thenReturn(Optional.of(quoted));
        when(nickNameService.getNickName("3763604733")).thenReturn(Optional.empty());

        String description = describer.describeMessage(buffered("-1661821917", List.of()));

        assertTrue(description.contains("引用消息 ID -1037619435"));
        assertTrue(description.contains("大肥鱼"));
        assertTrue(description.contains("暴力炸金"));
        assertTrue(description.indexOf("引用消息 ID") < description.indexOf("@QQ 3763604733"));
        assertTrue(description.indexOf("@QQ 3763604733") < description.indexOf("文字 \" 1\""));
        verify(lookup).getMessage("-1661821917");
        verify(lookup).getMessage("-1037619435");
        verify(lookup, never()).getMessage("999");
    }

    @Test
    void preservesMediaMetadataAndUnknownSegmentWithoutInlineData() throws Exception {
        when(lookup.getMessage("17")).thenReturn(Optional.empty());
        String description = describer.describeMessage(buffered("17", List.of(
                segment("image", "{\"file\":\"base64://secret\",\"name\":\"picture.png\"}"),
                segment("future", "{\"value\":1}"))));

        assertTrue(description.contains("媒体类型 image"));
        assertTrue(description.contains("picture.png"));
        assertTrue(description.contains("[内联内容已省略]"));
        assertFalse(description.contains("base64://secret"));
        assertTrue(description.contains("未识别消息段"));
    }

    @Test
    void passesThroughLegacyTextWithoutLookup() {
        String description = describer.describeMessage(new BufferedMessage("old", "甲", "原文本", null, null));
        assertEquals("原文本", description);
    }

    private BufferedMessage buffered(String messageId, List<OneBotMessageSegment> segments) {
        return new BufferedMessage("10001:" + messageId, "万矣筱晓", "", 
                new ChatTarget(10001L, ChatType.GROUP, 1104399740L),
                new OneBotMessageSnapshot(messageId, 2861880703L, segments, ""));
    }

    private OneBotMessageSegment segment(String type, String data) throws Exception {
        return new OneBotMessageSegment(type, objectMapper.readTree(data));
    }
}
