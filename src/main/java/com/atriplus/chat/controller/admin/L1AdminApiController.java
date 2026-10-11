package com.atriplus.chat.controller.admin;

import com.atriplus.chat.service.admin.L1MemoryAdminFacade;
import com.atriplus.chat.service.admin.L1PromptAdminFacade;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理面板唯一可见的数据入口。 */
@RestController
@RequestMapping("/api/admin/tencentdb/l1")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
public class L1AdminApiController {
    private final L1MemoryAdminFacade memories;
    private final L1PromptAdminFacade prompts;

    @GetMapping("/memories")
    public JsonNode list(@RequestParam(defaultValue = "20") int limit,
                         @RequestParam(defaultValue = "0") int offset,
                         @RequestParam(required = false) String type) {
        return memories.list(limit, offset, type);
    }

    @GetMapping("/memories/search")
    public JsonNode search(@RequestParam String query,
                           @RequestParam(defaultValue = "20") int limit,
                           @RequestParam(defaultValue = "0") int offset,
                           @RequestParam(required = false) String type) {
        return memories.search(query, limit, offset, type);
    }

    public record ContentRequest(String content) {}

    @PutMapping("/memories/{id}")
    public JsonNode update(@PathVariable String id, @RequestBody ContentRequest body) {
        return memories.update(id, body.content());
    }

    @DeleteMapping("/memories/{id}")
    public JsonNode delete(@PathVariable String id) { return memories.delete(id); }

    @GetMapping("/prompt")
    public L1PromptAdminFacade.PromptPage prompt() { return prompts.get(); }

    @GetMapping("/prompt/template")
    public Map<String, String> template() { return Map.of("content", prompts.template()); }

    @PutMapping("/prompt/draft")
    public L1PromptAdminFacade.Draft saveDraft(@RequestBody L1PromptAdminFacade.SaveDraftRequest body) {
        return prompts.saveDraft(body);
    }

    @PostMapping("/prompt/publish")
    public L1PromptAdminFacade.PromptPage publish() { return prompts.publish(); }

    @PostMapping("/prompt/rebase")
    public L1PromptAdminFacade.Draft rebase() { return prompts.rebase(); }

    @PostMapping("/prompt/history/{id}/restore")
    public L1PromptAdminFacade.Draft restore(@PathVariable long id) { return prompts.restore(id); }
}
