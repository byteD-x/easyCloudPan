package com.easypan.mappers;

import com.easypan.entity.po.TenantInfo;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户信息数据库操作接口.
 */
@Mapper
public interface TenantInfoMapper extends BaseMapper<TenantInfo> {
}
