# EasyCloudPan 协作与交付手册（AGENTS.md）

> 文档版本：v3.0  
> 最后更新：2026-02-15  
> 适用对象：自动化 Agent、后端开发、前端开发、测试、运维、代码审查者  
> 适用范围：仓库根目录 `easyCloudPan/` 下全部内容

---

## 1. 文档目标与使用规则

本文件是 EasyCloudPan 的生产级协作手册，目标是让任何协作者都能在“可验证、可回滚、可审计”的前提下进行交付。

### 1.1 目标

- 统一技术事实来源，避免文档与代码脱节。
- 统一开发、测试、部署、回滚流程。
- 统一接口规范、数据规范、安全规范。
- 统一 PR 验收标准与变更记录标准。

### 1.2 强制原则

- 默认选择更安全、更小改动、更可回滚、更可验证的方案。
- 不得假设“已成功”，必须用命令输出或测试结果验证。
- 不可在 2 分钟内解释清楚的改动，先拆分后实施。
- 禁止在未评估风险时进行高危操作（清库、删卷、强推、覆盖配置等）。

---

## 2. 项目概览

EasyCloudPan 是一个前后端分离网盘系统，包含用户认证、文件管理、文件分享、回收站、文件预览、多租户、监控告警等能力。

### 2.1 代码结构

```text
easyCloudPan/
├── backend/                    # Spring Boot 后端（Java 21）
├── frontend/                   # Vue 3 前端（Vite）
├── ops/                        # 本地启动、Docker 部署、健康检查、监控配置
├── docs/                       # 部署文档
├── database/                   # 数据库迁移说明（Flyway 脚本在 backend）
├── scripts/                    # 验证/基线采集脚本
└── agent_runs/                 # Agent 扫描与执行产物（本地可清理）
```

### 2.2 运行形态

- 本地开发：`ops/local/setup.ps1` + `ops/local/startup.ps1`
- Docker 全栈：`ops/docker/deploy_docker.ps1`
- Docker 精简基础设施：`ops/docker/docker-compose.simple.yml`（仅 PostgreSQL/Redis/MinIO）

---

## 3. 技术栈（以仓库清单为准）

## 3.1 后端技术栈（`backend/pom.xml`）

| 类别 | 技术 | 版本 |
|---|---|---|
| 语言/运行时 | Java | 21 |
| 框架 | Spring Boot | 3.2.12 |
| Web | spring-boot-starter-web | Boot 管理 |
| 安全 | Spring Security | Boot 管理 |
| OAuth2 客户端 | spring-boot-starter-oauth2-client | Boot 管理 |
| 密码加密 | BCrypt (`PasswordEncoder`) | Boot 管理 |
| ORM | MyBatis-Flex | 1.11.6 |
| 数据库 | PostgreSQL JDBC | 42.7.2 |
| 缓存 | Redis Starter + Caffeine | Boot 管理 + 3.1.8 |
| 布隆过滤器 | Guava | 32.1.3-jre |
| 对象存储 | AWS SDK v2 S3（用于 MinIO） | 2.25.10 |
| JWT | jjwt-api/impl/jackson | 0.12.3 |
| DB 迁移 | Flyway | 11.20.3 |
| 监控 | Actuator + Micrometer Prometheus | Boot 管理 |
| 文档 | springdoc-openapi-starter-webmvc-ui | 2.3.0 |
| 配置加密 | jasypt-spring-boot-starter | 3.0.5 |
| 日志 | Logback + JSON Layout | 1.5.16 + 0.1.5 |
| IO 工具 | okhttp | 4.12.0 |
| 工具库 | commons-lang3 / codec / io | 3.14.0 / 1.16.0 / 2.15.1 |
| 单测 | spring-boot-starter-test | Boot 管理 |
| 属性测试 | jqwik | 1.8.2 |
| 代码规范 | Maven Checkstyle Plugin | 3.3.1 |
| 静态分析 | SpotBugs Maven Plugin | 4.8.3.1 |
| 覆盖率 | JaCoCo Maven Plugin | 0.8.11 |

## 3.2 前端技术栈（`frontend/package.json`）

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue | 3.5.13 |
| 构建工具 | Vite | 7.3.1 |
| 路由 | Vue Router | 4.5.0 |
| 状态管理 | Pinia | 3.0.4 |
| UI 组件 | Element Plus | 2.9.0 |
| HTTP 客户端 | Axios | 1.7.9 |
| 国际化 | vue-i18n | 9.14.4 |
| 文件哈希 | spark-md5 / js-md5 | 3.0.2 / 0.8.3 |
| 预览能力 | vue-pdf-embed / docx-preview / xlsx / dplayer / aplayer | 见 `package.json` |
| PWA | vite-plugin-pwa | 1.2.0 |
| 压缩 | vite-plugin-compression | 0.5.1 |
| 图片优化 | vite-plugin-imagemin | 0.6.1 |
| 代码规范 | eslint + prettier | 8.57.0 + 3.2.5 |
| 类型检查 | vue-tsc | 3.2.4 |
| E2E | Playwright | 1.58.2 |

## 3.3 基础设施与运维

| 组件 | 版本/镜像 | 说明 |
|---|---|---|
| PostgreSQL | `postgres:15-alpine` | 主数据库 |
| Redis | `redis:7-alpine` | 缓存、Token 黑名单、会话辅助 |
| MinIO | `minio/minio:latest` | 对象存储 |
| Nginx | `nginx:stable-alpine` | 前端静态资源与 API 反向代理 |
| Prometheus | `prom/prometheus:latest` | 指标采集 |
| Grafana | `grafana/grafana:latest` | 监控展示 |
| Redis Exporter | `oliver006/redis_exporter:latest` | Redis 指标 |
| Postgres Exporter | `prometheuscommunity/postgres-exporter:latest` | PostgreSQL 指标 |
| 容器编排 | Docker Compose V2 | 本地/测试部署 |
| CI | - | 参考 §12.1 的命令清单（可用于 GitHub Actions 等平台） |

---

## 4. 环境要求与端口规划

## 4.1 最低环境

- JDK 21+
- Maven 3.9+
- Node.js 20+（`setup.ps1` 检查 >=20；前端 Docker 构建镜像使用 Node 22）
- Docker Desktop + `docker compose`（V2）

建议先验证：

```powershell
java -version
javac -version
mvn -version
node -v
npm -v
docker --version
docker compose version
```

## 4.2 默认端口

| 服务 | 端口 | 说明 |
|---|---|---|
| 前端 | 8080 | 浏览器访问入口 |
| 后端 | 7090 | Spring Boot 端口（上下文 `/api`） |
| PostgreSQL（全栈 compose） | 5433 | 映射到容器 5432 |
| PostgreSQL（simple compose） | 5432 | 映射到容器 5432 |
| Redis | 6379 | 缓存 |
| MinIO API | 9000 | 对象存储 API |
| MinIO Console | 9001 | 管理台 |
| Prometheus | 9090 | 指标采集 |
| Grafana | 3000 | 监控看板 |
| Redis Exporter | 9121 | Redis 指标 |
| Postgres Exporter | 9187 | PostgreSQL 指标 |

