package com.atriplus.chat.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NickNameAndQQNumber {

    /**
     * 根据QQ号获取用户昵称号
     * @param qqNumber QQ号
     * @return 用户昵称列表
     */
    @Select("select nick_name from qq_group_nickname where qq_number = #{qqNumber}")
    List<String> getNickName(@Param("qqNumber") Long qqNumber);
}
