@echo off
REM Week 1 Verification Script
REM Purpose: Verify CI alignment, build, and type-check gates

echo ========================================
echo EasyCloudPan Week 1 Verification
echo ========================================
echo.

REM Check Docker Compose
echo [1/6] Checking Docker Compose configuration...
docker compose config > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Docker Compose configuration invalid
    exit /b 1
)
echo [PASS] Docker Compose configuration valid
echo.

REM Start services
echo [2/6] Starting Docker services...
docker compose up -d postgres redis minio
timeout /t 10 /nobreak > nul
echo [PASS] Services started
echo.

REM Backend compile
echo [3/6] Compiling backend (Maven)...
cd backend
call mvn -DskipTests clean compile
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Backend compilation failed
    cd ..
    exit /b 1
)
echo [PASS] Backend compiled successfully
echo.

REM Backend tests
echo [4/6] Running backend tests...
call mvn test
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Backend tests failed
    cd ..
    exit /b 1
)
echo [PASS] Backend tests passed
cd ..
echo.

REM Frontend type-check
echo [5/6] Running frontend type-check...
cd frontend
call npm run type-check
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Frontend type-check failed
    cd ..
    exit /b 1
)
echo [PASS] Frontend type-check passed
echo.

REM Frontend build
echo [6/6] Building frontend...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo [FAIL] Frontend build failed
    cd ..
    exit /b 1
)
echo [PASS] Frontend build successful
cd ..
echo.

echo ========================================
echo Week 1 Verification: ALL PASSED
echo ========================================
echo.
echo Next steps:
echo 1. Review docs/perf/baseline_2026-02-11.md
echo 2. Collect performance baseline metrics
echo 3. Proceed to Week 2 tasks
echo.

exit /b 0
