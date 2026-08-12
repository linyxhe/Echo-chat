package com.echo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echo.pojo.ChatGroupMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatGroupMemberMapper extends BaseMapper<ChatGroupMember> {
}
