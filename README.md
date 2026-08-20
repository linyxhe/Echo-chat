# Echo Chat · 回声聊天室

Echo Chat 是一个面向 Web、Windows 桌面端和 Android 的实时社交聊天应用。项目以 Spring Boot 为服务端、Vue 3 为统一前端，提供即时通信、群聊、受控文件传输、音视频通话、AI 助手与知识库，并包含运营管理后台和发布链路。
在线地址：www.linyxhe.top/echo-chat
> 本文档以当前代码为准，数据库结构由 Flyway 迁移 `V1`～`V28` 管理。接口字段与实时消息事件请以 Controller、`ChatEndpoint` 和前端实现为准。

## 功能概览

### 实时聊天与社交

- 单聊与群聊：文本、图片、文件、输入状态、消息已读时间、离线未读、会话置顶、归档、清空记录和联系人备注。
- 可靠投递：客户端消息 ID 幂等、发送确认、断线重连、心跳看门狗；Redis 维护在线状态并通过 Pub/Sub 支持跨实例推送。
- 好友与群组：用户搜索、好友申请、群创建、邀请、审核入群、成员与群设置管理。
- 社交互动：朋友圈图文动态、可见范围、点赞、评论、举报、敏感词过滤和系统通知中心。
- 全文搜索：按当前用户权限范围搜索用户、私聊与群聊文本。

### 文件与音视频

- 小文件使用 HTTP 上传；大文件通过 `tusd` + Uppy 实现分片上传、断点恢复、进度展示和刷新后续传。
- 后端签发上传意图并校验 tusd Hook；文件仅在大小与 SHA-256 校验通过后变为 `READY`，随后存放到非公开目录并由受控下载接口提供访问。
- WebRTC 一对一音频/视频通话，信令走 WebSocket；TURN 配置仅由已登录用户从后端获取，支持 coturn shared-secret 短期凭据或静态凭据。

### AI 助手与知识库

- 基于 LangChain4j 和 OpenAI 兼容接口接入 DeepSeek 流式对话，支持多轮上下文、停止生成、Markdown 安全渲染、自定义私有助手与用量审计。
- 本地 RAG：使用 `bge-small-zh-v1.5` 中文嵌入模型，支持 `txt`、`md`、`pdf`、`docx` 文档解析、异步索引、分类检索以及私有资料优先召回。
- 受控 Agent：模型只能申请白名单工具；服务端负责权限、参数校验、调用预算、超时、审计和结果脱敏。支持当前时间、知识库检索、计算器、聊天记录检索、文件目录检索、联网搜索和实时天气。
- 每个自建助手独立授权工具。记忆保存、草稿生成和站内提醒属于确认型操作，必须由用户点击确认后才会落库；提醒到点后通过通知中心与 WebSocket 推送。
- 联网搜索与天气调用均由服务端持有密钥，并有后台可配置的启停开关、周期额度与保护阈值。

### 管理与多端发布

- 管理后台提供用户封禁/重置、举报审核、系统配置、系统统计、知识库文档管理、AI 用量审计和客户端安装包发布管理。
- Windows：Electron 生成 NSIS 安装包、便携版和校验文件；每次发布使用独立目录，避免运行中的旧客户端锁定构建产物。
- Android：将前端产物同步到 HBuilderX 工程，开发包使用局域网 HTTP/WS，生产包强制 HTTPS/WSS，并提供 APK 内容校验脚本。
- 前端在窄屏下自适应为移动端布局：主导航改为底部 Tab，聊天双栏改为单栏。

## 技术栈

| 范围 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、Spring Security、Spring WebSocket |
| 数据 | MySQL 8、MyBatis-Plus、Flyway、Druid、Redis |
| 实时能力 | WebSocket、Redis Pub/Sub、WebRTC、coturn / TURN REST |
| AI 与 RAG | LangChain4j、DeepSeek（OpenAI 兼容）、bge-small-zh-v1.5、PDFBox、Apache POI |
| 文件 | tusd、Uppy Tus、SHA-256 完整性校验、受控下载 |
| Web 前端 | Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios |
| 富文本 | marked、DOMPurify |
| 客户端 | Electron、electron-builder、HBuilderX HTML5+ |

## 架构

