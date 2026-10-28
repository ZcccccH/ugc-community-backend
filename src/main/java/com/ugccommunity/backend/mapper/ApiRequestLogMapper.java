package com.ugccommunity.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ugccommunity.backend.entity.ApiRequestLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiRequestLogMapper extends BaseMapper<ApiRequestLog> {
}
