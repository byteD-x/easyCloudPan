package com.easypan.utils;

import com.easypan.entity.po.EmailCode;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.po.FileShare;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.query.BaseParam;
import com.easypan.entity.query.FileInfoQuery;
import com.easypan.entity.query.FileShareQuery;
import com.easypan.entity.query.UserInfoQuery;
import com.easypan.entity.query.EmailCodeQuery;
import com.easypan.entity.query.SimplePage;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;

/**
 * 查询条件构建器，用于构建 MyBatis-Flex 的 QueryWrapper.
 */
public final class QueryWrapperBuilder {

    private QueryWrapperBuilder() {
    }

    /**
     * 构建 FileInfo 查询条件.
     *
     * @param query 查询参数
     * @return QueryWrapper
     */
    public static QueryWrapper build(FileInfoQuery query) {
        return build(query, true);
    }

    /**
     * 构建 FileInfo 查询条件.
     *
     * @param query          查询参数
     * @param applyOrderBy   是否应用排序
     * @return QueryWrapper
     */
    public static QueryWrapper build(FileInfoQuery query, boolean applyOrderBy) {
        QueryWrapper qw = QueryWrapper.create();
        
        if (query == null) {
            return qw;
        }

        if (StringUtils.isNotEmpty(query.getFileId())) {
            qw.eq(FileInfo::getFileId, query.getFileId());
        }
        if (StringUtils.isNotEmpty(query.getFileIdFuzzy())) {
            qw.like(FileInfo::getFileId, query.getFileIdFuzzy());
        }
        if (StringUtils.isNotEmpty(query.getUserId())) {
            qw.eq(FileInfo::getUserId, query.getUserId());
        }
        if (StringUtils.isNotEmpty(query.getUserIdFuzzy())) {
            qw.like(FileInfo::getUserId, query.getUserIdFuzzy());
        }
        if (StringUtils.isNotEmpty(query.getFileMd5())) {
            qw.eq(FileInfo::getFileMd5, query.getFileMd5());
        }
        if (StringUtils.isNotEmpty(query.getFileMd5Fuzzy())) {
            qw.like(FileInfo::getFileMd5, query.getFileMd5Fuzzy());
        }
        if (StringUtils.isNotEmpty(query.getFilePid())) {
            qw.eq(FileInfo::getFilePid, query.getFilePid());
        }
        if (StringUtils.isNotEmpty(query.getFilePidFuzzy())) {
            qw.like(FileInfo::getFilePid, query.getFilePidFuzzy());
        }
        if (query.getFileSize() != null) {
            qw.eq(FileInfo::getFileSize, query.getFileSize());
        }
        if (StringUtils.isNotEmpty(query.getFileName())) {
            qw.eq(FileInfo::getFileName, query.getFileName());
        }
        if (StringUtils.isNotEmpty(query.getFileNameFuzzy())) {
            qw.like(FileInfo::getFileName, query.getFileNameFuzzy());
        }
        if (StringUtils.isNotEmpty(query.getFilePath())) {
            qw.eq(FileInfo::getFilePath, query.getFilePath());
        }
        if (StringUtils.isNotEmpty(query.getFilePathFuzzy())) {
            qw.like(FileInfo::getFilePath, query.getFilePathFuzzy());
        }
        if (query.getFolderType() != null) {
            qw.eq(FileInfo::getFolderType, query.getFolderType());
        }
        if (query.getFileCategory() != null) {
            qw.eq(FileInfo::getFileCategory, query.getFileCategory());
        }
        if (query.getFileType() != null) {
            qw.eq(FileInfo::getFileType, query.getFileType());
        }
        if (query.getStatus() != null) {
            qw.eq(FileInfo::getStatus, query.getStatus());
        }
        if (query.getDelFlag() != null) {
            qw.eq(FileInfo::getDelFlag, query.getDelFlag());
        }
        if (query.getFileIdArray() != null && query.getFileIdArray().length > 0) {
            qw.in(FileInfo::getFileId, (Object[]) query.getFileIdArray());
        }
        if (query.getFilePidArray() != null && query.getFilePidArray().length > 0) {
            qw.in(FileInfo::getFilePid, (Object[]) query.getFilePidArray());
        }
        if (query.getExcludeFileIdArray() != null && query.getExcludeFileIdArray().length > 0) {
            qw.notIn(FileInfo::getFileId, (Object[]) query.getExcludeFileIdArray());
        }
        if (Boolean.TRUE.equals(query.getQueryExpire())) {
            qw.isNotNull(FileInfo::getRecoveryTime);
            qw.le(FileInfo::getRecoveryTime, new Date());
        }

        applyPagination(qw, query);
        if (applyOrderBy) {
            applyOrderBy(qw, query);
        }
        
        return qw;
    }

