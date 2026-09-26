package com.atriplus.chat.event.onebot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** OneBot 消息发送者信息。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OneBotSender(
        @JsonProperty("user_id") Long userId,
        String nickname
) {
}
