package com.echo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echo.pojo.Post;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostMapper extends BaseMapper<Post> {
}
