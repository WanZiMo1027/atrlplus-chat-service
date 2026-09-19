package com.atriplus.chat.service;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.client.dto.OneBotActionResponse;
import com.atriplus.chat.client.dto.SendGroupMessageRequest;
import com.atriplus.chat.client.dto.SendPrivateMessageRequest;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 通过 SnowLuma OneBot HTTP API 发送 Agent 回复。 */
@Service
@Profile("snowluma")
@RequiredArgsConstructor
@Slf4j
public class SnowLumaReplyGateway implements ReplyGateway {

    private final RestClient snowLumaRestClient;

    /**
     * 通过 SnowLuma OneBot HTTP API 发送 Agent 回复。
     * @param conversationId 会话 ID，用于确定回复目标。
     * @param target 回复目标，包括会话类型和目标 ID。
     * @param response 包含回复内容的 AgentChatResponse。
     */
    @Override
    public void send(String conversationId, ChatTarget target, AgentChatResponse response) {
        if (!response.shouldReply() || response.reply() == null || response.reply().isBlank()) {
            return;
        }
        if (target == null) {
            throw new IllegalArgumentException("SnowLuma reply target is missing: " + conversationId);
        }

        // 主要流程：根据会话类型选择群聊或私聊动作，并校验 OneBot 业务返回码。
        OneBotActionResponse actionResponse = target.type() == ChatType.GROUP
                ? post("/send_group_msg", new SendGroupMessageRequest(target.targetId(), response.reply()))
                : post("/send_private_msg", new SendPrivateMessageRequest(target.targetId(), response.reply()));

        if (actionResponse == null || actionResponse.retcode() != 0) {
            String reason = actionResponse == null
                    ? "empty response"
                    : actionResponse.message() + " " + actionResponse.wording();
            throw new IllegalStateException("SnowLuma action failed: " + reason.trim());
        }
        log.info("SnowLuma reply sent: conversationId={}, targetType={}, targetId={}",
                conversationId, target.type(), target.targetId());
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
