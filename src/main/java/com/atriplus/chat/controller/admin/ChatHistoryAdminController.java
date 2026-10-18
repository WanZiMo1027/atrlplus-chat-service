package com.atriplus.chat.controller.admin;

import com.atriplus.chat.client.ContextHistoryAdminClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 只读管理代理；内部令牌只能由服务端注入，不接收浏览器传入的 Redis key。 */
@RestController
@RequestMapping("/api/admin/chat-history")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
public class ChatHistoryAdminController {
    private final ContextHistoryAdminClient agent;
    @Value("${admin.internal-token}") private String token;

    @GetMapping("/conversations")
    public ResponseEntity<JsonNode> conversations(@RequestParam(defaultValue = "0") String cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(agent.conversations(token, cursor));
    }

    @GetMapping("/messages")
    public ResponseEntity<JsonNode> messages(@RequestParam String conversationId,
            @RequestParam(required = false) Long beforeSequence, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "all") String range) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(agent.messages(token, conversationId, beforeSequence, limit, query, range));
    }
}
