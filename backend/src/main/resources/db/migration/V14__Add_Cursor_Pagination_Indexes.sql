-- V14__Add_Cursor_Pagination_Indexes.sql
-- Purpose: optimize cursor pagination queries for file and share modules

-- 1) File cursor pagination (without extra filters)
CREATE INDEX IF NOT EXISTS idx_file_cursor_user_create_id
ON file_info(user_id, create_time DESC, file_id DESC);

COMMENT ON INDEX idx_file_cursor_user_create_id IS
'Index for file cursor pagination by user and (create_time, file_id)';

-- 2) File cursor pagination with folder + delete flag filters
CREATE INDEX IF NOT EXISTS idx_file_cursor_user_pid_del_create_id
ON file_info(user_id, file_pid, del_flag, create_time DESC, file_id DESC);

COMMENT ON INDEX idx_file_cursor_user_pid_del_create_id IS
'Index for filtered file cursor pagination by user/file_pid/del_flag and cursor columns';

-- 3) File cursor pagination with category filter
CREATE INDEX IF NOT EXISTS idx_file_cursor_user_pid_del_category_create_id
ON file_info(user_id, file_pid, del_flag, file_category, create_time DESC, file_id DESC);

COMMENT ON INDEX idx_file_cursor_user_pid_del_category_create_id IS
'Index for filtered file cursor pagination with category condition';

-- 4) Share cursor pagination
CREATE INDEX IF NOT EXISTS idx_share_cursor_user_time_id
ON file_share(user_id, share_time DESC, share_id DESC);

COMMENT ON INDEX idx_share_cursor_user_time_id IS
'Index for share cursor pagination by user and (share_time, share_id)';
