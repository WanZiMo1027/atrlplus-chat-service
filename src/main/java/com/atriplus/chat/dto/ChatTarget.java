package com.atriplus.chat.dto;

/**
 * 一条会话对应的实际回复目标。
 *
 * @param selfId 当前 SnowLuma 登录账号
 * @param type 群聊或私聊
 * @param targetId 群号或对方 QQ 号
 */
public record ChatTarget(long selfId, ChatType type, long targetId) {
}
