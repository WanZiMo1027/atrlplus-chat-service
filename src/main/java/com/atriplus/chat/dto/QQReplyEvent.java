package com.atriplus.chat.dto;

/** Python 回复消费者读取的 JSON 协议，conversationId 当前固定为 qq-default。 */
public record QQReplyEvent(String conversationId, boolean shouldReply, String reply) {
}