    /**
     * 构建 UserInfo 查询条件.
     *
     * @param query 查询参数
     * @return QueryWrapper
     */
    public static QueryWrapper build(UserInfoQuery query) {
        return build(query, true);
    }

    /**
     * 构建 UserInfo 查询条件.
     *
     * @param query          查询参数
     * @param applyOrderBy   是否应用排序
     * @return QueryWrapper
     */
    public static QueryWrapper build(UserInfoQuery query, boolean applyOrderBy) {
        QueryWrapper qw = QueryWrapper.create();
        
        if (query == null) {
            return qw;
        }

        if (StringUtils.isNotEmpty(query.getUserId())) {
            qw.eq(UserInfo::getUserId, query.getUserId());
        }
        if (StringUtils.isNotEmpty(query.getUserIdFuzzy())) {
            qw.like(UserInfo::getUserId, query.getUserIdFuzzy());
        }
        if (StringUtils.isNotEmpty(query.getEmail())) {
            qw.eq(UserInfo::getEmail, query.getEmail());
        }
        if (StringUtils.isNotEmpty(query.getEmailFuzzy())) {
            qw.like(UserInfo::getEmail, query.getEmailFuzzy());
        }
        if (StringUtils.isNotEmpty(query.getNickName())) {
            qw.eq(UserInfo::getNickName, query.getNickName());
        }
        if (StringUtils.isNotEmpty(query.getNickNameFuzzy())) {
            qw.like(UserInfo::getNickName, query.getNickNameFuzzy());
        }
        if (query.getStatus() != null) {
            qw.eq(UserInfo::getStatus, query.getStatus());
        }

        applyPagination(qw, query);
        if (applyOrderBy) {
            applyOrderBy(qw, query);
        }
        
        return qw;
    }

    /**
     * 构建 FileShare 查询条件.
     *
     * @param query 查询参数
     * @return QueryWrapper
     */
    public static QueryWrapper build(FileShareQuery query) {
        return build(query, true);
    }

    /**
     * 构建 FileShare 查询条件.
     *
     * @param query          查询参数
     * @param applyOrderBy   是否应用排序
     * @return QueryWrapper
     */
    public static QueryWrapper build(FileShareQuery query, boolean applyOrderBy) {
        QueryWrapper qw = QueryWrapper.create();
        
        if (query == null) {
            return qw;
        }

        if (StringUtils.isNotEmpty(query.getShareId())) {
            qw.eq(FileShare::getShareId, query.getShareId());
        }
        if (StringUtils.isNotEmpty(query.getUserId())) {
            qw.eq(FileShare::getUserId, query.getUserId());
        }
        if (StringUtils.isNotEmpty(query.getFileId())) {
            qw.eq(FileShare::getFileId, query.getFileId());
        }

        applyPagination(qw, query);
        if (applyOrderBy) {
            applyOrderBy(qw, query);
        }
        
        return qw;
    }

    /**
     * 构建 EmailCode 查询条件.
     *
     * @param query 查询参数
     * @return QueryWrapper
     */
    public static QueryWrapper build(EmailCodeQuery query) {
        QueryWrapper qw = QueryWrapper.create();
        
        if (query == null) {
            return qw;
        }

        if (StringUtils.isNotEmpty(query.getEmail())) {
            qw.eq(EmailCode::getEmail, query.getEmail());
        }
        if (StringUtils.isNotEmpty(query.getCode())) {
            qw.eq(EmailCode::getCode, query.getCode());
        }

        applyPagination(qw, query);
        applyOrderBy(qw, query);
        
        return qw;
    }

    private static void applyPagination(QueryWrapper qw, BaseParam param) {
        if (param == null) {
            return;
        }
        SimplePage page = param.getSimplePage();
        if (page != null) {
            Integer start = page.getStart();
            Integer pageSize = page.getPageSize();
            if (start != null && pageSize != null) {
                qw.limit(start, pageSize);
            }
        }
    }

    private static void applyOrderBy(QueryWrapper qw, BaseParam param) {
        if (param == null || StringUtils.isEmpty(param.getOrderBy())) {
            return;
        }
        String orderBy = param.getOrderBy().trim();
        if (orderBy.toLowerCase().contains("desc")) {
            String field = orderBy.replace("desc", "").replace("DESC", "").trim();
            qw.orderBy(field, false);
        } else if (orderBy.toLowerCase().contains("asc")) {
            String field = orderBy.replace("asc", "").replace("ASC", "").trim();
            qw.orderBy(field, true);
        }
    }
}
