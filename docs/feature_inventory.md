# EasyCloudPan 功能清单（阶段 0 自动发现）

## 1. 生成信息
- 生成时间：2026-02-13
- 运行态证据：
  - `http://localhost:7090/api/v3/api-docs` 导出成功（200），落盘 `docs/openapi.json`
  - `http://localhost:7090/api/actuator/health` 返回 200
  - `docker compose -f ops/docker/docker-compose.yml ps` 显示 `postgres/redis/minio` healthy
- 说明：`@RequestMapping` 未限制 HTTP Method，OpenAPI 会展开 `GET/POST/PUT/...`。前端实际通过 `frontend/src/utils/Request.ts` 统一使用 `POST + application/x-www-form-urlencoded`（流接口用 `GET`）。

## 2. 扫描来源（仅真实代码/文档）
- 后端接口与鉴权：
  - `backend/src/main/java/com/easypan/controller/*.java`
  - `backend/src/main/java/com/easypan/aspect/GlobalOperationAspect.java`
  - `backend/src/main/java/com/easypan/annotation/GlobalInterceptor.java`
  - `backend/src/main/resources/application.properties`
- OpenAPI：`docs/openapi.json`
- 前端页面与接口映射：
  - `frontend/src/router/index.ts`
  - `frontend/src/services/*.ts`
  - `frontend/src/views/**/*.vue`
  - `frontend/src/components/preview/*.vue`
  - `frontend/src/utils/Request.ts`
- 运维与依赖：
  - `ops/docker/docker-compose.yml`
  - `ops/tools/health_check.ps1`
  - `backend/src/main/resources/db/migration/*.sql`
- 文档补充信号：
  - `README.md`
  - `QUICK_START.md`
  - `database/README.md`

## 3. 全局协议与边界
- Base URL：`/api`
- 统一返回：`ResponseVO<T>`（`status/code/info/suggestion/data`）
- 认证模型：Session + JWT（Access Token + Refresh Token）
- 鉴权入口：`@GlobalInterceptor`
  - 默认：`checkLogin=true`
  - 管理员：`checkAdmin=true`
  - 参数校验：`checkParams=true + @VerifyParam`
- 安全实现：
  - JWT 黑名单：`RedisComponent.addBlacklistToken`（key 前缀 `easypan:jwt:blacklist:`）
  - Refresh Token：`RedisComponent.saveRefreshToken/validateRefreshToken`
  - 文件类型校验：`FileTypeValidator`（Magic Number + 危险扩展名黑名单）
- 多租户入口：`X-Tenant-Id`（`TenantInterceptor`）
- 文件存储：`StorageFailoverService`（主存储 + Local 回退），对象存储由 `S3Component`（MinIO/S3）支持

## 4. 后端功能清单（接口视角）

### 4.1 账户与认证（Account）
| 模块 | 接口/页面 | 鉴权 | 关键参数 | 关键响应 | 依赖服务 | 验收方法 |
|---|---|---|---|---|---|---|
| 账户 | `GET /api/checkCode` | 否 | `type` | 图片流 | Session | `M-AUTH-001` |
| 账户 | `POST /api/sendEmailCode` | 否 | `email/checkCode/type` | `code=200` | 邮件服务、`email_code` 表 | `M-AUTH-002` |
| 账户 | `POST /api/register` | 否 | `email/nickName/password/checkCode/emailCode` | `code=200` | PostgreSQL、Redis 配置 | `M-AUTH-003` |
| 账户 | `POST /api/login` | 否 | `email/password/checkCode` | `data.userInfo/token/refreshToken` | Session、JWT、Redis | `M-AUTH-004` |
| 账户 | `POST /api/refreshToken` | 否 | `refreshToken` | 新 `token` | JWT、Redis | `M-AUTH-005` |
| 账户 | `POST /api/logout` | 登录态可清理 | Header `Authorization` | `code=200` | JWT 黑名单、Redis | `M-AUTH-006` |
| 账户 | `POST /api/getUserInfo` | 是 | - | `SessionWebUserDto` | Session | `M-AUTH-007` |
| 账户 | `POST /api/getUseSpace` | 是 | - | `UserSpaceDto` | Redis、`file_info` | `M-FILE-001` |
| 账户 | `POST /api/updatePassword` | 是 | `password` | `code=200` | `user_info` | `M-AUTH-008` |
| 账户 | `POST /api/updateUserAvatar` | 是 | `avatar(file)` | `code=200` | 本地文件系统 | `M-AUTH-009` |
| 账户 | `GET /api/getAvatar/{userId}` | 否 | `userId` | 图片流 | 本地文件系统 | `M-AUTH-009` |
| 第三方登录 | `POST /api/qqlogin` / `POST /api/qqlogin/callback` | 否 | `callbackUrl` / `code,state` | QQ URL / `userInfo` | QQ OAuth 配置 | `M-AUTH-010` |

