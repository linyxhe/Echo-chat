$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot 'echo-backend'
$frontendDir = Join-Path $projectRoot 'echo-frontend'
$uploadDir = Join-Path $projectRoot 'upload'
$tusdScript = Join-Path $PSScriptRoot 'start-tusd.ps1'

# Both child processes inherit APP_FILE_MAX_SIZE when it is explicitly set.
# Without an override, Spring Boot and start-tusd.ps1 read application.yml.
Start-Process powershell.exe -ArgumentList '-NoExit', '-Command', "Set-Location '$backendDir'; `$env:SERVER_PORT='8088'; `$env:CONTEXT_PATH=''; `$env:APP_UPLOAD_DIR='$uploadDir'; mvn spring-boot:run"
Start-Process powershell.exe -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', $tusdScript

Set-Location $frontendDir
npm run dev -- --port 5174
