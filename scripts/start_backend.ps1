# EasyCloudPan Backend Local Startup Script
# Loads environment variables from .env and starts the Spring Boot application.

$ScriptDir = $PSScriptRoot
$RepoRoot = Split-Path -Parent $ScriptDir
$BackendDir = Join-Path $RepoRoot "backend"
$EnvFile = Join-Path $RepoRoot ".env"

# Load helper
$DotEnvScript = Join-Path $ScriptDir "_dotenv.ps1"
if (Test-Path $DotEnvScript) {
    . $DotEnvScript
} else {
    Write-Warning "_dotenv.ps1 not found. Environment variables will not be loaded from .env file."
}

# Check and load .env
if (Test-Path $EnvFile) {
    Write-Host "Loading environment variables from $EnvFile..." -ForegroundColor Cyan
    Load-DotEnv -Path $EnvFile
} else {
    Write-Warning ".env file not found at $EnvFile. Using default/application.properties values."
    if (Test-Path "$RepoRoot\.env.example") {
        Write-Host "Tip: Copy .env.example to .env to configure your environment." -ForegroundColor Yellow
    }
}

# Run the application
Push-Location $BackendDir
Write-Host "Starting EasyCloudPan Backend..." -ForegroundColor Green
Write-Host "Project Folder: $BackendDir"

# Use Maven wrapper if available, otherwise assume mvn is in PATH
if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd spring-boot:run
} else {
    mvn spring-boot:run
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "Application failed to start."
}

Pop-Location
