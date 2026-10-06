param(
    [string]$BaseUrl = "http://localhost:7090/api",
    [string]$TestEmail,
    [string]$TestNickname = "TestUser",
    [string]$TestPassword
)

$ErrorActionPreference = "Stop"

$null = Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue

$api = Join-Path $PSScriptRoot "_easypan_api.ps1"
if (-not (Test-Path $api)) {
    throw "Missing helper: $api"
}
. $api

Write-Host "=== EasyCloudPan Email and Registration Verification ===" -ForegroundColor Cyan
Write-Host ""

if ([string]::IsNullOrWhiteSpace($TestEmail)) {
    $TestEmail = $env:EASYPAN_EMAIL
}
if ([string]::IsNullOrWhiteSpace($TestEmail)) {
    throw "请通过 -TestEmail 或 EASYPAN_EMAIL 提供测试邮箱"
}
if ([string]::IsNullOrWhiteSpace($TestPassword)) {
    $TestPassword = $env:EASYPAN_TEST_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($TestPassword)) {
    $TestPassword = "Test-" + [guid]::NewGuid().ToString("N").Substring(0, 12) + "Aa1!"
}

$session = New-EasyPanSession

try {
    # 1. Get captcha
    Write-Host "[1/6] Getting captcha..." -ForegroundColor Yellow
    $captchaCode = Get-EasyPanCaptchaCode -BaseUrl $BaseUrl -Session $session
    
    if ([string]::IsNullOrWhiteSpace($captchaCode)) {
        Write-Host "WARNING: No captcha header, trying CAPTCHA_DEBUG_HEADER mode..." -ForegroundColor Yellow
        $captchaCode = "debug"
    } else {
        Write-Host "PASS: Get captcha (code=$captchaCode)" -ForegroundColor Green
    }
    Start-Sleep -Seconds 1

    # 2. Send email verification code
    Write-Host ""
    Write-Host "[2/6] Sending email verification code..." -ForegroundColor Yellow
    Write-Host "Target email: $TestEmail" -ForegroundColor Gray

    $body = @{
        email = $TestEmail
        checkCode = $captchaCode
        type = 0
    }
    
    $sendEmailResp = Invoke-WebRequest -Uri "$BaseUrl/sendEmailCode" -Method POST -WebSession $session -ContentType "application/x-www-form-urlencoded" -Body $body
    $sendEmailJson = $sendEmailResp.Content | ConvertFrom-Json
    
    $emailCodeSent = ($sendEmailJson.code -eq 200)
    if ($emailCodeSent) {
        Write-Host "PASS: Send email verification code (M-AUTH-002)" -ForegroundColor Green
    } else {
        Write-Host "FAIL: Send email verification code (M-AUTH-002)" -ForegroundColor Red
        Write-Host "   Response: $($sendEmailJson | ConvertTo-Json -Compress)" -ForegroundColor Red
    }
    Start-Sleep -Seconds 2

    if (-not $emailCodeSent) {
        Write-Host ""
        Write-Host "WARNING: Failed to send email, possible reasons:" -ForegroundColor Yellow
        Write-Host "   - SMTP configuration issue" -ForegroundColor Gray
        Write-Host "   - QQ email authorization code error" -ForegroundColor Gray
        Write-Host "   - Network connection issue" -ForegroundColor Gray
        Write-Host ""
        Write-Host "Note: Registration (M-AUTH-003) depends on email code, cannot continue" -ForegroundColor Yellow
        exit 1
    }

    # 3. Prompt user for email code
    Write-Host ""
    Write-Host "[3/6] Please check your email and enter the verification code..." -ForegroundColor Yellow
    $emailCode = Read-Host "Enter the email verification code"

    if ([string]::IsNullOrEmpty($emailCode)) {
        Write-Host "FAIL: No email code entered, aborting" -ForegroundColor Red
        exit 1
    }

    # 4. Register new user - need fresh captcha
    Write-Host ""
    Write-Host "[4/6] Registering new user..." -ForegroundColor Yellow
    
    # Get fresh captcha for registration
    $captchaCode2 = Get-EasyPanCaptchaCode -BaseUrl $BaseUrl -Session $session
    if ([string]::IsNullOrWhiteSpace($captchaCode2)) {
        $captchaCode2 = "debug"
    }
    
    $registerBody = @{
        email = $TestEmail
        nickName = $TestNickname
        password = $TestPassword
        checkCode = $captchaCode2
        emailCode = $emailCode
    }
    
    $registerResp = Invoke-WebRequest -Uri "$BaseUrl/register" -Method POST -WebSession $session -ContentType "application/x-www-form-urlencoded" -Body $registerBody
    $registerJson = $registerResp.Content | ConvertFrom-Json
    
    $registerSuccess = ($registerJson.code -eq 200)
    if ($registerSuccess) {
        Write-Host "PASS: Register new user (M-AUTH-003)" -ForegroundColor Green
    } else {
        Write-Host "FAIL: Register new user (M-AUTH-003)" -ForegroundColor Red
        Write-Host "   Response: $($registerJson | ConvertTo-Json -Compress)" -ForegroundColor Red
    }
    Start-Sleep -Seconds 1

    if ($registerJson.code -ne 200) {
        Write-Host ""
        Write-Host "WARNING: Registration failed, but email sent (M-AUTH-002 completed)" -ForegroundColor Yellow
        exit 1
    }

    # 5. Try to login new user
    Write-Host ""
    Write-Host "[5/6] Trying to login new user..." -ForegroundColor Yellow
    
    # Get fresh captcha for login
    $captchaCode3 = Get-EasyPanCaptchaCode -BaseUrl $BaseUrl -Session $session
    if ([string]::IsNullOrWhiteSpace($captchaCode3)) {
        $captchaCode3 = "debug"
    }
    
    $loginBody = @{
        email = $TestEmail
        password = $TestPassword
        checkCode = $captchaCode3
    }
    
    $loginResp = Invoke-WebRequest -Uri "$BaseUrl/login" -Method POST -WebSession $session -ContentType "application/x-www-form-urlencoded" -Body $loginBody
    $loginJson = $loginResp.Content | ConvertFrom-Json
    
    if ($loginJson.code -eq 200) {
        Write-Host "PASS: Login new user" -ForegroundColor Green
    } else {
        Write-Host "FAIL: Login new user" -ForegroundColor Red
        Write-Host "   Response: $($loginJson | ConvertTo-Json -Compress)" -ForegroundColor Red
    }
    Start-Sleep -Seconds 1

    # 6. Cleanup - delete test user
    Write-Host ""
    Write-Host "[6/6] Cleaning up test data (requires admin)..." -ForegroundColor Yellow
    Write-Host "Note: This step requires manual deletion in admin or database" -ForegroundColor Gray

    Write-Host ""
    Write-Host "=== Verification Complete ===" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Test user info:" -ForegroundColor Gray
    Write-Host "  Email: $TestEmail" -ForegroundColor Gray
    Write-Host "  Nickname: $TestNickname" -ForegroundColor Gray
    Write-Host "  Password: $TestPassword" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Remember to delete the test user!" -ForegroundColor Yellow

}
catch {
    Write-Host "FAIL: Error during test: $_" -ForegroundColor Red
    Write-Host $_.ScriptStackTrace -ForegroundColor Red
}
