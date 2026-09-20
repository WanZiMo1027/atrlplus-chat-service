package com.atriplus.chat.service;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.client.dto.OneBotActionResponse;
import com.atriplus.chat.client.dto.SendGroupMessageRequest;
import com.atriplus.chat.client.dto.SendImageMessageData;
import com.atriplus.chat.client.dto.SendImageMessageSegment;
import com.atriplus.chat.client.dto.SendMessageSegment;
import com.atriplus.chat.client.dto.SendPrivateMessageRequest;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import com.atriplus.chat.dto.Meme;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/** 通过 SnowLuma OneBot HTTP API 发送 Agent 回复。 */
@Service
@Profile("snowluma")
@RequiredArgsConstructor
@Slf4j
public class SnowLumaReplyGateway implements ReplyGateway {

    private final RestClient snowLumaRestClient;
    private final MemeService memeService;

    /**
     * 通过 SnowLuma OneBot HTTP API 发送 Agent 回复。
     * @param conversationId 会话 ID，用于确定回复目标。
     * @param target 回复目标，包括会话类型和目标 ID。
     * @param response 包含回复内容的 AgentChatResponse。
     */
    @Override
    public void send(String conversationId, ChatTarget target, AgentChatResponse response, Long replyToMessageId) {
        if (!response.shouldReply()) {
            return;
        }
        // 防止异常响应产生空消息或发送过多消息。
        List<String> replies = response.replies() == null
                ? List.of()
                : response.replies().stream()
                .filter(reply -> reply != null && !reply.isBlank())
                .map(String::strip)
                .limit(3)
                .toList();
        boolean hasReply = !replies.isEmpty();
        boolean hasMeme = response.meme() != null && !response.meme().isBlank();
        if (!hasReply && !hasMeme) {
            return;
        }
        // 校验回复目标是否为空。
        if (target == null) {
            throw new IllegalArgumentException("SnowLuma reply target is missing: " + conversationId);
        }

        // 主要流程：第一条文字携带 AI 选择的引用，后续文字作为独立消息发送。
        for (int index = 0; index < replies.size(); index++) {
            String reply = replies.get(index);
            if (index == 0 && replyToMessageId != null) {
                sendQuotedText(target, replyToMessageId, reply);
            } else {
                sendMessage(target, reply);
            }
        }

        if (hasMeme) {
            Meme meme = memeService.findByTag(response.meme()).orElse(null);
            if (meme == null) {
                log.warn("未找到或禁用表情标签: conversationId={}, tag={}", conversationId, response.meme());
                return;
            }
            List<SendImageMessageSegment> imageMessage = List.of(
                    new SendImageMessageSegment("image", new SendImageMessageData(meme.url(), 1, "[动画表情]"))
            );
            sendMessage(target, imageMessage);
        }

        log.info("SnowLuma 发送回复消息: conversationId={}, targetType={}, targetId={}, textCount={}, quoted={}, meme={}",
                conversationId, target.type(), target.targetId(), replies.size(), replyToMessageId != null, hasMeme);
    }

    /**
     * 发送一条由 reply 段和 text 段组成的引用回复。
     */
    private void sendQuotedText(ChatTarget target, long replyToMessageId, String text) {
        // 主要流程：reply 段必须位于 text 段之前，SnowLuma 才会显示引用气泡。
        List<SendMessageSegment> message = List.of(
                SendMessageSegment.reply(replyToMessageId),
                SendMessageSegment.text(text)
        );
        sendMessage(target, message);
    }

    /**
     * 向 SnowLuma OneBot 发送消息。
     * @param target 消息目标，包括会话类型和目标 ID。
     * @param message 要发送的消息，可以是文本、图片等。
     */
    private void sendMessage(ChatTarget target, Object message) {
        // 根据会话类型选择群聊或私聊动作，并校验每一条消息的 OneBot 业务返回码。
        OneBotActionResponse actionResponse = target.type() == ChatType.GROUP
                ? post("/send_group_msg", new SendGroupMessageRequest(target.targetId(), message))
                : post("/send_private_msg", new SendPrivateMessageRequest(target.targetId(), message));

        if (actionResponse == null || actionResponse.retcode() != 0) {
            String reason = actionResponse == null
                    ? "empty response"
                    : actionResponse.message() + " " + actionResponse.wording();
            throw new IllegalStateException("SnowLuma action failed: " + reason.trim());
        }
    }

    /**
     * 向 SnowLuma OneBot 发送 POST 请求，返回 OneBotActionResponse。
     * @param path API 路径，例如 "/send_group_msg" 或 "/send_private_msg"。
     * @param request 请求体，根据 API 文档填写。
     * @return OneBotActionResponse 包含 OneBot 业务返回码、消息等。
     */
    private OneBotActionResponse post(String path, Object request) {
        return snowLumaRestClient.post()
                .uri(path)
                .body(request)
                .retrieve()
                .body(OneBotActionResponse.class);
    }
}
