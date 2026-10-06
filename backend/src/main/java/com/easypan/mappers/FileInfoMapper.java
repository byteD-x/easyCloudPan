package com.easypan.mappers;

import com.easypan.entity.po.FileInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import com.mybatisflex.core.BaseMapper;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息数据库操作接口.
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {

    @Insert("<script>"
            + "<foreach collection='list' item='item' separator=';'>"
            + "INSERT INTO file_info (file_id, user_id, tenant_id, file_md5, file_pid, file_name, file_path, "
            + "file_size, file_cover, file_category, file_type, folder_type, status, del_flag, "
            + "recovery_time, create_time, last_update_time) "
            + "VALUES (#{item.fileId}, #{item.userId}, COALESCE(#{item.tenantId}, 'default'), #{item.fileMd5}, #{item.filePid}, "
            + "#{item.fileName}, #{item.filePath}, #{item.fileSize}, #{item.fileCover}, "
            + "#{item.fileCategory}, #{item.fileType}, #{item.folderType}, #{item.status}, "
            + "#{item.delFlag}, #{item.recoveryTime}, #{item.createTime}, #{item.lastUpdateTime}) "
            + "ON CONFLICT (file_id) DO UPDATE SET "
            + "tenant_id = EXCLUDED.tenant_id, "
            + "file_pid = EXCLUDED.file_pid, file_name = EXCLUDED.file_name, file_path = EXCLUDED.file_path, "
            + "file_size = EXCLUDED.file_size, file_cover = EXCLUDED.file_cover, file_category = EXCLUDED.file_category, "
            + "file_type = EXCLUDED.file_type, folder_type = EXCLUDED.folder_type, status = EXCLUDED.status, "
            + "del_flag = EXCLUDED.del_flag, recovery_time = EXCLUDED.recovery_time, last_update_time = EXCLUDED.last_update_time"
            + "</foreach>"
            + "</script>")
    int insertOrUpdateBatch(@Param("list") List<FileInfo> list);

    @Update("UPDATE file_info "
            + "SET status = #{bean.status}, "
            + "file_size = #{bean.fileSize}, "
            + "file_md5 = COALESCE(#{bean.fileMd5}, file_md5), "
            + "file_cover = #{bean.fileCover}, "
            + "recovery_time = COALESCE(#{bean.recoveryTime}, recovery_time), "
            + "last_update_time = CURRENT_TIMESTAMP "
            + "WHERE file_id = #{fileId} AND user_id = #{userId} AND status = #{oldStatus}")
    void updateFileStatusWithOldStatus(@Param("fileId") String fileId, @Param("userId") String userId,
            @Param("bean") FileInfo t, @Param("oldStatus") Integer oldStatus);

    @Update("<script>"
            + "UPDATE file_info SET del_flag = #{bean.delFlag}, recovery_time = #{bean.recoveryTime} "
            + "WHERE user_id = #{userId} AND del_flag = #{oldDelFlag} "
            + "<if test='filePidList != null and filePidList.size() > 0'>"
            + "AND file_pid IN <foreach collection='filePidList' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</if>"
            + "<if test='fileIdList != null and fileIdList.size() > 0'>"
            + "AND file_id IN <foreach collection='fileIdList' item='fid' open='(' separator=',' close=')'>#{fid}</foreach>"
            + "</if>"
            + "</script>")
    void updateFileDelFlagBatch(@Param("bean") FileInfo fileInfo,
            @Param("userId") String userId,
            @Param("filePidList") List<String> filePidList,
            @Param("fileIdList") List<String> fileIdList,
            @Param("oldDelFlag") Integer oldDelFlag);

    @Delete("<script>"
            + "DELETE FROM file_info WHERE user_id = #{userId} "
            + "<if test='oldDelFlag != null'>AND del_flag = #{oldDelFlag} </if>"
            + "<if test='filePidList != null and filePidList.size() > 0'>"
            + "AND file_pid IN <foreach collection='filePidList' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>"
            + "</if>"
            + "<if test='fileIdList != null and fileIdList.size() > 0'>"
            + "AND file_id IN <foreach collection='fileIdList' item='fid' open='(' separator=',' close=')'>#{fid}</foreach>"
            + "</if>"
            + "</script>")
    void delFileBatch(@Param("userId") String userId,
            @Param("filePidList") List<String> filePidList,
            @Param("fileIdList") List<String> fileIdList,
            @Param("oldDelFlag") Integer oldDelFlag);

    @Select("SELECT COALESCE(SUM(file_size), 0) FROM file_info WHERE user_id = #{userId} AND del_flag != 0")
    Long selectUseSpace(@Param("userId") String userId);

    @Select("SELECT COALESCE(SUM(file_size), 0) FROM file_info "
            + "WHERE tenant_id = #{tenantId} AND del_flag != 0")
    Long selectUseSpaceByTenantId(@Param("tenantId") String tenantId);

    @Delete("DELETE FROM file_info WHERE user_id = #{userId}")
    void deleteFileByUserId(@Param("userId") String userId);

    @Select("<script>"
            + "WITH RECURSIVE descendants AS ("
            + "SELECT file_id FROM file_info WHERE user_id = #{userId} "
            + "<if test='delFlag != null'>AND del_flag = #{delFlag} </if>"
            + "AND file_id IN <foreach collection='fileIdList' item='fid' open='(' separator=',' close=')'>#{fid}</foreach> "
            + "UNION ALL "
            + "SELECT f.file_id FROM file_info f "
            + "INNER JOIN descendants d ON f.file_pid = d.file_id "
            + "WHERE f.user_id = #{userId} "
            + "<if test='delFlag != null'>AND f.del_flag = #{delFlag} </if>"
            + ") SELECT file_id FROM descendants"
            + "</script>")
    List<String> selectDescendantFolderIds(@Param("fileIdList") List<String> fileIdList,
            @Param("userId") String userId,
            @Param("delFlag") Integer delFlag);

    @Select("<script>"
            + "WITH RECURSIVE descendants AS ("
            + "SELECT * FROM file_info WHERE user_id = #{userId} "
            + "<if test='delFlag != null'>AND del_flag = #{delFlag} </if>"
            + "AND file_id IN <foreach collection='fileIdList' item='fid' open='(' separator=',' close=')'>#{fid}</foreach> "
            + "UNION ALL "
            + "SELECT f.* FROM file_info f "
            + "INNER JOIN descendants d ON f.file_pid = d.file_id "
            + "WHERE f.user_id = #{userId} "
            + "<if test='delFlag != null'>AND f.del_flag = #{delFlag} </if>"
            + ") SELECT * FROM descendants"
            + "</script>")
    List<FileInfo> selectDescendantFiles(@Param("fileIdList") List<String> fileIdList,
            @Param("userId") String userId,
            @Param("delFlag") Integer delFlag);

    @Select("SELECT * FROM file_info WHERE user_id = #{userId} "
            + "AND (create_time, file_id) < (#{cursorTime}, #{cursorId}) "
            + "ORDER BY create_time DESC, file_id DESC LIMIT #{pageSize}")
    List<FileInfo> selectByCursorPagination(
            @Param("userId") String userId,
            @Param("cursorTime") Date cursorTime,
            @Param("cursorId") String cursorId,
            @Param("pageSize") int pageSize);

    /**
     * 游标分页查询（支持目录过滤）.
     * 用于文件列表的高性能分页，避免 OFFSET 深分页问题.
     */
    @Select("<script>"
            + "SELECT * FROM file_info WHERE user_id = #{userId} "
            + "<if test='filePid != null'>AND file_pid = #{filePid} </if>"
            + "<if test='delFlag != null'>AND del_flag = #{delFlag} </if>"
            + "<if test='fileCategory != null'>AND file_category = #{fileCategory} </if>"
            + "<if test='folderType != null'>AND folder_type = #{folderType} </if>"
            + "<if test='cursorTime != null and cursorId != null'>"
            + "AND (create_time, file_id) &lt; (#{cursorTime}, #{cursorId}) "
            + "</if>"
            + "ORDER BY create_time DESC, file_id DESC LIMIT #{pageSize}"
            + "</script>")
    List<FileInfo> selectByCursorWithFilter(
            @Param("userId") String userId,
            @Param("filePid") String filePid,
            @Param("delFlag") Integer delFlag,
            @Param("fileCategory") Integer fileCategory,
            @Param("folderType") Integer folderType,
            @Param("cursorTime") Date cursorTime,
            @Param("cursorId") String cursorId,
            @Param("pageSize") int pageSize);

    @Select("SELECT file_id, file_size, file_path, file_md5, user_id, tenant_id, file_cover "
            + "FROM file_info "
            + "WHERE file_md5 = #{fileMd5} AND status = #{status} "
            + "LIMIT 1")
    FileInfo selectOneByMd5AndStatus(@Param("fileMd5") String fileMd5,
            @Param("status") Integer status);

    /**
     * 查询仍被其他元数据记录引用的物理对象数量。
     */
    @Select("SELECT COUNT(1) FROM file_info WHERE file_path = #{filePath} OR file_cover = #{filePath}")
    long countReferencesByStoredPath(@Param("filePath") String filePath);

    /** 查询引用某个物理目录前缀的元数据数量。 */
    @Select("SELECT COUNT(1) FROM file_info "
            + "WHERE file_path LIKE #{pathPrefix} || '%' OR file_cover LIKE #{pathPrefix} || '%'")
    long countReferencesByStoredPathPrefix(@Param("pathPrefix") String pathPrefix);

    @Select("SELECT DISTINCT file_md5 FROM file_info WHERE file_md5 IS NOT NULL")
    List<String> selectAllMd5();

    @Update("<script>"
            + "<foreach collection='list' item='item' separator=';'>"
            + "UPDATE file_info SET "
            + "file_pid = #{item.filePid}, "
            + "file_name = #{item.fileName}, "
            + "last_update_time = #{item.lastUpdateTime} "
            + "WHERE file_id = #{item.fileId} AND user_id = #{item.userId}"
            + "</foreach>"
            + "</script>")
    void updateBatch(@Param("list") List<FileInfo> list);

    @Select("SELECT * FROM file_info WHERE file_id = #{fileId} AND user_id = #{userId}")
    FileInfo selectByFileIdAndUserId(@Param("fileId") String fileId, @Param("userId") String userId);

}
