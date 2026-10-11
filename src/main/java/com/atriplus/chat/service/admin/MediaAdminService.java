package com.atriplus.chat.service.admin;

import com.atriplus.chat.dto.StoredMedia;
import com.atriplus.chat.service.media.MediaUrl;
import com.atriplus.chat.service.media.MediaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 管理接收媒体的地址和描述；预览使用原始 URL。 */
@Service
@RequiredArgsConstructor
public class MediaAdminService {
    private final MediaRepository repository;
    private final ObjectMapper mapper;

    public record View(String id, String fileId, String kind, String status, JsonNode description,
                       String sourceUrl, long revision,
                       OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record Page(List<View> items, long total) {}

    public Page list(String kind, String query, String status, int page, int pageSize) {
        validateKind(kind);
        if (!List.of("", "processing", "ready", "failed").contains(status)
                || page < 1 || pageSize < 1 || pageSize > 100 || query.length() > 2048)
            throw bad("查询参数无效");
        return new Page(repository.list(kind, query, status, pageSize, (page - 1L) * pageSize)
                .stream().map(this::view).toList(), repository.count(kind, query, status));
    }

    public View create(String fileId, String kind, String url, JsonNode description) {
        validateKind(kind);
        url = validateUrl(url);
        validateDescription(kind, description);
        String file = fileId == null || fileId.isBlank() ? "manual:" + UUID.randomUUID() : fileId.trim();
        if (file.length() > 2048) throw bad("文件 ID 过长");
        try {
            StoredMedia row = repository.create(file, kind, url, description.toString());
            return view(row);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件 ID 已存在或已被永久忽略");
        }
    }

    public View edit(long id, long revision, String url, JsonNode description) {
        StoredMedia existing = active(id);
        validateDescription(existing.kind(), description);
        url = url == null || url.isBlank() ? null : validateUrl(url);
        if (url == null && existing.sourceUrl() != null) throw bad("图片地址不能为空");
        StoredMedia row = repository.edit(id, revision, url, description.toString())
                .orElseThrow(() -> conflict(id));
        return view(row);
    }

    public void delete(long id, long revision) {
        active(id);
        if (!repository.delete(id, revision)) throw conflict(id);
    }


    public StoredMedia active(long id) {
        return repository.byId(id).filter(row -> row.deletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "媒体记录不存在"));
    }

    private ResponseStatusException conflict(long id) {
        active(id);
        return new ResponseStatusException(HttpStatus.CONFLICT, "记录已更新，请刷新后重试");
    }

    private View view(StoredMedia row) {
        JsonNode description = null;
        try { if (row.descriptionJson() != null) description = mapper.readTree(row.descriptionJson()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "媒体描述格式无效");
        }
        return new View(Long.toString(row.id()), row.fileId(), row.kind(), row.status(), description,
                row.sourceUrl(), row.revision(), row.createdAt(), row.updatedAt());
    }

    private String validateUrl(String url) {
        if (url == null || url.length() > 8192) throw bad("请输入有效的图片地址");
        try { MediaUrl.validateUrl(url); return url; }
        catch (IllegalArgumentException exception) { throw bad(exception.getMessage()); }
    }

    private void validateKind(String kind) {
        if (!"image".equals(kind) && !"meme".equals(kind)) throw bad("媒体类型必须为 image 或 meme");
    }

    private void validateDescription(String kind, JsonNode description) {
        if (description == null || !description.isObject()) throw bad("描述必须为对象");
        text(description, "content", 2048, true);
        var allowed = "meme".equals(kind) ? List.of("content", "text", "emotion", "memeMeaning", "confidence") : List.of("content");
        description.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw bad("描述包含未知字段"); });
        if ("meme".equals(kind)) {
            text(description, "text", 512, false);
            text(description, "emotion", 256, false);
            text(description, "memeMeaning", 1024, false);
            JsonNode confidence = description.get("confidence");
            if (confidence != null && (!confidence.isNumber() || !Double.isFinite(confidence.asDouble())
                    || confidence.asDouble() < 0 || confidence.asDouble() > 1)) throw bad("置信度必须为 0～1 的数字");
        }
    }

    private void text(JsonNode description, String name, int max, boolean required) {
        JsonNode value = description.get(name);
        if (value == null && !required) return;
        if (value == null || !value.isTextual() || value.asText().length() > max
                || (required && value.asText().isBlank())) throw bad("描述字段 " + name + " 为空或长度/类型无效");
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
