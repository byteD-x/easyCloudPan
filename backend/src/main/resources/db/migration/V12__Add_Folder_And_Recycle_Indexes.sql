-- V12__Add_Folder_And_Recycle_Indexes.sql
-- 目的：补充回收站恢复和文件夹递归查询缺失的索引
-- 对应任务：T6 - 深度优化

-- 1. 回收站恢复递归查询索引
-- 典型使用场景：FileInfoServiceImpl.recoverFileBatch() -> selectDescendantFolderIds
-- 需要按 user_id + file_pid 查找 del_flag = 0 (DELETE) 的已删除子文件
CREATE INDEX IF NOT EXISTS idx_file_user_pid_del_0
ON file_info(user_id, file_pid, del_flag)
WHERE del_flag = 0;

COMMENT ON INDEX idx_file_user_pid_del_0 IS 'Index for recursive recovery queries - finding deleted sub-files';

-- 2. 回收站内层级查询索引
-- 典型使用场景：在回收站中浏览文件夹内容
-- 需要按 user_id + file_pid 查找 del_flag = 1 (RECYCLE) 的文件
CREATE INDEX IF NOT EXISTS idx_file_user_pid_del_1
ON file_info(user_id, file_pid, del_flag)
WHERE del_flag = 1;

COMMENT ON INDEX idx_file_user_pid_del_1 IS 'Index for browsing recycle bin folders';
