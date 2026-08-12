# Echo Chat Room — Claude 交接文档

交接时间：2026-08-07  
当前阶段：阶段 2 完成（tusd 大文件 + 文件受控管理 + 关闭旧目录），AI Gateway v1/v2/v3 完成（v3 = 知识库 RAG），知识库增强 v1/v2 完成，**消息可靠性 v1/v2/v3 完成**（跨节点 pub/sub + 隐私开关），**社交增强 v1/v2 完成**（群聊 + 系统通知 + 全文搜索），**群聊并入「消息」页**（ChatView 合并，移除独立 GroupsView）；待浏览器真机回归、社交增强 v3（语音/内容审核）、知识库增强后续（OCR/向量库）、消息可靠性 v4（outbox）等。

## 先做什么

> 状态：已完成。后端已重启并验证通过（见下文「本次修复」）。后续接手先跑一遍受控下载冒烟即可确认环境健康。

1. 后端已在端口 `8088` 运行（当前由 IDEA 启动，PID 会变）。不要删除 `upload/tusd` 目录。
2. 启动日志已确认 Flyway 执行 `V3__message_client_idempotency.sql`，应用正常启动。

## 本次已实现的架构

### 文件上传链路

```text
Vue/Uppy Tus -> tusd:1080 -> tusd hooks -> Spring Boot:8088
                                      -> MySQL file_asset
完成上传 -> POST /files/{id}/complete -> PROCESSING
后台线程 -> 移动文件 + SHA-256 -> READY
浏览器轮询状态 -> WebSocket CHAT_MESSAGE -> MESSAGE_ACK
```

- 小于等于 `10MB`：`/chat/file/upload` → `FileService.uploadSmallFile`，同步建 READY `file_asset`，返回受控 `/files/{id}/content?access=...` URL。
- 大于 `10MB`：`@uppy/core + @uppy/tus` 分片上传至独立 tusd 服务。
- tusd 仅存储字节流；Spring Boot 负责上传意图、权限、状态、完成校验和受控下载。
- 状态流：`UPLOADING -> UPLOADED -> PROCESSING -> READY`，异常时可为 `FAILED`、`EXPIRED`、`CANCELLED`。

### 刷新恢复与可靠发送

- 浏览器待发送文件任务存储于 `localStorage` 键 `echo.pending-file-messages`。
- 刷新恢复时，消息发送使用任务中持久化的 `receiverId`，不再使用可能尚未初始化的 `currentFriendId`。
- 文件 `READY` 后才发送 WebSocket 聊天消息；本地任务收到 `MESSAGE_ACK` 后才删除。
- `message.client_message_id` 通过 Flyway V3 和唯一索引实现发送去重。刷新发生在 WebSocket 发送与 ACK 之间时，重试会收到原消息 ACK，不会生成重复消息。
- `PROCESSING` 状态在每次状态查询和每 30 秒定时任务中都会重新投递后台校验，解决 Spring Boot 重启中断校验后永久轮询的问题。

### AI 助手（LangChain4j + DeepSeek）

```text
用户在聊天 UI 打开「AI 助手」合成会话 -> 发 CHAT_MESSAGE(receiverId=botUserId)
ChatEndpoint 跳过好友校验 -> 落库 + MESSAGE_ACK -> 触发 AiChatService
langchain4j StreamingChatModel(DeepSeek) 流式回调 -> WS 推 AI_STREAM_CHUNK(token)
onComplete -> 持久化 bot 回复(client_message_id="ai:<用户消息id>" 幂等) -> AI_STREAM_DONE
无 key / 非文字 -> 固定文案 DONE；异常 -> AI_STREAM_ERROR
```

- AI 助手是虚拟用户（`user.role='BOT'`，username `ai_assistant`，**id 不写死**，按 username 解析）；bot 对所有用户可见，无需加好友。
- 前端恒定在会话列表顶部注入合成「AI 助手」条目；`getConversations` 服务端过滤掉 bot 会话避免重复。
- WS 会话写入已串行化（每 Session 一把锁）：WS 处理线程与 AI 流式回调线程并发写同一 session 曾触发 `TEXT_FULL_WRITING`，全部 12 处 `sendText` 走 `ChatEndpoint.sendText` helper。
- **多轮记忆（v2）**：`AiChatServiceImpl` 从 `message` 表取该用户与 bot 最近 `app.ai.context-window-messages`（默认 20）条 TEXT 消息作为上下文（**DB 上下文窗口，不用 `MessageWindowChatMemory`**）——天然持久化、与清空会话联动（软删即失忆）、无需新增 `dev.langchain4j:langchain4j` 依赖。上下文按方向过滤软删字段。
- **Markdown 渲染（v2）**：前端 `marked@^12` + `dompurify@^3`；仅 `senderId === aiBotId` 的 TEXT 气泡 `v-html`（`renderMarkdown`），其余纯文本；`.markdown` 的 `:deep()` 样式覆盖（注意父级 `word-break: break-all`）。
- **清空会话（v2）**：`ChatServiceImpl.deleteMessages` 的 `"ALL"` 已实现（软删当前用户视角 + `ConversationService.clearConversation` 重置本方向会话行）；`getMessages` 已补软删过滤；`DELETE /chat/conversations/{friendId}` 通用（AI 与好友）。前端仅 AI 会话头部放了清空按钮，用 `ignoredStreams` 忽略在途流残余事件。
- **管理端 AI 开关（v2）**：`system_config.ai.enabled`（缺省启用，`"false"` 关闭）→ bot 回固定文案；`GET /ai/bot-info` 返回 `enabled`；管理端「系统配置」页顶部有 el-switch。
- 配置：`app.ai.*`（bot-username / bot-nickname / persona / context-window-messages）与 `langchain4j.open-ai.streaming-chat-model.*`（base-url `https://api.deepseek.com/v1`、api-key、model-name、temperature）。API key 在 `application-local.yml`。

