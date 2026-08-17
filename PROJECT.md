# Echo 聊天室 — 前端实现文档

> 本文档基于实际代码（`echo-frontend` 及 `echo-backend`）整理，描述前端项目的结构、运行方式、路由、API 契约与实时通信协议。
> 根目录已有的 `README.md` 为**概要设计文档**，其中部分内容（如文件分片上传、黑名单、群组等）在代码中尚未实现；本文档以真实代码为准。

---

## 一、项目简介

Echo 是一个基于 **Vue 3 + Element Plus** 的社交聊天应用，同时打包为 **Electron 桌面客户端**（Windows）。功能包括：账号注册/登录、单聊（文本/图片/文件）、WebRTC 音视频通话、好友管理、用户动态（图文朋友圈）、内容举报、以及管理员后台（用户管理 / 内容审核 / 系统监控 / 系统配置）。

- 实时消息、已读回执、通话信令通过 **WebSocket**（`/ws`）传输。
- 文件/图片上传通过 **HTTP**（`/chat/file/upload`）上传到后端本地存储。
- 鉴权使用 **JWT**，Token 存于 `localStorage`，前端附带 24h 本地过期校验。

### 技术栈

| 层 | 技术 |
|----|------|
| 前端框架 | Vue 3 (`<script setup>`) + Vite 5 |
| UI 组件 | Element Plus 2.7 + `@element-plus/icons-vue` |
| 状态/路由 | Pinia（已安装但未大量使用）、Vue Router 4（hash 模式） |
| HTTP | Axios（`src/util/request.js`） |
| 实时通信 | WebSocket（`src/util/webSocket.js`，自实现重连） |
| 音视频 | WebRTC（`RTCPeerConnection` + STUN，点对点） |
| 桌面端 | Electron 33 + electron-builder（NSIS 安装包、portable 免安装包、win-unpacked 调试目录） |

---

## 二、环境变量与配置

### `config.js`（前端 → 后端地址）
```js
export const config = { target: 'http://10.227.100.50:8088' }; // 后端基址
```
所有 HTTP 请求与（打包后）WebSocket 连接都基于此地址。开发时由 Vite 代理转发（见下）。

### `vite.config.js` 代理
开发服务器（`npm run dev`，端口 `8089`）将以下路径代理到 `config.target`：
- `/api` → 后端（去掉 `/api` 前缀）
- `/upload` → 后端静态资源
- `/ws` → 后端 WebSocket（`ws: true`）

### 运行时环境变量（可选，优先级高于 `config.js`）
- `VITE_API_BASE`：覆盖 HTTP 接口基址（生产/桌面端用）
- `VITE_WS_HOST` / `VITE_WS_PROTOCOL`：覆盖 WebSocket 主机/协议

> 注意：`electron/main.cjs` 中有一处占位 URL `releaseocalhost:8089`（开发态加载地址），打包后走 `dist/index.html`，不影响生产构建。

---

## 三、目录结构

```
echo-frontend/
├── config.js                  # 后端地址配置
├── vite.config.js             # Vite + 代理 + 别名(@)
├── package.json               # 依赖与构建脚本
├── index.html
├── electron/
│   ├── main.cjs               # Electron 主进程
│   └── preload.cjs            # 暴露 window.ECHO_DESKTOP
└── src/
    ├── main.js                # 应用入口（注册 Pinia / Router / Element Plus / zh-CN）
    ├── App.vue                # 仅 <router-view>
    ├── css/main.css
    ├── router/index.js        # 路由表 + 鉴权守卫
    ├── util/
    │   ├── request.js         # Axios 实例 + 拦截器 + upload URL 解析
    │   └── webSocket.js       # WebSocketService（连接/重连/事件）
    ├── img/avatar/            # 默认头像 Member001~009
    └── views/
        ├── LoginView.vue          # 用户登录
        ├── RegisterView.vue       # 用户注册（邮箱验证码）
        ├── HomeView.vue           # 主框架（侧边栏 + 子路由）
        ├── ChatView.vue           # 聊天 + 文件 + WebRTC 通话
        ├── FriendView.vue         # 好友列表/请求/添加
        ├── PostView.vue           # 动态发布/点赞/评论/举报
        ├── SettingsView.vue       # 资料与密码设置
        └── admin/
            ├── AdminLoginView.vue # 管理员登录
            ├── AdminLayout.vue    # 管理后台框架
            ├── UserManagementView.vue
            ├── ReportManagementView.vue
            ├── SystemMonitorView.vue
            └── SystemConfigView.vue
```

---

