package com.atriplus.chat.service.admin;

import com.atriplus.chat.client.L1AdminClient;
import com.atriplus.chat.client.dto.AdminPromptView;
import com.atriplus.chat.client.dto.PublishPromptRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** 草稿存在本地 PostgreSQL，只有发布操作才会修改 TencentDB。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.enabled", havingValue = "true")
public class L1PromptAdminFacade {
    private final L1AdminClient agent;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    @Value("${admin.internal-token}") private String token;

    public record Draft(String content, long revision, String basePromptId,
                        int baseVersion, boolean saved) {}
    public record History(long id, String content, String remotePromptId,
                          int remoteVersion, String status, String requestedAt) {}
    public record PromptPage(AdminPromptView active, Draft draft, List<History> history,
                             boolean remoteAvailable) {}
    public record SaveDraftRequest(String content, long expectedRevision) {}

    public PromptPage get() {
        AdminPromptView active = null;
        try {
            active = agent.prompt(token);
        } catch (FeignException ignored) {
            // 远端不可用时仍让管理员读取和编辑已保存的草稿。
        }
        if (active != null) reconcilePending(active);
        Draft draft = storedDraft();
        if (draft == null && active != null) {
            draft = new Draft(active.content(), 0, active.memoryPromptId(), active.version(), false);
        }
        if (draft == null) draft = new Draft("", 0, "unavailable", 0, false);
        return new PromptPage(active, draft, history(), active != null);
    }

    public String template() {
        return agent.template(token);
    }

    public synchronized Draft saveDraft(SaveDraftRequest request) {
        String content = normalize(request.content());
        if (content.isBlank() || content.length() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt 内容需为 1 到 10000 字");
        }
        Draft old = storedDraft();
        if (old == null) {
            if (request.expectedRevision() != 0) throw conflict();
            AdminPromptView active;
            try {
                active = agent.prompt(token);
            } catch (FeignException ignored) {
                active = null;
            }
            String baseId = active == null ? "unavailable" : active.memoryPromptId();
            int baseVersion = active == null ? 0 : active.version();
            int inserted = jdbc.update("""
                    insert into admin_l1_prompt_state(id, draft_content, draft_revision,
                    base_prompt_id, base_version) values (1, ?, 1, ?, ?)
                    on conflict (id) do nothing
                    """, content, baseId, baseVersion);
            if (inserted != 1) throw conflict();
        } else {
            int updated = jdbc.update("""
                    update admin_l1_prompt_state set draft_content = ?,
                    draft_revision = draft_revision + 1, updated_at = now()
                    where id = 1 and draft_revision = ?
                    """, content, request.expectedRevision());
            if (updated != 1) throw conflict();
        }
        Draft saved = storedDraft();
        audit("prompt-draft-save", "l1", old, saved);
        return saved;
    }

    public synchronized Draft rebase() {
        Draft draft = requireDraft();
        AdminPromptView active = agent.prompt(token);
        jdbc.update("""
                update admin_l1_prompt_state set base_prompt_id = ?, base_version = ?,
                draft_revision = draft_revision + 1, updated_at = now() where id = 1
                """, active.memoryPromptId(), active.version());
        Draft rebased = storedDraft();
        audit("prompt-draft-rebase", "l1", draft, rebased);
        return rebased;
    }

    public synchronized PromptPage publish() {
        Draft draft = requireDraft();
        AdminPromptView current = agent.prompt(token);
        if (!draft.basePromptId().equals(current.memoryPromptId())
                || draft.baseVersion() != current.version()) throw conflict();

        // 先持久化待发布快照。远端成功而本地确认失败时，下次读取可据此对账。
        long historyId = jdbc.queryForObject("""
                insert into admin_l1_prompt_history(content, remote_prompt_id, remote_version, status)
                values (?, ?, ?, 'pending') returning id
                """, Long.class, draft.content(), current.memoryPromptId(), current.version());
        AdminPromptView published;
        try {
            published = agent.publish(token,
                    new PublishPromptRequest(draft.content(), current.memoryPromptId(), current.version()));
        } catch (RuntimeException exception) {
            jdbc.update("update admin_l1_prompt_history set status = 'failed' where id = ? and status = 'pending'",
                    historyId);
            throw exception;
        }
        finishPublish(historyId, published);
        audit("prompt-publish", published.memoryPromptId(), current, published);
        return get();
    }

    public synchronized Draft restore(long historyId) {
        List<String> contents = jdbc.query("""
                select content from admin_l1_prompt_history where id = ? and status = 'published'
                """, (rs, rowNum) -> rs.getString(1), historyId);
        if (contents.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "历史版本不存在");
        AdminPromptView active = agent.prompt(token);
        Draft old = storedDraft();
        if (old == null) {
            jdbc.update("""
                    insert into admin_l1_prompt_state(id, draft_content, draft_revision,
                    base_prompt_id, base_version) values (1, ?, 1, ?, ?)
                    """, contents.getFirst(), active.memoryPromptId(), active.version());
        } else {
            jdbc.update("""
                    update admin_l1_prompt_state set draft_content = ?,
                    draft_revision = draft_revision + 1, base_prompt_id = ?,
                    base_version = ?, updated_at = now() where id = 1
                    """, contents.getFirst(), active.memoryPromptId(), active.version());
        }
        Draft restored = storedDraft();
        audit("prompt-history-restore", String.valueOf(historyId), old, restored);
        return restored;
    }

    private void finishPublish(long historyId, AdminPromptView published) {
        jdbc.update("""
                update admin_l1_prompt_history set status = 'published', remote_prompt_id = ?,
                remote_version = ?, published_at = now() where id = ?
                """, published.memoryPromptId(), published.version(), historyId);
        jdbc.update("""
                update admin_l1_prompt_state set base_prompt_id = ?, base_version = ?,
                updated_at = now() where id = 1
                """, published.memoryPromptId(), published.version());
    }

    private void reconcilePending(AdminPromptView active) {
        List<History> pending = jdbc.query("""
                select id, content, remote_prompt_id, remote_version, status, requested_at
                from admin_l1_prompt_history where status = 'pending' order by id
                """, this::mapHistory);
        for (History item : pending) {
            if (normalize(item.content()).equals(normalize(active.content()))
                    && active.version() >= item.remoteVersion()) {
                finishPublish(item.id(), active);
            }
        }
    }

    private Draft storedDraft() {
        List<Draft> rows = jdbc.query("""
                select draft_content, draft_revision, base_prompt_id, base_version
                from admin_l1_prompt_state where id = 1
                """, (rs, rowNum) -> new Draft(rs.getString(1), rs.getLong(2),
                rs.getString(3), rs.getInt(4), true));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Draft requireDraft() {
        Draft draft = storedDraft();
        if (draft == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先保存草稿");
        return draft;
    }

    private List<History> history() {
        return jdbc.query("""
                select id, content, remote_prompt_id, remote_version, status, requested_at
                from admin_l1_prompt_history order by id desc limit 30
                """, this::mapHistory);
    }

    private History mapHistory(ResultSet rs, int rowNum) throws SQLException {
        return new History(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getString(5), rs.getTimestamp(6).toInstant().toString());
    }

    private String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "草稿或远端 Prompt 已变化，请刷新后重试");
    }

    private void audit(String action, String targetId, Object before, Object after) {
        jdbc.update("""
                insert into admin_l1_audit(action, target_id, before_json, after_json, status, completed_at)
                values (?, ?, cast(? as jsonb), cast(? as jsonb), 'succeeded', now())
                """, action, targetId, json(before), json(after));
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化管理操作记录", exception);
        }
    }
}
