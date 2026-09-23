package com.atriplus.chat.client.dto;

import com.atriplus.chat.event.onebot.OneBotMessageSegment;

import java.util.List;
import java.util.Objects;

/**
 * 一机器人消息快照
 * messageId 消息ID
 * senderId 发送者ID
 * segments 消息段列表
 * rawMessage 原始消息内容
 */
public record OneBotMessageSnapshot(
        String messageId,
        Long senderId,
        List<OneBotMessageSegment> segments,
        String rawMessage
) {
    public OneBotMessageSnapshot {
        if (segments == null) {
            segments = List.of();
        } else {
            segments = segments.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
}