## 四、路由与鉴权

路由采用 **hash 模式**（`createWebHashHistory`）。核心路由：

| 路径 | 组件 | 说明 |
|------|------|------|
| `/login` | LoginView | 用户登录（已登录则跳 `/home/chat`） |
| `/register` | RegisterView | 注册 |
| `/home/chat` | ChatView | 聊天（Home 子路由） |
| `/home/friends` | FriendView | 好友 |
| `/home/posts` | PostView | 动态 |
| `/home/settings` | SettingsView | 设置 |
| `/admin/login` | AdminLoginView | 管理员登录 |
| `/admin/monitor` | SystemMonitorView | 系统监控（默认重定向） |
| `/admin/users` | UserManagementView | 用户管理 |
| `/admin/reports` | ReportManagementView | 内容审核 |
| `/admin/config` | SystemConfigView | 系统配置 |

**前端鉴权守卫**（`router.beforeEach`）：
- 从 `localStorage` 读取 `token` 与 `tokenExpiresAt`，过期则清空并视为未登录。
- 未登录访问受保护页面 → 重定向 `/login`。
- 管理员路由目前仅做简单放行（`/admin` 下除 `/admin/login` 外），后端需用 JWT 角色校验兜底。

**登录态存储字段**：`token`、`tokenExpiresAt`、`userId`、`username`。

---

## 五、HTTP API 契约

基础约定：
- 请求头：`Authorization: Bearer <token>`（由拦截器自动添加）；`Content-Type: application/json`。
- 响应统一结构：`{ code, message, data }`；`code === 200` 为成功，拦截层直接返回 `response.data`。
- `403` 响应 → 提示“登录已过期”并清理登录态跳登录。
- 文件上传为 `multipart/form-data`，字段 `file` + `receiverId`（动态/头像传 `0`）。

### 5.1 认证 `/auth`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/register` | 注册（需 `captcha`） |
| POST | `/auth/login` | 登录，返回 `token / userId / username` |
| POST | `/auth/captcha/send` | 发送邮箱验证码 `{ email, type: "REGISTER" }` |

### 5.2 用户 `/user`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/user/profile` | 获取个人资料 |
| PUT | `/user/profile` | 更新资料（昵称/头像等） |
| PUT | `/user/password` | 修改密码 `{ oldPassword, newPassword }` |

### 5.3 聊天 `/chat`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/chat/conversations` | 会话列表 `{ list: [...] }` |
| GET | `/chat/messages?friendId=` | 与某好友的历史消息 |
| POST | `/chat/file/upload` | 文件/图片上传，返回 `{ fileUrl, fileName, fileSize }` |

### 5.4 好友 `/friends`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/friends/list` | 我的好友 |
| GET | `/friends/requests` | 收到的好友请求 |
| GET | `/friends/search?keyword=` | 按用户名/昵称搜索用户 |
| POST | `/friends/request` | 发送好友请求 `{ targetUserId, remark }` |
| PUT | `/friends/request/{id}/handle` | 同意/拒绝 `{ action: ACCEPT|REJECT, remark }` |
| PUT | `/friends/{friendId}/remark` | 设置备注 |
| DELETE | `/friends/{friendId}` | 删除好友 |

### 5.5 动态 `/posts`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/posts` | 动态列表（含用户信息/状态） |
| POST | `/posts` | 发布 `{ content, visibility, mediaUrls }` |
| DELETE | `/posts/{postId}` | 删除动态 |
| POST | `/posts/{postId}/like` | 点赞/取消 |
| GET | `/posts/{postId}/comments` | 评论列表 |
| POST | `/posts/{postId}/comments` | 发表评论 `{ content }` |

> 动态发布前后端会做**敏感词过滤**（`system_config` 表 `sensitive.words`，逗号分隔），命中则拦截并返回提示。

### 5.6 举报 `/reports`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/reports` | 提交举报 `{ targetType: "POST", targetId, reportType, description }` |

### 5.7 管理员 `/admin`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/login` | 管理员登录 |
| GET | `/admin/users?username=&page=&size=` | 用户列表（分页） |
| PUT | `/admin/users/{userId}/status` | 改状态 `{ status: 1正常/0禁用/2封禁 }` |
| PUT | `/admin/users/{userId}/reset-password` | 重置密码为 `123456` |
| GET | `/admin/reports?status=&page=&size=` | 举报列表 |
| PUT | `/admin/reports/{id}/handle` | `{ action: PROCESS|DISMISS }` |
| GET | `/admin/monitor/stats` | 概览统计 `{ totalUsers, newUsersToday, totalPosts, pendingReports }` |
| GET | `/admin/system/configs` | 系统配置列表 |
| PUT | `/admin/system/configs` | 新增/更新配置 `{ key, value, description }` |

