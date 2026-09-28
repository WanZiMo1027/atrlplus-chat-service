package com.atriplus.chat.controller;

import com.atriplus.chat.dto.GroupReplyConfig;
import com.atriplus.chat.service.GroupReplyConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/group-reply-config")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
public class GroupReplyConfigController {
    private final GroupReplyConfigService service;

    @GetMapping("/list")
    public List<GroupReplyConfig> list() {
        return service.listAll();
    }

    public record StatusRequest(@NotNull @Positive Long groupId, @NotNull Boolean enabled) {}

    @PutMapping("/status")
    public GroupReplyConfig updateStatus(@Valid @RequestBody StatusRequest request) {
        return service.updateEnabled(request.groupId(), request.enabled());
    }
}
