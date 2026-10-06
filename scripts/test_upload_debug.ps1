$ErrorActionPreference = "Stop"
$null = Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue
. (Join-Path $PSScriptRoot "_easypan_api.ps1")

$BaseUrl = "http://localhost:7090/api"
$Email = $env:EASYPAN_EMAIL
$Password = $env:EASYPAN_PASSWORD
$TenantId = if ([string]::IsNullOrWhiteSpace($env:EASYPAN_TENANT_ID)) { "default" } else { $env:EASYPAN_TENANT_ID }
if ([string]::IsNullOrWhiteSpace($Email) -or [string]::IsNullOrWhiteSpace($Password)) {
    throw "请先设置 EASYPAN_EMAIL 和 EASYPAN_PASSWORD 环境变量"
}

$session = New-EasyPanSession
$login = Invoke-EasyPanLogin -BaseUrl $BaseUrl -Session $session -Email $Email -Password $Password -TenantId $TenantId
$token = [string]$login.token
Write-Output "Token obtained"

$handler = New-Object System.Net.Http.HttpClientHandler
$handler.UseCookies = $true
$handler.CookieContainer = $session.Cookies
$client = New-Object System.Net.Http.HttpClient($handler)
$client.DefaultRequestHeaders.Add("X-Tenant-Id", $TenantId)
$client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token)

$tmpDir = Join-Path $env:TEMP "test_upload_$(Get-Random)"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
$filePath = Join-Path $tmpDir "test.png"
$pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/6X8u6QAAAAASUVORK5CYII="
[System.IO.File]::WriteAllBytes($filePath, [Convert]::FromBase64String($pngBase64))
$fileMd5 = (Get-FileHash -Algorithm MD5 -Path $filePath).Hash.ToLowerInvariant()
$fileId = "TEST_" + [guid]::NewGuid().ToString("N").Substring(0, 8)
$fileName = "test.png"

Write-Output "FileId: $fileId"
Write-Output "FileMd5: $fileMd5"

$multipart = New-Object System.Net.Http.MultipartFormDataContent
$multipart.Add((New-Object System.Net.Http.StringContent($fileId)), "fileId")
$multipart.Add((New-Object System.Net.Http.StringContent($fileName)), "fileName")
$multipart.Add((New-Object System.Net.Http.StringContent("0")), "filePid")
$multipart.Add((New-Object System.Net.Http.StringContent($fileMd5)), "fileMd5")
$multipart.Add((New-Object System.Net.Http.StringContent("0")), "chunkIndex")
$multipart.Add((New-Object System.Net.Http.StringContent("1")), "chunks")

$fs = [System.IO.File]::OpenRead($filePath)
try {
    $fileContent = New-Object System.Net.Http.StreamContent($fs)
    $fileContent.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream")
    $multipart.Add($fileContent, "file", $fileName)
    
    $resp = $client.PostAsync("$BaseUrl/file/uploadFile", $multipart).Result
    $body = $resp.Content.ReadAsStringAsync().Result
    Write-Output "Response: $body"
} finally {
    $fs.Dispose()
}

Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
