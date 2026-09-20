package com.atriplus.chat.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** SnowLuma 群消息请求。 */
public record SendGroupMessageRequest(
        @JsonProperty("group_id") long groupId,
        Object message
) {
}