### 消息可靠性（Redis 在线状态 + 心跳淘汰 + 已读展示 + 重连补拉）

- **PresenceService**（`com.echo.service.PresenceService`）：Redis ZSET `presence:online` 做存在账本（member=userId，score=心跳 epoch）。内存 `ChatEndpoint.onlineUsers` 仍是 WS 路由表，两者在连接/关闭/心跳同步。Redis 挂时全部降级（isOnline=false/count=0），应用不崩。
- **心跳淘汰**：`ChatEndpoint.lastHeartbeatAt`（Session→时间）+ `@Scheduled` 每 60s 清扫，关闭心跳超时（默认 120s）的死会话并清 Redis 陈旧成员。配置 `app.presence.*`（online-ttl-seconds=150 / evict-idle-seconds=120 / sweep-interval-ms=60000）。
- **多会话**：onClose/onError 只在「无其他存活会话」时 markOffline + 广播离线（`hasOtherLiveSession`），修复多标签页 map 覆盖导致的存在误报。
- **列表/监控在线**：`GET /chat/conversations`、`GET /friends/list` 返回 `online`；`GET /admin/monitor/stats` 返回 `onlineUsers`。
- **前端 webSocket.js**：服务端存活看门狗（无入站 >65s 主动断开重连）、持久重连（指数退避 3s→30s 封顶）、close 1003/Authentication → `auth-failed`（清登录 + 跳 /login）、connect 前置 token 检查。`request.js` 导出 `getValidToken`/`clearAuthStorage`。
- **ChatView**：会话列表在线点（`conv.online` + `USER_ONLINE/OFFLINE` 帧）、重连补拉（open 非首次重拉会话+当前消息）、自气泡「已读/已发送」（`msg.isRead`，AI 会话不显示）。
- **FriendView**：好友在线点 + `useWebSocket` 实时更新 + focus 刷新。
- **多标签页会话路由（v2）**：`ChatEndpoint.onlineUsers` 为 `Map<Long, Set<Session>>`，所有投递（NEW_MESSAGE/回执/打字/呼叫/AI/广播）**扇出到该用户全部存活会话**；离线判定 = 会话集合清空。`sessionWriteLocks` 按 Session 串行化，扇出安全。
- **已读时间 read_at（v2）**：`message.read_at` 列 + `Message.readAt`；`handleMessageRead` 写入并让回执携带 `readAt`；前端 `.msg-status` 显示「已读 HH:mm」。
- **隐私开关（v3，默认关）**：`user.show_online_status` / `show_read_receipts`（V9）。在线列表字段与 USER_ONLINE/OFFLINE 广播按「目标用户自己的设置」隐藏；已读回执按「读者自己的设置」不发（发送者见「已发送」）。SettingsView「隐私」页签可改。
- **跨节点实时推送（v3）**：`WsEventPublisher`/`WsEventSubscriber`/`RedisPubSubConfig`，频道 `echo:ws:push`；`ChatEndpoint` 所有投递改发布（发布失败降级本实例直发）；CALL_SIGNAL 用 Redis presence 判离线。

### 群聊 + 系统通知（社交增强 v1）

- **群聊**：`chat_group`/`chat_group_member`/`chat_group_message`（V10）；`GroupService` 建群/成员/历史/未读（`last_read_message_id` 游标）；WS `GROUP_MESSAGE`（校验成员 → 幂等落库 → ACK → `broadcastToMembers` 按成员 `publishToUser` 扇出，跨实例）；`GroupController`（/groups）。
- **系统通知**：`notification` 表 + `NotificationService.notify`（插行 + WS `NOTIFICATION` 实时推送）/`notifyAll`；`NotificationController`（列表/未读/已读）；`AdminNotificationController` 全体广播；好友请求/被通过钩子。
- **前端**：`ChatView.vue` 会话列表**合并好友 + AI + 群**（群带「群」标签/首字符头像，`currentChatType` 分派消息/发送/已读；群成员按钮 + 建群对话框）；`HomeView` 顶栏通知铃铛（badge + dropdown + 过滤 NOTIFICATION 的 WS）+ **搜索框下拉（用户/聊天/群消息，防抖 250ms）**；`FriendView` `?tab=requests` / `?tab=search&keyword=` 深链。~~独立 GroupsView.vue 已删除~~（群入口统一走「消息」页 + 搜索 `?groupId=` 深链）。
- **全文搜索（v2）**：`SearchService`（searchUsers 附 isFriend / searchFriendMessages 软删过滤 / searchGroupMessages 我所在群）+ `SearchController` `GET /search` 三组结果。

