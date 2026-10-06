-- V7__Performance_Optimization_Indexes.sql
-- 目的：补充数据库性能优化相关索引
-- 对应任务：
--   - 4.1.2 添加秒传查询索引 idx_file_md5_status_user
--   - 4.1.4 添加用户空间查询索引 idx_file_user_status_size
--   - 4.1.5 添加分享过期查询索引 idx_share_expire
-- 说明：全部使用 IF NOT EXISTS，保证脚本可重复执行

-- 1. 秒传查询优化索引
-- 典型使用场景：FileInfoServiceImpl.uploadFile() 中通过 file_md5 + status + user_id 查找可复用文件
CREATE INDEX IF NOT EXISTS idx_file_md5_status_user
ON file_info(file_md5, status, user_id)
WHERE status = 2 AND file_md5 IS NOT NULL;

COMMENT ON INDEX idx_file_md5_status_user IS 'Composite index for instant upload (秒传) by file_md5, status and user_id';

-- 2. 用户空间统计查询优化
-- 典型使用场景：统计某用户当前使用空间（按 del_flag 过滤）
CREATE INDEX IF NOT EXISTS idx_file_user_status_size
ON file_info(user_id, status, file_size)
WHERE del_flag = 0;

COMMENT ON INDEX idx_file_user_status_size IS 'Index to speed up user space aggregation by user_id, status and file_size';

-- 3. 文件分享过期查询优化
-- 典型使用场景：定时任务清理已过期分享、查询即将过期分享记录
CREATE INDEX IF NOT EXISTS idx_share_expire
ON file_share(expire_time, valid_type)
WHERE valid_type = 1;

COMMENT ON INDEX idx_share_expire IS 'Index for querying expiring shares by expire_time and valid_type';

