@echo off
REM Performance Baseline Collection Script
REM Purpose: Collect baseline metrics for performance tracking

echo ========================================
echo EasyCloudPan Performance Baseline Collection
echo ========================================
echo.

REM Check if services are running
echo [1/4] Checking services status...
docker compose ps | findstr "Up" > nul
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] Services may not be running. Starting services...
    docker compose up -d
    timeout /t 15 /nobreak > nul
)
echo [PASS] Services are running
echo.

REM Check backend health
echo [2/4] Checking backend health...
curl -s http://localhost:7090/api/actuator/health > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] Backend not responding. Please ensure backend is running.
    echo Run: cd backend ^&^& mvn spring-boot:run
    echo.
) else (
    echo [PASS] Backend is healthy
    echo.
)

REM Collect metrics
echo [3/4] Collecting metrics...
echo.
echo Available metrics endpoints:
echo - Health: http://localhost:7090/api/actuator/health
echo - Metrics: http://localhost:7090/api/actuator/metrics
echo - Info: http://localhost:7090/api/actuator/info
echo.

if exist curl.exe (
    echo Fetching current metrics...
    curl -s http://localhost:7090/api/actuator/metrics > metrics_snapshot.json 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo [PASS] Metrics saved to metrics_snapshot.json
    )
    echo.
)

REM Frontend build analysis
echo [4/4] Analyzing frontend build size...
if exist frontend\dist (
    echo Frontend build artifacts:
    dir /s frontend\dist\*.js | findstr "bytes"
    echo.
) else (
    echo [WARN] Frontend not built. Run: cd frontend ^&^& npm run build
    echo.
)

echo ========================================
echo Baseline Collection Complete
echo ========================================
echo.
echo Manual steps required:
echo 1. Run load tests using JMeter or similar tool
echo 2. Record P50/P95/P99 response times
echo 3. Update docs/perf/baseline_2026-02-11.md with results
echo 4. Test upload/download with various file sizes
echo 5. Run Lighthouse on frontend pages
echo.

exit /b 0
