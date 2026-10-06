param(
    [string]$BaseUrl = "http://localhost:7090/api",
    [string]$Email,
    [string]$Password,
    [string]$TenantId = "default",
    [string]$CheckCode
)

$ErrorActionPreference = "Stop"

$null = Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue

$api = Join-Path $PSScriptRoot "_easypan_api.ps1"
if (-not (Test-Path $api)) {
    throw "Missing helper: $api"
}
. $api

if ([string]::IsNullOrWhiteSpace($Email)) {
    $Email = $env:EASYPAN_EMAIL
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = $env:EASYPAN_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($Email)) {
    throw "Missing Email. Provide -Email or set EASYPAN_EMAIL."
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw "Missing Password. Provide -Password or set EASYPAN_PASSWORD."
}

$script:passed = 0
$script:failed = 0
$script:failedTests = @()

function Write-TestResult {
    param([string]$Name, [bool]$Success, [string]$Message = "")
    if ($Success) {
        Write-Output "  [PASS] $Name"
        $script:passed++
    } else {
        Write-Output "  [FAIL] $Name - $Message"
        $script:failed++
        $script:failedTests += $Name
    }
}

Write-Output ""
Write-Output "========================================"
Write-Output "EasyCloudPan: Verify Update Password"
Write-Output "========================================"
Write-Output ""

try {
    Write-Output "[1/4] Login with original password..."
    $session = New-EasyPanSession
    try {
        $login = Invoke-EasyPanLogin -BaseUrl $BaseUrl -Session $session -Email $Email -Password $Password -TenantId $TenantId -CheckCode $CheckCode
        $token = [string]$login.token
        Write-TestResult "Login (original password)" $true
    } catch {
        Write-TestResult "Login (original password)" $false $_.Exception.Message
        throw "Login failed, cannot continue"
    }

    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.UseCookies = $true
    $handler.CookieContainer = $session.Cookies
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.DefaultRequestHeaders.Add("X-Tenant-Id", $TenantId)
    $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token)

    Write-Output ""
    Write-Output "[2/4] Update password..."
    $newPassword = $env:EASYPAN_NEW_PASSWORD
    if ([string]::IsNullOrWhiteSpace($newPassword)) {
        $newPassword = "Test-" + [guid]::NewGuid().ToString("N").Substring(0, 12) + "Aa1!"
    }
    $updateBody = "password=$newPassword"
    $updateResp = $client.PostAsync("$BaseUrl/updatePassword", 
        (New-Object System.Net.Http.StringContent($updateBody, [System.Text.Encoding]::UTF8, "application/x-www-form-urlencoded"))).Result
    $updateJson = $updateResp.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    Write-TestResult "Update Password" ($updateJson.code -eq 200) $updateJson.info

    Write-Output ""
    Write-Output "[3/4] Login with new password..."
    $session2 = New-EasyPanSession
    try {
        $login2 = Invoke-EasyPanLogin -BaseUrl $BaseUrl -Session $session2 -Email $Email -Password $newPassword -TenantId $TenantId -CheckCode $CheckCode
        Write-TestResult "Login (new password)" $true
    } catch {
        Write-TestResult "Login (new password)" $false $_.Exception.Message
    }

    Write-Output ""
    Write-Output "[4/4] Revert password back to original..."
    $handler2 = New-Object System.Net.Http.HttpClientHandler
    $handler2.UseCookies = $true
    $handler2.CookieContainer = $session2.Cookies
    $client2 = New-Object System.Net.Http.HttpClient($handler2)
    $client2.DefaultRequestHeaders.Add("X-Tenant-Id", $TenantId)
    $client2.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $login2.token)
    $revertBody = "password=$Password"
    $revertResp = $client2.PostAsync("$BaseUrl/updatePassword", 
        (New-Object System.Net.Http.StringContent($revertBody, [System.Text.Encoding]::UTF8, "application/x-www-form-urlencoded"))).Result
    $revertJson = $revertResp.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    Write-TestResult "Revert Password" ($revertJson.code -eq 200) $revertJson.info

} finally {
    if ($client) { $client.Dispose() }
    if ($client2) { $client2.Dispose() }
}

Write-Output ""
Write-Output "========================================"
Write-Output "Results: $script:passed PASS, $script:failed FAIL"
if ($script:failedTests.Count -gt 0) {
    Write-Output "Failed: $($script:failedTests -join ', ')"
}
Write-Output "========================================"
Write-Output ""

if ($script:failed -gt 0) {
    exit 1
} else {
    exit 0
}
