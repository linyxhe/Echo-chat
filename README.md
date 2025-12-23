# 聊天系统设计与开发文档

## 一、系统概述

### 1.1 项目简介
基于SpringBoot + WebSocket + MySQL + Vue3的实时聊天系统，支持文本、文件传输、好友管理、用户动态等功能。

### 1.2 技术栈
- **后端**: SpringBoot 3.x + WebSocket + MyBatis-Plus + Redis
- **前端**: Vue3 + JavaScript + Element Plus + Axios
- **数据库**: MySQL 8.0
- **消息队列**: Redis (可选，用于分布式扩展)
- **文件存储**: 本地存储
- **邮件服务**: JavaMail + SMTP

## 二、系统架构设计

### 2.1 整体架构
```
┌─────────────────────────────────────────┐
│               前端 Vue3                  │
├─────────────────────────────────────────┤
│         HTTP/WebSocket 网关层            │
├─────────────────────────────────────────┤
│             业务逻辑层                   │
│  ┌────────┬─────────┬─────────┐        │
│  │用户服务│聊天服务 │好友服务 │文件服务│
├─────────────────────────────────────────┤
│             数据访问层                   │
│  ┌────────┬─────────┬─────────┐        │
│  │ MySQL  │  Redis  │  文件存储│       │
└─────────────────────────────────────────┘
```

### 2.2 通信流程
1. HTTP用于常规请求（注册、登录、好友管理等）
2. WebSocket用于实时消息推送
3. 文件上传使用HTTP分片上传
4. Redis用于会话管理和缓存

## 三、数据库设计

### 3.1 用户表 (user)
```sql
CREATE TABLE `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
  `nickname` VARCHAR(50) NOT NULL COMMENT '昵称',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '加密密码',
  `email` VARCHAR(100) UNIQUE NOT NULL COMMENT '邮箱',
  `email_verified` BOOLEAN DEFAULT FALSE COMMENT '邮箱验证状态',
  `avatar_url` VARCHAR(500) COMMENT '头像URL',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-正常 2-封禁',
  `role` ENUM('USER', 'ADMIN') DEFAULT 'USER' COMMENT '角色',
  `last_login_at` DATETIME COMMENT '最后登录时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 3.2 好友关系表 (friendship)
```sql
CREATE TABLE `friendship` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `friend_id` BIGINT NOT NULL COMMENT '好友ID',
  `remark` VARCHAR(50) COMMENT '好友备注',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-删除 1-正常',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
  FOREIGN KEY (`user_id`) REFERENCES `user`(`id`),
  FOREIGN KEY (`friend_id`) REFERENCES `user`(`id`)
);
```

### 3.3 消息表 (message)
```sql
CREATE TABLE `message` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
  `receiver_id` BIGINT NOT NULL COMMENT '接收者ID',
  `content` TEXT COMMENT '消息内容',
  `message_type` ENUM('TEXT', 'IMAGE', 'FILE', 'SYSTEM') DEFAULT 'TEXT',
  `file_url` VARCHAR(500) COMMENT '文件URL',
  `file_name` VARCHAR(255) COMMENT '文件名',
  `file_size` BIGINT COMMENT '文件大小',
  `is_read` BOOLEAN DEFAULT FALSE COMMENT '是否已读',
  `deleted_by_sender` BOOLEAN DEFAULT FALSE,
  `deleted_by_receiver` BOOLEAN DEFAULT FALSE,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_conversation` (`sender_id`, `receiver_id`, `created_at`),
  INDEX `idx_unread` (`receiver_id`, `is_read`)
);
```

### 3.4 聊天会话表 (conversation)
```sql
CREATE TABLE `conversation` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user1_id` BIGINT NOT NULL,
  `user2_id` BIGINT NOT NULL,
  `last_message_id` BIGINT COMMENT '最后一条消息ID',
  `unread_count` INT DEFAULT 0 COMMENT '未读消息数',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_user_pair` (`user1_id`, `user2_id`)
);
```

### 3.5 用户动态表 (post)
```sql
CREATE TABLE `post` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `content` TEXT NOT NULL,
  `media_urls` JSON COMMENT '媒体文件URL数组',
  `visibility` ENUM('PUBLIC', 'FRIENDS', 'PRIVATE') DEFAULT 'PUBLIC',
  `like_count` INT DEFAULT 0,
  `comment_count` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-删除 1-正常',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_user_timeline` (`user_id`, `created_at`)
);
```

### 3.6 举报记录表 (report)
```sql
CREATE TABLE `report` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `reporter_id` BIGINT NOT NULL COMMENT '举报人ID',
  `reported_user_id` BIGINT NOT NULL COMMENT '被举报用户ID',
  `report_type` ENUM('HARASSMENT', 'SPAM', 'FRAUD', 'OTHER') COMMENT '举报类型',
  `description` TEXT COMMENT '举报描述',
  `evidence` JSON COMMENT '证据（消息ID等）',
  `status` ENUM('PENDING', 'PROCESSED', 'DISMISSED') DEFAULT 'PENDING',
  `admin_id` BIGINT COMMENT '处理管理员ID',
  `processed_at` DATETIME,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 3.7 系统配置表 (system_config)