### 知识库 RAG（本地中文嵌入 + 余弦检索 + 管理端）

- **嵌入模型**：`BgeSmallZhV15EmbeddingModel`（`langchain4j-embeddings-bge-small-zh-v15:1.18.1-beta28`，ONNX 打包在 jar，512 维，无 API key）。检索 query 前加 BGE 中文指令前缀「为这个句子生成表示以用于检索相关文章：」。
- **向量存储 = MySQL**：`kb_document` + `kb_chunk`（embedding 存 JSON 字符串），持久化避免重启重算；`KbService` 缓存向量列表做余弦 top-k（内存缓存，ingest/delete 失效）。数千分片后可换 pgvector/ES。
- **注入**：`AiChatServiceImpl` persona SystemMessage 后追加含知识库片段的 SystemMessage；知识库空/低于 `app.ai.kb.min-score`（0.3）不注入。配置 `app.ai.kb.*`（enabled/chunk-size=500/chunk-overlap=50/top-k=4/max-doc-size=1MB）。
- **管理端**：`/admin/kb`（GET documents / POST upload / DELETE {id} / GET stats），isAdmin 守卫；前端 `KnowledgeBaseView.vue`（状态卡 + 拖拽上传 + 文档表格删除），路由 `/admin/kb` + AdminLayout「知识库」菜单。
- **格式（增强 v1）**：`.txt/.md/.pdf/.docx`（PDFBox 3.0.8 + POI 5.5.1；`KbService.extractText` 按扩展名分派）。扫描件 PDF 无文本层需 OCR（未做）。
- **异步索引（增强 v1）**：`KbService.submitUpload` 同步校验+插 PENDING → `KbIndexWorker.indexDocument`（`@Async`，由 KbController 跨 bean 触发）→ `PENDING→INDEXING→READY/FAILED`（error_message 记录失败原因）。上传不阻塞。
- **断点恢复（增强 v2）**：`KbIndexRecovery`（ApplicationReadyEvent）启动时把 PENDING/INDEXING 文档交给 `KbIndexWorker.recoverDocument` 从已存 `content` 重建索引（进程重启不丢在途索引）。`KbService.indexText` 已持久化 `content`（预览 + 恢复依赖）。
- **分类/搜索/预览（增强 v2）**：`kb_document.category`（V8）；`GET /documents?keyword=` 按文件名/分类过滤且列表瘦身（content=null）；`GET /documents/{id}` 返回完整文档供预览；`PUT /documents/{id}` 改分类；`markFailed` 清残留分片。

## 关键文件

后端：

