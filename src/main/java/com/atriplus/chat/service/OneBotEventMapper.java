package com.atriplus.chat.service;

import com.atriplus.chat.client.dto.OneBotMessageSnapshot;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import com.atriplus.chat.dto.IncomingChatMessage;
import com.atriplus.chat.event.onebot.OneBotEvent;
import com.atriplus.chat.event.onebot.OneBotMessageSegment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 将 SnowLuma OneBot 事件转换为聊天服务内部消息。
 */
@Component
@Profile("snowluma")
public class OneBotEventMapper {

    public Optional<IncomingChatMessage> map(OneBotEvent event) {
        if (event == null || !"message".equals(event.postType())) {
            return Optional.empty();
        }
        if (event.selfId() == null || event.userId() == null || event.messageId() == null) {
            return Optional.empty();
        }
        if (event.selfId().equals(event.userId())) {
            return Optional.empty();
        }

        ChatType chatType = parseChatType(event.messageType());
        if (chatType == null) {
            return Optional.empty();
        }
        Long targetId = chatType == ChatType.GROUP ? event.groupId() : event.userId();
        if (targetId == null) {
            return Optional.empty();
        }

        List<OneBotMessageSegment> segments = event.message() == null ? List.of() : event.message();
        String text = extractText(segments, event.rawMessage());

        // 检查是否有非文本消息段
        boolean hasNonTextSegment = false;
        for (OneBotMessageSegment segment : segments) {
            if (segment != null && segment.type() != null && !"text".equals(segment.type())) {
                hasNonTextSegment = true;
                break;
            }
        }

        // @、引用、图片等消息段即使没有文字，也要进入缓冲。
        if (text.isBlank() && !hasNonTextSegment) {
            return Optional.empty();
        }

        String conversationId = "qq:%d:%s:%d".formatted(
                event.selfId(),
                chatType.name().toLowerCase(Locale.ROOT),
                targetId
        );
        String eventId = event.selfId() + ":" + event.messageId().asText();
        ChatTarget target = new ChatTarget(event.selfId(), chatType, targetId);
        // 保存事件中已有的消息段，供后台 get_msg 失败时降级使用。
        OneBotMessageSnapshot snapshot = new OneBotMessageSnapshot(
                event.messageId().asText(),
                event.userId(),
                segments,
                event.rawMessage()
        );
        // 使用稳定 QQ ID 构造会话键，避免群名重复导致上下文串线。
        return Optional.of(new IncomingChatMessage(
                eventId,
                conversationId,
                resolveSenderName(event),
                text,
                target,
                snapshot
        ));
    }

    private ChatType parseChatType(String value) {
        if ("group".equals(value)) {
            return ChatType.GROUP;
        }
        if ("private".equals(value)) {
            return ChatType.PRIVATE;
        }
        return null;
    }

    /**
     * 从消息段中提取文本内容。
     * @param segments 消息段列表
     * @param rawMessage 原始消息内容
     * @return 提取到的文本内容
     */
    private String extractText(List<OneBotMessageSegment> segments, String rawMessage) {
        if (!segments.isEmpty()) {
            StringBuilder text = new StringBuilder();
            for (OneBotMessageSegment segment : segments) {
                if (segment != null && "text".equals(segment.type()) && segment.data() != null) {
                    text.append(segment.data().path("text").asText(""));
                }
            }
            return text.toString().trim();
        }

        return rawMessage == null ? "" : rawMessage.trim();
    }

    private String resolveSenderName(OneBotEvent event) {
        if (event.sender() != null) {
            if (event.sender().nickname() != null && !event.sender().nickname().isBlank()) {
                return event.sender().nickname();
            }
        }
        return String.valueOf(event.userId());
    }
}
