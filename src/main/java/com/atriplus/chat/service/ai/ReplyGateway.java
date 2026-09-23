package com.atriplus.chat.service.ai;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.dto.ChatTarget;

/**
 * 代理接口，定义了 Agent 回复的统一发送出口，由当前激活的消息适配器提供实现。
 */
public interface ReplyGateway {

    void send(String conversationId, ChatTarget target, AgentChatResponse response, Long replyToMessageId);
}
