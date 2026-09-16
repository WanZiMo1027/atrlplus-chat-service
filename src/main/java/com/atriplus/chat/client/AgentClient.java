package com.atriplus.chat.client;

import com.atriplus.chat.client.dto.AgentChatRequest;
import com.atriplus.chat.client.dto.AgentChatResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestClient;

/**
 * 代理客户端
 */
@FeignClient(name = "agent-service")
public interface  AgentClient {
    /**
     * 代理聊天
     */
    @PostMapping("/api/agent/chat")
    AgentChatResponse chat(@RequestBody AgentChatRequest request);
}
