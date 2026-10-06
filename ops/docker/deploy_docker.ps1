# EasyCloudPan Docker 部署脚本
# 用法: .\ops\docker\deploy_docker.ps1 [-NoBuild]

param(
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"
$DockerDir = $PSScriptRoot
$RepoRoot = Split-Path -Parent $DockerDir

function Print-Header {
    param([string]$Title)
    Write-Host "=" * 74
    Write-Host $Title
    Write-Host "=" * 74
}

Print-Header "EasyCloudPan One-Click Docker Deployment"

# 检查 Docker
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Docker Desktop is not installed or not running." -ForegroundColor Red
    exit 1
}

docker compose version 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] docker compose is unavailable. Please enable Docker Compose V2." -ForegroundColor Red
    exit 1
}

# 检查 .env 文件
$envFile = "$DockerDir\.env"
if (-not (Test-Path $envFile)) {
    Write-Host "[INFO] .env not found. Creating from .env.example..."
    Copy-Item "$DockerDir\.env.example" $envFile
    Write-Host "[OK] Created .env file. You may want to edit it for production use." -ForegroundColor Green
}

# 部署
Push-Location $DockerDir
if ($NoBuild) {
    Write-Host "[1/2] Starting containers without rebuilding images..."
    docker compose up -d
} else {
    Write-Host "[1/2] Building and starting containers..."
    docker compose up -d --build
}

if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Host "[ERROR] Deployment failed." -ForegroundColor Red
    exit 1
}

Write-Host "[2/2] Checking container status..."
docker compose ps
Pop-Location

Write-Host ""
Write-Host "Frontend: http://localhost:8080"
Write-Host "Backend : http://localhost:7090/api"
Write-Host "MinIO   : http://localhost:9001"
Write-Host ""
Write-Host "To stop all containers, run: .\ops\docker\stop_docker.ps1"
