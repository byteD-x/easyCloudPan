package com.easypan.service;

import com.easypan.entity.dto.SessionShareDto;
import com.easypan.entity.po.FileShare;
import com.easypan.entity.query.FileShareQuery;
import com.easypan.entity.vo.PaginationResultVO;

import java.util.List;

/**
 * 分享信息业务接口.
 */
public interface FileShareService {

    /**
     * 根据条件查询列表.
     *
     * @param param 查询参数
     * @return 列表
     */
    List<FileShare> findListByParam(FileShareQuery param);

    /**
     * 根据条件查询数量.
     *
     * @param param 查询参数
     * @return 数量
     */
    Integer findCountByParam(FileShareQuery param);

    /**
     * 分页查询.
     *
     * @param param 查询参数
     * @return 分页结果
     */
    PaginationResultVO<FileShare> findListByPage(FileShareQuery param);

    /**
     * 新增.
     *
     * @param bean 实体对象
     * @return 影响行数
     */
    Integer add(FileShare bean);

    /**
     * 批量新增.
     *
     * @param listBean 实体列表
     * @return 影响行数
     */
    Integer addBatch(List<FileShare> listBean);

    /**
     * 批量新增/修改.
     *
     * @param listBean 实体列表
     * @return 影响行数
     */
    Integer addOrUpdateBatch(List<FileShare> listBean);

    /**
     * 根据分享ID查询对象.
     *
     * @param shareId 分享ID
     * @return 实体对象
     */
    FileShare getFileShareByShareId(String shareId);

    /**
     * 根据分享ID修改.
     *
     * @param bean    实体对象
     * @param shareId 分享ID
     * @return 影响行数
     */
    Integer updateFileShareByShareId(FileShare bean, String shareId);

    /**
     * 根据分享ID删除.
     *
     * @param shareId 分享ID
     * @return 影响行数
     */
    Integer deleteFileShareByShareId(String shareId);

    /**
     * 保存分享.
     *
     * @param share 分享对象
     */
    void saveShare(FileShare share);

    /**
     * 批量删除分享.
     *
     * @param shareIdArray 分享ID数组
     * @param userId       用户ID
     */
    void deleteFileShareBatch(String[] shareIdArray, String userId);

    /**
     * 校验分享码.
     *
     * @param shareId 分享ID
     * @param code    分享码
     * @return 分享会话信息
     */
    SessionShareDto checkShareCode(String shareId, String code);

    /**
     * 游标分页查询分享列表.
     *
     * @param userId   用户ID
     * @param cursor   游标
     * @param pageSize 每页数量
     * @return 游标分页结果
     */
    com.easypan.entity.query.CursorPage<FileShare> findShareListByCursor(String userId, String cursor,
            Integer pageSize);
}