package com.easypan.service;

import com.easypan.entity.po.EmailCode;
import com.easypan.entity.query.EmailCodeQuery;
import com.easypan.entity.vo.PaginationResultVO;

import java.util.List;

/**
 * 邮箱验证码业务接口.
 */
public interface EmailCodeService {

    /**
     * 根据条件查询列表.
     *
     * @param param 查询参数
     * @return 列表
     */
    List<EmailCode> findListByParam(EmailCodeQuery param);

    /**
     * 根据条件查询数量.
     *
     * @param param 查询参数
     * @return 数量
     */
    Integer findCountByParam(EmailCodeQuery param);

    /**
     * 分页查询.
     *
     * @param param 查询参数
     * @return 分页结果
     */
    PaginationResultVO<EmailCode> findListByPage(EmailCodeQuery param);

    /**
     * 新增.
     *
     * @param bean 实体对象
     * @return 影响行数
     */
    Integer add(EmailCode bean);

    /**
     * 批量新增.
     *
     * @param listBean 实体列表
     * @return 影响行数
     */
    Integer addBatch(List<EmailCode> listBean);

    /**
     * 批量新增/修改.
     *
     * @param listBean 实体列表
     * @return 影响行数
     */
    Integer addOrUpdateBatch(List<EmailCode> listBean);

    /**
     * 根据邮箱和验证码查询对象.
     *
     * @param email 邮箱
     * @param code 验证码
     * @return 实体对象
     */
    EmailCode getEmailCodeByEmailAndCode(String email, String code);

    /**
     * 根据邮箱和验证码修改.
     *
     * @param bean 实体对象
     * @param email 邮箱
     * @param code 验证码
     * @return 影响行数
     */
    Integer updateEmailCodeByEmailAndCode(EmailCode bean, String email, String code);

    /**
     * 根据邮箱和验证码删除.
     *
     * @param email 邮箱
     * @param code 验证码
     * @return 影响行数
     */
    Integer deleteEmailCodeByEmailAndCode(String email, String code);

    /**
     * 发送邮箱验证码.
     *
     * @param toEmail 目标邮箱
     * @param type 类型
     */
    void sendEmailCode(String toEmail, Integer type);

    /**
     * 校验验证码.
     *
     * @param email 邮箱
     * @param code 验证码
     */
    void checkCode(String email, String code);
}