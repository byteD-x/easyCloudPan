-- V13__Add_User_File_Name_Index.sql
-- Optimizes file search by name within a user's files

CREATE INDEX IF NOT EXISTS idx_file_user_name 
ON file_info(user_id, file_name);

COMMENT ON INDEX idx_file_user_name IS 'Index to support file name search and sorting for users';