```sql
CREATE TABLE `system_config` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `config_key` VARCHAR(100) UNIQUE NOT NULL,
  `config_value` TEXT,
  `description` VARCHAR(255),
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 四、功能模块详细设计

### 4.1 用户模块

#### 4.1.1 认证模块
- **注册功能**
  - 用户名、密码、邮箱注册
  - 邮箱验证码验证
  - 密码加密存储（md5）
- **登录功能**
  - JWT Token认证
  - 登录状态维护（Redis存储Session）
  - 登录日志记录
- **密码管理**
  - 密码重置（通过邮箱）
  - 密码修改
- **邮箱验证**
  - SMTP邮件发送
  - 验证码有效期（5分钟）
  - 验证码防刷机制

#### 4.1.2 个人资料管理
- 基本信息修改（昵称、头像等）
- 在线状态设置
- 隐私设置（谁可以看到我的动态）

### 4.2 聊天模块

#### 4.2.1 实时消息
- **WebSocket连接管理**
  - 连接建立/断开处理
  - 心跳检测（30秒）
  - 断线重连机制
- **消息收发**
  - 单聊消息实时推送
  - 消息确认机制（ACK）
  - 消息状态更新（发送中、已发送、已读）
- **消息存储**
  - 消息持久化到MySQL
  - 最近消息缓存（Redis）
  - 消息分页查询

#### 4.2.2 文件传输
- **小文件传输（<10M）**
  - Base64编码传输（WebSocket）
  - 进度显示
- **大文件传输（>=10M）**
  - 分片上传（HTTP）
  - 断点续传
  - 文件校验（MD5）
- **文件管理**
  - 文件类型限制
  - 病毒扫描接口
  - 文件清理任务（定期删除过期文件）

#### 4.2.3 会话管理
- 会话列表（最近联系人）
- 会话置顶/取消
- 会话免打扰
- 清空聊天记录

### 4.3 好友模块

#### 4.3.1 好友发现
- ID搜索
- 昵称模糊搜索
- 可能认识的人推荐

#### 4.3.2 好友管理
- 发送/接收好友请求
- 好友请求列表
- 同意/拒绝好友请求
- 好友备注设置
- 好友分组/标签
- 删除好友

#### 4.3.3 黑名单
- 添加/移除黑名单
- 黑名单用户消息拦截

### 4.4 用户动态模块

#### 4.4.1 动态发布
- 图文动态发布
- 可见范围设置（公开/好友/私密）
- @好友功能
- 话题标签

#### 4.4.2 动态展示
- 好友动态时间线
- 热门动态推荐
- 动态点赞/评论
- 动态分享

#### 4.4.3 互动功能
- 点赞/取消点赞
- 评论/回复
- 动态删除

### 4.5 管理员模块

#### 4.5.1 用户管理
- 用户列表查询（分页+筛选）
- 用户状态管理（启用/禁用）
- 密码重置
- 用户信息查看

#### 4.5.2 内容审核
- 举报处理流程
- 敏感词过滤
- 违规内容删除
- 封禁操作记录

#### 4.5.3 系统监控
- 在线用户统计
- 消息数量统计
- 系统性能监控
- 操作日志审计

#### 4.5.4 系统配置
- 系统参数配置
- 敏感词库管理
- 公告管理

### 4.6 其他补充功能

#### 4.6.1 通知系统
- 系统通知
- 好友请求通知
- 消息提醒（桌面通知）

#### 4.6.2 搜索功能
- 全局消息搜索
- 文件搜索
- 联系人搜索

#### 4.6.3 数据统计
- 活跃用户统计
- 消息量统计
- 用户增长统计

## 五、关键组件设计

### 5.1 WebSocket服务端
```java
// 核心组件
1. WebSocketConfig: WebSocket配置类
2. ChatWebSocketHandler: 消息处理器
3. WebSocketInterceptor: 连接拦截器（验证Token）
4. WebSocketSessionManager: 会话管理
5. MessageDispatcher: 消息分发器
```

### 5.2 文件服务
```java
// 文件上传流程
1. 前端计算文件hash，查询是否已存在
2. 服务端返回上传token和分片信息
3. 分片上传，服务端校验
4. 合并分片，生成文件访问URL
5. 文件信息入库
```

### 5.3 消息推送机制
1. **在线用户**: WebSocket实时推送
2. **离线用户**: Redis存储离线消息，登录后拉取
3. **群组消息**: Redis Pub/Sub广播（预留扩展）

## 六、非功能需求

### 6.1 性能要求
- 消息延迟 < 200ms
- 同时在线用户支持 10000+
- 单机QPS > 1000

### 6.2 安全性
- 通信加密（WSS）
- SQL注入防护
- XSS攻击防护
- 文件上传安全校验
- 敏感信息脱敏

### 6.3 可靠性
- 消息可靠性（至少一次送达）
- 数据备份机制
- 服务降级方案

### 6.4 可扩展性
- 微服务化预留接口
- 水平扩展支持
- 插件化架构设计


## 八、数据库索引优化建议

1. **消息表**:
   - (sender_id, receiver_id, created_at) 复合索引
   - (receiver_id, is_read) 未读消息查询

2. **用户表**:
   - username唯一索引
   - email唯一索引
   - (status, created_at) 用户统计

3. **好友关系表**:
   - (user_id, friend_id) 唯一索引
   - (user_id, status) 好友列表查询

## 九、注意事项

1. **消息幂等性**: 通过消息ID保证消息不重复处理
2. **文件清理**: 定期清理临时文件和过期文件
3. **内存管理**: WebSocket连接数监控，防止内存泄漏
4. **日志记录**: 关键操作日志记录，便于审计和排查问题
5. **备份策略**: 定期备份数据库和用户文件
