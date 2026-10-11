package com.atriplus.chat.service.media;

import com.atriplus.chat.dto.StoredMedia;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 接收和管理共用 PostgreSQL 记录；通过版本校验避免人工编辑被并发覆盖。 */
@Repository
@RequiredArgsConstructor
public class MediaRepository {
    private final JdbcTemplate jdbc;
    private static final String COLUMNS = "id, file_id, media_kind, status, description_json::text, "
            + "source_url, revision, created_at, updated_at, deleted_at";
    private static final RowMapper<StoredMedia> ROW = (rs, n) -> new StoredMedia(rs.getLong("id"),
            rs.getString("file_id"), rs.getString("media_kind"), rs.getString("status"),
            rs.getString("description_json"), rs.getString("source_url"), rs.getLong("revision"),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class),
            rs.getObject("deleted_at", OffsetDateTime.class));

    public Optional<StoredMedia> byId(long id) {
        return jdbc.query("select " + COLUMNS + " from media_description where id = ?", ROW, id).stream().findFirst();
    }

    public boolean isDeleted(String file) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from media_description "
                + "where file_id = ? and deleted_at is not null)", Boolean.class, file));
    }

    public Long observe(String file, String kind, String url) {
        // 不改变已有类型和人工描述；没有有效新地址时保留旧地址，删除记录不再入库。
        return jdbc.query("""
                insert into media_description (file_id, media_kind, status, source_url)
                values (?, ?, 'failed', ?)
                on conflict (file_id) do update set
                  source_url = coalesce(excluded.source_url, media_description.source_url), updated_at = now()
                where media_description.deleted_at is null returning id
                """, (rs, n) -> rs.getLong(1), file, kind, url).stream().findFirst().orElse(null);
    }

    public List<StoredMedia> list(String kind, String query, String status, int limit, long offset) {
        return jdbc.query("select " + COLUMNS + " from media_description where " + FILTER
                + " order by updated_at desc, id desc limit ? offset ?", ROW,
                kind, status, status, query, query, limit, offset);
    }

    private static final String FILTER = "deleted_at is null and media_kind = ? and (? = '' or status = ?) "
            + "and (? = '' or position(lower(?) in lower(coalesce(description_json::text, ''))) > 0)";

    public long count(String kind, String query, String status) {
        return jdbc.queryForObject("select count(*) from media_description where " + FILTER, Long.class,
                kind, status, status, query, query);
    }

    public StoredMedia create(String file, String kind, String url, String description) {
        return jdbc.query("insert into media_description (file_id, media_kind, status, source_url, description_json) "
                + "values (?, ?, 'ready', ?, ?::jsonb) returning " + COLUMNS, ROW,
                file, kind, url, description).getFirst();
    }

    @Transactional
    public Optional<StoredMedia> edit(long id, long revision, String url, String description) {
        var previous = lock(id);
        if (previous.isEmpty() || previous.get().deletedAt() != null || previous.get().revision() != revision)
            return Optional.empty();
        // 人工编辑递增版本；识别结果只在原版本匹配时写回。
        return jdbc.query("update media_description set source_url = ?, description_json = ?::jsonb, status = 'ready', "
                + "revision = revision + 1, updated_at = now() where id = ? returning " + COLUMNS,
                ROW, url, description, id).stream().findFirst();
    }

    @Transactional
    public boolean delete(long id, long revision) {
        var previous = lock(id);
        if (previous.isEmpty() || previous.get().deletedAt() != null || previous.get().revision() != revision)
            return false;
        // 保留 file_id 删除标记，后续接收和识别不能让记录重新出现。
        jdbc.update("update media_description set deleted_at = now(), revision = revision + 1, updated_at = now() "
                + "where id = ?", id);
        return true;
    }

    private Optional<StoredMedia> lock(long id) {
        return jdbc.query("select " + COLUMNS + " from media_description where id = ? for update", ROW, id)
                .stream().findFirst();
    }

}