```text
Web / Electron / Android
          │
          ├── HTTP REST ─────────────────────────────┐
          ├── WebSocket (/ws) ────────────────────────┤
          └── WebRTC（媒体直连，TURN 中继兜底）       │
                                                     ▼
                                      Spring Boot API 与实时服务
                                   ┌─────────┬─────────┴──────────┐
                                   │         │                    │
                                MySQL     Redis                tusd
                            业务数据、     在线状态、          大文件分片
                            审计、迁移     Pub/Sub              上传 Hook
                                   │
                                   └── AI Gateway / 受控 Agent
                                       ├── DeepSeek 流式模型
                                       ├── 本地知识库检索
                                       └── 外部天气、联网搜索
```

## 目录说明

```text
Echo-chat-room/
├── echo-backend/                 # Spring Boot 服务端
│   ├── src/main/java/com/echo/
│   │   ├── controller/           # REST API、管理端与 tusd Hook
│   │   ├── websocket/            # WS 会话、消息路由与 Redis Pub/Sub
│   │   ├── ai/                   # AI 会话、模型适配与知识库关联
│   │   ├── agent/                # 受控工具编排、授权、确认、审计
│   │   ├── file/                 # 上传意图、完成确认与受控下载
│   │   ├── service/              # 业务服务
│   │   └── mapper/、pojo/        # MyBatis 映射与实体
│   └── src/main/resources/db/migration/  # Flyway V1～V28
├── echo-frontend/                # Vue 3 Web 与 Electron 桌面客户端
│   ├── electron/                 # 主进程与 preload
│   └── src/views/                # 用户端、AI 助手页、管理后台
├── echo-chat-phone/              # HBuilderX Android/iOS 工程
├── scripts/                      # 开发、打包、发布、APK 校验脚本
├── deploy/                       # Nginx、coturn、FRP 与生产配置模板
├── tools/                        # tusd 与测试辅助工具
└── upload/                       # 本地运行数据（不提交）
```

## 本地开发

### 前置条件

- JDK 17+、Maven 3.8+、Node.js 18+
- MySQL 8.0 与 Redis
- Windows PowerShell（本项目提供的 tusd 与发布脚本基于 PowerShell）

### 1. 创建数据库

```sql
CREATE DATABASE echo_chat
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

首次启动时 Flyway 会执行迁移。请不要手工修改已执行的迁移文件，也不要把历史 `echo_chat.sql` 当作后续升级脚本。

### 2. 设置本地配置

敏感信息不要写入仓库。可通过系统环境变量或在 `echo-backend/src/main/resources/application-local.yml` 中配置本机值。至少需要：

| 配置 | 用途 |
| --- | --- |
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | MySQL 连接 |
| `JWT_SECRET` | JWT 签名密钥，必须使用本机随机长字符串 |
| `MAIL_USERNAME`、`MAIL_PASSWORD` | 邮箱验证码（需要注册邮件时配置） |
| `AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL` | AI 对话模型；未配置时 AI 不可用 |
| `AI_WEB_SEARCH_API_KEY` | Tavily 联网搜索，可选 |
| `QWEATHER_API_HOST`、`QWEATHER_API_KEY` 或 `QWEATHER_API_TOKEN` | 和风天气，可选 |
| `RTC_TURN_URLS` 与 TURN 凭据 | 生产环境音视频通话必需 |
| `APP_FILE_MAX_SIZE` | 文件上限，默认 20 GiB；需与 tusd 保持一致 |

### 3. 启动服务

在两个终端分别执行：

```powershell
# 终端 1：后端（默认 http://localhost:8088）
cd echo-backend
mvn spring-boot:run

# 终端 2：前端与本地 tusd（默认 http://localhost:8089）
cd echo-frontend
npm install
npm run dev
```

`npm run dev` 会检查 `1080` 端口；若未运行 tusd，则自动启动。也可以在项目根目录使用：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-dev.ps1
```

浏览器访问 `http://localhost:8089`。Vite 会代理 `/api`、`/files`、`/upload` 与 `/ws` 到后端。

### 常用验证

```powershell
# 后端测试
cd echo-backend
mvn test

# 前端生产构建
cd ..\echo-frontend
npm run build
```

## 构建与发布

### Windows 桌面端与后端发布包

