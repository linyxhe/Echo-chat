param(
  [switch]$Download,
  [string]$HostName = "127.0.0.1",
  [int]$Port = 1080,
  [Int64]$MaxFileSize = 0
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $projectRoot 'tools\tusd'
$binary = Join-Path $toolsDir 'tusd.exe'
$uploadDir = Join-Path $projectRoot 'upload\tusd'
$applicationYml = Join-Path $projectRoot 'echo-backend\src\main\resources\application.yml'

# Keep this value aligned with Spring Boot app.file.max-size. Command argument wins.
if ($MaxFileSize -le 0) {
  if ($env:APP_FILE_MAX_SIZE) {
    $MaxFileSize = [Int64]::Parse($env:APP_FILE_MAX_SIZE)
  } else {
    $applicationContent = Get-Content -LiteralPath $applicationYml -Raw -Encoding UTF8
    $configuredValue = [regex]::Match($applicationContent, 'max-size:\s*\$\{APP_FILE_MAX_SIZE:(\d+)\}').Groups[1].Value
    $MaxFileSize = if ($configuredValue) { [Int64]::Parse($configuredValue) } else { 21474836480 }
  }
}
if ($MaxFileSize -le 0) { throw "MaxFileSize must be a positive byte value." }

if (-not (Test-Path -LiteralPath $binary) -and (Test-Path -LiteralPath $toolsDir)) {
  $downloadedBinary = Get-ChildItem -LiteralPath $toolsDir -Recurse -Filter 'tusd.exe' -File | Select-Object -First 1
  if ($null -ne $downloadedBinary) { $binary = $downloadedBinary.FullName }
}

if (-not (Test-Path -LiteralPath $binary)) {
  if (-not $Download) {
    throw "tusd.exe was not found. Run .\scripts\start-tusd.ps1 -Download first."
  }
  New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
  $zip = Join-Path $env:TEMP 'tusd_windows_amd64.zip'
  Invoke-WebRequest -Uri 'https://github.com/tus/tusd/releases/download/v2.9.2/tusd_windows_amd64.zip' -OutFile $zip
  Expand-Archive -LiteralPath $zip -DestinationPath $toolsDir -Force
}

if (-not (Test-Path -LiteralPath $binary)) {
  $downloadedBinary = Get-ChildItem -LiteralPath $toolsDir -Recurse -Filter 'tusd.exe' -File | Select-Object -First 1
  if ($null -eq $downloadedBinary) { throw "tusd.exe was not found after extraction." }
  $binary = $downloadedBinary.FullName
}

New-Item -ItemType Directory -Path $uploadDir -Force | Out-Null
$origins = '^http://(localhost|127\\.0\\.0\\.1):(8088|8089)$'
$hookEndpoint = 'http://127.0.0.1:8088/internal/tusd/hooks'

& $binary `
  -host $HostName `
  -port $Port `
  -base-path '/files/' `
  -upload-dir $uploadDir `
  -max-size $MaxFileSize `
  -disable-download `
  -hooks-http $hookEndpoint `
  -hooks-enabled-events 'pre-create,pre-finish,post-finish,post-terminate' `
  -cors-allow-origin $origins `
  -cors-allow-methods 'POST,HEAD,PATCH,DELETE,OPTIONS' `
  -cors-allow-headers 'Authorization,Content-Type,Upload-Length,Upload-Offset,Tus-Resumable,Upload-Metadata,Upload-Defer-Length' `
  -cors-expose-headers 'Location,Upload-Offset,Tus-Resumable,Upload-Length'
