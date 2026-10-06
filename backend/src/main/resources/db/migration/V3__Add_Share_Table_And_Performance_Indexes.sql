-- Add File Share Table and Performance Indexes
-- Created: 2026-02-11
-- Purpose: Support share functionality and optimize query performance

-- 1. Create File Share Table
CREATE TABLE IF NOT EXISTS file_share (
    share_id VARCHAR(20) PRIMARY KEY,
    file_id VARCHAR(20) NOT NULL REFERENCES file_info(file_id) ON DELETE CASCADE,
    user_id VARCHAR(20) NOT NULL REFERENCES user_info(user_id) ON DELETE CASCADE,
    valid_type SMALLINT DEFAULT 0, -- 0:1day, 1:7days, 2:30days, 3:forever
    expire_time TIMESTAMP,
    share_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    code VARCHAR(10), -- Share code (optional)
    show_count INT DEFAULT 0, -- View count
    file_name VARCHAR(255), -- File name at time of sharing
    folder_type SMALLINT DEFAULT 0, -- 0:file, 1:folder
    file_category SMALLINT, -- 1:video, 2:audio, 3:image, 4:document, 5:other
    file_type SMALLINT, -- File type code
    file_cover VARCHAR(500), -- File cover/thumbnail
    status SMALLINT DEFAULT 0 -- 0:Valid, 1:Expired, 2:Cancelled
);

-- 2. Performance Indexes for Share Table
-- Query shares by user
CREATE INDEX IF NOT EXISTS idx_share_user_status 
    ON file_share (user_id, status, share_time DESC);

-- Query shares by file
CREATE INDEX IF NOT EXISTS idx_share_file 
    ON file_share (file_id);

-- Query valid shares by expire time
CREATE INDEX IF NOT EXISTS idx_share_status_expire 
    ON file_share (status, expire_time);

-- 3. Additional Performance Indexes for File Operations
-- Optimize batch operations (copy/move/delete)
CREATE INDEX IF NOT EXISTS idx_file_info_user_status_del 
    ON file_info (user_id, status, del_flag);

-- Optimize file name search within folder
CREATE INDEX IF NOT EXISTS idx_file_info_user_pid_name 
    ON file_info (user_id, file_pid, file_name);

-- Optimize space calculation queries
CREATE INDEX IF NOT EXISTS idx_file_info_user_del_size 
    ON file_info (user_id, del_flag, file_size);

-- 4. Add Comments for Documentation
COMMENT ON TABLE file_share IS 'File sharing records';
COMMENT ON COLUMN file_share.valid_type IS '0:1day, 1:7days, 2:30days, 3:forever';
COMMENT ON COLUMN file_share.status IS '0:Valid, 1:Expired, 2:Cancelled';
COMMENT ON COLUMN file_info.status IS '0:Transcoding, 1:Failed, 2:Success, 3:RecycleBin';
COMMENT ON COLUMN file_info.del_flag IS '0:Normal, 1:Recycle, 2:Deleted';
