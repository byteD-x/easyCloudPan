-- 新增 OAuth 通用字段
ALTER TABLE user_info ADD COLUMN IF NOT EXISTS oauth_provider VARCHAR(32);
ALTER TABLE user_info ADD COLUMN IF NOT EXISTS oauth_provider_id VARCHAR(256);

-- 创建唯一索引：同一 provider 下的 provider_id 唯一
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_oauth_provider
    ON user_info (oauth_provider, oauth_provider_id)
    WHERE oauth_provider IS NOT NULL AND oauth_provider_id IS NOT NULL;

-- 迁移已有 QQ 数据到新字段
UPDATE user_info
SET oauth_provider = 'qq', oauth_provider_id = qq_open_id
WHERE qq_open_id IS NOT NULL AND qq_open_id != '';
