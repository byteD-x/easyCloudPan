# EasyCloudPan Docker 停止脚本
# 用法: .\ops\docker\stop_docker.ps1 [-Volumes]

param(
    [switch]$Volumes
)

$ErrorActionPreference = "Stop"
$DockerDir = $PSScriptRoot

Push-Location $DockerDir

if ($Volumes) {
    Write-Host "[WARN] Stopping containers and removing volumes (all data will be lost)..."
    docker compose down -v
} else {
    Write-Host "Stopping containers..."
    docker compose down
}

Pop-Location

Write-Host "[OK] All containers stopped." -ForegroundColor Green
