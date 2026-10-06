# EasyCloudPan Docker 部署教程

本教程用于在本地或测试机上用 Docker 一次启动完整栈。

## 1. 前置条件

- 已安装 Docker Desktop
- `docker compose` 可用

检查命令：
```bat
docker --version
docker compose version
```

## 2. 可选：自定义环境变量

```bat
copy ops\docker\.env.example ops\docker\.env
```

编辑 `ops\docker\.env` 后再部署：为 `POSTGRES_PASSWORD`、`REDIS_PASSWORD`、`MINIO_ROOT_PASSWORD`、`JWT_SECRET`、`JASYPT_ENCRYPTOR_PASSWORD` 和 `GRAFANA_ADMIN_PASSWORD` 分别填入不同的随机密钥。可使用 `openssl rand -hex 32` 生成；`JWT_SECRET` 至少需要 32 字节。模板中的敏感值留空，未配置时 Compose 会拒绝启动，避免使用公开的示例凭据。

常见可配置项：
- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`
- `MINIO_ROOT_PASSWORD`
- `JASYPT_ENCRYPTOR_PASSWORD`
- `GRAFANA_ADMIN_PASSWORD`
- `QQ_APP_ID` / `QQ_APP_KEY`
- `SPRING_MAIL_PASSWORD`

## 3. 一键部署

```bat
ops\docker\deploy_docker.bat
```

如需跳过镜像构建，直接启动已有镜像：
```bat
ops\docker\deploy_docker.bat --no-build
```

等价命令：
```bat
docker compose -f ops/docker/docker-compose.yml up -d --build
```

## 4. 验证

1. 查看容器状态：
```bat
docker compose -f ops/docker/docker-compose.yml ps
```

2. 访问地址：
- 前端：`http://localhost:8080`
- 后端：`http://localhost:7090/api`
- MinIO Console：`http://localhost:9001`

3. 查看后端日志：
```bat
docker compose -f ops/docker/docker-compose.yml logs -f backend
```

## 5. 停止与清理

停止服务：
```bat
ops\docker\stop_docker.bat
```

停止并删除卷（危险操作）：
```bat
ops\docker\stop_docker.bat --volumes
```

等价命令：
```bat
docker compose -f ops/docker/docker-compose.yml down
```

删除容器和卷（危险操作，会清空数据）：
```bat
docker compose -f ops/docker/docker-compose.yml down -v
```

## 6. 常见问题

### 6.1 backend 启动失败
- 先看日志：`docker compose -f ops/docker/docker-compose.yml logs backend`
- 确认 `postgres`、`redis`、`minio-init` 状态正常
- 检查 `.env` 中密码与容器参数是否一致

### 6.2 前端无法访问后端
- 检查后端容器是否健康
- 确认 `7090` 端口没有被占用

### 6.3 MinIO 上传失败
- 确认 `minio-init` 执行成功
- 确认桶名 `MINIO_BUCKET` 与后端配置一致（默认 `easypan`）
