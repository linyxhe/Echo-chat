$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot 'echo-backend'
$frontendDir = Join-Path $projectRoot 'echo-frontend'
$staticDir = Join-Path $backendDir 'src\main\resources\static'
$uploadDir = Join-Path $projectRoot 'upload'

$env:SERVER_PORT = '8088'
$env:CONTEXT_PATH = '/echo-chat'
$env:SERVER_FORWARD_HEADERS_STRATEGY = 'framework'
$env:APP_UPLOAD_DIR = $uploadDir

Set-Location $frontendDir
npm run build

New-Item -ItemType Directory -Path $staticDir -Force | Out-Null
Copy-Item -Path (Join-Path $frontendDir 'dist\*') -Destination $staticDir -Recurse -Force

Set-Location $backendDir
mvn clean package -DskipTests
java -jar 'target\Echo-chat-0.0.1-SNAPSHOT.jar'
