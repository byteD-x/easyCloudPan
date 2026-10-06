# EasyCloudPan Local One-Click Startup Script
# Usage: .\ops\local\startup.ps1 [-NoBrowser] [-AllowDevMode] [-ExposeCaptcha] [-EnableMailDebug]

param(
    [switch]$NoBrowser,
    [switch]$AllowDevMode,
    [switch]$ExposeCaptcha,
    [switch]$EnableMailDebug
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Get-Item $PSScriptRoot).Parent.Parent.FullName
$DockerDir = "$RepoRoot\ops\docker"
$EnvFile = "$DockerDir\.env"

function Print-Header {
    param([string]$Title)
    Write-Host ("=" * 74)
    Write-Host $Title
    Write-Host ("=" * 74)
}

# Check config file
if (-not (Test-Path $EnvFile)) {
    Write-Host "[ERROR] $EnvFile not found." -ForegroundColor Red
    Write-Host "Run .\ops\local\setup.ps1 first to create it."
    exit 1
}

Print-Header "EasyCloudPan One-Click Local Start"

# Load environment variables
Write-Host "[INFO] Loading environment variables from $EnvFile..."
Get-Content $EnvFile | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
    $parts = $_.Split('=', 2)
    [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
}

$requiredEnv = @(
    "POSTGRES_DB",
    "POSTGRES_USER",
    "POSTGRES_PASSWORD",
    "REDIS_PASSWORD",
    "MINIO_ROOT_USER",
    "MINIO_ROOT_PASSWORD",
    "MINIO_BUCKET",
    "JWT_SECRET",
    "JASYPT_ENCRYPTOR_PASSWORD"
)
$missingEnv = @()
foreach ($name in $requiredEnv) {
    $value = [Environment]::GetEnvironmentVariable($name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        $missingEnv += $name
    }
}

if ($missingEnv.Count -gt 0) {
    Write-Host "[ERROR] Missing required environment variable(s): $($missingEnv -join ', ')" -ForegroundColor Red
    Write-Host "Please update $EnvFile or run .\ops\local\setup.ps1 first."
    exit 1
}

# docker compose parses the full file and may require Grafana password even when only infra services are started.
if ([string]::IsNullOrWhiteSpace($env:GRAFANA_ADMIN_PASSWORD)) {
    $env:GRAFANA_ADMIN_PASSWORD = ([Guid]::NewGuid().ToString("N") + "Aa!")
    Write-Host "[WARN] GRAFANA_ADMIN_PASSWORD is missing in .env. A temporary runtime value has been generated for this session only."
}

if (-not $AllowDevMode) {
    # Enforce real authentication flow by default in local one-click startup.
    $env:DEV_MODE = "false"
    Write-Host "[INFO] DEV_MODE is forced to false for auth verification. Use -AllowDevMode to keep bypass."
}
else {
    Write-Host "[WARN] DEV_MODE from .env is kept because -AllowDevMode is specified."
}

if ($ExposeCaptcha) {
    # Convenience for local automation (smoke tests): expose captcha code in response header.
    $env:CAPTCHA_DEBUG_HEADER = "true"
    Write-Host "[WARN] CAPTCHA_DEBUG_HEADER is enabled. /api/checkCode will include X-EasyPan-CheckCode header."
}
else {
    $env:CAPTCHA_DEBUG_HEADER = "false"
}

if ($EnableMailDebug) {
    $env:SPRING_MAIL_DEBUG = "true"
    Write-Host "[WARN] SPRING_MAIL_DEBUG is enabled. Jakarta Mail protocol logs will be verbose."
}
else {
    # Keep startup logs concise by default.
    $env:SPRING_MAIL_DEBUG = "false"
}

if ([string]::IsNullOrWhiteSpace($env:LOG_ROOT_LEVEL)) {
    $env:LOG_ROOT_LEVEL = "info"
}

# Local dev (Windows) should not use the Docker-style PROJECT_FOLDER (e.g. /data/easypan/),
# otherwise multipart uploads may be written relative to Tomcat's temp dir.
$projectFolder = ($RepoRoot -replace '\\\\', '/') + '/backend/file/'
$env:PROJECT_FOLDER = $projectFolder
Write-Host "[INFO] PROJECT_FOLDER is set to $projectFolder"

if ([string]::IsNullOrWhiteSpace($env:LOG_FILE_DIR)) {
    $env:LOG_FILE_DIR = "$projectFolder" + "logs"
}
if ([string]::IsNullOrWhiteSpace($env:LOG_ARCHIVE_DIR)) {
    $env:LOG_ARCHIVE_DIR = ($env:LOG_FILE_DIR.TrimEnd('/')) + "/archive"
}
if ([string]::IsNullOrWhiteSpace($env:LOG_MAX_FILE_SIZE)) {
    $env:LOG_MAX_FILE_SIZE = "50MB"
}
if ([string]::IsNullOrWhiteSpace($env:LOG_MAX_HISTORY)) {
    $env:LOG_MAX_HISTORY = "30"
}
if ([string]::IsNullOrWhiteSpace($env:LOG_TOTAL_SIZE_CAP)) {
    $env:LOG_TOTAL_SIZE_CAP = "5GB"
}
if ([string]::IsNullOrWhiteSpace($env:LOG_CLEAN_HISTORY_ON_START)) {
    $env:LOG_CLEAN_HISTORY_ON_START = "false"
}
Write-Host "[INFO] LOG_FILE_DIR is set to $($env:LOG_FILE_DIR)"
Write-Host "[INFO] LOG_ARCHIVE_DIR is set to $($env:LOG_ARCHIVE_DIR)"