### 4.2 文件管理（File）
| 模块 | 接口/页面 | 鉴权 | 关键参数 | 关键响应 | 依赖服务 | 验收方法 |
|---|---|---|---|---|---|---|
| 文件 | `POST /api/file/loadDataList` | 是 | `pageNo/pageSize/filePid/category` | `PaginationResultVO<FileInfoVO>` | PostgreSQL | `M-FILE-001` |
| 文件 | `POST /api/file/loadDataListCursor` | 是 | `cursor/pageSize` | `CursorPage<FileInfoVO>` | PostgreSQL 索引 | `M-FILE-002` |
| 文件 | `POST /api/file/newFoloder` | 是 | `filePid/fileName` | `FileInfo` | PostgreSQL | `M-FILE-003` |
| 文件 | `POST /api/file/rename` | 是 | `fileId/fileName` | `FileInfoVO` | PostgreSQL | `M-FILE-004` |
| 文件 | `POST /api/file/uploadFile` | 是 | `file,fileName,filePid,fileMd5,chunkIndex,chunks,fileId` | `UploadResultDto(status)` | Redis、存储策略、Magic Number | `M-FILE-005` |
| 文件 | `POST /api/file/uploadedChunks` | 是 | `fileId,filePid` | `List<Integer>` | 本地 temp 分片目录 | `M-FILE-006` |
| 文件 | `POST /api/file/createDownloadUrl/{fileId}` | 是 | `fileId` | 下载码 | Redis | `M-FILE-007` |
| 文件 | `GET /api/file/download/{code}` | 否 | `code` | 文件流 | Redis、存储 | `M-FILE-007` |
| 文件 | `POST /api/file/delFile` | 是 | `fileIds` | `code=200` | PostgreSQL | `M-FILE-008` |
| 文件 | `GET /api/file/batchDownload/{fileIds}` | 是 | `fileIds` | ZIP 流 | 文件系统/对象存储 | `M-FILE-009` |
| 文件 | `GET /api/file/getFile/{fileId}` | 会话依赖 | `fileId` | 文件/转码流 | 权限注解 `@FileAccessCheck` | `M-FILE-010` |
| 文件 | `GET /api/file/getImage/{imageFolder}/{imageName}` | 是 | path 参数 | 图片流 | 图片访问校验 | `M-FILE-010` |
| 文件 | `GET /api/file/ts/getVideoInfo/{fileId}` | 会话依赖 | `fileId` | m3u8/ts 流 | 转码产物 | `M-FILE-010` |
| 文件 | `POST /api/file/getFolderInfo` `POST /api/file/loadAllFolder` `POST /api/file/changeFileFolder` | 是 | `path`/`filePid`/`fileIds` | 目录树、迁移结果 | PostgreSQL | `M-FILE-011` |

