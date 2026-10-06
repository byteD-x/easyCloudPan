package com.easypan.mappers;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.mybatisflex.core.BaseMapper;
import com.easypan.entity.po.UserInfo;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/**
 * 用户信息数据库操作接口.
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    Integer insertOrUpdateBatch(@Param("list") List<UserInfo> list);

    @Update("UPDATE user_info SET use_space = #{useSpace} WHERE user_id = #{userId}")
    Integer updateUseSpace(@Param("userId") String userId, @Param("useSpace") Long useSpace);

    @Update("UPDATE user_info SET total_space = #{totalSpace} WHERE user_id = #{userId}")
    Integer updateTotalSpace(@Param("userId") String userId, @Param("totalSpace") Long totalSpace);

    @Update("UPDATE user_info SET use_space = #{useSpace}, total_space = #{totalSpace} WHERE user_id = #{userId}")
    Integer updateUserSpace(@Param("userId") String userId, @Param("useSpace") Long useSpace,
            @Param("totalSpace") Long totalSpace);

    /** 原子增加用户已用空间，避免并发上传丢失更新或将总空间覆盖为 null。 */
    @Update("UPDATE user_info SET use_space = COALESCE(use_space, 0) + #{deltaSize} WHERE user_id = #{userId}")
    Integer incrementUseSpace(@Param("userId") String userId, @Param("deltaSize") Long deltaSize);

    @Select("SELECT * FROM user_info ORDER BY last_login_time DESC LIMIT #{limit}")
    List<UserInfo> selectActiveUsers(@Param("limit") int limit);

    @Select("SELECT COUNT(1) FROM user_info WHERE tenant_id = #{tenantId}")
    long countByTenantId(@Param("tenantId") String tenantId);

}
