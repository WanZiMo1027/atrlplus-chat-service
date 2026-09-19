package com.atriplus.chat.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** SnowLuma 私聊消息请求。 */
public record SendPrivateMessageRequest(
        @JsonProperty("user_id") long userId,
        String message
) {
}
