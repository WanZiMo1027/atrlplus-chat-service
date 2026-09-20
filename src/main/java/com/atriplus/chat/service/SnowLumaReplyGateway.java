package com.atriplus.chat.service;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.client.dto.OneBotActionResponse;
import com.atriplus.chat.client.dto.SendGroupMessageRequest;
import com.atriplus.chat.client.dto.SendImageMessageData;
import com.atriplus.chat.client.dto.SendImageMessageSegment;
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
    public void send(String conversationId, ChatTarget target, AgentChatResponse response) {
        if (!response.shouldReply()) {
            return;
        }

        boolean hasReply = response.reply() != null && !response.reply().isBlank();
        boolean hasMeme = response.meme() != null && !response.meme().isBlank();
        if (!hasReply && !hasMeme) {
            return;
        }
        if (target == null) {
            throw new IllegalArgumentException("SnowLuma reply target is missing: " + conversationId);
        }

        // 主要流程：文字和表情分开发送，严格保证文字先于表情。
        if (hasReply) {
            sendMessage(target, response.reply());
        }

        if (hasMeme) {
            Meme meme = memeService.findByTag(response.meme()).orElse(null);
            if (meme == null) {
                log.warn("Meme tag not found or disabled: conversationId={}, tag={}",
                        conversationId, response.meme());
                return;
            }

            List<SendImageMessageSegment> imageMessage = List.of(
                    new SendImageMessageSegment(
                            "image",
                            new SendImageMessageData(meme.url(), 1, "[动画表情]")
                    )
            );
            sendMessage(target, imageMessage);
        }

        log.info("SnowLuma reply sent: conversationId={}, targetType={}, targetId={}, text={}, meme={}",
                conversationId, target.type(), target.targetId(), hasReply, hasMeme);
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
