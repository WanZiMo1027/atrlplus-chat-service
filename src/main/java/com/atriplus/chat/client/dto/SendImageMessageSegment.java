package com.atriplus.chat.client.dto;

/** OneBot 图片消息段。 */
public record SendImageMessageSegment(
        String type,
        SendImageMessageData data
) {
}
