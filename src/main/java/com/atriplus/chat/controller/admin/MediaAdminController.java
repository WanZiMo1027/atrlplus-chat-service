package com.atriplus.chat.controller.admin;

import com.atriplus.chat.service.admin.MediaAdminService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 媒体管理与现有面板共用管理员登录和 CSRF 保护。 */
@RestController
@RequestMapping("/api/admin/media")
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MediaAdminController {
    private final MediaAdminService service;

    public record CreateRequest(String fileId, String kind, String sourceUrl, JsonNode description) {}
    public record EditRequest(@NotNull @PositiveOrZero Long revision, String sourceUrl, JsonNode description) {}
    public record DeleteRequest(@NotNull @PositiveOrZero Long revision) {}

    @GetMapping
    public MediaAdminService.Page list(@RequestParam String kind, @RequestParam(defaultValue = "") String query,
                                       @RequestParam(defaultValue = "") String status,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        return service.list(kind, query.trim(), status, page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MediaAdminService.View create(@RequestBody CreateRequest request) {
        return service.create(request.fileId(), request.kind(), request.sourceUrl(), request.description());
    }

    @PutMapping("/{id}")
    public MediaAdminService.View edit(@PathVariable long id, @Valid @RequestBody EditRequest request) {
        return service.edit(id, request.revision(), request.sourceUrl(), request.description());
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id, @Valid @RequestBody DeleteRequest request) {
        service.delete(id, request.revision());
        return Map.of("deleted", true);
    }

}
