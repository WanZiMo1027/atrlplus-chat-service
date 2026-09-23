package com.atriplus.chat.service.ai;


import com.atriplus.chat.client.dto.OneBotMessageSnapshot;
import com.atriplus.chat.dto.BufferedMessage;
import com.atriplus.chat.event.onebot.OneBotMessageSegment;
import com.atriplus.chat.service.NickNameAndQQNumberService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Profile("snowluma")
@RequiredArgsConstructor
@Slf4j
/**
 * 机器人消息描述服务
 */
public class OneBotMessageDescriptionService {

    private static final int MAX_DESCRIPTION_LENGTH = 8192;
    private static final int MAX_METADATA_LENGTH = 256;
    private static final int MAX_UNKNOWN_LENGTH = 1024;
    private static final List<String> MEDIA_FIELDS =
            List.of("name", "file", "file_id", "id", "url", "size", "md5");

    private final SnowLumaMessageLookupService snowLumaMessageLookupService;
    private final ObjectMapper objectMapper;
    private final NickNameAndQQNumberService nickNameAndQQNumberService;

    /**
     * 描述消息
     * @param message 消息缓冲区
     * @return 消息描述
     */
    public String describeMessage(BufferedMessage message) {
        OneBotMessageSnapshot snapshot = message.snapshot();
        if(snapshot == null){
            return message.text();
        }

        JsonNode detail = snowLumaMessageLookupService.getMessage(snapshot.messageId()).orElse(null);
        List<OneBotMessageSegment> segments = readSegments(
                detail, snapshot.segments()
        );

        // 每条消息优先补查 get_msg，失败时使用 webhook 已保存的消息段。
        StringBuilder description = new StringBuilder();
        description.append("消息ID：").append(snapshot.messageId()).append('\n');
        description.append("发送者：").append(jsonString(message.senderName()))
                   .append("（QQ ").append(snapshot.senderId()).append("）\n");
        if (message.target() != null) {
            description.append("会话：").append(message.target().type())
                    .append(' ').append(message.target().targetId()).append('\n');
        }
        if (detail != null && detail.hasNonNull("time")) {
            description.append("时间戳：").append(detail.path("time").asText()).append('\n');
        }
        // 保留原始段顺序；段数组不可用时才回退到原始内容。
        if (segments.isEmpty() && snapshot.rawMessage() != null && !snapshot.rawMessage().isBlank()) {
            description.append("原始内容：").append(jsonString(snapshot.rawMessage())).append('\n');
        } else {
            appendSegments(description, segments, true);
        }

        return limit(description.toString(), MAX_DESCRIPTION_LENGTH);
    }

