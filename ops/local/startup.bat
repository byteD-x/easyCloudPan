@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
for %%i in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fi"
set "DOCKER_DIR=%REPO_ROOT%\ops\docker"
cd /d "%REPO_ROOT%"

set "ENV_FILE=%REPO_ROOT%\ops\docker\.env"
set "OPEN_BROWSER=1"
set "REQUEST_HELP=0"

call :parse_args %*
if errorlevel 1 exit /b 1
if "%REQUEST_HELP%"=="1" (
    call :print_usage
    exit /b 0
)

if not exist "%ENV_FILE%" (
    echo [ERROR] %ENV_FILE% not found.
    echo Run ops\local\setup.bat first to create it.
    exit /b 1
)

echo [INFO] Loading environment variables from %ENV_FILE%...
for /f "usebackq eol=# tokens=1* delims==" %%A in ("%ENV_FILE%") do (
    set "%%A=%%B"
)

for %%V in (POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD REDIS_PASSWORD MINIO_ROOT_USER MINIO_ROOT_PASSWORD MINIO_BUCKET JWT_SECRET JASYPT_ENCRYPTOR_PASSWORD) do (
    if not defined %%V (
        echo [ERROR] Missing required environment variable: %%V
        exit /b 1
    )
)

if not defined GRAFANA_ADMIN_PASSWORD (
    for /f "delims=" %%G in ('powershell -NoProfile -Command "[guid]::NewGuid().ToString('N') + 'Aa!'"') do set "GRAFANA_ADMIN_PASSWORD=%%G"
    echo [WARN] GRAFANA_ADMIN_PASSWORD 未配置，本次会话已生成临时值
)

REM Map Docker environment variables to Spring Boot properties
set "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/%POSTGRES_DB%"
set "SPRING_DATASOURCE_USERNAME=%POSTGRES_USER%"
set "SPRING_DATASOURCE_PASSWORD=%POSTGRES_PASSWORD%"
if not defined SPRING_DATA_REDIS_HOST set "SPRING_DATA_REDIS_HOST=localhost"
if not defined SPRING_DATA_REDIS_PORT set "SPRING_DATA_REDIS_PORT=6379"
set "SPRING_DATA_REDIS_PASSWORD=%REDIS_PASSWORD%"
set "MINIO_ENDPOINT=http://localhost:9000"
set "MINIO_ACCESS_KEY=%MINIO_ROOT_USER%"
set "MINIO_SECRET_KEY=%MINIO_ROOT_PASSWORD%"
set "MINIO_BUCKET_NAME=%MINIO_BUCKET%"

echo ===========================================================================
echo EasyCloudPan One-Click Local Start
echo ===========================================================================

call :check_prerequisites
if errorlevel 1 exit /b 1

echo [1/3] Starting infrastructure containers (PostgreSQL, Redis, MinIO)...
pushd "%DOCKER_DIR%"
docker compose up -d postgres redis minio minio-init
set "COMPOSE_EXIT=%ERRORLEVEL%"
popd
if not "%COMPOSE_EXIT%"=="0" (
    echo [ERROR] Failed to start infrastructure containers.
    exit /b 1
)

echo [2/4] Starting backend in a new window...
set "BACKEND_CMD=chcp 65001 >nul && cd /d "%REPO_ROOT%\backend" && set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dproject.folder=%PROJECT_FOLDER%" && set "SPRING_DATASOURCE_URL=%SPRING_DATASOURCE_URL%" && set "SPRING_DATASOURCE_USERNAME=%SPRING_DATASOURCE_USERNAME%" && set "SPRING_DATASOURCE_PASSWORD=%SPRING_DATASOURCE_PASSWORD%" && set "SPRING_DATA_REDIS_HOST=%SPRING_DATA_REDIS_HOST%" && set "SPRING_DATA_REDIS_PORT=%SPRING_DATA_REDIS_PORT%" && set "SPRING_DATA_REDIS_PASSWORD=%SPRING_DATA_REDIS_PASSWORD%" && set "MINIO_ENDPOINT=%MINIO_ENDPOINT%" && set "MINIO_ACCESS_KEY=%MINIO_ACCESS_KEY%" && set "MINIO_SECRET_KEY=%MINIO_SECRET_KEY%" && set "MINIO_BUCKET_NAME=%MINIO_BUCKET_NAME%" && set "JWT_SECRET=%JWT_SECRET%" && set "JASYPT_ENCRYPTOR_PASSWORD=%JASYPT_ENCRYPTOR_PASSWORD%" && set "SPRING_MAIL_HOST=%SPRING_MAIL_HOST%" && set "SPRING_MAIL_PORT=%SPRING_MAIL_PORT%" && set "SPRING_MAIL_USERNAME=%SPRING_MAIL_USERNAME%" && set "SPRING_MAIL_PASSWORD=%SPRING_MAIL_PASSWORD%" && set "ADMIN_EMAILS=%ADMIN_EMAILS%" && set "LOG_ROOT_LEVEL=%LOG_ROOT_LEVEL%" && set "DEV_MODE=%DEV_MODE%" && set "QQ_APP_ID=%QQ_APP_ID%" && set "QQ_APP_KEY=%QQ_APP_KEY%" && set "OAUTH_GITHUB_CLIENT_ID=%OAUTH_GITHUB_CLIENT_ID%" && set "OAUTH_GITHUB_CLIENT_SECRET=%OAUTH_GITHUB_CLIENT_SECRET%" && set "OAUTH_GITEE_CLIENT_ID=%OAUTH_GITEE_CLIENT_ID%" && set "OAUTH_GITEE_CLIENT_SECRET=%OAUTH_GITEE_CLIENT_SECRET%" && set "OAUTH_GOOGLE_CLIENT_ID=%OAUTH_GOOGLE_CLIENT_ID%" && set "OAUTH_GOOGLE_CLIENT_SECRET=%OAUTH_GOOGLE_CLIENT_SECRET%" && set "OAUTH_MICROSOFT_CLIENT_ID=%OAUTH_MICROSOFT_CLIENT_ID%" && set "OAUTH_MICROSOFT_CLIENT_SECRET=%OAUTH_MICROSOFT_CLIENT_SECRET%" && mvn spring-boot:run -DskipTests"
start "EasyCloudPan Backend" cmd /k "%BACKEND_CMD%"