- `echo-backend/src/main/resources/application.yml`：文件大小、目录与端口配置。
- `echo-backend/src/main/java/com/echo/file/FileServiceImpl.java`：上传意图、状态查询、完成确认、恢复任务、`uploadSmallFile`。
- `echo-backend/src/main/java/com/echo/file/FileFinalizationService.java`：异步移动与 SHA-256 校验。
- `echo-backend/src/main/java/com/echo/file/LegacyFileMigrationRunner.java`：一次性 `/upload/**` → `/files/**` 存量迁移（`--app.migrate-legacy-files=true` 触发，幂等）。
- `echo-backend/src/main/java/com/echo/controller/FileController.java`：`/files/*` API。
- `echo-backend/src/main/java/com/echo/controller/TusHookController.java`：tusd hooks。
- `echo-backend/src/main/java/com/echo/websocket/ChatEndpoint.java`：文件就绪校验及 `clientMessageId` 幂等。
- `echo-backend/src/main/java/com/echo/pojo/Message.java`：新增 `clientMessageId`。
- `echo-backend/src/main/resources/db/migration/V2__file_asset_tus_uploads.sql`：文件元数据表。
- `echo-backend/src/main/resources/db/migration/V3__message_client_idempotency.sql`：消息幂等字段与唯一索引。
- `echo-backend/src/main/resources/db/migration/V4__ai_bot.sql`：`user.role` 加 `BOT` + 插入 `ai_assistant` 用户（不写死 id）。
- `echo-backend/src/main/java/com/echo/ai/AiChatService(Impl).java`：LLM 流式调用、幂等、兜底、WS 推送 CHUNK/DONE/ERROR；**多轮记忆（DB 上下文窗口）+ `ai.enabled` 总开关（system_config）**。
- `echo-backend/src/main/java/com/echo/ai/BotUserService.java`：按 username 解析并缓存 bot 身份。
- `echo-backend/src/main/java/com/echo/service/ConversationService(Impl).java`：会话更新（收发双方双向行），WS 与 AI 共用；`clearConversation` 重置本方向会话行。
- `echo-backend/src/main/java/com/echo/controller/AiController.java`：`GET /ai/bot-info`（bot 身份 + enabled 开关状态）。
- `echo-backend/src/main/java/com/echo/controller/ChatController.java`：新增 `DELETE /chat/conversations/{friendId}`（清空会话）。
- `echo-backend/src/main/java/com/echo/service/ChatServiceImpl.java`：`deleteMessages` 已实现软删；`getMessages` 补软删过滤。
- `echo-backend/src/main/java/com/echo/service/PresenceService.java`：Redis 在线状态账本（ZSET presence:online）。
- `echo-backend/src/main/java/com/echo/websocket/ChatEndpoint.java`：心跳追踪 + `@Scheduled` 清扫 + **多标签页会话路由（Map<Long,Set<Session>>）** + read_at 写入/回执 + **投递改 Redis pub/sub 发布 + 隐私门控（在线/已读）**。
- `echo-backend/src/main/java/com/echo/websocket/WsEventPublisher.java` / `WsEventSubscriber.java` / `RedisPubSubConfig.java`：跨实例 WS 帧发布/订阅。
- `echo-backend/src/main/resources/db/migration/V7__message_read_at.sql`：message.read_at；`V9__user_privacy_settings.sql`：user.show_online_status/show_read_receipts。
- `echo-backend/src/main/java/com/echo/service/GroupService.java`：群聊建群/成员/消息/未读 + `broadcastToMembers`。
- `echo-backend/src/main/java/com/echo/service/NotificationService.java`：通知插行 + WS 推送 + `notifyAll`。
- `echo-backend/src/main/java/com/echo/controller/GroupController.java` / `NotificationController.java` / `AdminNotificationController.java`：/groups、/notifications、/admin/notifications/broadcast。
- `echo-backend/src/main/java/com/echo/service/SearchService.java` / `controller/SearchController.java`：全文搜索（用户 + 1:1 消息 + 群消息）。
- `echo-backend/src/main/resources/db/migration/V10__social_enhancement.sql`：chat_group/member/message + notification。
- `echo-backend/src/main/java/com/echo/service/KbService.java`：知识库分块/嵌入/余弦检索（BGE 中文本地模型）；`submitUpload`/`extractText`（txt/md/pdf/docx）/`indexText`/`markFailed`。
- `echo-backend/src/main/java/com/echo/service/KbIndexWorker.java`：`@Async` 异步索引 + `recoverDocument` 断点恢复（PENDING→INDEXING→READY/FAILED）。
- `echo-backend/src/main/java/com/echo/service/KbIndexRecovery.java`：启动时恢复中断索引（ApplicationReadyEvent）。
- `echo-backend/src/main/resources/db/migration/V8__knowledge_base_category.sql`：kb_document.category。
- `echo-backend/src/main/java/com/echo/controller/KbController.java`：`/admin/kb` 上传/列表/删除/stats。
- `echo-backend/src/main/resources/db/migration/V5__knowledge_base.sql`：kb_document + kb_chunk；`V6__knowledge_base_extensions.sql`：kb_document.error_message。
- `echo-backend/pom.xml`：`langchain4j-open-ai-spring-boot-starter`（1.18.1-beta28，直接钉版本）。

前端：

- `echo-frontend/src/util/tusUpload.js`：Uppy/Tus 上传、完成确认。
- `echo-frontend/src/views/ChatView.vue`：上传进度、处理中文案、本地恢复、自动发送与 ACK 清理；AI 合成会话 + `AI_STREAM_*` 流式气泡；**bot 回复 Markdown 渲染（marked + dompurify）+ AI 会话清空按钮**；会话列表在线点、重连补拉、自气泡已读/已发送。
- `echo-frontend/src/views/FriendView.vue`：好友在线点 + WS 实时更新。
- `echo-frontend/src/util/webSocket.js`：服务端存活看门狗、持久重连、auth-failed。
- `echo-frontend/src/util/request.js`：导出 `getValidToken`/`clearAuthStorage`。
- `echo-frontend/src/views/admin/SystemConfigView.vue`：新增「AI 助手」开关（`ai.enabled`）。
- `echo-frontend/src/views/ChatView.vue`：会话列表合并好友+AI+群、`currentChatType` 分派、群成员/建群对话框（群聊已并入消息页，独立 GroupsView 已删）。
- `echo-frontend/src/views/HomeView.vue`：顶栏通知铃铛（badge + dropdown）+ 搜索框下拉。
- `echo-frontend/src/views/admin/KnowledgeBaseView.vue`：知识库管理页（状态/上传/列表/删除）。
- `echo-frontend/package.json`：新增 `marked`、`dompurify`。
- `echo-frontend/src/util/request.js`、`echo-frontend/vite.config.js`：`/files` 请求及开发代理。
- `echo-frontend/scripts/dev.mjs`：`npm run dev` 同时管理 Vite 与本地 tusd。

