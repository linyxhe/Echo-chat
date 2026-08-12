# Echo Chat Room · 回声聊天室

一个功能完整的即时通讯 + 社交 + AI 助手的 Web 应用，包含 **Spring Boot 后端**、**Vue 3 前端** 与 **Electron 桌面客户端**（Windows）。支持实时单聊/群聊、大文件断点续传、WebRTC 音视频通话、朋友圈动态、DeepSeek AI 助手（含本地知识库 RAG）、全文搜索、内容审核与管理员后台。

> 本 README 反映仓库当前代码状态。前端实现细节见 [`PROJECT.md`](./PROJECT.md)，后端本地开发/安全边界见 [`echo-backend/LOCAL_DEVELOPMENT.md`](./echo-backend/LOCAL_DEVELOPMENT.md)。完整接口契约请以 `echo-backend` 中各 Controller 为准。

---

## 一、功能特性

### 💬 消息
- **实时单聊**：WebSocket 推送、已读回执（含已读时间）、“正在输入”、发送中/已读状态、心跳与断线重连。
- **消息可靠性**：`client_message_id` 幂等去重（Flyway 唯一索引）、Redis 在线状态账本、多标签页会话路由、跨节点实时推送（Redis Pub/Sub）、隐私开关（在线状态/已读回执默认隐藏）。
- **群聊**：建群、邀请/入群、群成员管理、群消息实时广播、未读游标、群文件/图片、联系人备注、会话置顶/归档。
- **会话管理**：会话列表（好友 + AI + 群）、置顶、隐藏/恢复、清空聊天记录（软删）。

### 📁 文件传输
- **小文件（≤10MB）**：HTTP `multipart` 上传，受控下载 URL（`/files/{id}/content?access=...`）。
- **大文件（>10MB）**：`tusd` 分片上传 + 断点续传，上传进度、处理中状态、`READY` 后自动发送消息、刷新/断网恢复（`localStorage` 任务持久化）。
- 所有文件经 **SHA-256 完整性校验** 后进入受控下载，不公开静态托管；`message`/`file_asset` 元数据落库。

### 🤖 AI 助手
- **DeepSeek 流式对话**（langchain4j + OpenAI 兼容接口）：WS 流式推送 token、Markdown 渲染（`marked` + `dompurify`）、多轮记忆（DB 上下文窗口）、清空会话即失忆、管理端开关。
- **自定义 AI 助手**：用户可创建/编辑/删除私有助手，备注、停止生成。
- **知识库 RAG**：本地中文嵌入模型 `bge-small-zh-v1.5`（无 API key）、MySQL 持久化向量 + 余弦检索、`txt/md/pdf/docx` 解析、异步索引与断点恢复、知识库分类与文档管理（管理端）。

### 👥 社交
- **好友**：搜索、请求/同意/拒绝、备注、删除、在线状态。
- **朋友圈动态**：图文发布、可见范围、点赞/评论、删除、举报、敏感词过滤。
- **通知**：系统通知、好友请求、管理员全体广播（顶部铃铛 + 未读角标 + WS 实时推送）。
- **全文搜索**：用户 / 单聊消息 / 群消息（防抖下拉联想）。

### 🎮 其他
- **音视频通话**：WebRTC 点对点（音频/视频），信令经 WebSocket，STUN/TURN 支持。
- **内容审核**：举报提交/处理流程、敏感词库（`system_config` 可配置）。
- **管理员后台**：用户管理（封禁/重置密码）、举报审核、系统监控（在线/数据统计）、系统配置（AI 开关、敏感词）、知识库管理。
- **桌面端**：Electron 打包（NSIS + portable），`config.js` / 环境变量切换后端地址。
- **移动端适配**：≤768px 断点，侧边栏变底部 Tab，聊天双栏变单栏。

---

## 二、技术栈

| 层 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.x · Spring WebSocket · Spring Security (JWT) |
| ORM / 数据库 | MyBatis-Plus 3.5.5 · MySQL 8.0 · Flyway（迁移脚本） · Druid 连接池 |
| 缓存 / 实时 | Redis（会话、在线状态、跨节点 Pub/Sub） |
| AI | langchain4j 1.18 · DeepSeek（OpenAI 兼容流式） · bge-small-zh-v1.5 本地嵌入 |
| 文件 | tusd（分片上传） · 受控下载 · SHA-256 校验 |
| 文档解析 | PDFBox 3.0.8 · Apache POI 5.5.1（知识库） |
| 前端框架 | Vue 3 (`<script setup>`) · Vite 5 · Vue Router 4 (hash) · Pinia |
| UI / 请求 | Element Plus 2.7 · Axios |
| 实时通信 | WebSocket（自实现重连/看门狗） · WebRTC |
| 上传 | Uppy (`@uppy/core` + `@uppy/tus`) |
| Markdown | marked + dompurify |
| 桌面端 | Electron 33 · electron-builder |

