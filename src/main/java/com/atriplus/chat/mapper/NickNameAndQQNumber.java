package com.atriplus.chat.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NickNameAndQQNumber {

    /**
     * 根据群号和 QQ 号获取用户当前昵称。
     * @param groupId 群号
     * @param qqNumber QQ号
     * @return 用户当前昵称
     */
    @Select("""
            select nickname
            from qq_group_nickname
            where group_id = #{groupId}
              and qq_number = #{qqNumber}
            order by updated_at desc
            limit 1
            """)
    String getNickName(
            @Param("groupId") Long groupId,
            @Param("qqNumber") Long qqNumber
    );

    /**
     * 保存一个历史昵称；相同昵称再次出现时刷新时间，使其成为最新昵称。
     *
     * @return 实际插入或更新的行数
     */
    @Insert("""
            insert into qq_group_nickname (group_id, qq_number, nickname)
            values (#{groupId}, #{qqNumber}, #{nickName})
            on conflict (group_id, qq_number, nickname) do update
            set updated_at = current_timestamp
            """)
    int upsertNickName(
            @Param("groupId") Long groupId,
            @Param("qqNumber") Long qqNumber,
            @Param("nickName") String nickName
    );
}