脚本与说明：

- `scripts/start-tusd.ps1`：下载（首次）并启动独立 tusd。
- `echo-backend/TUSD_LOCAL_DEVELOPMENT.md`：本地运行和安全边界说明。
- `开发日志.md`：持续开发记录；新记录应添加在文档顶部。

## 本地启动与配置

- Spring Boot：IDEA 启动，端口 `8088`，工作目录通常是 `echo-backend`。
  - **注意**：本阶段新增了 langchain4j 依赖。IDEA 运行配置的 classpath 在启动时固定，新增依赖后 **DevTools 热重启无法加载新 jar**——必须 IDEA **Stop 后重新 Run**（会重建含 langchain4j 的 classpath）。当前 8088 由 `mvn spring-boot:run` 实例接管（曾结束一个 classpath 过期导致启动失败的旧 IDEA 实例）。
- 前端：在 `echo-frontend` 运行 `npm run dev`；它会启动或复用端口 `1080` 的 tusd，再启动 Vite。
- tusd：独立 Go 服务，不内置在 Spring Boot 中。端口 `1080`，路径 `/files/`。
- MySQL：`echo_chat`；Redis 同时是后端本地依赖。
- **Maven 镜像环境**：全局 `D:\Program Files\Maven\...\conf\settings.xml` 的 aliyun 镜像为 HTTP，被 Maven 3.8.1+ 阻止；已新增用户级 `~/.m2/settings.xml` 用 HTTPS 镜像（`https://maven.aliyun.com/repository/central/`）覆盖。新依赖下载依赖此文件，勿删。
- **AI 配置**：DeepSeek API key 在 `application-local.yml`（gitignored）。`app.ai.bot-username` 默认 `ai_assistant`；前后端 bot id 均动态解析（`/ai/bot-info`），无需手工同步。

文件上限的唯一默认来源为 `echo-backend/src/main/resources/application.yml`：

```yaml
app:
  file:
    max-size: ${APP_FILE_MAX_SIZE:21474836480}
```

即默认 20 GiB。改动后需重启 Spring Boot 和 tusd；`npm run dev` 会读取该默认值。`APP_FILE_MAX_SIZE` 是临时环境变量覆盖项。

重要目录约定：

- tusd 临时文件：项目根目录 `upload/tusd`
- 后端永久文件：项目根目录 `upload/files`
- **重要修正**：IDEA 的 Spring Boot 运行配置未指定工作目录，模块根为项目根，后端实际以**项目根**为 `user.dir`（此前交接文档误认为 `echo-backend`）。因此 `application.yml` 中 `app.file.tusd-upload-dir` 与 `app.file.storage-dir` 统一使用 `upload/tusd`、`upload/files`（与 tusd 的绝对路径及前端 dev.mjs 一致）。**不要改回 `../upload/...`**，否则会从项目根解析到项目外、导致下载 404。
- 旧公开静态目录 `/upload/**` 已关闭（阶段 C）：`upload/` 根目录不再被 Web 服务托管，仅保留 `tusd/`（临时分片）、`files/`（永久文件）、`backup-20260806/`（迁移备份与孤儿文件）三个子目录。文件访问一律走受控 `/files/{id}/content?access=...`。

## 本次修复与迁移（2026-08-06 晚）