---

## 5. 配置与密钥管理

## 5.1 配置文件

- 模板：`ops/docker/.env.example`
- 实际配置：`ops/docker/.env`
- 后端默认配置：`backend/src/main/resources/application.properties`

## 5.2 关键环境变量（生产必须覆盖默认值）

- 数据库：`POSTGRES_DB` `POSTGRES_USER` `POSTGRES_PASSWORD`
- Redis：`REDIS_PASSWORD`
- MinIO：`MINIO_ROOT_USER` `MINIO_ROOT_PASSWORD` `MINIO_BUCKET`
- 邮件：`SPRING_MAIL_HOST` `SPRING_MAIL_PORT` `SPRING_MAIL_USERNAME` `SPRING_MAIL_PASSWORD`
- QQ 登录：`QQ_APP_ID` `QQ_APP_KEY` `QQ_URL_REDIRECT`
- 安全/日志：`ADMIN_EMAILS` `LOG_ROOT_LEVEL`
- 加密：`JASYPT_ENCRYPTOR_PASSWORD`（应用启动必填）

## 5.3 安全要求

- 严禁提交或打印密钥、Token、连接串、`.env` 明文敏感值。
- 所有默认密码仅用于本地调试，生产必须替换。
- 加密配置统一通过 Jasypt，密钥走环境变量，不写入仓库。

---

## 6. 本地开发流程（可执行）

## 6.1 首次初始化

```powershell
.\ops\local\setup.ps1
```

脚本会做：

- 依赖检查（JDK/Maven/Node/npm/Docker）
- 前端依赖安装
- 后端构建（`mvn clean install -DskipTests`）
- 本地目录初始化（`backend/file/...`）
- 生成 `ops/docker/.env`

## 6.2 一键启动开发环境

```powershell
.\ops\local\startup.ps1
```

脚本会做：

- 启动基础设施容器：PostgreSQL/Redis/MinIO/minio-init
- 启动后端：`mvn spring-boot:run`
- 启动前端：`npm run dev`

## 6.3 健康检查

```powershell
.\ops\tools\health_check.ps1
```

关键验证点：

- `docker compose ps` 可获取容器状态
- PostgreSQL `pg_isready` 成功
- Redis `PONG`
- MinIO `http://localhost:9000/minio/health/live` 返回 200
- 后端 `http://localhost:7090/api/actuator/health` 返回 200（本地模式若未启动后端可为 WARN）

## 6.4 增量构建建议（最佳实践）

- **建议在日常项目构建与重启中，如果仅仅是修改了 Java 逻辑代码，直接调用 `mvn package -DskipTests`（或对应的打包、编译命令），请尽量避免前置加 `clean` 操作。**
- Maven 完全支持增量构建，保留 `target` 目录缓存可以使编译效率翻倍，同时也能避免 IDE（例如 VSCode Java 语言服务器）因为监控目录被清空而引发大面积“假阴性”的 `cannot be resolved` 报错。

---

## 7. Docker 部署流程（生产基线）

## 7.1 全栈部署

```powershell
copy ops\docker\.env.example ops\docker\.env
.\ops\docker\deploy_docker.ps1
```

跳过构建：

```powershell
.\ops\docker\deploy_docker.ps1 -NoBuild
```

等价命令：

```powershell
docker compose -f ops\docker\docker-compose.yml up -d --build
```

## 7.2 部署后验证

```powershell
docker compose -f ops\docker\docker-compose.yml ps
.\ops\tools\health_check.ps1
```

访问地址：

