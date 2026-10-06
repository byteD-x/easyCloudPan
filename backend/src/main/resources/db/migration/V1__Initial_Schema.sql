-- EasyCloudPan PostgreSQL Initialization Script
-- Created: 2026-02-11

-- 1. Create Tables

-- User Info Table
CREATE TABLE IF NOT EXISTS user_info (
    user_id VARCHAR(20) PRIMARY KEY,
    nick_name VARCHAR(20) NOT NULL,
    email VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL, -- Increased length for BCrypt
    avatar VARCHAR(150),
    join_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_time TIMESTAMP,
    status SMALLINT DEFAULT 1, -- 0:Disabled, 1:Enabled
    use_space BIGINT DEFAULT 0,
    total_space BIGINT DEFAULT 5368709120, -- 5GB default
    is_admin BOOLEAN DEFAULT FALSE
);

-- File Info Table
CREATE TABLE IF NOT EXISTS file_info (
    file_id VARCHAR(20) PRIMARY KEY,
    user_id VARCHAR(20) NOT NULL REFERENCES user_info(user_id) ON DELETE CASCADE,
    file_md5 VARCHAR(32),
    file_pid VARCHAR(20), -- Parent folder ID
    file_size BIGINT,
    file_name VARCHAR(200) NOT NULL,
    file_cover VARCHAR(100),
    file_path VARCHAR(200), -- S3 key
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    folder_type SMALLINT DEFAULT 0, -- 0:File, 1:Folder
    file_category SMALLINT, -- 1:Video, 2:Audio, 3:Image, 4:Doc, 5:Other
    file_type SMALLINT,
    status SMALLINT DEFAULT 2, -- 0:Transcoding, 1:Failed, 2:Success, 3:RecycleBin
    del_flag SMALLINT DEFAULT 0, -- 0:Normal, 1:Recycle, 2:Deleted
    recovery_time TIMESTAMP
);

-- Email Code Table
CREATE TABLE IF NOT EXISTS email_code (
    email VARCHAR(150) NOT NULL,
    code VARCHAR(10) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status SMALLINT DEFAULT 0, -- 0:Unused, 1:Used
    PRIMARY KEY (email, code)
);

-- 2. Indices
CREATE INDEX IF NOT EXISTS idx_file_user ON file_info(user_id);
CREATE INDEX IF NOT EXISTS idx_file_pid ON file_info(file_pid);
CREATE INDEX IF NOT EXISTS idx_file_md5 ON file_info(file_md5);
CREATE INDEX IF NOT EXISTS idx_user_pid ON file_info(user_id, file_pid);

-- 3. Initial Data (Optional - Admin User)
-- Password '123456' hashed with BCrypt (example placeholder, actual migration handled by service)
-- INSERT INTO user_info (user_id, nick_name, email, password, is_admin) 
-- VALUES ('10000', 'admin', 'admin@easypan.com', '$2a$10$8.UnVuG9HHgffUDAlk8q6uy.88yG.4x4iVv9.64556.7654321', TRUE);
