package com.atriplus.chat.client;

import com.atriplus.chat.client.dto.AdminPromptView;
import com.atriplus.chat.client.dto.PublishPromptRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** chat-service 只通过 agent-service 访问 TencentDB，不持有记忆库凭据。 */
@FeignClient(name = "agent-service", contextId = "l1AdminClient")
public interface L1AdminClient {
    @GetMapping("/internal/admin/tencentdb/l1/memories")
    JsonNode list(@RequestHeader("X-Admin-Internal-Token") String token,
                  @RequestParam int limit, @RequestParam int offset,
                  @RequestParam(required = false) String type);

    @GetMapping("/internal/admin/tencentdb/l1/memories/search")
    JsonNode search(@RequestHeader("X-Admin-Internal-Token") String token,
                    @RequestParam String query, @RequestParam int limit,
                    @RequestParam int offset,
                    @RequestParam(required = false) String type);

    @GetMapping("/internal/admin/tencentdb/l1/memories/{id}")
    JsonNode get(@RequestHeader("X-Admin-Internal-Token") String token, @PathVariable String id);

    record ContentRequest(String content) {}

    @PutMapping("/internal/admin/tencentdb/l1/memories/{id}")
    JsonNode update(@RequestHeader("X-Admin-Internal-Token") String token,
                    @PathVariable String id, @RequestBody ContentRequest body);

    @DeleteMapping("/internal/admin/tencentdb/l1/memories/{id}")
    JsonNode delete(@RequestHeader("X-Admin-Internal-Token") String token, @PathVariable String id);

    @GetMapping("/internal/admin/tencentdb/l1/prompt")
    AdminPromptView prompt(@RequestHeader("X-Admin-Internal-Token") String token);

    @GetMapping("/internal/admin/tencentdb/l1/prompt/template")
    String template(@RequestHeader("X-Admin-Internal-Token") String token);

    @PostMapping("/internal/admin/tencentdb/l1/prompt/publish")
    AdminPromptView publish(@RequestHeader("X-Admin-Internal-Token") String token,
                            @RequestBody PublishPromptRequest request);
}
