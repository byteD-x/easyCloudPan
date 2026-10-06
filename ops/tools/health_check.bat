@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
for %%i in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fi"
set "DOCKER_DIR=%REPO_ROOT%\ops\docker"

echo ===========================================================================
echo EasyCloudPan 服务健康检查
echo ===========================================================================
echo.

set "ALL_OK=1"

echo [1/5] 检查 Docker 容器状态...
docker compose -f "%DOCKER_DIR%\docker-compose.yml" ps 2>nul
if errorlevel 1 (
    echo [WARN] 无法获取容器状态，可能 Docker 未运行或容器未启动
    set "ALL_OK=0"
)
echo.

echo [2/5] 检查 PostgreSQL 数据库...
docker exec easypan-postgres pg_isready -U postgres -d easypan >nul 2>nul
if errorlevel 1 (
    echo [FAIL] PostgreSQL 未就绪
    set "ALL_OK=0"
) else (
    echo [OK] PostgreSQL 运行正常
)
echo.

echo [3/5] 检查 Redis...
if not defined REDIS_PASSWORD (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%DOCKER_DIR%\.env") do if "%%A"=="REDIS_PASSWORD" set "REDIS_PASSWORD=%%B"
)
if not defined REDIS_PASSWORD (
    echo [FAIL] REDIS_PASSWORD 未配置
    set "ALL_OK=0"
) else (
    docker exec -e REDISCLI_AUTH="%REDIS_PASSWORD%" easypan-redis redis-cli ping >nul 2>nul
    if errorlevel 1 (
        echo [FAIL] Redis 未就绪
        set "ALL_OK=0"
    ) else (
        echo [OK] Redis 运行正常
    )
)
echo.

echo [4/5] 检查 MinIO...
curl -s -o nul -w "%%{http_code}" http://localhost:9000/minio/health/live 2>nul | findstr "200" >nul
if errorlevel 1 (
    echo [FAIL] MinIO 未就绪
    set "ALL_OK=0"
) else (
    echo [OK] MinIO 运行正常
)
echo.

echo [5/5] 检查后端 API...
curl -s -o nul -w "%%{http_code}" http://localhost:7090/api/actuator/health 2>nul | findstr "200" >nul
if errorlevel 1 (
    echo [WARN] 后端 API 未响应（如果是本地开发模式，请确认后端已启动）
) else (
    echo [OK] 后端 API 运行正常
)
echo.

echo ===========================================================================
if "%ALL_OK%"=="1" (
    echo [SUCCESS] 所有基础设施服务运行正常！
    echo.
    echo 访问地址：
    echo   前端: http://localhost:8080
    echo   后端: http://localhost:7090/api
    echo   MinIO: http://localhost:9001
) else (
    echo [FAILED] 部分服务未就绪，请检查上方输出
    echo.
    echo 排查建议：
    echo   1. 确认 Docker Desktop 正在运行
    echo   2. 运行 ops\local\startup.bat 启动服务
    echo   3. 查看日志: docker compose -f ops\docker\docker-compose.yml logs
)
echo ===========================================================================

exit /b 0