将 [`deploy/.env.production.example`](./deploy/.env.production.example) 复制为 `deploy/.env.production` 并填写域名、数据库、JWT、AI 与 TURN 配置。该文件包含密钥，已被 Git 忽略。

```powershell
# 完整发布：Web、后端 JAR、Windows 客户端、移动端资源与 Nginx 配置
powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1

# 仅构建桌面端（跳过后端和移动端）
powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1 -SkipBackend -SkipMobile
```

产物写入 `deploy/dist/<时间戳>/`。其中 `desktop/` 包含安装包、便携版及 `SHA256.txt`，`backend/` 包含 JAR 与启动脚本，`nginx/` 包含替换过域名的反向代理配置。

### Android

```powershell
# 使用明确的局域网 IP 构建开发资源
powershell -ExecutionPolicy Bypass -File scripts\build-mobile-bundle.ps1 `
  -Environment development -DevelopmentHost 192.168.1.100

# 使用 deploy/.env.production 的 HTTPS/WSS 配置构建生产资源
powershell -ExecutionPolicy Bypass -File scripts\build-mobile-bundle.ps1 -Environment production
```

用 HBuilderX 打开 `echo-chat-phone/echo-chat-phone/` 后进行云打包。打包完成可使用 `scripts/verify-apk.ps1` 验证构建 ID、运行环境、X5 WebView 与媒体权限是否符合预期。

### 生产部署要点

- 生产页面必须使用 HTTPS，WebSocket 使用 WSS；手机与桌面端均从构建期运行时配置读取公共地址。
- Nginx 分别反代应用路径和 tusd 公共路径；tusd 需要 `-behind-proxy` 与正确的 `-base-path`，对应 location 必须允许分片大小（如 `client_max_body_size 0`）。
- 大文件上传服务与后端均需启动：发布目录内分别运行 `start-backend.ps1` 与 `start-tusd.ps1`。
- 音视频通话必须部署真实 coturn。推荐 shared-secret 短期凭据，开放 UDP/TCP 3478 和 relay 端口范围；示例见 [`deploy/coturn/turnserver.conf.example`](./deploy/coturn/turnserver.conf.example)。

## 安全与数据边界

- HTTP 与 WebSocket 使用 JWT 认证，账号状态会在连接与业务请求时校验。
- 文件不作为静态资源公开：tusd 禁止直读，下载经访问令牌与服务端权限判断。
- AI、天气和联网搜索密钥只保存在服务端配置，绝不下发到浏览器、Electron 或 APK。
- Agent 不直接访问数据库、文件路径和任意 URL；仅能使用服务端白名单工具。敏感读取须由助手创建者授权，写入类动作必须经过一次性确认令牌。
- Agent 运行、工具调用、AI 用量与检索来源保留最小化审计信息；不会记录模型推理、密钥或完整私有资料正文。

## 相关文档

| 文档 | 内容 |
| --- | --- |
| [`PROJECT.md`](./PROJECT.md) | 前端实现与历史接口说明（部分章节待与当前模块同步） |
| [`Agent模块技术设计.md`](./Agent模块技术设计.md) | 受控 Agent 的权限、审计与编排设计 |
| [`Agent工具扩展规划.md`](./Agent工具扩展规划.md) | Agent 工具演进规划 |
| [`echo-backend/LOCAL_DEVELOPMENT.md`](./echo-backend/LOCAL_DEVELOPMENT.md) | 后端本地配置与 Flyway 约定 |
| [`echo-backend/TUSD_LOCAL_DEVELOPMENT.md`](./echo-backend/TUSD_LOCAL_DEVELOPMENT.md) | Tus 大文件上传本地调试 |
| [`部署命令文档.md`](./部署命令文档.md) | 现有部署命令记录 |
| [`开发日志.md`](./开发日志.md) | 持续开发、验证与回归记录 |

## 注意事项

- `upload/`、`target/`、`dist/`、`node_modules/`、`deploy/dist/` 均为运行或构建产物，不应提交。
- 数据库结构只能通过新增 Flyway 迁移升级；不要修改已经执行过的 `V*.sql`。
- Redis 是实时在线状态与跨节点推送的加速层，MySQL 是业务数据真源。
- 默认 CORS 配置便于本地和多端调试；正式公网部署前应按实际域名收紧来源策略。