- 修复存储路径解析 bug：后端实际 cwd=项目根，原配置 `../upload/tusd`、`../upload/files` 解析到项目外，导致受控下载全 404。已改为 `upload/tusd`、`upload/files`（详见「重要目录约定」）。
- Flyway V1/V2/V3 全部成功应用；`message.client_message_id` 唯一索引存在；无卡在 `PROCESSING`/`FAILED` 的任务。
- 小文件受控迁移 A+B 已完成：新 `/chat/file/upload` 返回受控 URL；存量 35 处 `/upload/**` 引用已改写为 `/files/**`，物理文件从 `upload/` 搬入 `upload/files/`。迁移前备份在 `upload/backup-20260806/pre-migration.sql`。
- 阶段 C 已完成：删除 `WebMvcConfig`（`/upload/**` 静态映射）与 SecurityConfig 的 `/upload/**` permitAll，移除 `application.yml` 的 `app.upload-dir`；20 个孤儿文件迁入 `upload/backup-20260806/orphan-files/`；删除依赖旧目录的测试 `uploadDirFileIsPubliclyAccessible`。已验证 `/upload/**` 已认证 404 / 未认证 403，`/files/**` 受控下载 200。
- **AI Gateway v1 已完成**：langchain4j 1.18.1-beta28 + DeepSeek 流式聊天。Flyway V4 加 BOT 角色与 `ai_assistant` 用户（不写死 id，避免 AUTO_INCREMENT 顶爆）；bot 按 username 解析；WS 流式推 CHUNK/DONE/ERROR；bot 回复幂等；并发写锁修复 `TEXT_FULL_WRITING`；前端合成「AI 助手」会话。
- **AI Gateway v2 已完成**：多轮记忆（DB 上下文窗口，`app.ai.context-window-messages`）；bot 回复 Markdown 渲染（marked + dompurify，仅 bot 气泡）；AI 会话清空（`ChatServiceImpl.deleteMessages` 软删实现 + `DELETE /chat/conversations/{friendId}` + `getMessages` 软删过滤 + `ConversationService.clearConversation`）；管理端 AI 开关（`system_config.ai.enabled`，`/ai/bot-info` 返回 enabled，管理端「系统配置」页 el-switch）。
- **消息可靠性 v1 已完成**：Redis 在线状态（PresenceService + 好友/会话列表 online + 监控 onlineUsers）；服务端心跳超时淘汰（@Scheduled 清扫 + 多会话存在判定）；前端已读/已发送展示、WS 看门狗与持久重连、重连补拉、好友/会话在线点。
- **AI Gateway v3（知识库 RAG）已完成**：本地中文嵌入 bge-small-zh（无 API key）+ MySQL 持久化向量 + 余弦检索注入 AI 提示词；管理端 `/admin/kb` 上传/列表/删除 + `KnowledgeBaseView.vue`。
- **知识库增强 v1 已完成**：PDF/Word 解析（pdfbox 3.0.8 + poi-ooxml 5.5.1）+ 异步索引（KbIndexWorker，PENDING→INDEXING→READY/FAILED + error_message）；前端状态流转与失败提示 + 轮询。
- **修复 AI 固定文案回复不显示**：管理员关闭 AI 等「只推 DONE 不推 CHUNK」的场景，前端 `handleAiDone` 原先因找不到流式气泡而丢弃回复（需刷新才见）。已改为无气泡时直接用 DONE 的 message 创建 bot 气泡。`ChatView.vue`。
- **消息可靠性 v2 已完成**：多标签页会话路由（onlineUsers 改 Map<Long,Set<Session>>，投递扇出到全部存活会话）+ 已读时间 read_at（V7 列 + 回执携带 + 前端「已读 HH:mm」）。
- **知识库增强 v2 已完成**：断点恢复（KbIndexRecovery + recoverDocument，重启不丢在途索引）+ 文档分类（V8 category）+ 搜索/预览（keyword 过滤 + 详情端点 + markFailed 清残留分片）；修复 indexText 未持久化 content 的潜藏 bug。
- **收尾清理已完成**：3 个孤儿 ISO 置 EXPIRED（1 个真实文件保留）；request.js `resolveUploadUrl` 移除 `/upload/` 死代码并修复 URL 解析丢 query 的隐患。
- **消息可靠性 v3 已完成**：隐私开关（在线/已读默认隐藏，V9 user 列 + 列表/广播/回执门控 + SettingsView）+ 跨节点实时推送（WsEventPublisher/Subscriber，投递改 Redis pub/sub 发布）。
- **社交增强 v1 已完成**：群聊（V10 表 + GroupService + WS GROUP_MESSAGE 扇出 + /groups REST + GroupsView）+ 系统通知（NotificationService + /notifications + admin 广播 + 铃铛 UI + 好友请求钩子）。
- **社交增强 v2（全文搜索）已完成**：SearchService/SearchController（用户 + 1:1 消息 + 群消息）+ HomeView 搜索框下拉 + FriendView 深链。
- **群聊并入「消息」页已完成**：ChatView 合并好友+AI+群（`currentChatType` 分派），移除独立 GroupsView；群消息复用好友气泡 UI（发送者名 + 群标签区分）。
- **真机验收 7 项问题已修复**：铃铛跳转刷新请求列表；好友备注语义（验证信息不再设为备注 + 前端可编辑备注）；AI「正在思考」提示；管理端系统广播入口；文件上传进度条提示（含 <10M 小文件）；在线点移头像右下角；群成员可互加好友。
- **大文件发送中刷新卡「校验」已修复**：tus 创建 intent 即落 localStorage（onIntent），刷新后 `restorePendingFileMessages` 重建「校验中」气泡并恢复——UPLOADED 补发 complete、UPLOADING 提示重新上传。
- **消息全部渲染成对方发的已修复**：admin 门户登录只写 token 不写 userId → 主应用 currentUserId 错位。已让 AdminLoginView 同步 userId/username + ChatView 挂载自愈（profile 与 localStorage 不一致则修正重载）。

## 已验证与未验证

已通过：