---

## 三、架构概览

```
┌────────────────────────────────────────────────────────────┐
│  前端 Vue3（浏览器 / Electron / 移动端自适应）                 │
│  HTTP REST + WebSocket(/ws) + WebRTC                        │
└──────────────┬──────────────────────────────┬───────────────┘
               │ /api（Vite 代理，去前缀）        │ /files 受控下载
┌──────────────▼──────────────────────┐   ┌────▼──────────────┐
│  Spring Boot 后端 (:8088)           │   │  tusd (:1080)      │
│  ├ Controller / Service / Mapper   │◄──┤  大文件分片存储     │
│  ├ ChatEndpoint (WebSocket 会话)   │   └────────────────────┘
│  ├ 消息可靠性：Presence + Pub/Sub  │
│  ├ AI：langchain4j → DeepSeek / KB │
│  └ 内容审核 / 通知 / 搜索           │
└───────┬──────────────┬─────────────┘
        ▼              ▼
    MySQL 8.0      Redis（会话/在线/pubsub）
```

**通信约定**
- HTTP 用于注册/登录/资料/好友/动态/管理/文件等常规请求。
- WebSocket（`/ws?token=...`）用于实时消息、已读回执、通话信令、通知、AI 流式 token。
- 鉴权为 JWT，Token 存 `localStorage`，前端附带本地过期校验（24h）；后端 WS 连接校验 JWT 与账号状态。
- 大文件走独立 tusd 服务，Spring Boot 负责上传意图、权限、状态机与受控下载。

---

## 四、目录结构

```
Echo-chat-room/
├── echo-backend/                      # Spring Boot 后端
│   ├── src/main/java/com/echo/
│   │   ├── config/                    # Security / WebSocket / Redis Pub/Sub 配置
│   │   ├── controller/                # REST 接口（认证/用户/好友/聊天/动态/群/通知/AI/知识库/管理/文件…）
│   │   ├── service/                   # 业务逻辑（含 Presence / Group / Notification / Search / Kb…）
│   │   ├── ai/                        # AI 聊天 / Bot 用户解析
│   │   ├── file/                      # 文件上传意图、完成校验、迁移
│   │   ├── websocket/                 # ChatEndpoint + WsEventPublisher/Subscriber
│   │   ├── pojo/                      # 实体
│   │   └── mapper/                    # MyBatis-Plus Mapper
│   ├── src/main/resources/
│   │   ├── application.yml            # 端口/目录/文件大小/AI/RAG/心跳等配置（全部可用环境变量覆盖）
│   │   ├── db/migration/              # Flyway V1~V21 迁移脚本（含权限/群聊/知识库/隐私等）
│   │   └── echo_chat.sql              # 完整建表脚本
│   └── pom.xml
├── echo-frontend/                     # Vue 3 前端 + Electron 桌面端
│   ├── config.js                      # 后端地址（也可用 VITE_API_BASE 等覆盖）
│   ├── vite.config.js                 # 代理 /api /files /upload /ws → 后端
│   ├── electron/                      # Electron 主进程与 preload
│   ├── scripts/dev.mjs                # npm run dev：同时管理 Vite 与本地 tusd
│   └── src/
│       ├── router/index.js            # hash 路由 + 鉴权守卫
│       ├── util/request.js            # Axios 实例 + 拦截器 + upload URL 解析
│       ├── util/webSocket.js          # WebSocketService（重连/看门狗/auth-failed）
│       ├── util/tusUpload.js          # Uppy/Tus 大文件上传
│       ├── composables/               # 移动端断点等组合式函数
│       └── views/                     # 登录/注册/首页(聊天/好友/动态/设置)/管理后台/AI 助手/知识库…
├── scripts/                           # start-dev / start-prod / start-tusd (PowerShell)
├── tools/                             # 本地工具（tusd 二进制、WS 测试脚本）
├── upload/                            # 运行数据：tusd 临时分片 + 永久文件（不入库）
├── README.md / PROJECT.md / CLAUDE_HANDOFF.md / api文档.md / 开发日志.md
```

