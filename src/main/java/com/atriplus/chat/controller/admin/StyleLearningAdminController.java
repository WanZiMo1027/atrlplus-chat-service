package com.atriplus.chat.controller.admin;

import com.atriplus.chat.client.StyleLearningAdminClient;
import com.fasterxml.jackson.databind.JsonNode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.function.Supplier;

/** 只读代理沿用管理员会话；服务间 Token 始终留在后端。 */
@RestController
@RequestMapping("/api/admin/style-learning")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
public class StyleLearningAdminController {
    private final StyleLearningAdminClient agent;
    @Value("${admin.internal-token}") private String token;

    @GetMapping("/catalog") public ResponseEntity<JsonNode> catalog() { return read(() -> agent.catalog(token)); }

    @GetMapping("/fragments") public ResponseEntity<JsonNode> fragments(@RequestParam String conversationId,
            @RequestParam(defaultValue = "0") long afterId, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String version, @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String intent, @RequestParam(required = false) String seriousness) {
        return read(() -> agent.fragments(token, conversationId, afterId, limit, version, query, intent, seriousness));
    }
    private ResponseEntity<JsonNode> read(Supplier<JsonNode> query) {
        try { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(query.get()); }
        catch (FeignException.NotFound unavailable) {
            // 模块关闭与尚未部署接口都可能返回404，不把它伪装成没有片段。
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Agent 互动片段接口未启用或尚未更新，请检查学习及内部管理开关并重启 Agent");
        }
    }
}
