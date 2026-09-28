package com.atriplus.chat.mapper;

import com.atriplus.chat.dto.GroupReplyConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupReplyConfigMapper {
    void upsertName(@Param("groupId") long groupId, @Param("groupName") String groupName);

    GroupReplyConfig findByGroupId(@Param("groupId") long groupId);

    List<GroupReplyConfig> listAll();

    int updateEnabled(@Param("groupId") long groupId, @Param("enabled") boolean enabled);
}
