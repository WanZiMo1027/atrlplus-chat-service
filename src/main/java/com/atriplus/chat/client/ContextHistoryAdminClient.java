package com.atriplus.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** 历史由 Agent 负责读取，面板不耦合 Redis 内部布局。 */
@FeignClient(name = "agent-service", contextId = "contextHistoryAdminClient")
public interface ContextHistoryAdminClient {
    @GetMapping("/internal/admin/chat-history/conversations")
    JsonNode conversations(@RequestHeader("X-Admin-Internal-Token") String token, @RequestParam String cursor);

    @GetMapping("/internal/admin/chat-history/messages")
    JsonNode messages(@RequestHeader("X-Admin-Internal-Token") String token,
                      @RequestParam String conversationId, @RequestParam(required = false) Long beforeSequence,
                      @RequestParam int limit, @RequestParam String query, @RequestParam String range);
}
