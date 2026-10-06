-- V6__Add_User_QQ_OpenId_Index.sql
-- 目的：为 QQ 登录场景补充字段和索引，提升 user_info 表通过 qq_open_id 查询的性能
-- 对应设计文档 2.1.1：QQ 登录查询优化索引

-- 1. 添加 QQ 登录相关字段
ALTER TABLE user_info ADD COLUMN IF NOT EXISTS qq_open_id VARCHAR(64);
ALTER TABLE user_info ADD COLUMN IF NOT EXISTS qq_avatar VARCHAR(255);

-- 2. 针对 QQ OpenID 查询的部分索引
-- 典型使用场景：UserInfoServiceImpl.qqLogin() 中的 userInfoMapper.selectByQqOpenId(openId)
-- 只为非空 qq_open_id 建立索引，减少索引体积
CREATE INDEX IF NOT EXISTS idx_user_qq_openid
ON user_info(qq_open_id)
WHERE qq_open_id IS NOT NULL;

