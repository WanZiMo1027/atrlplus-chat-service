package com.atriplus.chat.service.ai;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.dto.ChatTarget;
import java.util.function.BooleanSupplier;

/**
 * 代理接口，定义了 Agent 回复的统一发送出口，由当前激活的消息适配器提供实现。
 */
public interface ReplyGateway {

    void send(String conversationId, ChatTarget target, AgentChatResponse response, Long replyToMessageId);

    /** 带处理资格校验的出口；适配器可在每次实际发送前再次检查持有权。 */
    default void sendWhileOwned(String conversationId, ChatTarget target, AgentChatResponse response,
            Long replyToMessageId, BooleanSupplier canSend) {
        if (canSend.getAsBoolean()) send(conversationId, target, response, replyToMessageId);
    }
}
