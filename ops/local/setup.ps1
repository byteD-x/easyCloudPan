# EasyCloudPan Local Environment Setup Script
# Usage: .\ops\local\setup.ps1 [-Force] [-SkipNpm]

param(
    [switch]$Force,
    [switch]$SkipNpm
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Get-Item $PSScriptRoot).Parent.Parent.FullName

function Print-Header {
    param([string]$Title)
    Write-Host ("=" * 74)
    Write-Host $Title
    Write-Host ("=" * 74)
}

function Test-Command {
    param([string]$Command, [string]$DisplayName)
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        Write-Host "[ERROR] Missing dependency: $DisplayName" -ForegroundColor Red
        exit 1
    }
}

function Test-JavaVersion {
    $javacVersion = (javac -version 2>&1 | Out-String).Trim() -replace 'javac ', ''
    $majorVersion = $javacVersion.Split('.')[0]
    if ([int]$majorVersion -lt 21) {
        Write-Host "[ERROR] JDK 21+ is required. Current version: $javacVersion" -ForegroundColor Red
        exit 1
    }
}

function Test-NodeVersion {
    $nodeVersion = (node -p "process.versions.node.split('.')[0]" 2>&1).Trim()
    if ([int]$nodeVersion -lt 20) {
        Write-Host "[ERROR] Node.js 20+ is required. Current version: $nodeVersion" -ForegroundColor Red
        exit 1
    }
}

Print-Header "EasyCloudPan Local Setup"

# Check toolchain
Write-Host "[1/5] Checking toolchain..."
Test-Command "java" "Java (JDK 21+)"
Test-Command "javac" "JDK compiler"
Test-Command "mvn" "Maven"
Test-Command "node" "Node.js (20+)"
Test-Command "npm" "npm"
Test-Command "docker" "Docker Desktop"
Test-JavaVersion
Test-NodeVersion
Write-Host "[OK] Toolchain check passed." -ForegroundColor Green

# Install frontend dependencies
if ($SkipNpm) {
    Write-Host "[2/5] Skipping frontend dependency installation (--SkipNpm)."
} else {
    Write-Host "[2/5] Installing frontend dependencies..."
    Push-Location "$RepoRoot\frontend"
    npm install
    if ($LASTEXITCODE -ne 0) {
        Pop-Location
        Write-Host "[ERROR] npm install failed." -ForegroundColor Red
        exit 1
    }
    Pop-Location
    Write-Host "[OK] Frontend dependencies are ready." -ForegroundColor Green
}

# Build backend
Write-Host "[3/5] Building backend dependencies..."
Push-Location "$RepoRoot\backend"
mvn clean install -DskipTests
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Host "[ERROR] Backend build failed." -ForegroundColor Red
    exit 1
}
Pop-Location
Write-Host "[OK] Backend built successfully." -ForegroundColor Green

# Create directories
Write-Host "[4/5] Preparing local runtime directories..."
$dirs = @(
    "$RepoRoot\backend\file",
    "$RepoRoot\backend\file\temp",
    "$RepoRoot\backend\file\file",
    "$RepoRoot\backend\file\avatar"
)
foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}
Write-Host "[OK] Local directories prepared." -ForegroundColor Green

# Create config file
Write-Host "[5/5] Setting up environment configuration..."
$envFile = "$RepoRoot\ops\docker\.env"
$envExample = "$RepoRoot\ops\docker\.env.example"

if (-not (Test-Path $envFile)) {
    Write-Host "[INFO] ops\docker\.env not found. Creating from example..."
    Copy-Item $envExample $envFile
    Write-Host "[OK] Created ops\docker\.env" -ForegroundColor Green
} else {
    Write-Host "[INFO] ops\docker\.env already exists. Skipping creation."
}

$requiredEnvDefaults = @{
    "GRAFANA_ADMIN_USER" = "admin"
    "GRAFANA_ADMIN_PASSWORD" = ""
    "LOG_ROOT_LEVEL" = "info"
    "LOG_MAX_FILE_SIZE" = "50MB"
    "LOG_MAX_HISTORY" = "30"
    "LOG_TOTAL_SIZE_CAP" = "5GB"
    "LOG_CLEAN_HISTORY_ON_START" = "false"
    "SPRING_MAIL_DEBUG" = "false"
}

$envLines = Get-Content -Path $envFile
foreach ($key in $requiredEnvDefaults.Keys) {
    $hasKey = $false
    foreach ($line in $envLines) {
        if ($line -match "^\s*$([Regex]::Escape($key))\s*=") {
            $hasKey = $true
            break
        }
    }
    if (-not $hasKey) {
        Add-Content -Path $envFile -Value "$key=$($requiredEnvDefaults[$key])"
        Write-Host "[WARN] Added missing $key into ops\docker\.env (local default)." -ForegroundColor Yellow
    }
}

Write-Host ("=" * 74)
Write-Host "Setup finished." -ForegroundColor Green
Write-Host "Next step:"
Write-Host "  1) .\ops\local\startup.ps1          (local dev one-click start)"
Write-Host "  2) .\ops\docker\deploy_docker.ps1   (full docker deployment)"
Write-Host ("=" * 74)
