package com.atriplus.chat.dto;

/** 管理页面展示的群聊回复配置；数据库自增 ID 不参与业务接口。 */
public record GroupReplyConfig(long groupId, String groupName, boolean enabled, boolean receiveEnabled) {
    public GroupReplyConfig(long groupId, String groupName, boolean enabled) {
        this(groupId, groupName, enabled, true);
    }
}
