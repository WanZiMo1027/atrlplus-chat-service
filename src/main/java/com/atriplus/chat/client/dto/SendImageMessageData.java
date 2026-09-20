package com.atriplus.chat.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** OneBot 图片消息段的数据。 */
public record SendImageMessageData(
        String file,
        @JsonProperty("sub_type") int subType,
        String summary
) {
}
