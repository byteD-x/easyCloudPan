package com.easypan.service;

import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.query.UserInfoQuery;
import com.easypan.entity.vo.PaginationResultVO;

import java.util.List;

/**
 * 用户信息业务接口.
 */
public interface UserInfoService {

    /**
     * 根据条件查询列表.
     *
     * @param param 查询参数
     * @return 用户列表
     */
    List<UserInfo> findListByParam(UserInfoQuery param);

    /**
     * 根据条件查询数量.
     *
     * @param param 查询参数
     * @return 数量
     */
    Integer findCountByParam(UserInfoQuery param);

    /**
     * 分页查询.
     *
     * @param param 查询参数
     * @return 分页结果
     */
    PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param);

    /**
     * 新增.
     *
     * @param bean 用户信息
     * @return 影响行数
     */
    Integer add(UserInfo bean);

    /**
     * 批量新增.
     *
     * @param listBean 用户列表
     * @return 影响行数
     */
    Integer addBatch(List<UserInfo> listBean);

    /**
     * 批量新增/修改.
     *
     * @param listBean 用户列表
     * @return 影响行数
     */
    Integer addOrUpdateBatch(List<UserInfo> listBean);

    /**
     * 根据UserId查询对象.
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    UserInfo getUserInfoByUserId(String userId);

    /**
     * 根据UserId修改.
     *
     * @param bean 用户信息
     * @param userId 用户ID
     * @return 影响行数
     */
    Integer updateUserInfoByUserId(UserInfo bean, String userId);

    /** 使用当前密码编码策略更新用户密码。 */
    Integer updatePassword(String userId, String rawPassword);

    /**
     * 根据UserId删除.
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    Integer deleteUserInfoByUserId(String userId);

    /**
     * 根据Email查询对象.
     *
     * @param email 邮箱
     * @return 用户信息
     */
    UserInfo getUserInfoByEmail(String email);

    /**
     * 根据Email修改.
     *
     * @param bean 用户信息
     * @param email 邮箱
     * @return 影响行数
     */
    Integer updateUserInfoByEmail(UserInfo bean, String email);

    /**
     * 根据Email删除.
     *
     * @param email 邮箱
     * @return 影响行数
     */
    Integer deleteUserInfoByEmail(String email);

    /**
     * 根据NickName查询对象.
     *
     * @param nickName 昵称
     * @return 用户信息
     */
    UserInfo getUserInfoByNickName(String nickName);

    /**
     * 根据NickName修改.
     *
     * @param bean 用户信息
     * @param nickName 昵称
     * @return 影响行数
     */
    Integer updateUserInfoByNickName(UserInfo bean, String nickName);

    /**
     * 根据NickName删除.
     *
     * @param nickName 昵称
     * @return 影响行数
     */
    Integer deleteUserInfoByNickName(String nickName);

    /**
     * 根据QqOpenId查询对象.
     *
     * @param qqOpenId QQ OpenID
     * @return 用户信息
     */
    UserInfo getUserInfoByQqOpenId(String qqOpenId);

    /**
     * 根据QqOpenId修改.
     *
     * @param bean 用户信息
     * @param qqOpenId QQ OpenID
     * @return 影响行数
     */
    Integer updateUserInfoByQqOpenId(UserInfo bean, String qqOpenId);

    /**
     * 根据QqOpenId删除.
     *
     * @param qqOpenId QQ OpenID
     * @return 影响行数
     */
    Integer deleteUserInfoByQqOpenId(String qqOpenId);

    /**
     * 用户登录.
     *
     * @param email 邮箱
     * @param password 密码
     * @return 用户会话信息
     */
    SessionWebUserDto login(String email, String password);

    /**
     * 用户注册.
     *
     * @param email 邮箱
     * @param nickName 昵称
     * @param password 密码
     * @param emailCode 邮箱验证码
     */
    void register(String email, String nickName, String password, String emailCode);

    /**
     * 重置密码.
     *
     * @param email 邮箱
     * @param password 新密码
     * @param emailCode 邮箱验证码
     */
    void resetPwd(String email, String password, String emailCode);

    /**
     * 更新用户状态.
     *
     * @param userId 用户ID
     * @param status 状态
     */
    void updateUserStatus(String userId, Integer status);

    /**
     * QQ登录.
     *
     * @param code 授权码
     * @return 用户会话信息
     */
    SessionWebUserDto qqLogin(String code);

    /**
     * 更改用户空间（增量）.
     *
     * @param userId 用户ID
     * @param changeSpace 变更空间大小（MB），正数增加，负数减少
     */
    void changeUserSpace(String userId, Integer changeSpace);

    /**
     * 设置用户空间（绝对值）.
     *
     * @param userId 用户ID
     * @param totalSpaceMB 总空间大小（MB）
     */
    void setUserSpace(String userId, Long totalSpaceMB);
}