# Set Spring Boot properties
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5433/$env:POSTGRES_DB"
$env:SPRING_DATASOURCE_USERNAME = $env:POSTGRES_USER
$env:SPRING_DATASOURCE_PASSWORD = $env:POSTGRES_PASSWORD
$env:SPRING_DATA_REDIS_PASSWORD = $env:REDIS_PASSWORD
$env:MINIO_ENDPOINT = "http://localhost:9000"
$env:MINIO_ACCESS_KEY = $env:MINIO_ROOT_USER
$env:MINIO_SECRET_KEY = $env:MINIO_ROOT_PASSWORD
$env:MINIO_BUCKET_NAME = $env:MINIO_BUCKET

# Start infrastructure containers
Write-Host "[1/3] Starting infrastructure containers (PostgreSQL, Redis, MinIO)..."
Push-Location $DockerDir
docker compose up -d postgres redis minio minio-init
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Host "[ERROR] Failed to start infrastructure containers." -ForegroundColor Red
    exit 1
}
Pop-Location

# Wait a bit for infrastructure to be ready
Write-Host "[2/3] Waiting for infrastructure to be ready..."
Start-Sleep -Seconds 5
Write-Host "[OK] Infrastructure should be ready." -ForegroundColor Green

# Create backend startup script
$backendScript = "$env:TEMP\easypan-backend.ps1"
$pgDb = $env:POSTGRES_DB
$pgUser = $env:POSTGRES_USER
$pgPass = $env:POSTGRES_PASSWORD
$redisPass = $env:REDIS_PASSWORD
$minioUser = $env:MINIO_ROOT_USER
$minioPass = $env:MINIO_ROOT_PASSWORD
$minioBucket = $env:MINIO_BUCKET
$backendContent = @"
chcp 65001 > `$null
`$env:SPRING_PROFILES_ACTIVE='local'
`$env:SERVER_PORT='7090'
`$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/$pgDb'
`$env:SPRING_DATASOURCE_USERNAME='$pgUser'
`$env:SPRING_DATASOURCE_PASSWORD='$pgPass'
`$env:SPRING_DATA_REDIS_PASSWORD='$redisPass'
`$env:MINIO_ENDPOINT='http://localhost:9000'
`$env:MINIO_ACCESS_KEY='$minioUser'
`$env:MINIO_SECRET_KEY='$minioPass'
`$env:MINIO_BUCKET_NAME='$minioBucket'
`$env:JWT_SECRET='$env:JWT_SECRET'
`$env:JASYPT_ENCRYPTOR_PASSWORD='$env:JASYPT_ENCRYPTOR_PASSWORD'
`$env:CAPTCHA_DEBUG_HEADER='$env:CAPTCHA_DEBUG_HEADER'
`$env:PROJECT_FOLDER='$projectFolder'
`$env:DEV_MODE='$env:DEV_MODE'
`$env:SPRING_MAIL_DEBUG='$env:SPRING_MAIL_DEBUG'
`$env:LOG_ROOT_LEVEL='$env:LOG_ROOT_LEVEL'
`$env:LOG_FILE_DIR='$env:LOG_FILE_DIR'
`$env:LOG_ARCHIVE_DIR='$env:LOG_ARCHIVE_DIR'
`$env:LOG_MAX_FILE_SIZE='$env:LOG_MAX_FILE_SIZE'
`$env:LOG_MAX_HISTORY='$env:LOG_MAX_HISTORY'
`$env:LOG_TOTAL_SIZE_CAP='$env:LOG_TOTAL_SIZE_CAP'
`$env:LOG_CLEAN_HISTORY_ON_START='$env:LOG_CLEAN_HISTORY_ON_START'
`$env:MAVEN_OPTS='-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dmail.debug=$env:SPRING_MAIL_DEBUG'
cd '$RepoRoot\backend'
mvn -q "-Dmaven.test.skip=true" "-Dcheckstyle.skip=true" "-Dspotbugs.skip=true" spring-boot:run
"@
Set-Content -Path $backendScript -Value $backendContent -Encoding UTF8

# Start backend
Write-Host "[3/5] Starting backend in a new window..."
Start-Process powershell -ArgumentList "-NoExit", "-File", $backendScript

# Wait for backend to be ready
Write-Host "[4/5] Waiting for backend to be ready..."
$backendReady = $false
$maxAttempts = 60
$attempt = 0
while (-not $backendReady -and $attempt -lt $maxAttempts) {
    $attempt++
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:7090/api/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            $backendReady = $true
            Write-Host "[OK] Backend is ready!" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "  Waiting... ($attempt/$maxAttempts)"
        Start-Sleep -Seconds 2
    }
}

if (-not $backendReady) {
    Write-Host "[WARN] Backend did not become ready within timeout. Starting frontend anyway..." -ForegroundColor Yellow
}

# Start frontend
Write-Host "[5/5] Starting frontend in a new window..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$RepoRoot\frontend'; npm run dev"

# Open browser
if (-not $NoBrowser) {
    Start-Sleep -Seconds 2
    Start-Process "http://localhost:8080"
}

Write-Host ("=" * 74)
Write-Host "Started." -ForegroundColor Green
Write-Host "Frontend: http://localhost:8080"
Write-Host "Backend : http://localhost:7090/api"
Write-Host "MinIO   : http://localhost:9001"
Write-Host "Logs    : $($env:LOG_FILE_DIR)"
Write-Host "Archive : $($env:LOG_ARCHIVE_DIR)"
Write-Host ("=" * 74)