### 4.3 分享（Share + WebShare）
| 模块 | 接口/页面 | 鉴权 | 关键参数 | 关键响应 | 依赖服务 | 验收方法 |
|---|---|---|---|---|---|---|
| 用户分享 | `POST /api/share/loadShareList` | 是 | 分页参数 | 分享分页 | PostgreSQL | `M-SHARE-001` |
| 用户分享 | `POST /api/share/shareFile` | 是 | `fileId,validType,code` | `FileShare` | PostgreSQL | `M-SHARE-002` |
| 用户分享 | `POST /api/share/getShareUrl` | 是 | `shareId` | `shareUrl+code` | PostgreSQL | `M-SHARE-003` |
| 用户分享 | `POST /api/share/checkShareStatus` | 是 | `shareId` | 状态数据 | PostgreSQL | `M-SHARE-003` |
| 用户分享 | `POST /api/share/cancelShare` | 是 | `shareIds` | `code=200` | PostgreSQL | `M-SHARE-004` |
| 访客分享 | `POST /api/showShare/getShareInfo` | 否 | `shareId` | `ShareInfoVO` | `file_share/file_info/user_info` | `M-SHARE-005` |
| 访客分享 | `POST /api/showShare/checkShareCode` | 否 | `shareId,code` | `code=200` | Session、`share_access_log` | `M-SHARE-006` |
| 访客分享 | `POST /api/showShare/loadFileList` | 否（需分享会话） | `shareId,filePid` | 文件分页 | PostgreSQL | `M-SHARE-007` |
| 访客分享 | `POST /api/showShare/saveShare` | 是 | `shareId,shareFileIds,myFolderId` | `code=200` | PostgreSQL | `M-SHARE-008` |
| 访客分享 | `GET /api/showShare/getFile/*` `getImage/*` `ts/getVideoInfo/*` `download/*` | 否（需分享会话/下载码） | path 参数 | 流下载/预览 | Redis、存储 | `M-SHARE-009` |

### 4.4 回收站（Recycle）
| 模块 | 接口/页面 | 鉴权 | 关键参数 | 关键响应 | 依赖服务 | 验收方法 |
|---|---|---|---|---|---|---|
| 回收站 | `POST /api/recycle/loadRecycleList` | 是 | `pageNo/pageSize` | 回收分页 | PostgreSQL | `M-RECYCLE-001` |
| 回收站 | `POST /api/recycle/recoverFile` | 是 | `fileIds` | `code=200` | PostgreSQL | `M-RECYCLE-002` |
| 回收站 | `POST /api/recycle/delFile` | 是 | `fileIds` | `code=200` | PostgreSQL + 存储删除 | `M-RECYCLE-003` |

### 4.5 管理端（Admin）
| 模块 | 接口/页面 | 鉴权 | 关键参数 | 关键响应 | 依赖服务 | 验收方法 |
|---|---|---|---|---|---|---|
| 管理 | `POST /api/admin/getSysSettings` / `saveSysSettings` | 管理员 | 邮件模板、配额参数 | `SysSettingsDto`/`code=200` | Redis | `M-ADMIN-001` |
| 管理 | `POST /api/admin/loadUserList` | 管理员 | `UserInfoQuery` | 用户分页 | PostgreSQL | `M-ADMIN-002` |
| 管理 | `POST /api/admin/updateUserStatus` | 管理员 | `userId,status` | `code=200` | PostgreSQL | `M-ADMIN-003` |
| 管理 | `POST /api/admin/updateUserSpace` | 管理员 | `userId,changeSpace` | `code=200` | PostgreSQL/Redis | `M-ADMIN-004` |
| 管理 | `POST /api/admin/loadFileList` / `delFile` | 管理员 | 文件筛选 / `fileIdAndUserIds` | 列表/删除结果 | PostgreSQL + 存储 | `M-ADMIN-005` |
| 管理流接口 | `GET /api/admin/getFile/*` `getImage/*` `ts/*` `download/*` | 管理员或下载码 | path 参数 | 流式访问 | 文件访问控制 | `M-ADMIN-006` |

