package com.atriplus.chat.dto;

import com.atriplus.chat.client.dto.ChatImageItem;

/**
 * 图片在当前消息中的文字与可选原图。
 * @param text 图片在当前消息中的文字
 * @param image 图片项
 */
public record MediaOutcome(
        String text,
        ChatImageItem image
) {
}
