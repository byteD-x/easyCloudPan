# Ops 目录说明

`ops/` 用于统一存放部署与启动相关脚本，并按职责分层。

## 目录结构

- `ops/local/`：本地开发启动脚本
- `ops/docker/`：Docker 部署脚本与 Compose 配置
- `ops/lib/`：脚本公共函数
- `ops/tools/`：辅助工具脚本
- `ops/monitoring/`：监控配置（Prometheus、Grafana、告警规则）

## 入口脚本

| 脚本 | 用途 | 说明 |
|------|------|------|
| `ops/local/setup.ps1` | 本地环境初始化 | 首次运行必须执行 |
| `ops/local/startup.ps1` | 本地一键启动 | 启动开发环境 |
| `ops/docker/deploy_docker.ps1` | Docker 全栈部署 | 生产环境推荐 |
| `ops/docker/stop_docker.ps1` | 停止 Docker 服务 | - |
| `ops/tools/health_check.ps1` | 服务健康检查 | 排查问题使用 |

> **提示**：Windows 用户推荐使用 PowerShell 脚本（`.ps1`），批处理脚本（`.bat`）也可用。

## Docker Compose 文件

| 文件 | 用途 | 包含服务 |
|------|------|----------|
| `docker-compose.simple.yml` | 精简版 | PostgreSQL, Redis, MinIO |
| `docker-compose.yml` | 完整版 | 精简版 + Backend + Frontend + 监控 |

新手推荐使用 `docker-compose.simple.yml` 配合本地开发模式。

两个 Compose 文件都要求显式配置数据库、Redis 和 MinIO 密码；完整栈还要求至少 32 字节的 `JWT_SECRET`。请从 `.env.example` 复制配置模板并为每项生成独立随机值，不要使用固定默认凭据。

## 监控配置

### Prometheus 配置
- 配置文件：`ops/monitoring/prometheus.yml`
- 采集目标：
  - `easypan-backend`：后端应用指标（`/api/actuator/prometheus`）
  - `postgres`：PostgreSQL 数据库指标
  - `redis`：Redis 缓存指标

### Grafana 仪表板
- 仪表板目录：`ops/monitoring/grafana/dashboards/`
- 包含仪表板：
  - `system-overview.json`：系统概览（CPU、内存、磁盘、网络）
  - `performance.json`：性能监控（API 响应时间、数据库查询、缓存命中率）
  - `business.json`：业务指标（文件上传、用户活跃度、存储空间）

### 告警规则
- 规则文件：`ops/monitoring/alert_rules.yml`
- 告警类型：
  - **P0（严重）**：API 错误率 > 5%、数据库连接池耗尽、Redis/PostgreSQL 宕机
  - **P1（警告）**：API P95 响应时间 > 2s、缓存命中率 < 70%、高内存使用
  - **P2（提示）**：慢查询、虚拟线程数量过高

## 故障排查

### 常见问题诊断

#### 后端无法启动
1. 检查依赖服务状态：
   ```bash
   docker compose ps
   docker compose logs postgres redis minio
   ```
2. 检查端口占用：
   ```bash
   netstat -ano | findstr "7090 5432 6379 9000"
   ```
3. 检查配置文件：确认 `ops/docker/.env` 配置正确

#### 数据库连接失败
1. 检查 PostgreSQL 状态：`docker compose logs postgres`
2. 检查连接配置：`POSTGRES_HOST`、`POSTGRES_PORT`、`POSTGRES_USER`、`POSTGRES_PASSWORD`
3. 检查连接池：访问 `/api/actuator/health` 查看数据库连接状态

#### Redis 连接失败
1. 检查 Redis 状态：`docker compose logs redis`
2. 检查密码配置：`SPRING_DATA_REDIS_PASSWORD`
3. 测试连接：`redis-cli -h localhost -p 6379 -a <password> ping`

#### 文件上传失败
1. 检查 MinIO 状态：`docker compose logs minio`
2. 检查存储桶：访问 MinIO Console `http://localhost:9001`
3. 检查磁盘空间：`df -h`
4. 检查文件大小限制：`spring.servlet.multipart.max-file-size`

#### 性能问题排查
1. 查看 Prometheus 指标：`http://localhost:7090/api/actuator/prometheus`
2. 检查应用日志：`backend/file/logs/easypan.log`
3. 检查访问日志：`backend/file/logs/easypan-access.log`
4. 检查错误日志：`backend/file/logs/easypan-error.log`
5. 检查结构化日志：`backend/file/logs/easypan-json.log`
3. 检查缓存命中率：Grafana 性能仪表板
4. 检查虚拟线程状态：`/api/actuator/health/custom`

### 日志查看
- 应用日志：`backend/file/logs/easypan.log`
- 访问日志：`backend/file/logs/easypan-access.log`
- 错误日志：`backend/file/logs/easypan-error.log`
- 结构化日志：`backend/file/logs/easypan-json.log`
- 历史归档：`backend/file/logs/archive/`
- Docker 日志：`docker compose logs <service>`
- 实时日志：`docker compose logs -f --tail=100 backend`

## 性能调优

### JVM 调优
```bash
# 推荐 JVM 参数（Java 21 虚拟线程）
-Xms512m -Xmx2g -XX:+UseZGC -XX:+ZGenerational
```

### 数据库调优
```sql
-- 查看慢查询
SELECT * FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 10;

-- 查看索引使用情况
SELECT * FROM pg_stat_user_indexes WHERE idx_scan = 0;

-- 分析查询计划
EXPLAIN ANALYZE SELECT * FROM file_info WHERE user_id = 'xxx' AND del_flag = 0;
```

### Redis 调优
```bash
# 查看内存使用
redis-cli INFO memory

# 查看缓存命中率
redis-cli INFO stats | grep keyspace

# 清理过期 Key
redis-cli SCAN 0 MATCH "easypan:*" COUNT 1000
```

### 连接池调优
```yaml
# HikariCP 配置（虚拟线程场景）
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      idle-timeout: 300000
      connection-timeout: 30000
```

### 缓存 TTL 调优
| 数据类型 | 推荐 TTL | 说明 |
|----------|----------|------|
| 用户信息 | 1 小时 | 变更频率低 |
| 文件列表 | 5 分钟 | 需要及时更新 |
| 系统配置 | 30 分钟 | 很少变更 |
| 热点数据 | 15 分钟 | 高频访问 |