- `cd echo-backend; mvn test`：2/2 通过（contextLoads + uploadSmallFileCreatesControlledAsset）。
- `cd echo-frontend; npm run build`：通过，1634 modules transformed。
- `git diff --check`：无空白错误。
- 受控下载恢复正常：43MB wav 全量 `200`、711MB zip Range `206`、旧 `/upload/**` 静态资源仍 `200`。
- 端到端真实上传（12MB）：意图 → tusd `201` 建上传 → 两段 `PATCH` + `HEAD` 续传 → `complete` → `READY`（约 2s）→ 下载 SHA-256 完全一致。测试数据已清理。
- 小文件受控迁移：35 引用改写、35 物理文件迁移，`/upload/**` 引用归零；迁移后头像/图片/文件下载均 `200`；新 `/chat/file/upload` 返回 `/files/` URL 且可下载。
- 阶段 C：`/upload/**` 静态映射与 permitAll 已移除（已认证 GET 返回 404、未认证返回 403），`/files/**` 受控下载 200；20 个孤儿文件已迁入 `upload/backup-20260806/orphan-files/`；`mvn test` 2/2 通过。
- AI Gateway：`mvn test` 2/2、`npm run build` 通过；Flyway V4 生效（bot `ai_assistant` role=BOT）；`GET /ai/bot-info` 返回 bot 身份；Node WS 端到端（真实 DeepSeek）ACK→流式 CHUNK→DONE 持久化；并发两用户各自独立完成（写锁修复后无 `TEXT_FULL_WRITING`）；同 clientMessageId 重发幂等不重复生成；非文字给 bot 回固定文案；`/chat/conversations` 过滤 bot 会话。
- AI Gateway v2：多轮记忆（pyf 让 bot 记住「小明」→ 追问答出「小明」）；清空会话（17 条历史 → `DELETE /chat/conversations/10` → 历史为空 → 再问「我叫什么名字」bot 已失忆）；管理端开关（`ai.enabled=false` → `bot-info.enabled=false` + 固定「已关闭」回复；恢复 true 正常）；`git diff --check` 干净。
- 消息可靠性 v1：WS 连接 → `USER_ONLINE` 广播 + `/friends/list` `online:true` + monitor `onlineUsers` 变化；同用户两连接关其一仍在线、关最后一个才离线（多会话修复）；已读回执 A→B 全链路（ACK→NEW_MESSAGE→MESSAGE_READ→`MESSAGE_READ_RECEIPT {messageIds:[408],readerId:2}`）；心跳淘汰（8091 隔离实例 evict=15s → 静默客户端 15.8s 被 code 1008 关闭）。
- 消息可靠性 v2：两个 pyf 会话 + linyxhe 发消息 → **两个 pyf 都收到 NEW_MESSAGE id=447**（多标签页）；linyxhe 收到 `MESSAGE_READ_RECEIPT {readAt:"2026-08-07T15:53:41",messageIds:[447],readerId:1}`；DB `message 447 read_at` 已写入；隔离单会话存在测试 on→2/off→1。
- 消息可靠性 v3：隐私默认关时好友列表 `online:false`、读消息对方收不到回执；开启后恢复；广播隐私门控（隔离重跑 PASS）；pyf 连 8095 / linyxhe 连 8096 **双向互发消息均跨实例实时收到**；单实例 AI 流式经 pub/sub 正常。
- 社交增强 v1：pyf 建群（加 linyxhe）→ 双方可见、发 GROUP_MESSAGE → linyxhe 实时收到、未读>0 → read 清零、历史命中；pyf→admin 好友请求 → admin 实时 NOTIFICATION + unread+1 → 同意 → pyf 收到 ACCEPT；admin 全体广播 → 收到 SYSTEM 通知。
- 社交增强 v2（搜索）：`keyword=lin` 命中 linyxhe 且 isFriend；SEARCHX1 命中 1:1 消息；SEARCHX2 命中群消息；空关键字三组空。
- AI Gateway v3（RAG）：上传含「默认端口 8088」文档 → 索引 1 分片 → 问「默认后端端口」答 **8088**；无关问题正常（1+1=2）；删除文档 stats 归零；清空 AI 会话历史后再问 → bot 不知道（瞎猜 8080，证明 RAG 与多轮记忆各自独立）。
- 知识库增强 v1：上传 PDF（pdfbox 生成，事实 KBPDF-7788）与 DOCX（PowerShell zip 生成，事实 KBDOCX-9900）→ 立即返回「已提交索引」→ 2s 内 READY → 问 bot 分别答对 **KBPDF-7788/蓝** 与 **KBDOCX-9900/上海**；损坏 PDF → FAILED + error_message「End-of-File...」；空文件/不支持扩展名提交前拒绝。
- 知识库增强 v2：断点恢复（上传含 RECOVER-999 文档 → SQL 模拟崩溃置 INDEXING+删分片 → 重启 → 启动恢复自动重建 READY → 问 bot 答对 **RECOVER-999**）；分类上传/编辑/按分类搜索；按文件名搜索命中、不匹配为 0；预览端点返回 content（修复前 NULL）。

尚未完成：