---

## 六、WebSocket 实时协议（`/ws`）

连接地址：`ws(s)://<host>/ws?token=<jwt>`。连接时后端校验 JWT 与账号状态（状态非 `1` 直接关闭）。

### 客户端 → 服务端 消息类型
| type | data | 说明 |
|------|------|------|
| `CHAT_MESSAGE` | `{ receiverId, messageType: TEXT|IMAGE|FILE, content, clientMessageId }` | 发送消息（`FILE` 的 content 为 JSON `{url,name,size}`） |
| `MESSAGE_READ` | `{ senderId, messageIds: [] }` | 已读回执 |
| `CALL_SIGNAL` | `{ toUserId, kind, callId, callType, payload }` | 通话信令（见下） |
| `HEARTBEAT` | — | 心跳（后端原样回 `HEARTBEAT`） |
| `TYPING` | `{ friendId }` | “正在输入”状态（前端未消费展示） |

### 服务端 → 客户端 消息类型
| type | data | 触发时机 |
|------|------|----------|
| `NEW_MESSAGE` | `{ id, senderId, receiverId, content, messageType, createdAt, isRead, fileUrl?, fileName?, fileSize? }` | 收到新消息 |
| `MESSAGE_ACK` | `{ clientMessageId, serverMessageId?, status: SENT|REJECTED_NOT_FRIEND, reason? }` | 消息落库确认（非好友会被撤销乐观更新） |
| `MESSAGE_READ_RECEIPT` | `{ messageIds: [], readerId }` | 对方已读 |
| `CALL_SIGNAL` | `{ fromUserId, toUserId, kind, callId, callType, payload }` | 转发通话信令 |
| `USER_ONLINE` / `USER_OFFLINE` | `{ userId }` | 上下线广播（前端未消费） |
| `HEARTBEAT` | — | 心跳回应 |
| `TYPING` | `{ userId }` | 对方正在输入 |

### 好友关系校验
服务端 `CHAT_MESSAGE` 会校验双方是否为 `status=1` 好友；非好友返回 `MESSAGE_ACK.status = REJECTED_NOT_FRIEND`，前端撤销该条乐观更新消息并提示。

### WebRTC 通话信令（`CALL_SIGNAL` 的 `kind`）
- `OFFER`：发起方 SDP offer
- `ANSWER`：接听方 SDP answer
- `ICE`：ICE candidate（远端描述未就绪时缓存，就绪后补加）
- `DECLINE`：拒绝
- `BUSY`：忙线
- `OFFLINE`：对方不在线（信令服务器探测不到对端时回发）
- `END`：挂断

通话为**点对点**：使用 `RTCPeerConnection`，STUN 服务器 `stun:stun.l.google.com:19302`，仅音频用 `{audio:true}`，视频用 `{audio:true, video:true}`。要求 **HTTPS 或 localhost** 的安全上下文才能访问摄像头/麦克风。

---

## 七、关键前端实现点

### 7.1 `util/request.js`
- `getApiOrigin()`：开发环境基址为空（走 Vite 代理 `/api`）；生产/桌面/灰度 `file:` 协议下用 `config.target`。
- `resolveUploadUrl(value)`：把后端返回的相对 `/upload/...` 或旧地址（`localhost:8088` / `127.0.0.1:8088` / `10.227.100.50:8088`）统一解析为当前环境可访问的绝对 URL。
- 拦截器：请求自动加 `Bearer`；响应直接展开 `data`；`403` 清理登录态。
- **未登录且无 token 访问非 `/auth/` 接口时，自动跳转 `/login`**（仅浏览器环境）。

### 7.2 `util/webSocket.js`
- `WebSocketService` 类：`maxReconnectAttempts=5`、指数退避重连（`reconnectInterval * attempts`）、事件订阅（`on/open/message/close/error`）、`send`（对象自动 JSON 序列化）。
- `buildWebSocketUrl`：打包/生产下用 `config.target`（或 `VITE_WS_HOST/PROTOCOL`）；开发下用当前 `location.host` 走代理。
- `useWebSocket(options)`：组合式封装，自动 `connect()`，在 `onUnmounted` 时 `close()`。

