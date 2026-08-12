# 本机 Tus 大文件上传

阶段 2 使用独立的 `tusd` 进程接收文件字节流；Spring Boot 仅创建上传意图、处理 tusd 钩子、确认文件并提供受控下载。

## 启动顺序

1. 启动 MySQL、Redis 和 Spring Boot（端口 `8088`）。
2. 首次执行 `powershell -ExecutionPolicy Bypass -File ..\scripts\start-tusd.ps1 -Download` 下载 tusd。
3. 在 `echo-frontend` 运行 `npm run dev`。该 Node 启动器会自动启动 tusd（端口 `1080`）与 Vite；若 `1080` 已有 tusd，则复用已有服务。聊天页面选择超过 10MB 的文件即可进入 Tus 分片上传。

临时分片位于项目根目录 `upload/tusd`，完成确认后移动到 `upload/files`。这两个目录均不由 Spring MVC 静态暴露；tusd 同时使用 `-disable-download` 禁用直读。

## 调整文件大小上限

后端 `application.yml` 的 `app.file.max-size` 与 `start-tusd.ps1` 共用环境变量 `APP_FILE_MAX_SIZE`。例如调整为 5GB：

```powershell
$env:APP_FILE_MAX_SIZE = '5368709120'
# 在同一个终端启动后端；另一个终端也设置该变量后启动 tusd
.\scripts\start-tusd.ps1 -MaxFileSize 5368709120
```

前端不会再写死上限，而是读取后端上传意图返回的 `maxFileSize`，因此无需修改 Vue 代码。

默认情况下，`npm run dev`、`scripts/start-dev.ps1` 与 `scripts/start-tusd.ps1` 会读取后端 `application.yml` 中 `app.file.max-size` 的默认值。修改该值后，重启 Spring Boot 与 tusd 即可。仅在需要临时覆盖时，才设置 `APP_FILE_MAX_SIZE`。

## 本机局域网调试

若使用手机访问开发机，需要让 tusd 监听开发机网卡，并在后端本地配置中设置：

```yaml
app:
  file:
    tus-endpoint: http://开发机IP:1080/files/
```

同时把该手机页面的来源加入 `scripts/start-tusd.ps1` 的 CORS 来源。不要将 `upload/tusd` 或 `upload/files` 映射为 Web 服务器静态目录。

## 当前安全边界

- Tus 创建请求携带后端签发的一次性 `uploadToken`，`pre-create` 钩子会校验用户、接收者、大小和过期时间。
- 文件完成后，后端从临时目录再次校验实际大小与 SHA-256，并迁移到非公开永久目录；仅状态为 `READY` 的文件可作为聊天消息发送。
- 下载 URL 带随机访问令牌，聊天双方登录后也可读取。生产环境应将本机基础扫描替换为病毒扫描服务，并将 tusd 置于反向代理与 HTTPS 后。