- 真实 9GB 文件的断网/刷新恢复、`READY` 自动发消息的真机验证。
- 断网、刷新、恢复、取消、过期清理的完整真机回归。
- ~~DB 中 3 个孤儿 ISO 记录~~：已清理（1fab6054/69f1ccce/bdde515b 置 EXPIRED；ca8157fc 为真实 4.7GB 文件且有消息引用，保留）。
- ~~前端 `request.js` 的 `/upload/` 透传死代码~~：已清理（resolveUploadUrl 仅保留 /files/，并修复 query 丢失隐患）。
- AI v2 遗留：多轮上下文按消息条数计（≈10 轮，未做 token 级窗口）；普通好友会话清空按钮未做（后端已通用支持）；AI 会话物理删除未做；清空瞬间在途流无服务端取消（前端忽略残余事件）。
- AI 助手浏览器真机验收（Markdown 渲染视觉效果、清空按钮、管理端开关、多轮记忆）未执行；测试产生的 bot 对话留在 DB（pyf/linyxhe 的 AI 历史，属正常数据）。
- 消息可靠性 v4 未做：持久化投递队列（outbox，保证 Redis 挂时不丢实时帧）、订阅并发消费/分片（当前单线程保顺序）；送达回执 delivered_at 因隐私考虑**不做**（已读即最高粒度）。
- 社交增强 v3 已完成群文件、群邀请流、语音与内容审核 v1；群内逐条已读回执已撤回；搜索为 LIKE 全表扫描（数据量大后可换全文索引/ES）。
- 群聊并入「消息」页后**浏览器真机回归未执行**（好友/群/AI/文件/搜索链路）。
- 知识库局限（增强 v2 后剩余）：扫描件 PDF 需 OCR（无文本层）；`.doc` 旧格式不解析；内存缓存余弦检索（数千分片后可换 pgvector/ES）；分类仅管理端组织用（bot 检索不按分类过滤）；无文档权限。**管理端上传/删除 + AI 知识库问答已浏览器真机验收通过（用户，2026-08-07）；PDF/docx、断点恢复、分类/搜索/预览待浏览器验收**。
- 生产环境病毒扫描、对象存储、反向代理与 HTTPS；当前项目仅本地开发，未部署阿里云。

## 已知注意事项

- `PROCESSING` 会进行完整 SHA-256，9GB 文件在机械硬盘或受 I/O 限制环境中可能需要较长时间；这是后台任务，不应阻塞浏览器请求。
- 后台校验运行中不要修改 `application.yml` 或重新编译后端，否则 IDEA/Devtools 的重启可能中断该后台线程；重启后恢复机制会重试。
- 工作树中有大量未提交改动，其中部分来自此前的移动端优化与阶段 2 实现。不要执行 `git reset --hard`、`git checkout --` 或批量清理 `upload/`、`target/`、`node_modules/`。
- `application-local.yml` 含本地私密配置（DB、邮箱、**DeepSeek API key**、jwt secret），不应提交或在交接文档中复制凭据。
- 当前 8088 由用户 IDEA 实例托管。**消息可靠性 v3 后需 Stop→Run 完整重启**（新类 + V9 DDL + pub/sub 订阅者；无新 jar，但类变更 DevTools 热重启可能不稳）。注意：**WS 投递现走 Redis pub/sub 热路径**——Redis 必须运行；Redis 挂时投递降级为本实例直发（DB 真源兜底）。
- 若用 `mvn spring-boot:run` 临时托管，启动带 `-Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=false"` 关闭 DevTools 自动重启（否则 `mvn compile` 触发的 DevTools 重启会杀掉 mvn-run JVM，已踩坑两次），且**不要在 mvn 实例运行时 `mvn compile`**。
- langchain4j 的 Spring Boot starter 只有 `-betaXX` 版号（1.18.1-beta28），核心模块为稳定版；BOM 版号与 starter 不对齐，故 pom 直接钉版本。

## 建议 Claude 的下一轮操作

1. 真机执行大文件断网/刷新/取消/过期回归（9GB 级，需浏览器）；AI 助手 v3 的**知识库问答已真机验收通过**，剩余 AI v2 UI 项（Markdown、清空按钮、AI 开关、多轮记忆）与消息可靠性 v1（在线点、已读/已发送、断线重连补拉）待验收，通过后更新 `开发日志.md` 顶部记录。
2. 知识库增强后续：扫描件 OCR、向量库升级（pgvector/ES/milvus，数千分片以上）、按分类过滤检索/文档权限、云端嵌入模型（OpenAI/通义）。
3. 消息可靠性 v4（可选）：持久化投递队列（outbox）保证 Redis 挂时不丢实时帧、订阅并发消费/分片。送达回执不做（隐私）。
4. ~~收尾清理~~：孤儿 ISO 已置 EXPIRED、request.js `/upload/` 死代码已移除（2026-08-07）。
5. 社交增强 v3：群文件/邀请流/语音/内容审核 v1 已完成；搜索可升级全文索引/ES。
6. 浏览器真机回归：群聊并入「消息」页（好友/群/AI/文件/搜索），含手机热点访问。

> 迁移脚本用法（幂等，独立实例不影响 8088）：`mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090 --app.migrate-legacy-files=true"`，日志出现 "Legacy migration complete" 后停止。
