package com.atriplus.chat.dto;

import java.time.OffsetDateTime;

/** 接收媒体描述库的内部记录，保留版本和删除标记供并发校验。 */
public record StoredMedia(long id, String fileId, String kind, String status, String descriptionJson,
                          String sourceUrl,
                          long revision, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                          OffsetDateTime deletedAt) {
}
