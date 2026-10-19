package com.atriplus.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** 片段由 Agent 查询；管理面板不连接学习数据库，不接收浏览器内部令牌。 */
@FeignClient(name = "agent-service", contextId = "styleLearningAdminClient")
public interface StyleLearningAdminClient {
    @GetMapping("/internal/admin/style-learning/catalog")
    JsonNode catalog(@RequestHeader("X-Admin-Internal-Token") String token);

    @GetMapping("/internal/admin/style-learning/fragments")
    JsonNode fragments(@RequestHeader("X-Admin-Internal-Token") String token,
            @RequestParam String conversationId, @RequestParam long afterId, @RequestParam int limit,
            @RequestParam(required = false) String version, @RequestParam String query,
            @RequestParam(required = false) String intent, @RequestParam(required = false) String seriousness);
}
