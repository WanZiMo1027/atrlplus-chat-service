package com.atriplus.chat.service;

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

        String text = extractText(event.message(), event.rawMessage());
        if (text.isBlank()) {
            return Optional.empty();
        }

        String conversationId = "qq:%d:%s:%d".formatted(
                event.selfId(),
                chatType.name().toLowerCase(Locale.ROOT),
                targetId
        );
        String eventId = event.selfId() + ":" + event.messageId().asText();
        ChatTarget target = new ChatTarget(event.selfId(), chatType, targetId);

        // 主要流程：使用稳定 QQ ID 构造会话键，避免群名重复导致上下文串线。
        return Optional.of(new IncomingChatMessage(
                eventId,
                conversationId,
                resolveSenderName(event),
                text,
                target
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

    private String extractText(List<OneBotMessageSegment> segments, String rawMessage) {
        if (segments != null) {
            StringBuilder text = new StringBuilder();
            for (OneBotMessageSegment segment : segments) {
                if (segment != null && "text".equals(segment.type()) && segment.data() != null) {
                    text.append(segment.data().path("text").asText(""));
                }
            }
            if (!text.isEmpty()) {
                return text.toString().trim();
            }
        }
        return rawMessage == null ? "" : rawMessage.trim();
    }

    private String resolveSenderName(OneBotEvent event) {
        if (event.sender() != null) {
            if (event.sender().card() != null && !event.sender().card().isBlank()) {
                return event.sender().card();
            }
            if (event.sender().nickname() != null && !event.sender().nickname().isBlank()) {
                return event.sender().nickname();
            }
        }
        return String.valueOf(event.userId());
    }
}