- 前端：`http://localhost:8080`
- 后端：`http://localhost:7090/api`
- MinIO Console：`http://localhost:9001`
- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`

## 7.3 停止与回滚

```powershell
.\ops\docker\stop_docker.ps1
```

危险操作（会删除卷并清空数据）：

```powershell
.\ops\docker\stop_docker.ps1 -Volumes
```

---

## 8. 接口文档规范（详细）

## 8.1 文档入口与分组

- Swagger UI：`/api/swagger-ui/index.html`
- OpenAPI JSON：`/api/v3/api-docs`

接口事实来源（必须与源码一致）：

- 控制器：`backend/src/main/java/com/easypan/controller/*.java`
- 参数校验与鉴权：`backend/src/main/java/com/easypan/aspect/GlobalOperationAspect.java`
- 路由上下文：`backend/src/main/resources/application.properties`（`server.servlet.context-path=/api`）

OpenAPI 分组（`SwaggerConfig`）：

- `User Module`：`/account/**` `/userInfo/**`
- `File Module`：`/file/**`
- `Share Module`：`/share/**` `/showShare/**`
- `Admin Module`：`/admin/**`

联调注意：

- `AccountController` 无类级 `@RequestMapping`，真实用户接口路径是 `/api/login` 这种根路径，不是 `/api/account/login`。

## 8.2 全局协议

- Base URL：`/api`
- 前端 SDK 默认调用方式：`POST` + `application/x-www-form-urlencoded`（见 `frontend/src/utils/Request.ts`）
- 文件上传：`multipart/form-data`
- 文件/图片/视频流/下载：`GET`
- 认证头：`Authorization: Bearer <access_token>`
- 多租户头：`X-Tenant-Id: <tenant_id>`（缺省落到 `default`）
- 统一响应结构：`ResponseVO<T>`
- 认证模型：`Session + JWT` 并存（登录写入 Session，同时返回 `token` 和 `refreshToken`）
- 后端多数接口使用 `@RequestMapping`（未限制 Method），前端约定按 `POST` 调用，流接口按 `GET`

```json
{
  "status": "success|error",
  "code": 200,
  "info": "请求成功",
  "suggestion": "可选",
  "data": {}
}
```

## 8.3 认证与令牌协议

登录接口返回：

- `token`：Access Token（JWT）
- `refreshToken`：刷新令牌（Redis 持久化）
- `userInfo`：`SessionWebUserDto`

刷新接口：

- `POST /api/refreshToken`
- 入参：`refreshToken`
- 出参：新 `token` + 当前 `refreshToken`

登出接口：

- `POST /api/logout`
- 行为：会话失效 + Refresh Token 删除 + Access Token 加入黑名单

验证码会话键：

- 普通验证码：`check_code_key`
- 邮箱验证码场景：`check_code_key_email`

常用错误码（`ResponseCodeEnum`）：

| code | 含义 | 典型触发场景 |
|---|---|---|
| `200` | 请求成功 | 正常返回 |
| `600` | 请求参数错误 | `@VerifyParam` 校验失败 |
| `901` | 登录超时 | Token 或 Session 无效 |
| `902` | 分享链接失效 | 分享不存在/过期 |
| `903` | 分享验证失效 | 提取码会话失效 |
| `904` | 空间不足 | 上传或保存分享超配额 |

## 8.4 统一返回数据结构字典

| 结构 | 字段 | 说明 |
|---|---|---|
| `SessionWebUserDto` | `userId` `nickName` `admin` `avatar` `tenantId` | 会话用户信息（含租户标识） |
| `UserSpaceDto` | `useSpace` `totalSpace` | 用户空间（字节） |
| `UploadResultDto` | `fileId` `status` | 上传结果，`status`：`uploading` `upload_finish` `upload_seconds` |
| `PaginationResultVO<T>` | `totalCount` `pageSize` `pageNo` `pageTotal` `list` | 分页结果 |
| `CursorPage<T>` | `list` `nextCursor` `hasMore` `pageSize` `totalCount` | 游标分页结果 |
| `FileInfoVO` | `fileId` `filePid` `fileName` `fileSize` `fileCover` `recoveryTime` `lastUpdateTime` `folderType` `fileCategory` `fileType` `status` | 文件视图 |
| `ShareInfoVO` | `shareTime` `expireTime` `nickName` `fileName` `currentUser` `fileId` `avatar` `userId` | 分享页信息 |
| `SysSettingsDto` | `registerEmailTitle` `registerEmailContent` `userInitUseSpace` | 系统设置 |

## 8.5 账户模块接口明细（`AccountController`）

| 接口 | 鉴权 | 推荐请求方式 | 关键参数 | 参数约束 | 返回 `data` |
|---|---|---|---|---|---|
| `/api/checkCode` | 否 | GET | `type` | 可选；`0/null` 普通验证码，其他值邮箱验证码 | 图片流（`image/jpeg`） |
| `/api/sendEmailCode` | 否 | POST | `email` `checkCode` `type` | `email` 正则 EMAIL，最大 150；`checkCode` 必填 | `null` |
| `/api/register` | 否 | POST | `email` `nickName` `password` `checkCode` `emailCode` | `nickName` 最大 20；`password` 正则 PASSWORD，长度 8-32 | `null` |
| `/api/login` | 否 | POST | `email` `password` `checkCode` | 均必填 | `{ userInfo, token, refreshToken }` |
| `/api/resetPwd` | 否 | POST | `email` `password` `checkCode` `emailCode` | `password` 正则 PASSWORD，长度 8-32 | `null` |
| `/api/getAvatar/{userId}` | 否 | GET | `userId` | 必填 | 图片流 |
| `/api/getUserInfo` | 是 | POST | - | 会话必须有效 | `SessionWebUserDto` |
| `/api/getUseSpace` | 是 | POST | - | 会话必须有效 | `UserSpaceDto` |
| `/api/logout` | 否（可匿名） | POST | - | 若已登录则清理会话与黑名单；未登录也返回成功 | `null` |
| `/api/refreshToken` | 否 | POST | `refreshToken` | 必填 | `{ token, refreshToken }` |
| `/api/updateUserAvatar` | 是 | POST | `avatar` | `multipart/form-data`，业务上必须传文件 | `null` |
| `/api/updatePassword` | 是 | POST | `password` | PASSWORD 正则，长度 8-32 | `null` |
| `/api/qqlogin` | 否 | POST | `callbackUrl` | 可选 | QQ 授权 URL 字符串 |
| `/api/qqlogin/callback` | 否 | POST | `code` `state` | 必填 | `{ callbackUrl, userInfo }` |

## 8.6 文件模块接口明细（`FileInfoController`）

| 接口 | 鉴权 | 推荐请求方式 | 关键参数 | 参数约束 | 返回 `data` |
|---|---|---|---|---|---|
| `/api/file/loadDataList` | 是 | POST | `FileInfoQuery` + `category` | `category` 支持 `video/music/image/doc/others` | `PaginationResultVO<FileInfoVO>` |
| `/api/file/loadDataListCursor` | 是 | POST | `cursor` `pageSize` | 游标分页 | `CursorPage<FileInfoVO>` |
| `/api/file/uploadFile` | 是 | POST | `fileId` `file` `fileName` `filePid` `fileMd5` `chunkIndex` `chunks` | `chunkIndex>=0 && chunkIndex<chunks`；首片做扩展名黑名单+MagicNumber 校验；并发限流；`status` 返回 `uploading/upload_finish/upload_seconds` | `UploadResultDto` |
| `/api/file/uploadedChunks` | 是 | POST | `fileId` `filePid` | 均必填 | `List<Integer>` 已上传分片序号 |
| `/api/file/getImage/{imageFolder}/{imageName}` | 是 | GET | 路径参数 | 需通过图片访问校验 | 图片流 |
| `/api/file/ts/getVideoInfo/{fileId}` | 依赖会话 | GET | `fileId` | 虽未显式注解登录拦截，但方法内部依赖 Session 用户 | 视频分片流 |
| `/api/file/getFile/{fileId}` | 依赖会话 | GET | `fileId` | 同上，依赖 Session 用户 | 文件流 |
| `/api/file/newFoloder` | 是 | POST | `filePid` `fileName` | 均必填 | `FileInfo` |
| `/api/file/getFolderInfo` | 是 | POST | `path` | 必填 | `List<FolderVO>` |
| `/api/file/rename` | 是 | POST | `fileId` `fileName` | 均必填 | `FileInfoVO` |
| `/api/file/loadAllFolder` | 是 | POST | `filePid` `currentFileIds` | `filePid` 必填 | `List<FileInfoVO>` |
| `/api/file/changeFileFolder` | 是 | POST | `fileIds` `filePid` | 均必填 | `null` |
| `/api/file/createDownloadUrl/{fileId}` | 是 | POST | `fileId` | 必填且必须是文件非目录 | `String`（下载码） |
| `/api/file/download/{code}` | 否 | GET | `code` | 必填 | 文件流下载 |
| `/api/file/delFile` | 是 | POST | `fileIds` | 必填，逗号分隔 | `null` |
| `/api/file/batchDownload/{fileIds}` | 是 | GET | `fileIds` | 必填，逗号分隔 | ZIP 文件流 |

联调注意：

- `/api/file/newFoloder` 为后端现有拼写，前后端需保持一致。
- `uploadedChunks` 接口后端实际参数是 `fileId + filePid`，不是 `fileMd5`。

## 8.7 分享模块接口明细（`ShareController` + `WebShareController`）

用户侧分享管理（`/api/share`）：

| 接口 | 鉴权 | 推荐请求方式 | 关键参数 | 参数约束 | 返回 `data` |
|---|---|---|---|---|---|
| `/api/share/loadShareList` | 是 | POST | `FileShareQuery` | 支持分页/时间/条件 | `PaginationResultVO<FileShare>` |
| `/api/share/shareFile` | 是 | POST | `fileId` `validType` `code` | `validType`：`0/1/2/3` | `FileShare` |
| `/api/share/cancelShare` | 是 | POST | `shareIds` | 必填，逗号分隔 | `null` |
| `/api/share/getShareUrl` | 是 | POST | `shareId` | 必填 | `{ shareUrl, code, shareId }` |
| `/api/share/checkShareStatus` | 是 | POST | `shareId` | 必填 | `{ shareId, fileName, shareTime, expireTime, showCount, validType, status }` |

访客侧分享访问（`/api/showShare`）：

| 接口 | 鉴权 | 推荐请求方式 | 关键参数 | 参数约束 | 返回 `data` |
|---|---|---|---|---|---|
| `/api/showShare/getShareLoginInfo` | 否 | POST | `shareId` | 必填 | `ShareInfoVO` 或 `null` |
| `/api/showShare/getShareInfo` | 否 | POST | `shareId` | 必填 | `ShareInfoVO` |
| `/api/showShare/checkShareCode` | 否 | POST | `shareId` `code` | 必填 | `null` |
| `/api/showShare/loadFileList` | 否 | POST | `shareId` `filePid` | `shareId` 必填 | `PaginationResultVO<FileInfoVO>` |
| `/api/showShare/getFolderInfo` | 否 | POST | `shareId` `path` | 必填 | `List<FolderVO>` |
| `/api/showShare/getFile/{shareId}/{fileId}` | 否 | GET | 路径参数 | 需通过分享会话校验 | 文件流 |
| `/api/showShare/getImage/{shareId}/{imageFolder}/{imageName}` | 否 | GET | 路径参数 | 需通过分享会话校验 | 图片流 |
| `/api/showShare/ts/getVideoInfo/{shareId}/{fileId}` | 否 | GET | 路径参数 | 需通过分享会话校验 | 视频流 |
| `/api/showShare/createDownloadUrl/{shareId}/{fileId}` | 否 | POST | 路径参数 | 必填 | `String`（下载码） |
| `/api/showShare/download/{code}` | 否 | GET | `code` | 必填 | 文件流下载 |
| `/api/showShare/saveShare` | 是 | POST | `shareId` `shareFileIds` `myFolderId` | 必填；禁止保存自己分享的文件 | `null` |

## 8.8 回收站与管理端接口明细

回收站（`/api/recycle`）：

| 接口 | 鉴权 | 推荐请求方式 | 关键参数 | 参数约束 | 返回 `data` |
|---|---|---|---|---|---|
| `/api/recycle/loadRecycleList` | 是 | POST | `pageNo` `pageSize` | 分页参数可选 | `PaginationResultVO<FileInfoVO>` |
| `/api/recycle/recoverFile` | 是 | POST | `fileIds` | 必填，逗号分隔 | `null` |
| `/api/recycle/delFile` | 是 | POST | `fileIds` | 必填，逗号分隔 | `null` |

管理端（`/api/admin`，均需管理员）：

| 接口 | 鉴权 | 推荐请求方式 | 关键参数 | 参数约束 | 返回 `data` |
|---|---|---|---|---|---|
| `/api/admin/getSysSettings` | 管理员 | POST | - | 需 admin | `SysSettingsDto` |
| `/api/admin/saveSysSettings` | 管理员 | POST | `registerEmailTitle` `registerEmailContent` `userInitUseSpace` | 均必填 | `null` |
| `/api/admin/loadUserList` | 管理员 | POST | `UserInfoQuery` | 分页/条件查询 | `PaginationResultVO<UserInfoVO>` |
| `/api/admin/updateUserStatus` | 管理员 | POST | `userId` `status` | 均必填 | `null` |
| `/api/admin/updateUserSpace` | 管理员 | POST | `userId` `changeSpace` | 均必填，单位 MB | `null` |
| `/api/admin/loadFileList` | 管理员 | POST | `FileInfoQuery` | 支持按用户/文件筛选 | `PaginationResultVO<FileInfo>` |
| `/api/admin/getFolderInfo` | 管理员 | POST | `path` | 必填 | `List<FolderVO>` |
| `/api/admin/getFile/{userId}/{fileId}` | 管理员 | GET | 路径参数 | 必填 | 文件流 |
| `/api/admin/getImage/{userId}/{imageFolder}/{imageName}` | 管理员 | GET | 路径参数 | 必填 | 图片流 |
| `/api/admin/ts/getVideoInfo/{userId}/{fileId}` | 管理员 | GET | 路径参数 | 必填 | 视频流 |
| `/api/admin/createDownloadUrl/{userId}/{fileId}` | 管理员 | POST | 路径参数 | 必填 | `String`（下载码） |
| `/api/admin/download/{code}` | 否 | GET | `code` | 必填 | 文件流下载 |
| `/api/admin/delFile` | 管理员 | POST | `fileIdAndUserIds` | 格式：`{userId}_{fileId},{userId}_{fileId}` | `null` |

## 8.9 分析指标接口明细（`AnalyticsController`）

| 接口 | 鉴权 | 请求方式 | 入参 | 返回 `data` |
|---|---|---|---|---|
| `/api/analytics/web-vitals` | 否 | POST JSON | `name` `value` `rating` `delta` `id` `navigationType` `page` `timestamp` `userAgent` `deviceType` `connectionType` | `null` |
| `/api/analytics/web-vitals/stats` | 否 | GET | - | `{ totalMetrics, poorMetrics, avgLCP, avgINP, avgCLS, avgFCP, avgTTFB }` |

## 8.10 查询对象字段（联调必须对齐）

基础分页对象（`BaseParam`）：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `pageNo` | Integer | `1` | 页码 |
| `pageSize` | Integer | `15` | 每页条数 |
| `orderBy` | String | - | 排序表达式 |

`FileInfoQuery`（全字段）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `fileId` `fileIdFuzzy` | String | 文件 ID 精确/模糊 |
| `userId` `userIdFuzzy` | String | 用户 ID 精确/模糊 |
| `fileMd5` `fileMd5Fuzzy` | String | MD5 精确/模糊 |
| `filePid` `filePidFuzzy` | String | 父目录 ID |
| `fileSize` | Long | 文件大小 |
| `fileName` `fileNameFuzzy` | String | 文件名精确/模糊 |
| `fileCover` `fileCoverFuzzy` | String | 封面路径精确/模糊 |
| `filePath` `filePathFuzzy` | String | 存储路径精确/模糊 |
| `createTimeStart` `createTimeEnd` | String | 创建时间范围 |
| `lastUpdateTimeStart` `lastUpdateTimeEnd` | String | 更新时间范围 |
| `folderType` | Integer | `0` 文件，`1` 目录 |
| `fileCategory` | Integer | `1` 视频，`2` 音频，`3` 图片，`4` 文档，`5` 其他 |
| `fileType` | Integer | 文件细分类 |
| `status` | Integer | 转码状态 |
| `recoveryTimeStart` `recoveryTimeEnd` | String | 回收时间范围 |
| `delFlag` | Integer | 删除标记 |
| `fileIdArray` `filePidArray` | String[] | 批量过滤 |
| `excludeFileIdArray` | String[] | 排除集合 |
| `queryExpire` `queryNickName` | Boolean | 扩展查询开关 |

`FileShareQuery`（全字段）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `shareId` `shareIdFuzzy` | String | 分享 ID |
| `fileId` `fileIdFuzzy` | String | 文件 ID |
| `userId` `userIdFuzzy` | String | 用户 ID |
| `validType` | Integer | 有效期类型 |
| `expireTimeStart` `expireTimeEnd` | String | 过期时间范围 |
| `shareTimeStart` `shareTimeEnd` | String | 分享时间范围 |
| `code` `codeFuzzy` | String | 提取码 |
| `showCount` | Integer | 浏览次数 |
| `queryFileName` | Boolean | 是否联表查询文件名 |

`UserInfoQuery`（全字段）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `userId` `userIdFuzzy` | String | 用户 ID |
| `nickName` `nickNameFuzzy` | String | 昵称 |
| `email` `emailFuzzy` | String | 邮箱 |
| `qqAvatar` `qqAvatarFuzzy` | String | QQ 头像 |
| `qqOpenId` `qqOpenIdFuzzy` | String | QQ OpenID |
| `password` `passwordFuzzy` | String | 密码字段过滤 |
| `joinTimeStart` `joinTimeEnd` | String | 注册时间范围 |
| `lastLoginTimeStart` `lastLoginTimeEnd` | String | 登录时间范围 |
| `status` | Integer | 用户状态 |
| `useSpace` `totalSpace` | Long | 空间过滤条件 |

## 8.11 接口变更硬性要求

- 任何接口变更必须同时更新：
  - 后端 Controller + 参数校验注解
  - 前端 `src/services/*`
  - 前端 `src/types/*`
  - 文档本节
- 保持 `ResponseVO<T>` 契约不破坏兼容。
- 新增错误码时同步更新：
  - 后端 `ResponseCodeEnum`
  - 前端 `src/locales/*` 的错误文案映射

## 8.12 联调请求样例（生产可复用）

登录：

```http
POST /api/login
Content-Type: application/x-www-form-urlencoded

email=test@example.com&password=Abcd1234!&checkCode=8K9QX
```

分片上传（首片）：

```http
POST /api/file/uploadFile
Content-Type: multipart/form-data

file=<binary>&fileName=demo.mp4&filePid=0&fileMd5=1bc29b36f623ba82aaf6724fd3b16718&chunkIndex=0&chunks=20&fileId=TMP_001
```

分享提取码校验：

```http
POST /api/showShare/checkShareCode
Content-Type: application/x-www-form-urlencoded

shareId=SHARE_001&code=K3F8P
```

---

## 9. 数据文档规范（详细）

## 9.1 数据库迁移机制

- 数据库类型：PostgreSQL（`spring.datasource.url=jdbc:postgresql://...`）
- 迁移工具：Flyway
- 迁移目录：`backend/src/main/resources/db/migration/`
- 命名规则：`V{版本号}__{描述}.sql`
- 执行规则：历史脚本只读，不改历史，只追加。

当前版本链：

- `V1__Initial_Schema.sql`
- `V2__Add_Performance_Indexes_For_File_Cleanup.sql`
- `V3__Add_Share_Table_And_Performance_Indexes.sql`
- `V4__Add_File_Query_Indexes.sql`
- `V5__Add_Tenant_Support.sql`
- `V6__Add_User_QQ_OpenId_Index.sql`
- `V7__Performance_Optimization_Indexes.sql`
- `V8__Add_Share_Access_Log.sql`
- `V9__Add_Web_Vitals_Metrics_Table.sql`
- `V10__Add_Share_File_Fields.sql`
- `V11__Add_OAuth_Provider_Fields.sql`
- `V12__Add_Folder_And_Recycle_Indexes.sql`
- `V13__Add_User_File_Name_Index.sql`

## 9.2 表关系总览

| 关系 | 约束 | 来源迁移 | 说明 |
|---|---|---|---|
| `file_info.user_id -> user_info.user_id` | `ON DELETE CASCADE` | V1 | 用户删除时级联删除文件记录 |
| `file_share.file_id -> file_info.file_id` | `ON DELETE CASCADE` | V3 | 文件删除时级联删除分享 |
| `file_share.user_id -> user_info.user_id` | `ON DELETE CASCADE` | V3 | 用户删除时级联删除分享 |
| `user_info.tenant_id -> tenant_info.tenant_id` | `fk_user_tenant` | V5 | 用户租户绑定 |
| `file_info.tenant_id -> tenant_info.tenant_id` | `fk_file_tenant` | V5 | 文件租户绑定 |
| `file_share.tenant_id -> tenant_info.tenant_id` | `fk_share_tenant` | V5 | 分享租户绑定 |

补充说明：

- `share_access_log`、`web_vitals_metrics` 当前无外键，优先保证写入吞吐与异步落库稳定性。

## 9.3 核心表字段字典

### 9.3.1 `user_info`

| 字段 | 类型 | 约束 | 默认值 | 说明 |
|---|---|---|---|---|
| `user_id` | `VARCHAR(20)` | PK, NOT NULL | - | 用户 ID |
| `nick_name` | `VARCHAR(20)` | NOT NULL | - | 昵称 |
| `email` | `VARCHAR(50)` | UNIQUE, NOT NULL | - | 邮箱 |
| `password` | `VARCHAR(100)` | NOT NULL | - | 密码哈希 |
| `avatar` | `VARCHAR(150)` | NULL | NULL | 头像 |
| `join_time` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 注册时间 |
| `last_login_time` | `TIMESTAMP` | NULL | NULL | 最近登录时间 |
| `status` | `SMALLINT` | NULL | `1` | 用户状态（0禁用/1启用） |
| `use_space` | `BIGINT` | NULL | `0` | 已用空间（字节） |
| `total_space` | `BIGINT` | NULL | `5368709120` | 总空间（字节） |
| `is_admin` | `BOOLEAN` | NULL | `FALSE` | 是否管理员 |
| `tenant_id` | `VARCHAR(10)` | FK | `'default'` | 租户 ID |
| `qq_open_id` | `VARCHAR(64)` | NULL | NULL | QQ OpenID |
| `qq_avatar` | `VARCHAR(255)` | NULL | NULL | QQ 头像 |

表级规则：

- `email` 全局唯一（唯一索引约束）。
- `is_admin` 仅用于权限判断，不代表租户管理员角色扩展。

### 9.3.2 `file_info`

| 字段 | 类型 | 约束 | 默认值 | 说明 |
|---|---|---|---|---|
| `file_id` | `VARCHAR(20)` | PK, NOT NULL | - | 文件 ID |
| `user_id` | `VARCHAR(20)` | FK, NOT NULL | - | 所属用户 |
| `file_md5` | `VARCHAR(32)` | NULL | NULL | 文件 MD5 |
| `file_pid` | `VARCHAR(20)` | NULL | NULL | 父目录 ID |
| `file_size` | `BIGINT` | NULL | NULL | 文件大小（字节） |
| `file_name` | `VARCHAR(200)` | NOT NULL | - | 文件名 |
| `file_cover` | `VARCHAR(100)` | NULL | NULL | 封面路径 |
| `file_path` | `VARCHAR(200)` | NULL | NULL | 存储路径 |
| `create_time` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 创建时间 |
| `last_update_time` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 更新时间 |
| `folder_type` | `SMALLINT` | NULL | `0` | 0文件/1目录 |
| `file_category` | `SMALLINT` | NULL | NULL | 文件分类 |
| `file_type` | `SMALLINT` | NULL | NULL | 文件类型 |
| `status` | `SMALLINT` | NULL | `2` | 转码状态 |
| `del_flag` | `SMALLINT` | NULL | `0` | 删除标志 |
| `recovery_time` | `TIMESTAMP` | NULL | NULL | 回收时间 |
| `tenant_id` | `VARCHAR(10)` | FK | `'default'` | 租户 ID |

表级规则：

- 软删除模型依赖 `del_flag`，业务读取“正常文件”使用 `FileDelFlagEnums.USING`（值 `2`）。
- 秒传依赖 `file_md5 + status` 查询链路，必须保留对应索引。
- 文件/目录同表存储，`folder_type` 区分。

### 9.3.3 `file_share`

| 字段 | 类型 | 约束 | 默认值 | 说明 |
|---|---|---|---|---|
| `share_id` | `VARCHAR(20)` | PK, NOT NULL | - | 分享 ID |
| `file_id` | `VARCHAR(20)` | FK, NOT NULL | - | 文件 ID |
| `user_id` | `VARCHAR(20)` | FK, NOT NULL | - | 分享人 |
| `valid_type` | `SMALLINT` | NULL | `0` | 有效期类型（0/1/2/3） |
| `expire_time` | `TIMESTAMP` | NULL | NULL | 过期时间 |
| `share_time` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 分享时间 |
| `code` | `VARCHAR(10)` | NULL | NULL | 提取码 |
| `show_count` | `INT` | NULL | `0` | 浏览次数 |
| `status` | `SMALLINT` | NULL | `0` | 0有效/1过期/2取消 |
| `tenant_id` | `VARCHAR(10)` | FK | `'default'` | 租户 ID |

表级规则：

- `code` 允许为空；业务层为空时自动生成 5 位随机提取码。
- `show_count` 在提取码校验成功后递增（`checkShareCode` 流程）。

### 9.3.4 `email_code`

| 字段 | 类型 | 约束 | 默认值 | 说明 |
|---|---|---|---|---|
| `email` | `VARCHAR(150)` | PK 组合键, NOT NULL | - | 邮箱 |
| `code` | `VARCHAR(10)` | PK 组合键, NOT NULL | - | 验证码 |
| `create_time` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 创建时间 |
| `status` | `SMALLINT` | NULL | `0` | 0未使用/1已使用 |

### 9.3.5 `tenant_info`

| 字段 | 类型 | 约束 | 默认值 | 说明 |
|---|---|---|---|---|
| `tenant_id` | `VARCHAR(10)` | PK, NOT NULL | - | 租户 ID |
| `tenant_name` | `VARCHAR(150)` | NOT NULL | - | 租户名称 |
| `tenant_code` | `VARCHAR(50)` | UNIQUE, NOT NULL | - | 租户编码 |
| `storage_quota` | `BIGINT` | NULL | `10737418240` | 存储配额（字节） |
| `user_quota` | `INTEGER` | NULL | `100` | 用户配额 |
| `status` | `INTEGER` | NULL | `1` | 0禁用/1启用 |
| `create_time` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 创建时间 |
| `expire_time` | `TIMESTAMP` | NULL | NULL | 到期时间 |

表级规则：

- V5 会插入默认租户 `default`；初始化数据的 `storage_quota=107374182400`（100GB），与字段默认值 10GB 不同。

### 9.3.6 `share_access_log`

| 字段 | 类型 | 约束 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGSERIAL` | PK, NOT NULL | 自增 | 主键 |
| `share_id` | `VARCHAR(20)` | NOT NULL | - | 分享 ID |
| `file_id` | `VARCHAR(20)` | NULL | NULL | 文件 ID |
| `visitor_id` | `VARCHAR(32)` | NULL | NULL | 访问者用户 ID |
| `visitor_ip` | `VARCHAR(50)` | NULL | NULL | 访问 IP |
| `visitor_user_agent` | `VARCHAR(500)` | NULL | NULL | UA |
| `access_type` | `VARCHAR(20)` | NOT NULL | - | 访问类型（VIEW/DOWNLOAD/CHECK_CODE） |
| `access_time` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 访问时间 |
| `success` | `BOOLEAN` | NULL | `true` | 是否成功 |
| `error_message` | `VARCHAR(500)` | NULL | NULL | 错误信息 |

### 9.3.7 `web_vitals_metrics`

| 字段 | 类型 | 约束 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | PK, NOT NULL | Identity | 主键 |
| `metric_name` | `VARCHAR(20)` | NOT NULL | - | 指标名（LCP/INP/CLS/FCP/TTFB） |
| `metric_value` | `DOUBLE PRECISION` | NOT NULL | - | 指标值 |
| `rating` | `VARCHAR(20)` | NOT NULL | - | 评级（good/needs-improvement/poor） |
| `page_url` | `VARCHAR(500)` | NULL | NULL | 页面地址 |
| `user_agent` | `VARCHAR(500)` | NULL | NULL | 浏览器 UA |
| `user_id` | `BIGINT` | NULL | NULL | 登录用户 ID |
| `session_id` | `VARCHAR(100)` | NULL | NULL | 会话 ID |
| `country` | `VARCHAR(50)` | NULL | NULL | 国家 |
| `device_type` | `VARCHAR(20)` | NULL | NULL | 设备类型 |
| `connection_type` | `VARCHAR(20)` | NULL | NULL | 网络类型 |
| `created_at` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 采集时间 |

### 9.3.8 `performance_summary`

| 字段 | 类型 | 约束 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | PK, NOT NULL | Identity | 主键 |
| `metric_name` | `VARCHAR(20)` | NOT NULL | - | 指标名 |
| `period_type` | `VARCHAR(10)` | NOT NULL | - | 聚合周期（hourly/daily/weekly） |
| `period_start` | `TIMESTAMP` | NOT NULL | - | 窗口开始 |
| `period_end` | `TIMESTAMP` | NOT NULL | - | 窗口结束 |
| `sample_count` | `INT` | NOT NULL | `0` | 样本数 |
| `avg_value` | `DOUBLE PRECISION` | NULL | NULL | 均值 |
| `median_value` | `DOUBLE PRECISION` | NULL | NULL | 中位数 |
| `p75_value` | `DOUBLE PRECISION` | NULL | NULL | P75 |
| `p95_value` | `DOUBLE PRECISION` | NULL | NULL | P95 |
| `good_count` | `INT` | NULL | `0` | good 数 |
| `needs_improvement_count` | `INT` | NULL | `0` | needs-improvement 数 |
| `poor_count` | `INT` | NULL | `0` | poor 数 |
| `device_type` | `VARCHAR(20)` | NULL | NULL | 设备维度 |
| `created_at` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | NULL | `CURRENT_TIMESTAMP` | 更新时间（触发器自动改写） |

表级规则：

- V9 已创建 `updated_at` 自动维护触发器：`update_performance_summary_updated_at`。
- 当前仓库未见对应独立 PO/Service 完整写链路，作为预聚合表保留。

## 9.4 枚举值字典（数据库语义）

- `file_info.folder_type`：`0` 文件，`1` 目录
- `file_info.status`：`0` 转码中，`1` 转码失败，`2` 可用
- `file_info.del_flag`：`0` 删除，`1` 回收站，`2` 正常
- `file_share.valid_type`：`0` 1天，`1` 7天，`2` 30天，`3` 永久
- `file_share.status`：`0` 有效，`1` 过期，`2` 取消
- `user_info.status`：`0` 禁用，`1` 启用

语义兼容说明（必须关注）：

- 早期迁移注释与当前业务代码对 `file_info.del_flag` 存在历史差异；联调与 SQL 以 `FileDelFlagEnums` 为准。
- 早期注释曾出现 `file_info.status=3` 的描述，当前业务枚举只使用 `0/1/2`。

## 9.5 全量索引清单（迁移脚本事实）

| 索引名 | 表 | 字段/条件 | 来源 |
|---|---|---|---|
| `idx_file_user` | `file_info` | `(user_id)` | V1 |
| `idx_file_pid` | `file_info` | `(file_pid)` | V1 |
| `idx_file_md5` | `file_info` | `(file_md5)` | V1 |
| `idx_user_pid` | `file_info` | `(user_id, file_pid)` | V1 |
| `idx_file_info_del_flag_recovery_time` | `file_info` | `(del_flag, recovery_time)` | V2 |
| `idx_file_info_user_del_pid_last_update` | `file_info` | `(user_id, del_flag, file_pid, last_update_time DESC)` | V2 |
| `idx_file_info_user_del_category_last_update` | `file_info` | `(user_id, del_flag, file_category, last_update_time DESC)` | V2 |
| `idx_file_info_md5_status` | `file_info` | `(file_md5, status)` | V2 |
| `idx_share_user_status` | `file_share` | `(user_id, status, share_time DESC)` | V3 |
| `idx_share_file` | `file_share` | `(file_id)` | V3 |
| `idx_share_status_expire` | `file_share` | `(status, expire_time)` | V3 |
| `idx_file_info_user_status_del` | `file_info` | `(user_id, status, del_flag)` | V3 |
| `idx_file_info_user_pid_name` | `file_info` | `(user_id, file_pid, file_name)` | V3 |
| `idx_file_info_user_del_size` | `file_info` | `(user_id, del_flag, file_size)` | V3 |
| `idx_file_user_pid_del` | `file_info` | `(user_id, file_pid, del_flag) WHERE del_flag = 2` | V4 |
| `idx_file_user_category_time` | `file_info` | `(user_id, file_category, create_time DESC) WHERE del_flag = 2` | V4 |
| `idx_file_recycle` | `file_info` | `(user_id, recovery_time DESC) WHERE del_flag = 1` | V4 |
| `idx_share_user_time` | `file_share` | `(user_id, share_time DESC)` | V4 |
| `idx_user_email` | `user_info` | `(email) WHERE status = 1` | V4 |
| `idx_user_tenant` | `user_info` | `(tenant_id)` | V5 |
| `idx_file_tenant` | `file_info` | `(tenant_id)` | V5 |
| `idx_share_tenant` | `file_share` | `(tenant_id)` | V5 |
| `idx_user_qq_openid` | `user_info` | `(qq_open_id) WHERE qq_open_id IS NOT NULL` | V6 |
| `idx_file_md5_status_user` | `file_info` | `(file_md5, status, user_id) WHERE status = 2 AND file_md5 IS NOT NULL` | V7 |
| `idx_file_user_status_size` | `file_info` | `(user_id, status, file_size) WHERE del_flag = 0` | V7 |
| `idx_share_expire` | `file_share` | `(expire_time, valid_type) WHERE valid_type = 1` | V7 |
| `idx_share_access_log_share_id` | `share_access_log` | `(share_id)` | V8 |
| `idx_share_access_log_visitor_id` | `share_access_log` | `(visitor_id)` | V8 |
| `idx_share_access_log_access_time` | `share_access_log` | `(access_time)` | V8 |
| `idx_wv_metric_name` | `web_vitals_metrics` | `(metric_name)` | V9 |
| `idx_wv_rating` | `web_vitals_metrics` | `(rating)` | V9 |
| `idx_wv_created_at` | `web_vitals_metrics` | `(created_at)` | V9 |
| `idx_wv_user_id` | `web_vitals_metrics` | `(user_id)` | V9 |
| `idx_wv_device_type` | `web_vitals_metrics` | `(device_type)` | V9 |
| `idx_ps_unique_period` | `performance_summary` | `(metric_name, period_type, period_start, device_type)` UNIQUE | V9 |
| `idx_ps_period_start` | `performance_summary` | `(period_start)` | V9 |

## 9.6 Redis 数据字典（详细）

| Key | Value 结构 | TTL | 写入点 | 读取点 |
|---|---|---|---|---|
| `easypan:download:{code}` | `DownloadFileDto{downloadCode,filePath,fileName}` | 5 分钟 | `saveDownloadCode` | `getDownloadCode` |
| `easypan:syssetting:` | `SysSettingsDto` | 30 分钟 | `saveSysSettingsDto` | `getSysSettingsDto` |
| `easypan:user:spaceuse:{userId}` | `UserSpaceDto` | 6 小时 | `saveUserSpaceUse/resetUserSpaceUse` | `getUserSpaceUse` |
| `easypan:user:file:temp:{userId}{fileId}` | 分片累计大小（Long） | 1 小时 | `saveFileTempSize` | `getFileTempSize` |
| `easypan:jwt:blacklist:{token}` | 空值 | Token 剩余有效期 | `addBlacklistToken` | `isTokenBlacklisted` |
| `easypan:jwt:refresh:{userId}` | `refreshToken` 字符串 | 登录时写入（约 30 天） | `saveRefreshToken` | `getRefreshToken/validateRefreshToken` |

值结构补充：

- `DownloadFileDto`：`downloadCode` `filePath` `fileName`
- `SysSettingsDto`：`registerEmailTitle` `registerEmailContent` `userInitUseSpace`
- `UserSpaceDto`：`useSpace` `totalSpace`

## 9.7 数据生命周期任务

- 回收站清理任务：`FileCleanTask`
  - 触发方式：`fixedDelay`（默认 180000ms）
  - 作用：分批清理过期回收站文件并释放空间
- 分片清理任务：`ChunkCleanupTask`
  - 触发时间：每天 `03:00`
  - 作用：清理过期上传分片目录与状态键
- 缓存预热任务：`CacheWarmupService`
  - 触发时间：每天 `02:00`
  - 作用：预热热点缓存，降低冷启动抖动

## 9.8 数据变更要求

- 任何表结构变更必须新增 Flyway 脚本。
- 变更必须包含：目的、影响范围、回滚说明、验证 SQL。
- 涉及索引变更必须附带 `EXPLAIN` 前后对比。

---

## 10. 开发规范（工程化）

## 10.1 后端规范

- Controller 对外接口优先使用：
  - `@GlobalInterceptor`（登录/管理员/参数校验）
  - `@VerifyParam`（必填、长度、正则）
- 统一响应：`ABaseController.getSuccessResponseVO`
- 统一异常出口：`AGlobalExceptionHandlerController`
- 文件访问必须经过 `@FileAccessCheck`
- 上传安全校验必须保留：
  - 扩展名黑名单（`FileTypeValidator.isDangerousFileType`）
  - Magic Number 校验（`FileTypeValidator.validateFileType`）
  - 并发上传限流（`UploadRateLimiter`）
- 密码策略：
  - 注册/重置使用 BCrypt
  - 登录兼容旧 MD5 并自动升级为 BCrypt

## 10.2 前端规范

- API 调用统一经 `src/utils/Request.ts`（含 Token 自动刷新）。
- 业务请求统一收敛在 `src/services/*`，禁止页面散落拼接 URL。
- 所有接口类型定义必须落在 `src/types/*`。
- 新增页面文案同步更新 `src/locales/zh-CN.ts` 与 `src/locales/en-US.ts`。
- 路由守卫权限逻辑统一维护在 `src/router/index.ts`。

## 10.3 数据库规范

- 禁止手工改库绕过 Flyway。
- SQL 变更优先幂等写法：`IF NOT EXISTS`。
- 大字段/大表变更必须附带性能评估和回滚方案。

## 10.4 日志规范

- 使用结构化日志（JSON）和文本日志双输出。
- 日志文件路径：`{project.folder}/logs/`
- 默认滚动策略：按天+大小滚动，保留 30 天（见 `logback-spring.xml`）。
- 禁止打印密码、Token、邮箱验证码、密钥。

---

## 11. 安全基线与威胁模型

在处理“用户数据/权限/认证/分享”相关代码前，先明确以下威胁并给出防护措施：

1. 认证绕过  
- 风险：未登录访问受限接口。  
- 防护：`@GlobalInterceptor(checkLogin/checkAdmin)` + JWT 黑名单校验。  

2. 横向越权/租户越权  
- 风险：用户读取或下载他人文件。  
- 防护：`@FileAccessCheck`、`userId` 绑定查询、`X-Tenant-Id` 与 `TenantContextHolder`。  

3. 恶意上传与存储污染  
- 风险：脚本木马/伪造文件类型上传。  
- 防护：扩展名黑名单 + 文件头校验 + 上传限流 + 分片校验。  

红线（不可违反）：

- 禁止执行未审计远程脚本（例如 `curl | bash`）。
- 禁止泄露任何敏感配置到日志、PR、Issue。
- 禁止未经授权执行破坏性命令（删卷、清库、强推等）。

---

## 12. 质量门禁与验证命令

## 12.1 CI / 构建基线（以命令为准）

- 说明：当前仓库未包含仓库级 `.github/workflows/*.yml`；以下步骤可用于本地或任意 CI 平台（例如 GitHub Actions）。
- `backend-build`：`mvn -DskipTests clean compile` + `mvn test`
- `frontend-build`：`npm ci` + `npm run type-check` + `npm run build`

## 12.2 本地强制验证（提交前）

```powershell
# 1) Compose 配置合法性
docker compose -f ops\docker\docker-compose.yml config

# 2) 后端编译与测试
cd backend
mvn -DskipTests clean compile
mvn test
mvn checkstyle:check
mvn spotbugs:check

# 3) 前端类型检查、构建、Lint
cd ..\frontend
npm ci
npm run type-check
npm run build
npm run lint

# 4) 可选 E2E
npx playwright test
```

验证输出要求：

- 编译与测试命令退出码必须为 0。
- 如有失败，必须在 PR 描述中说明原因和处置计划。
- 禁止写“理论可行但未执行”的验收结论。

---

## 13. 监控与运维规范

## 13.1 指标与健康检查

- 健康检查：`/api/actuator/health`
- 指标列表：`/api/actuator/metrics`
- Prometheus 拉取：`/api/actuator/prometheus`（容器内 `backend:7090`）

## 13.2 监控配置文件

- Prometheus：`ops/monitoring/prometheus.yml`
- 告警规则：`ops/monitoring/alert_rules.yml`
- Grafana 仪表板：`ops/monitoring/grafana/dashboards/*.json`

## 13.3 告警示例（已配置）

- `HighAPILatency`
- `HighErrorRate`
- `LowCacheHitRate`
- `HighDatabaseConnections`
- `DatabaseConnectionPoolExhausted`
- `RedisConnectionFailure`
- `PostgreSQLDown`
- `VirtualThreadStarvation`

---

## 14. Git 与 PR 纪律

- 提交信息必须使用 Conventional Commits：
  - `feat` `fix` `refactor` `docs` `test` `chore` `ci` `build`
- 单个提交/PR 只做一件事，禁止功能与重构混杂。
- PR 描述必须包含：
  - What（做了什么）
  - Why（为什么）
  - How to verify（命令 + 期望结果）
- 禁止无意义大范围格式化。
- 禁止强推受保护分支（除非明确授权）。

---

## 15. Agent 执行清单（Start / During / End）

## 15.1 Start

- 明确需求边界、影响文件、回滚点。
- 标记是否影响接口、数据库、部署、监控、安全。
- 先读相关实现再动手，禁止盲改。

## 15.2 During

- 小步提交，每一步都做最小可验证检查。
- 发现与预期不一致的仓库状态，先暂停并确认。
- 不修改与任务无关文件。

## 15.3 End

- 汇总变更文件与行为影响。
- 列出执行过的验证命令及结果。
- 明确剩余风险与后续建议。

---

## 16. 事实来源（本文件依据）

以下文件是本手册的主要事实来源，若冲突以代码为准：

- `backend/pom.xml`
- `frontend/package.json`
- `frontend/vite.config.ts`
- `ops/docker/docker-compose.yml`
- `ops/docker/docker-compose.simple.yml`
- `ops/local/setup.ps1`
- `ops/local/startup.ps1`
- `ops/docker/deploy_docker.ps1`
- `ops/tools/health_check.ps1`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/db/migration/*.sql`
- `backend/src/main/java/com/easypan/controller/*.java`
- `backend/src/main/java/com/easypan/config/*.java`
- `backend/src/main/java/com/easypan/component/*.java`
- `backend/src/main/java/com/easypan/entity/po/*.java`
