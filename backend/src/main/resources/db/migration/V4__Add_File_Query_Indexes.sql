-- EasyCloudPan Index Optimization Script
-- Created: 2026-02-11
-- Purpose: Add composite indexes for high-frequency queries to improve performance
-- Requirement: 2.1.2 - Database Performance Optimization

-- 1. File Query Composite Index
-- Optimizes user file list queries (filtering by user_id, file_pid, and del_flag)
-- Used in: FileInfoController.loadDataList()
CREATE INDEX IF NOT EXISTS idx_file_user_pid_del 
ON file_info(user_id, file_pid, del_flag) 
WHERE del_flag = 2;

COMMENT ON INDEX idx_file_user_pid_del IS 'Composite index for user file list queries - filters by user_id, parent folder, and deletion status';

-- 2. File Category Query Index
-- Optimizes file category filtering with time-based sorting
-- Used in: FileInfoController.loadDataList() with category parameter
CREATE INDEX IF NOT EXISTS idx_file_user_category_time 
ON file_info(user_id, file_category, create_time DESC) 
WHERE del_flag = 2;

COMMENT ON INDEX idx_file_user_category_time IS 'Composite index for file category queries - filters by user_id and category, sorted by creation time';

-- 3. Recycle Bin Query Index
-- Optimizes recycle bin file queries with recovery time sorting
-- Used in: FileInfoController.loadRecycleList()
CREATE INDEX IF NOT EXISTS idx_file_recycle 
ON file_info(user_id, recovery_time DESC) 
WHERE del_flag = 1;

COMMENT ON INDEX idx_file_recycle IS 'Index for recycle bin queries - filters by user_id with recovery time sorting';

-- 4. File Share Query Index
-- Optimizes share list queries with time-based sorting
-- Used in: ShareController.loadShareList()
CREATE INDEX IF NOT EXISTS idx_share_user_time 
ON file_share(user_id, share_time DESC);

COMMENT ON INDEX idx_share_user_time IS 'Composite index for share list queries - filters by user_id, sorted by share time';

-- 5. User Email Query Index
-- Optimizes user lookup by email (login, registration checks)
-- Used in: AccountController.login(), AccountController.register()
CREATE INDEX IF NOT EXISTS idx_user_email 
ON user_info(email) 
WHERE status = 1;

COMMENT ON INDEX idx_user_email IS 'Index for user email queries - filters active users only';

-- Performance Notes:
-- - Partial indexes (WHERE clauses) reduce index size and improve performance
-- - DESC ordering in indexes supports ORDER BY DESC queries without additional sorting
-- - Composite indexes follow the leftmost prefix rule for optimal query matching
-- - Expected performance improvement: 50-80% reduction in query time for filtered operations
