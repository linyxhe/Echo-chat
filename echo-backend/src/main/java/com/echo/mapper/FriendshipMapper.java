package com.echo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echo.pojo.Friendship;
import org.apache.ibatis.annotations.Mapper;

/**
 * 好友关系Mapper接口
 */
@Mapper
public interface FriendshipMapper extends BaseMapper<Friendship> {
}