### 4.6 分析与监控（Analytics + Actuator + Prometheus）
| 模块 | 接口/页面 | 鉴权 | 关键参数 | 关键响应 | 依赖服务 | 验收方法 |
|---|---|---|---|---|---|---|
| Web Vitals | `POST /api/analytics/web-vitals` | 否 | `name,value,rating,page,timestamp` | `status=success` | PostgreSQL（可选 mapper） | `M-OBS-001` |
| Web Vitals | `GET /api/analytics/web-vitals/stats` | 否 | - | 聚合统计 | 内存队列 | `M-OBS-002` |
| 健康检查 | `GET /api/actuator/health` | 否 | - | 组件健康（db/redis/mail） | Spring Actuator | `M-OBS-003` |
| 指标 | `GET /api/actuator/prometheus` | 否 | - | 指标文本 | Micrometer | `M-OBS-004` |

## 5. 前端页面 -> 接口映射（联调视角）
| 页面路由 | 页面文件 | 主调用接口 | 说明 |
|---|---|---|---|
| `/login` | `frontend/src/views/Login.vue` | `/checkCode /sendEmailCode /register /login /resetPwd /qqlogin` | 登录/注册/找回密码/QQ 登录入口 |
| `/qqlogincallback` | `frontend/src/views/QqLoginCallback.vue` | `/qqlogin/callback` | 三方登录回调 |
| `/main/:category` | `frontend/src/views/main/Main.vue` | `/file/loadDataList /file/uploadFile /file/newFoloder /file/rename /file/delFile /file/changeFileFolder /file/createDownloadUrl` | 文件主工作区 |
| 右上上传器 | `frontend/src/views/main/Uploader.vue` | `/file/uploadFile /file/uploadedChunks` | 分片上传、断点续传、秒传状态 |
| `/myshare` | `frontend/src/views/share/Share.vue` + `main/ShareFile.vue` | `/share/loadShareList /share/shareFile /share/cancelShare` | 分享管理 |
| `/shareCheck/:shareId` | `frontend/src/views/webshare/ShareCheck.vue` | `/showShare/getShareInfo /showShare/checkShareCode` | 分享提取码验证 |
| `/share/:shareId` | `frontend/src/views/webshare/Share.vue` | `/showShare/getShareLoginInfo /showShare/loadFileList /showShare/createDownloadUrl /showShare/saveShare /share/cancelShare` | 访客访问分享 |
| `/recycle` | `frontend/src/views/recycle/Recycle.vue` | `/recycle/loadRecycleList /recycle/recoverFile /recycle/delFile` | 回收站 |
| `/settings/userList` | `frontend/src/views/admin/UserList.vue` | `/admin/loadUserList /admin/updateUserStatus /admin/updateUserSpace` | 管理员用户管理 |
| `/settings/fileList` | `frontend/src/views/admin/FileList.vue` | `/admin/loadFileList /admin/delFile /admin/createDownloadUrl` | 管理员文件管理 |
| `/settings/sysSetting` | `frontend/src/views/admin/SysSettings.vue` | `/admin/getSysSettings /admin/saveSysSettings` | 系统参数配置 |
| 公共预览组件 | `frontend/src/components/preview/*.vue` | `/file|getFile|getImage|ts/getVideoInfo`（含 admin/share 变体） | 图片/PDF/视频/音频/Office/文本预览 |

## 6. 非显式能力（由代码+文档补全）
- 分片上传 + 断点续传 + 秒传：`Uploader.vue` + `FileInfoServiceImpl.uploadFile` + BloomFilter
- JWT 双 Token + 黑名单：`AccountController` + `JwtTokenProvider` + `JwtBlacklistService` + Redis key
- 审计日志：`ShareAccessLogServiceImpl` 写入 `share_access_log`
- 监控：Actuator + Micrometer + Prometheus + Grafana（`ops/monitoring/*`）
- Flyway 迁移链：`V1` ~ `V10`，实际数据库版本以 `flyway_schema_history` 为准

## 7. 阶段 0 结论
- 已建立完整功能清单，覆盖：账户、文件、分享、回收站、管理端、分析监控、前端页面联调路径。
- 下一步执行文件：`docs/acceptance_matrix.md`（逐项验收与 P0/P1/P2 清零）。
