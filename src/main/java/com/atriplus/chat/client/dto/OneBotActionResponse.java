package com.atriplus.chat.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** SnowLuma OneBot 动作的标准响应信封。 */
public record OneBotActionResponse(
        String status,
        int retcode,
        JsonNode data,
        String message,
        String wording
) {
}
