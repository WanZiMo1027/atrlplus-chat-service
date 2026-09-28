package com.atriplus.chat.service.admin;

import com.atriplus.chat.client.L1AdminClient;
import com.atriplus.chat.client.dto.AdminPromptView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class L1PromptAdminFacadeTest {
    private L1AdminClient agent;
    private JdbcTemplate jdbc;
    private L1PromptAdminFacade facade;

    @BeforeEach
    void setup() {
        agent = mock(L1AdminClient.class);
        jdbc = mock(JdbcTemplate.class);
        facade = new L1PromptAdminFacade(agent, jdbc, new ObjectMapper());
        ReflectionTestUtils.setField(facade, "token", "internal-token");
    }

    @Test
    void savingDraftDoesNotPublishRemotePrompt() {
        when(jdbc.query(contains("from admin_l1_prompt_state"), any(RowMapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(new L1PromptAdminFacade.Draft("新草稿", 1, "mp-1", 1, true)));
        when(agent.prompt("internal-token")).thenReturn(new AdminPromptView("mp-1", 1, "旧内容", "custom"));
        when(jdbc.update(contains("insert into admin_l1_prompt_state"), any(), any(), any()))
                .thenReturn(1);

        facade.saveDraft(new L1PromptAdminFacade.SaveDraftRequest("新草稿", 0));

        verify(agent, never()).publish(anyString(), any());
    }

    @Test
    void publishingRejectsRemoteVersionConflict() {
        when(jdbc.query(contains("from admin_l1_prompt_state"), any(RowMapper.class)))
                .thenReturn(List.of(new L1PromptAdminFacade.Draft("新草稿", 2, "mp-1", 1, true)));
        when(agent.prompt("internal-token")).thenReturn(new AdminPromptView("mp-1", 2, "远端新内容", "custom"));

        assertThatThrownBy(facade::publish).isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        verify(agent, never()).publish(anyString(), any());
    }
}