---

## 五、本地快速启动

### 前置依赖
- JDK 17+、Maven 3.8+、Node.js 18+、MySQL 8.0、Redis

### 1. 初始化数据库
创建库并导入脚本（Flyway 会自动执行 `db/migration` 迁移，也可手动执行 `echo_chat.sql`）：

```sql
CREATE DATABASE echo_chat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置后端环境变量
后端所有敏感配置均通过环境变量注入（默认值见 `application.yml`）。本地可复制 `application.yml` 为 `application-local.yml`（已 gitignore）填写：

| 变量 | 说明 |
| --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接 |
| `JWT_SECRET` | JWT 签名密钥 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 邮箱（验证码） |
| `AI_API_KEY` | DeepSeek API key（不填则 AI 返回固定文案） |
| `SERVER_PORT` | 默认 `8088`；`CONTEXT_PATH` 生产可设 `/echo-chat` |
| `APP_FILE_MAX_SIZE` | 文件大小上限（默认 20 GiB，需同步 tusd） |

### 3. 启动后端
```bash
cd echo-backend
mvn spring-boot:run        # 或直接运行 OnlineChatRoomApplication（端口 8088）
```

### 4. 启动 tusd（大文件分片）
```powershell
powershell -File scripts/start-tusd.ps1   # 首次会自动下载 tusd 并监听 :1080
```

### 5. 启动前端
```bash
cd echo-frontend
npm install
npm run dev                # Vite 端口 8089，自动打开浏览器；同时管理本地 tusd
```

> 浏览器访问 `http://localhost:8089`。Vite 已将 `/api`、`/files`、`/upload`、`/ws` 代理到后端 `8088`。局域网设备（手机）可用 `npm run dev` 的 host 地址访问。

### 构建 / 打包
```bash
cd echo-frontend
npm run build               # 仅构建前端到 dist/
npm run electron:build      # 构建前端 + 打包 Windows 桌面程序（release/）
```

```powershell
# 全量打包（前端构建 + 复制到后端 static + 后端打 jar）
powershell -File scripts/start-prod.ps1
```

---

## 六、主要数据表

详见 `echo-backend/src/main/resources/echo_chat.sql` 与 `db/migration/`：

- `user` / `friendship` / `friend_request`
- `conversation` / `message`（含 `client_message_id`、`read_at`、文件字段）
- `file_asset`（受控文件元数据）
- `chat_group` / `chat_group_member` / `chat_group_message` / `chat_group_invitation`
- `post` / `comment` / `post_like` / `report`
- `notification` / `system_config`
- `kb_document` / `kb_chunk`（知识库 + 向量）
- `ai_assistant`（用户自定义 AI 助手）

---

## 七、文档导航

| 文档 | 内容 |
| --- | --- |
| [`PROJECT.md`](./PROJECT.md) | 前端实现细节、路由、API 契约、WebSocket 协议、Electron |
| [`echo-backend/LOCAL_DEVELOPMENT.md`](./echo-backend/LOCAL_DEVELOPMENT.md) | 后端本地开发与安全边界说明 |
| [`echo-backend/TUSD_LOCAL_DEVELOPMENT.md`](./echo-backend/TUSD_LOCAL_DEVELOPMENT.md) | tusd 大文件上传本地运行说明 |
| [`开发日志.md`](./开发日志.md) | 持续开发记录与版本进度 |
| [`新模块开发流程.md`](./新模块开发流程.md) | 新增功能模块的规范流程 |
| [`CLAUDE_HANDOFF.md`](./CLAUDE_HANDOFF.md) | Claude 交接文档（架构与验证记录） |

---

## 八、注意事项

- `application-local.yml`、`echo-backend/src/main/resources/static/config.js` 含本地/生产私密配置（DB、邮箱、DeepSeek key、JWT secret、TURN 凭据），**均已 gitignore**，请勿提交真实凭据。
- `upload/`、`target/`、`dist/`、`node_modules/` 为运行数据与构建产物，不入库。
- 大文件状态流：`UPLOADING → UPLOADED → PROCESSING → READY`（异常：`FAILED/EXPIRED/CANCELLED`）；`PROCESSING` 会做完整 SHA-256，超大文件可能需要较长时间。
- WS 投递依赖 Redis（Pub/Sub 热路径）；Redis 不可用时降级为本实例直发，DB 为真源兜底。
- 音视频通话需要 HTTPS 或 localhost 的安全上下文才能调用摄像头/麦克风。
