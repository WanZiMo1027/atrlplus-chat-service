package com.atriplus.chat.service.admin;

import com.atriplus.chat.client.L1AdminClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class L1MemoryAdminFacadeTest {
    @Test
    void deletionPersistsRemoteSnapshotBeforeCallingAgent() throws Exception {
        L1AdminClient agent = mock(L1AdminClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        L1MemoryAdminFacade facade = new L1MemoryAdminFacade(agent, jdbc, mapper);
        ReflectionTestUtils.setField(facade, "token", "internal-token");
        JsonNode before = mapper.readTree("{\"id\":\"note-1\",\"content\":\"旧记忆\"}");
        JsonNode result = mapper.readTree("{\"deleted_count\":1}");
        when(agent.get("internal-token", "note-1")).thenReturn(before);
        when(agent.delete("internal-token", "note-1")).thenReturn(result);
        when(jdbc.queryForObject(contains("insert into admin_l1_audit"), eq(Long.class),
                eq("delete"), eq("note-1"), anyString())).thenReturn(12L);

        assertThat(facade.delete("note-1").path("deleted_count").asInt()).isEqualTo(1);
        InOrder order = inOrder(agent, jdbc);
        order.verify(agent).get("internal-token", "note-1");
        order.verify(jdbc).queryForObject(contains("insert into admin_l1_audit"), eq(Long.class),
                eq("delete"), eq("note-1"), anyString());
        order.verify(agent).delete("internal-token", "note-1");
        order.verify(jdbc).update(contains("update admin_l1_audit"), eq("succeeded"), anyString(), eq(12L));
    }
}
