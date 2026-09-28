package com.atriplus.chat.service.ai;


import com.atriplus.chat.client.dto.ChatImageItem;
import com.atriplus.chat.client.dto.DescribedMessage;
import com.atriplus.chat.client.dto.OneBotMessageSnapshot;
import com.atriplus.chat.dto.BufferedMessage;
import com.atriplus.chat.dto.MediaOutcome;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import com.atriplus.chat.event.onebot.OneBotMessageSegment;
import com.atriplus.chat.service.NickNameAndQQNumberService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private final MediaDescriptionService mediaDescriptionService;

    /**
     * 描述消息
     * @param message 消息缓冲区
     * @return 消息描述
     */
    public DescribedMessage describeMessage(BufferedMessage message) {
        OneBotMessageSnapshot snapshot = message.snapshot();
        return describeMessage(message, snapshot == null
                ? Optional.empty()
                : snowLumaMessageLookupService.getMessage(snapshot.messageId()));
    }

    /** 使用刷新阶段已查询的详情，避免首条群消息重复请求 get_msg。 */
    public DescribedMessage describeMessage(BufferedMessage message, Optional<JsonNode> messageDetail) {
        OneBotMessageSnapshot snapshot = message.snapshot();
        if(snapshot == null){
            return new DescribedMessage(message.text(), List.of());
        }

        Long groupId = getGroupId(message.target());
        if (groupId != null) {
            // 主要流程：利用事件中可靠的发送者信息持续补全群内 QQ 与昵称映射。
            nickNameAndQQNumberService.saveNickName(
                    groupId,
                    String.valueOf(snapshot.senderId()),
                    message.senderName()
            );
        }

        JsonNode detail = messageDetail.orElse(null);
        List<OneBotMessageSegment> segments = readSegments(
                detail, snapshot.segments()
        );

        // 每条消息优先补查 get_msg，失败时使用 webhook 已保存的消息段。
        StringBuilder description = new StringBuilder();
        List<ChatImageItem> images = new ArrayList<>();
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
            appendSegments(description, segments, true, groupId,images,true);
        }

        return new DescribedMessage(
                limit(description.toString(), MAX_DESCRIPTION_LENGTH),
                List.copyOf(images)
        );
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
    private void appendSegments(
            StringBuilder out,
            List<OneBotMessageSegment> segments,
            boolean expandReply,
            Long groupId,
            List<ChatImageItem> images,
            boolean collectImages
    ) {

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
                // @ 只读取已积累的业务映射，不额外请求群成员资料。
                Optional<String> nickName = groupId == null
                        ? Optional.empty()
                        : nickNameAndQQNumberService.getNickName(groupId, qqNumber);
                out.append(nickName.orElse("QQ")).append(':').append(qqNumber);
            } else if ("reply".equals(type)) {
                hasReply = true;
                String replyId = field(data, "id");
                out.append("引用消息 ID ").append(replyId);
                if (expandReply && !expandedReply && !replyId.isBlank()) {
                    expandedReply = true;
                    // 主要流程：引用只补查一层；描述被引用消息时不再查询其引用。
                    appendQuotedMessage(out, replyId, groupId);
                }
            } else if ("image".equals(type)) {
                appendImage(out, data, images, collectImages);
                hasBody = true;
            } else if (List.of("record", "video", "file").contains(type)) {
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

    private void appendQuotedMessage(StringBuilder out, String replyId, Long groupId) {
        Optional<JsonNode> quoted = snowLumaMessageLookupService.getMessage(replyId);
        if (quoted.isEmpty()) {
            out.append("；引用详情不可用");
            return;
        }

        JsonNode detail = quoted.get();
        JsonNode sender = detail.path("sender");
        String senderName = field(sender, "nickname");
        if (senderName.isBlank()) {
            senderName = detail.path("user_id").asText("");
        }
        if (groupId != null) {
            nickNameAndQQNumberService.saveNickName(
                    groupId,
                    detail.path("user_id").asText(""),
                    senderName
            );
        }
        out.append("\n被引用消息发送者：").append(jsonString(senderName))
                .append("（QQ ").append(detail.path("user_id").asText("未知")).append("）\n");
        List<OneBotMessageSegment> quotedSegments = readSegments(detail, List.of());
        if (quotedSegments.isEmpty()) {
            out.append("被引用原始内容：")
                    .append(jsonString(detail.path("raw_message").asText("")));
        } else {
            // 引用中的图片只读取文字描述，不能作为本轮新图片再次发给 Agent。
            appendSegments(out, quotedSegments, false, groupId, List.of(), false);
        }
    }

    /**
     * 追加图片描述
     * @param out 消息描述
     * @param data 图片数据
     * @param images 图片项列表
     * @param collectImages 是否收集图片项
     */
    private void appendImage(StringBuilder out, JsonNode data, List<ChatImageItem> images, boolean collectImages) {
        String file = field(data, "file");
        String summary = field(data, "summary");
        String url = field(data, "url");

        // 主要流程：按 summary 分类；查库、识别和异步入库由媒体服务负责。
        MediaOutcome outcome = mediaDescriptionService.resolve(file, summary, url, collectImages);

        out.append(outcome.text());

        if (collectImages && outcome.image() != null) {
            images.add(outcome.image());
        }
    }

    private Long getGroupId(ChatTarget target) {
        return target != null && target.type() == ChatType.GROUP
                ? target.targetId()
                : null;
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
