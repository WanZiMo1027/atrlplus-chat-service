package com.atriplus.chat.controller;

import com.atriplus.chat.dto.GroupReplyConfig;
import com.atriplus.chat.service.GroupReplyConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupReplyConfigControllerTest {
    private final GroupReplyConfigService service = mock(GroupReplyConfigService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new GroupReplyConfigController(service)).build();

    @Test
    void listsGroupsAndUpdatesOnlySpecifiedStatus() throws Exception {
        GroupReplyConfig group = new GroupReplyConfig(1104399740L, "睦子开发xtt", true);
        when(service.listAll()).thenReturn(List.of(group));
        when(service.updateEnabled(1104399740L, false))
                .thenReturn(new GroupReplyConfig(1104399740L, "睦子开发xtt", false));

        mvc.perform(get("/api/admin/group-reply-config/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupName").value("睦子开发xtt"))
                .andExpect(jsonPath("$[0].enabled").value(true));
        mvc.perform(put("/api/admin/group-reply-config/status")
                        .contentType("application/json")
                        .content("{\"groupId\":1104399740,\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        verify(service).updateEnabled(1104399740L, false);
    }

    @Test
    void rejectsInvalidRequestAndUnknownGroup() throws Exception {
        mvc.perform(put("/api/admin/group-reply-config/status")
                        .contentType("application/json")
                        .content("{\"groupId\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);

        when(service.updateEnabled(42L, false))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "群配置不存在"));
        mvc.perform(put("/api/admin/group-reply-config/status")
                        .contentType("application/json")
                        .content("{\"groupId\":42,\"enabled\":false}"))
                .andExpect(status().isNotFound());
    }
}
