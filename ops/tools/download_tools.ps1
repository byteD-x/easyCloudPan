$ErrorActionPreference = "Stop"

$toolsDir = Join-Path $PSScriptRoot "tools"
if (-not (Test-Path $toolsDir)) {
    New-Item -ItemType Directory -Path $toolsDir | Out-Null
    Write-Host "Created tools directory at $toolsDir"
}

# --- Redis ---
$redisDir = Join-Path $toolsDir "redis"
if (-not (Test-Path $redisDir)) {
    Write-Host "[INFO] Downloading Redis (v5.0.14.1)..."
    $redisUrl = "https://github.com/tporadowski/redis/releases/download/v5.0.14.1/Redis-x64-5.0.14.1.zip"
    $redisZip = Join-Path $toolsDir "redis.zip"
    
    try {
        Invoke-WebRequest -Uri $redisUrl -OutFile $redisZip -UserAgent "Mozilla/5.0"
        
        Write-Host "[INFO] Extracting Redis..."
        Expand-Archive -Path $redisZip -DestinationPath $redisDir -Force
        
        Write-Host "[OK] Redis installed to $redisDir"
    }
    catch {
        Write-Host "[ERROR] Failed to download or extract Redis: $_"
        exit 1
    }
    finally {
        if (Test-Path $redisZip) { Remove-Item $redisZip -ErrorAction SilentlyContinue }
    }
}
else {
    Write-Host "[SKIP] Redis already exists."
}

# --- FFmpeg ---
$ffmpegDir = Join-Path $toolsDir "ffmpeg"
if (-not (Test-Path $ffmpegDir)) {
    Write-Host "[INFO] Downloading FFmpeg (gyan.dev essentials)..."
    # Using a specific version link to avoid 'latest' redirect issues or changing folder names if possible, 
    # but 'release-essentials' is the stable link.
    $ffmpegUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
    $ffmpegZip = Join-Path $toolsDir "ffmpeg.zip"

    try {
        Invoke-WebRequest -Uri $ffmpegUrl -OutFile $ffmpegZip -UserAgent "Mozilla/5.0"
        
        Write-Host "[INFO] Extracting FFmpeg..."
        # Extract to a temp folder first to handle the subfolder structure
        $ffmpegTemp = Join-Path $toolsDir "ffmpeg_temp"
        Expand-Archive -Path $ffmpegZip -DestinationPath $ffmpegTemp -Force
        
        # Move the inner folder to specific ffmpeg dir
        $innerDir = Get-ChildItem -Path $ffmpegTemp -Directory | Select-Object -First 1
        if ($innerDir) {
            Move-Item -Path $innerDir.FullName -Destination $ffmpegDir
            Write-Host "[OK] FFmpeg installed to $ffmpegDir"
        }
        else {
            Write-Html "[ERROR] Could not find FFmpeg folder in zip."
            exit 1
        }
        
        Remove-Item $ffmpegTemp -Recurse -Force
    }
    catch {
        Write-Host "[ERROR] Failed to download or extract FFmpeg: $_"
        exit 1
    }
    finally {
        if (Test-Path $ffmpegZip) { Remove-Item $ffmpegZip -ErrorAction SilentlyContinue }
    }
}
else {
    Write-Host "[SKIP] FFmpeg already exists."
}
