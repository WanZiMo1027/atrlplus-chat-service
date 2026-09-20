package com.atriplus.chat.client.dto;

/**
 * OneBot 出站消息段。
 *
 * 使用静态工厂方法构造不同类型的消息段，避免为每个单字段 data
 * 单独创建顶层 DTO，同时保留明确的 JSON 结构。
 */
public record SendMessageSegment(
        String type,
        Object data
) {

    public static SendMessageSegment reply(long messageId) {
        return new SendMessageSegment("reply", new ReplyData(messageId));
    }

    public static SendMessageSegment text(String text) {
        return new SendMessageSegment("text", new TextData(text));
    }

    /** OneBot reply 消息段数据。 */
    public record ReplyData(long id) {
    }

    /** OneBot text 消息段数据。 */
    public record TextData(String text) {
    }
}
