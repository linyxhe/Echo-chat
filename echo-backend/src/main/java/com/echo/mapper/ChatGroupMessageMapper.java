package com.echo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echo.pojo.ChatGroupMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatGroupMessageMapper extends BaseMapper<ChatGroupMessage> {
}
