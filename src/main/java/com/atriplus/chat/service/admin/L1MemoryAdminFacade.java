package com.atriplus.chat.service.admin;

import com.atriplus.chat.client.L1AdminClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** L1 记忆管理与修改审计。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
public class L1MemoryAdminFacade {
    private final L1AdminClient agent;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Value("${admin.internal-token}") private String token;

    public JsonNode list(int limit, int offset, String type) {
        return agent.list(token, limit, offset, type);
    }

    public JsonNode search(String query, int limit, int offset, String type) {
        return agent.search(token, query, limit, offset, type);
    }

    public JsonNode update(String id, String content) {
        JsonNode before = agent.get(token, id);
        long auditId = beginAudit("update", id, before);
        JsonNode result;
        try {
            result = agent.update(token, id, new L1AdminClient.ContentRequest(content));
        } catch (RuntimeException exception) {
            completeAudit(auditId, "failed", null);
            throw exception;
        }
        completeAudit(auditId, "succeeded", result);
        return result;
    }

    public JsonNode delete(String id) {
        // 删除没有恢复接口，先把真实远端条目写入审计表，再执行远端删除。
        JsonNode before = agent.get(token, id);
        long auditId = beginAudit("delete", id, before);
        JsonNode result;
        try {
            result = agent.delete(token, id);
        } catch (RuntimeException exception) {
            completeAudit(auditId, "failed", null);
            throw exception;
        }
        completeAudit(auditId, "succeeded", result);
        return result;
    }

    private long beginAudit(String action, String id, JsonNode before) {
        return jdbc.queryForObject("""
                insert into admin_l1_audit(action, target_id, before_json, status)
                values (?, ?, cast(? as jsonb), 'pending') returning id
                """, Long.class, action, id, json(before));
    }

    private void completeAudit(long id, String status, JsonNode after) {
        jdbc.update("""
                update admin_l1_audit set status = ?, after_json = cast(? as jsonb),
                completed_at = now() where id = ?
                """, status, after == null ? null : json(after), id);
    }

    private String json(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化审计记录", exception);
        }
    }
}