echo [3/5] Waiting for backend to be ready...
powershell -Command "$attempt=0; while($attempt -lt 60){$attempt++; try{$r=Invoke-WebRequest -Uri 'http://localhost:7090/api/actuator/health' -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue; if($r.StatusCode -eq 200){Write-Host '[OK] Backend is ready!' -ForegroundColor Green; exit 0}}catch{} Write-Host \"  Waiting... ($attempt/60)\"; Start-Sleep 2}; Write-Host '[WARN] Backend did not become ready within timeout.' -ForegroundColor Yellow"

echo [4/5] Starting frontend in a new window...
start "EasyCloudPan Frontend" cmd /k "cd /d "%REPO_ROOT%\frontend" && npm run dev"

echo [5/5] Opening browser...
if "%OPEN_BROWSER%"=="1" (
    timeout /t 2 >nul
    start "" "http://localhost:8080"
)

echo ===========================================================================
echo Started.
echo Frontend: http://localhost:8080
echo Backend : http://localhost:7090/api
echo MinIO   : http://localhost:9001
echo ===========================================================================
exit /b 0

:check_prerequisites
where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Missing dependency: Java ^(JDK 21+^)
    exit /b 1
)
where javac >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Missing dependency: JDK compiler
    exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Missing dependency: Maven
    exit /b 1
)
where node >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Missing dependency: Node.js ^(20+^)
    exit /b 1
)
where npm >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Missing dependency: npm
    exit /b 1
)
where docker >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Missing dependency: Docker Desktop
    exit /b 1
)
docker compose version >nul 2>nul
if errorlevel 1 (
    echo [ERROR] docker compose is unavailable. Please enable Docker Compose V2.
    exit /b 1
)

set "JAVAC_VERSION="
set "JAVA_MAJOR="
for /f "tokens=2 delims= " %%v in ('javac -version 2^>^&1') do set "JAVAC_VERSION=%%v"
for /f "tokens=1 delims=." %%v in ("%JAVAC_VERSION%") do set "JAVA_MAJOR=%%v"
if "%JAVA_MAJOR%"=="" (
    echo [ERROR] Unable to detect Java version.
    exit /b 1
)
if %JAVA_MAJOR% LSS 21 (
    echo [ERROR] JDK 21+ is required. Current javac version: %JAVAC_VERSION%
    exit /b 1
)

set "NODE_MAJOR="
for /f "delims=." %%v in ('node -v') do set "NODE_MAJOR=%%v"
set "NODE_MAJOR=%NODE_MAJOR:~1%"
if "%NODE_MAJOR%"=="" (
    echo [ERROR] Unable to detect Node.js version.
    exit /b 1
)
if %NODE_MAJOR% LSS 20 (
    echo [ERROR] Node.js 20+ is required. Current major version: %NODE_MAJOR%
    exit /b 1
)
exit /b 0

:parse_args
if "%~1"=="" exit /b 0

if /I "%~1"=="--no-browser" (
    set "OPEN_BROWSER=0"
    shift
    goto :parse_args
)

if /I "%~1"=="--help" (
    set "REQUEST_HELP=1"
    exit /b 0
)

echo [ERROR] Unknown argument: %~1
call :print_usage
exit /b 1

:print_usage
echo Usage: ops\local\startup.bat [--no-browser]
echo.
echo   --no-browser   Do not open browser automatically
exit /b 0