### 7.3 `ChatView.vue`（最核心）
- 会话列表 + 聊天窗口双栏；移动端（≤768px）切换为单栏（返回按钮）。
- 消息支持 `TEXT`/`IMAGE`/`FILE`，文件 ≤10MB，上传前校验。
- **乐观更新**：发送后先本地追加（临时 id），收到 `MESSAGE_ACK` 后替换为 `serverMessageId`；被拒则移除。
- 进入会话或收到新消息时发送 `MESSAGE_READ`，对方收到 `MESSAGE_READ_RECEIPT` 标记已读。
- 音视频通话完整状态机：`idle → calling/ringing → connecting → in_call → 挂断`；处理 OFFER/ANSWER/ICE/DECLINE/BUSY/OFFLINE/END。
- 通过 `window` 事件 `profile-updated` 与 `SettingsView` 联动更新头像。

### 7.4 跨组件联动
- `HomeView` / `ChatView` 监听自定义事件 `profile-updated`（资料更新后 `SettingsView` 派发），刷新头像/昵称。
- `FriendView.startChat` 通过 `router.push('/home/chat', { query: { friendId, nickname, avatar } })` 跳转到指定会话；`ChatView` 监听 `route.query.friendId` 自动选中会话。

### 7.5 移动端适配
`HomeView`、`ChatView`、`FriendView`、`RegisterView` 均通过 `window.matchMedia('(max-width: 768px)')` 监听断点：`HomeView` 侧边栏变底部 Tab，`ChatView` 会话/聊天切换显示。

---

## 八、Electron 桌面端

- `package.json` 中 `main: electron/main.cjs`，`build` 配置 `appId: com.echo.chatroom`，`productName: EchoChat`，桌面端发布安装包、免安装包和解包目录。
- `preload.cjs` 通过 `contextBridge` 暴露 `window.ECHO_DESKTOP.isElectron = true`（用于区分桌面环境，当前前端未大量使用）。
- 渲染进程 `sandbox:false`、`contextIsolation:true`、`nodeIntegration:false`。
- 外部链接（`shell.openExternal`）在独立浏览器打开，Electron 内拒绝。

### 构建命令
```bash
npm install
npm run dev              # 开发（Vite，端口 8089，自动打开浏览器）
npm run build            # 仅构建前端到 dist/
powershell -ExecutionPolicy Bypass -File ..\scripts\build-release.ps1 -SkipBackend -SkipMobile
# 只生成一个 NSIS 安装包及 SHA-256 校验文件
```

> 打包后的桌面程序通过 `config.target`（或 `VITE_API_BASE` / `VITE_WS_*`）连接后端；若后端地址变化，需修改 `config.js` 或注入对应环境变量后重新打包。

---

## 九、与后端数据模型对应（关键表）

> 详细建表语句见 `echo-backend/src/main/resources/echo_chat.sql`。

- `user`：用户（含 `status` 0禁用/1正常/2封禁、`role` USER/ADMIN、`avatar_url`）。
- `friendship`：好友关系（含 `remark` 备注）。
- `friend_request`：好友请求（`status` PENDING/ACCEPTED/REJECTED）。
- `message`：消息（`message_type` TEXT/IMAGE/FILE、文件字段、`is_read`）。
- `conversation`：会话（双方各一条，`user1_id<->user2_id`，`unread_count`）。
- `post`：`status` 0已屏蔽/1正常，`visibility` PUBLIC/PRIVATE，`media_urls` JSON。
- `comment` / `post_like`：动态评论与点赞。
- `report`：举报（`target_type` POST/USER、`report_type`、`status` PENDING/PROCESSED/DISMISSED）。
- `system_config`：`config_key` / `config_value`（含 `sensitive.words` 敏感词库）。

---

## 十、已知差异与待完善（相对 `README.md` 设计）

1. **文件上传**：实际为单次 `multipart` 上传（≤10MB），无设计文档中的“分片上传 / 断点续传 / MD5 校验”。
2. **黑名单、群组、@好友、话题、桌面通知、搜索、数据统计图表**等在设计文档中提及，但前端/后端代码中尚未实现。
3. **密码加密**：设计文档称 `md5`，以后端实现为准（需核对 `UserServiceImpl`）。
4. **管理员鉴权**：前端路由对 `/admin` 仅简单放行，真实权限应以后端 JWT 角色校验为准。
5. **WebSocket 上下线/正在输入**：后端会广播 `USER_ONLINE`/`USER_OFFLINE`/`TYPING`，但前端目前未消费展示。
6. **Electron 开发加载地址**存在占位字符串 `releaseocalhost:8089`，需修正为 `http://localhost:8089`（仅影响 `npm run electron` 开发模式，不影响打包）。
