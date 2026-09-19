package com.atriplus.chat.event.onebot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/** OneBot v11 消息段。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OneBotMessageSegment(String type, JsonNode data) {
}
