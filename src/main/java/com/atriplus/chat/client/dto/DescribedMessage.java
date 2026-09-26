package com.atriplus.chat.client.dto;


import java.util.List;

/**
 * 描述消息
 * @param text 消息文本
 * @param images 图片项
 */
public record DescribedMessage(
        String text,
        List<ChatImageItem> images
) {}