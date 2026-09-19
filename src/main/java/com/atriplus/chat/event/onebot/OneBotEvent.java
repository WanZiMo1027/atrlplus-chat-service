package com.atriplus.chat.event.onebot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** SnowLuma 上报的 OneBot v11 事件，只声明当前业务需要的字段。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OneBotEvent(
        @JsonProperty("post_type") String postType,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("self_id") Long selfId,
        @JsonProperty("message_id") JsonNode messageId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("group_id") Long groupId,
        List<OneBotMessageSegment> message,
        @JsonProperty("raw_message") String rawMessage,
        OneBotSender sender
) {
}
