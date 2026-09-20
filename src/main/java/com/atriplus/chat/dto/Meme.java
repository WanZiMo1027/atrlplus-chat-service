package com.atriplus.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** chat-service 内部使用的 QQ 表情定义。 */
public record Meme(
        @JsonProperty("id") String id,
        String name,
        List<String> tags,
        String url,
        String md5,
        boolean enabled
) {
    public Meme {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