    /**
     * 读取消息段
     * @param detail 消息详情
     * @param fallback 回退消息段
     * @return 消息段列表
     */
    private List<OneBotMessageSegment> readSegments(JsonNode detail, List<OneBotMessageSegment> fallback) {
        if (detail != null && detail.path("message").isArray() && !detail.path("message").isEmpty()) {
            try {
                List<OneBotMessageSegment> segments = objectMapper.convertValue(
                        detail.path("message"),
                        new TypeReference<List<OneBotMessageSegment>>() {}
                );
                return segments.stream().filter(java.util.Objects::nonNull).toList();
            } catch (IllegalArgumentException exception) {
                log.warn("get_msg 消息段无法解析，已使用事件快照，messageId={}",
                        detail.path("message_id").asText(), exception);
            }
        } else if (detail != null) {
            log.warn("get_msg 未提供消息段数组，已使用事件快照，messageId={}",
                    detail.path("message_id").asText());
        }
        return fallback == null ? List.of() : fallback;
    }
    /**
     * 追加消息段描述
     * @param out 消息描述
     * @param segments 消息段列表
     * @param expandReply 是否展开引用消息
     */
    private void appendSegments(StringBuilder out, List<OneBotMessageSegment> segments, boolean expandReply) {

        boolean hasBody = false;
        boolean hasReply = false;
        boolean expandedReply = false;

        for (int i = 0; i < segments.size(); i++) {
            OneBotMessageSegment segment = segments.get(i);
            if (segment == null) {
                continue;
            }

            JsonNode data = segment.data();
            String type = segment.type();
            out.append("内容段 ").append(i + 1).append("：");

            if ("text".equals(type)) {
                String value = field(data, "text");
                if (value.isBlank()) {
                    out.append("空白文字");
                } else {
                    out.append("文字 ").append(jsonString(value));
                    hasBody = true;
                }
            } else if ("at".equals(type)) {
                String qqNumber = field(data, "qq");
                // 主要流程：业务昵称存在时优先展示昵称，否则保留原始 QQ 号作为降级结果。
                nickNameAndQQNumberService.getNickName(qqNumber).ifPresentOrElse(
                        nickName -> out.append('@').append(nickName)
                                .append("（QQ ").append(qqNumber).append("）"),
                        () -> out.append("@QQ ").append(qqNumber)
                );
            } else if ("reply".equals(type)) {
                hasReply = true;
                String replyId = field(data, "id");
                out.append("引用消息 ID ").append(replyId);
                if (expandReply && !expandedReply && !replyId.isBlank()) {
                    expandedReply = true;
                    // 主要流程：引用只补查一层；描述被引用消息时不再查询其引用。
                    appendQuotedMessage(out, replyId);
                }
            }
            else if (List.of("image", "record", "video", "file").contains(type)) {
                out.append("媒体类型 ").append(type);
                appendMediaMetadata(out, data);
            }
            else {
                out.append("未识别消息段，类型=")
                        .append(jsonString(type))
                        .append("，数据=")
                        .append(limit(data == null ? "{}" : data.toString(), MAX_UNKNOWN_LENGTH));
            }
            out.append('\n');
        }

        if (!hasReply) out.append("引用：无\n");
        if (!hasBody) out.append("正文：无\n");
    }

    private void appendQuotedMessage(StringBuilder out, String replyId) {
        Optional<JsonNode> quoted = snowLumaMessageLookupService.getMessage(replyId);
        if (quoted.isEmpty()) {
            out.append("；引用详情不可用");
            return;
        }

        JsonNode detail = quoted.get();
        JsonNode sender = detail.path("sender");
        String senderName = field(sender, "card");
        if (senderName.isBlank()) {
            senderName = field(sender, "nickname");
        }
        out.append("\n被引用消息发送者：").append(jsonString(senderName))
                .append("（QQ ").append(detail.path("user_id").asText("未知")).append("）\n");
        List<OneBotMessageSegment> quotedSegments = readSegments(detail, List.of());
        if (quotedSegments.isEmpty()) {
            out.append("被引用原始内容：")
                    .append(jsonString(detail.path("raw_message").asText("")));
        } else {
            appendSegments(out, quotedSegments, false);
        }
    }

    private void appendMediaMetadata(StringBuilder out, JsonNode data) {
        if (data == null || !data.isObject()) {
            return;
        }
        for (String name : MEDIA_FIELDS) {
            JsonNode value = data.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            String text = value.asText();
            if (text.isBlank()) {
                continue;
            }
            // 内联 base64 不是媒体元数据，不发送给 Agent。
            String safeValue = text.startsWith("base64://") ? "[内联内容已省略]" : limit(text, MAX_METADATA_LENGTH);
            out.append(' ').append(name).append('=').append(jsonString(safeValue));
        }
    }

    private String field(JsonNode data, String name) {
        return data == null ? "" : data.path(name).asText("");
    }

    private String jsonString(String value) {
        return objectMapper.valueToTree(value == null ? "" : value).toString();
    }

    private String limit(String value, int maxLength) {
        String suffix = "…[已截断]";
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength - suffix.length()) + suffix;
    }


}
