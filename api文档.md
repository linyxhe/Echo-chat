# 聊天系统接口文档

## 文档信息
- **项目名称**: 聊天系统
- **版本**: V1.0
- **基础URL**: `http://localhost:8088/api`
- **认证方式**: Token (JWT)

---

## 一、认证模块

### 1.1 用户注册
- **接口**: `POST /auth/register`
- **描述**: 用户注册
- **请求头**:
  ```json
  {
    "Content-Type": "application/json"
  }
  ```
- **请求体**:
  ```json
  {
    "username": "string, 必填, 用户名",
    "password": "string, 必填, 密码",
    "nickname": "string, 必填, 昵称",
    "email": "string, 必填, 邮箱",
    "captcha": "string, 必填, 邮箱验证码"
  }
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "注册成功",
    "data": {
      "userId": 1,
      "username": "testuser",
      "nickname": "测试用户",
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
  }
  ```

### 1.2 发送邮箱验证码
- **接口**: `POST /auth/captcha/send`
- **描述**: 发送邮箱验证码
- **请求体**:
  ```json
  {
    "email": "string, 必填, 邮箱地址",
    "type": "string, 必填, 验证码类型(REGISTER|RESET_PASSWORD|CHANGE_EMAIL)"
  }
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "验证码发送成功",
    "data": null
  }
  ```

### 1.3 用户登录
- **接口**: `POST /auth/login`
- **描述**: 用户登录
- **请求体**:
  ```json
  {
    "username": "string, 必填, 用户名或邮箱",
    "password": "string, 必填, 密码"
  }
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "登录成功",
    "data": {
      "userId": 1,
      "username": "testuser",
      "nickname": "测试用户",
      "avatar": "http://example.com/avatar.jpg",
      "email": "test@example.com",
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "expireTime": 1745164800000
    }
  }
  ```

### 1.4 退出登录
- **接口**: `POST /auth/logout`
- **描述**: 退出登录
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "退出成功",
    "data": null
  }
  ```

### 1.5 刷新Token
- **接口**: `POST /auth/refresh-token`
- **描述**: 刷新访问令牌
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **请求体**:
  ```json
  {
    "refreshToken": "string, 必填, 刷新令牌"
  }
  ```

---

## 二、用户模块

### 2.1 获取当前用户信息
- **接口**: `GET /user/profile`
- **描述**: 获取当前登录用户信息
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "id": 1,
      "username": "testuser",
      "nickname": "测试用户",
      "email": "test@example.com",
      "avatar": "http://example.com/avatar.jpg",
      "status": 1,
      "createdAt": "2024-01-01 10:00:00",
      "lastLoginAt": "2024-01-15 15:30:00"
    }
  }
  ```

### 2.2 更新用户信息
- **接口**: `PUT /user/profile`
- **描述**: 更新用户个人信息
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **请求体**:
  ```json
  {
    "nickname": "string, 可选, 昵称",
    "avatar": "string, 可选, 头像URL",
    "signature": "string, 可选, 个性签名"
  }
  ```

### 2.3 修改密码
- **接口**: `PUT /user/password`
- **描述**: 修改密码
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **请求体**:
  ```json
  {
    "oldPassword": "string, 必填, 旧密码",
    "newPassword": "string, 必填, 新密码"
  }
  ```

### 2.4 重置密码
- **接口**: `POST /user/password/reset`
- **描述**: 通过邮箱重置密码
- **请求体**:
  ```json
  {
    "email": "string, 必填, 邮箱",
    "captcha": "string, 必填, 验证码",
    "newPassword": "string, 必填, 新密码"
  }
  ```

### 2.5 上传头像
- **接口**: `POST /user/avatar/upload`
- **描述**: 上传用户头像
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}",
    "Content-Type": "multipart/form-data"
  }
  ```
- **请求体** (FormData):
  ```
  file: File (图片文件, 最大2M)
  ```

---

## 三、好友模块

### 3.1 搜索用户
- **接口**: `GET /friends/search`
- **描述**: 搜索用户（ID或昵称）
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **查询参数**:
  ```
  keyword: string, 必填, 搜索关键词
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认10
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "list": [
        {
          "id": 2,
          "username": "user2",
          "nickname": "用户二",
          "avatar": "http://example.com/avatar2.jpg",
          "isFriend": false,
          "friendStatus": "NONE"
        }
      ],
      "total": 1,
      "page": 1,
      "size": 10
    }
  }
  ```

### 3.2 发送好友请求
- **接口**: `POST /friends/request`
- **描述**: 发送好友请求
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **请求体**:
  ```json
  {
    "targetUserId": "integer, 必填, 目标用户ID",
    "remark": "string, 可选, 验证信息"
  }
  ```

### 3.3 获取好友请求列表
- **接口**: `GET /friends/requests`
- **描述**: 获取待处理的好友请求
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **查询参数**:
  ```
  status: string, 可选, 状态(PENDING|ACCEPTED|REJECTED)，默认PENDING
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认10
  ```

### 3.4 处理好友请求
- **接口**: `PUT /friends/request/{requestId}/handle`
- **描述**: 处理好友请求
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **路径参数**:
  ```
  requestId: integer, 必填, 好友请求ID
  ```
- **请求体**:
  ```json
  {
    "action": "string, 必填, 处理动作(ACCEPT|REJECT)",
    "remark": "string, 可选, 好友备注"
  }
  ```

### 3.5 获取好友列表
- **接口**: `GET /friends/list`
- **描述**: 获取好友列表
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **查询参数**:
  ```
  keyword: string, 可选, 搜索关键词
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认20
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "list": [
        {
          "id": 2,
          "friendId": 2,
          "nickname": "用户二",
          "remark": "老王",
          "avatar": "http://example.com/avatar2.jpg",
          "online": true,
          "lastActiveTime": "2024-01-15 15:30:00",
          "unreadCount": 3
        }
      ],
      "total": 15,
      "page": 1,
      "size": 20
    }
  }
  ```

### 3.6 更新好友备注
- **接口**: `PUT /friends/{friendId}/remark`
- **描述**: 更新好友备注
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **路径参数**:
  ```
  friendId: integer, 必填, 好友用户ID
  ```
- **请求体**:
  ```json
  {
    "remark": "string, 必填, 新的备注名"
  }
  ```

### 3.7 删除好友
- **接口**: `DELETE /friends/{friendId}`
- **描述**: 删除好友
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **路径参数**:
  ```
  friendId: integer, 必填, 好友用户ID
  ```

---

## 四、聊天模块

### 4.1 WebSocket连接
- **接口**: `ws://localhost:8088/ws`
- **描述**: WebSocket实时通信连接
- **连接参数**:
  ```
  token: JWT令牌（通过URL参数或首帧发送）
  ```
- **消息格式**:
  ```json
  {
    "type": "string, 消息类型",
    "data": {}, // 消息数据
    "timestamp": 1745164800000,
    "messageId": "uuid"
  }
  ```

#### 4.1.1 发送聊天消息
- **WebSocket消息类型**: `CHAT_MESSAGE`
- **发送数据**:
  ```json
  {
    "type": "CHAT_MESSAGE",
    "data": {
      "receiverId": 2,
      "messageType": "TEXT",
      "content": "你好",
      "clientMessageId": "client-uuid-123"
    },
    "timestamp": 1745164800000,
    "messageId": "uuid-123"
  }
  ```
- **服务器响应**:
  ```json
  {
    "type": "MESSAGE_ACK",
    "data": {
      "clientMessageId": "client-uuid-123",
      "serverMessageId": 1001,
      "status": "SENT",
      "timestamp": 1745164800000
    }
  }
  ```

#### 4.1.2 消息已读确认
- **WebSocket消息类型**: `MESSAGE_READ`
- **发送数据**:
  ```json
  {
    "type": "MESSAGE_READ",
    "data": {
      "messageIds": [1001, 1002, 1003],
      "senderId": 2
    }
  }
  ```

### 4.2 获取聊天记录
- **HTTP接口**: `GET /chat/messages`
- **描述**: 获取与指定用户的聊天记录
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **查询参数**:
  ```
  friendId: integer, 必填, 好友用户ID
  beforeTime: string, 可选, 查询此时间之前的消息
  limit: integer, 可选, 返回数量，默认20，最大100
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "messages": [
        {
          "id": 1001,
          "senderId": 1,
          "receiverId": 2,
          "content": "你好",
          "messageType": "TEXT",
          "isRead": true,
          "createdAt": "2024-01-15 10:00:00"
        }
      ],
      "hasMore": true
    }
  }
  ```

### 4.3 获取会话列表
- **接口**: `GET /chat/conversations`
- **描述**: 获取所有聊天会话
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **查询参数**:
  ```
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认20
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "list": [
        {
          "conversationId": "1_2",
          "friendId": 2,
          "friendNickname": "用户二",
          "friendAvatar": "http://example.com/avatar2.jpg",
          "lastMessage": {
            "content": "你好",
            "messageType": "TEXT",
            "senderId": 1,
            "createdAt": "2024-01-15 10:00:00"
          },
          "unreadCount": 3,
          "isTop": false,
          "isMuted": false,
          "updatedAt": "2024-01-15 10:00:00"
        }
      ],
      "total": 10
    }
  }
  ```

### 4.4 文件上传
- **接口**: `POST /chat/file/upload`
- **描述**: 上传聊天文件
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}",
    "Content-Type": "multipart/form-data"
  }
  ```
- **请求体** (FormData):
  ```
  file: File (文件，最大10M)
  receiverId: integer, 必填, 接收者ID
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "fileId": "uuid-123",
      "fileName": "document.pdf",
      "fileSize": 5242880,
      "fileUrl": "http://example.com/files/document.pdf",
      "fileType": "PDF"
    }
  }
  ```

### 4.5 大文件分片上传
- **接口**: `POST /chat/file/chunk/init`
- **描述**: 初始化分片上传
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **请求体**:
  ```json
  {
    "fileName": "large_file.zip",
    "fileSize": 10485760,
    "fileHash": "md5_hash_string",
    "totalChunks": 5
  }
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "uploadId": "upload_uuid_123",
      "chunkSize": 2097152,
      "chunks": [
        {"chunkNumber": 1, "status": "PENDING"},
        {"chunkNumber": 2, "status": "PENDING"}
      ]
    }
  }
  ```

- **接口**: `POST /chat/file/chunk/upload`
- **描述**: 上传文件分片
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}",
    "Content-Type": "multipart/form-data"
  }
  ```
- **请求体** (FormData):
  ```
  uploadId: string, 必填, 上传ID
  chunkNumber: integer, 必填, 分片序号
  chunk: File, 必填, 分片文件
  ```

- **接口**: `POST /chat/file/chunk/complete`
- **描述**: 完成分片上传
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **请求体**:
  ```json
  {
    "uploadId": "upload_uuid_123",
    "fileHash": "md5_hash_string",
    "receiverId": 2
  }
  ```

### 4.6 删除聊天记录
- **接口**: `DELETE /chat/messages`
- **描述**: 删除与指定用户的聊天记录
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **请求体**:
  ```json
  {
    "friendId": 2,
    "deleteType": "ALL|BEFORE_TIME",
    "beforeTime": "2024-01-01 00:00:00"
  }
  ```

---

## 五、用户动态模块

### 5.1 发布动态
- **接口**: `POST /posts`
- **描述**: 发布用户动态
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **请求体**:
  ```json
  {
    "content": "string, 必填, 动态内容",
    "visibility": "string, 可选, 可见性(PUBLIC|FRIENDS|PRIVATE)，默认PUBLIC",
    "imageUrls": ["string, 可选, 图片URL数组"],
    "topic": "string, 可选, 话题"
  }
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "发布成功",
    "data": {
      "postId": 1,
      "content": "今天天气真好",
      "createdAt": "2024-01-15 10:00:00"
    }
  }
  ```

### 5.2 获取动态列表
- **接口**: `GET /posts`
- **描述**: 获取动态时间线
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **查询参数**:
  ```
  userId: integer, 可选, 指定用户ID，不传则获取好友动态
  type: string, 可选, 类型(FRIENDS|FOLLOWING|ALL)，默认FRIENDS
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认10
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "list": [
        {
          "id": 1,
          "userId": 2,
          "userNickname": "用户二",
          "userAvatar": "http://example.com/avatar2.jpg",
          "content": "今天天气真好",
          "imageUrls": ["http://example.com/image1.jpg"],
          "likeCount": 10,
          "commentCount": 3,
          "isLiked": true,
          "visibility": "PUBLIC",
          "createdAt": "2024-01-15 10:00:00"
        }
      ],
      "total": 50,
      "page": 1,
      "size": 10
    }
  }
  ```

### 5.3 点赞动态
- **接口**: `POST /posts/{postId}/like`
- **描述**: 点赞或取消点赞
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **路径参数**:
  ```
  postId: integer, 必填, 动态ID
  ```

### 5.4 发表评论
- **接口**: `POST /posts/{postId}/comments`
- **描述**: 发表评论
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **路径参数**:
  ```
  postId: integer, 必填, 动态ID
  ```
- **请求体**:
  ```json
  {
    "content": "string, 必填, 评论内容",
    "parentId": "integer, 可选, 父评论ID（用于回复）"
  }
  ```

### 5.5 获取评论列表
- **接口**: `GET /posts/{postId}/comments`
- **描述**: 获取动态评论
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **路径参数**:
  ```
  postId: integer, 必填, 动态ID
  ```
- **查询参数**:
  ```
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认20
  ```

### 5.6 删除动态
- **接口**: `DELETE /posts/{postId}`
- **描述**: 删除自己的动态
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **路径参数**:
  ```
  postId: integer, 必填, 动态ID
  ```

---

## 六、管理员模块

### 6.1 管理员登录
- **接口**: `POST /admin/login`
- **描述**: 管理员登录
- **请求体**:
  ```json
  {
    "username": "string, 必填, 管理员账号",
    "password": "string, 必填, 密码"
  }
  ```

### 6.2 获取在线用户列表
- **接口**: `GET /admin/users/online`
- **描述**: 获取当前在线用户
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {admin_token}"
  }
  ```
- **查询参数**:
  ```
  keyword: string, 可选, 搜索关键词
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认20
  ```

### 6.3 获取用户列表
- **接口**: `GET /admin/users`
- **描述**: 获取所有用户
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {admin_token}"
  }
  ```
- **查询参数**:
  ```
  username: string, 可选, 用户名搜索
  nickname: string, 可选, 昵称搜索
  email: string, 可选, 邮箱搜索
  status: integer, 可选, 状态筛选
  startTime: string, 可选, 注册开始时间
  endTime: string, 可选, 注册结束时间
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认20
  ```

### 6.4 封禁/解封用户
- **接口**: `PUT /admin/users/{userId}/status`
- **描述**: 修改用户状态
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {admin_token}"
  }
  ```
- **路径参数**:
  ```
  userId: integer, 必填, 用户ID
  ```
- **请求体**:
  ```json
  {
    "status": "integer, 必填, 状态(0-禁用, 1-正常, 2-封禁)",
    "reason": "string, 可选, 原因说明",
    "banDays": "integer, 可选, 封禁天数（永久封禁传0）"
  }
  ```

### 6.5 重置用户密码
- **接口**: `PUT /admin/users/{userId}/password/reset`
- **描述**: 重置用户密码
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {admin_token}"
  }
  ```
- **路径参数**:
  ```
  userId: integer, 必填, 用户ID
  ```
- **请求体**:
  ```json
  {
    "newPassword": "string, 必填, 新密码"
  }
  ```

### 6.6 获取举报列表
- **接口**: `GET /admin/reports`
- **描述**: 获取用户举报列表
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {admin_token}"
  }
  ```
- **查询参数**:
  ```
  status: string, 可选, 状态(PENDING|PROCESSED|DISMISSED)
  reporterId: integer, 可选, 举报人ID
  reportedUserId: integer, 可选, 被举报人ID
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认20
  ```

### 6.7 处理举报
- **接口**: `PUT /admin/reports/{reportId}/handle`
- **描述**: 处理用户举报
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {admin_token}"
  }
  ```
- **路径参数**:
  ```
  reportId: integer, 必填, 举报ID
  ```
- **请求体**:
  ```json
  {
    "action": "string, 必填, 处理动作(PROCESS|DISMISS)",
    "punishment": "string, 可选, 处罚措施(WARNING|BAN_TEMPORARY|BAN_PERMANENT)",
    "banDays": "integer, 可选, 封禁天数",
    "remark": "string, 可选, 处理备注"
  }
  ```

### 6.8 系统数据统计
- **接口**: `GET /admin/statistics`
- **描述**: 获取系统统计数据
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {admin_token}"
  }
  ```
- **查询参数**:
  ```
  period: string, 可选, 统计周期(DAY|WEEK|MONTH)，默认DAY
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "userStatistics": {
        "totalUsers": 1000,
        "todayNewUsers": 10,
        "activeUsers": 500,
        "onlineUsers": 150
      },
      "messageStatistics": {
        "totalMessages": 50000,
        "todayMessages": 1000,
        "avgMessagesPerUser": 50
      },
      "systemStatistics": {
        "cpuUsage": "45%",
        "memoryUsage": "60%",
        "diskUsage": "75%"
      }
    }
  }
  ```

### 6.9 敏感词管理
- **接口**: `GET /admin/sensitive-words`
- **描述**: 获取敏感词列表
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {admin_token}"
  }
  ```

- **接口**: `POST /admin/sensitive-words`
- **描述**: 添加敏感词
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {admin_token}"
  }
  ```
- **请求体**:
  ```json
  {
    "word": "string, 必填, 敏感词",
    "level": "integer, 可选, 敏感级别(1-3)，默认1",
    "replaceWord": "string, 可选, 替换词"
  }
  ```

---

## 七、系统模块

### 7.1 举报用户
- **接口**: `POST /system/report`
- **描述**: 举报其他用户
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **请求体**:
  ```json
  {
    "reportedUserId": "integer, 必填, 被举报用户ID",
    "reportType": "string, 必填, 举报类型(HARASSMENT|SPAM|FRAUD|OTHER)",
    "description": "string, 必填, 举报描述",
    "evidence": {
      "messageIds": [1001, 1002],
      "imageUrls": ["http://example.com/evidence.jpg"]
    }
  }
  ```

### 7.2 获取系统公告
- **接口**: `GET /system/announcements`
- **描述**: 获取系统公告
- **查询参数**:
  ```
  status: string, 可选, 状态(ACTIVE|ALL)，默认ACTIVE
  page: integer, 可选, 页码，默认1
  size: integer, 可选, 每页数量，默认10
  ```

### 7.3 获取未读消息数量
- **接口**: `GET /system/unread-count`
- **描述**: 获取各类未读消息数量
- **请求头**:
  ```json
  {
    "Authorization": "Bearer {token}"
  }
  ```
- **响应成功**:
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "friendRequests": 3,
      "unreadMessages": 15,
      "systemNotifications": 2
    }
  }
  ```

---

## 八、WebSocket消息类型说明

### 8.1 客户端发送消息类型
| 类型           | 说明         | 数据格式                                  |
| -------------- | ------------ | ----------------------------------------- |
| `CHAT_MESSAGE` | 发送聊天消息 | 见4.1.1                                   |
| `MESSAGE_READ` | 消息已读确认 | 见4.1.2                                   |
| `HEARTBEAT`    | 心跳包       | `{"type":"HEARTBEAT"}`                    |
| `TYPING`       | 正在输入状态 | `{"type":"TYPING","data":{"friendId":2}}` |

### 8.2 服务器推送消息类型
| 类型                      | 说明         | 数据格式                                                     |
| ------------------------- | ------------ | ------------------------------------------------------------ |
| `MESSAGE_ACK`             | 消息发送确认 | 见4.1.1                                                      |
| `NEW_MESSAGE`             | 新消息通知   | 同CHAT_MESSAGE格式                                           |
| `MESSAGE_READ_RECEIPT`    | 已读回执     | `{"type":"MESSAGE_READ_RECEIPT","data":{"messageIds":[1001],"readerId":2}}` |
| `FRIEND_REQUEST`          | 好友请求通知 | `{"type":"FRIEND_REQUEST","data":{"requestId":1,"fromUserId":2,"nickname":"用户二"}}` |
| `FRIEND_REQUEST_ACCEPTED` | 好友请求通过 | `{"type":"FRIEND_REQUEST_ACCEPTED","data":{"fromUserId":2,"nickname":"用户二"}}` |
| `USER_ONLINE`             | 用户上线通知 | `{"type":"USER_ONLINE","data":{"userId":2}}`                 |
| `USER_OFFLINE`            | 用户下线通知 | `{"type":"USER_OFFLINE","data":{"userId":2}}`                |
| `SYSTEM_NOTIFICATION`     | 系统通知     | `{"type":"SYSTEM_NOTIFICATION","data":{"title":"系统维护","content":"今晚23:00-24:00系统维护"}}` |

---

## 九、错误码说明

### 9.1 通用错误码
| 错误码 | 说明             |
| ------ | ---------------- |
| 200    | 成功             |
| 400    | 请求参数错误     |
| 401    | 未授权/Token过期 |
| 403    | 权限不足         |
| 404    | 资源不存在       |
| 429    | 请求过于频繁     |
| 500    | 服务器内部错误   |

### 9.2 业务错误码
| 错误码 | 说明               |
| ------ | ------------------ |
| 1001   | 用户不存在         |
| 1002   | 密码错误           |
| 1003   | 用户已被封禁       |
| 1004   | 邮箱已注册         |
| 1005   | 用户名已存在       |
| 2001   | 好友关系已存在     |
| 2002   | 不能添加自己为好友 |
| 2003   | 好友请求不存在     |
| 3001   | 文件大小超限       |
| 3002   | 文件类型不支持     |
| 4001   | 动态不存在         |
| 4002   | 无权限操作此动态   |
| 5001   | 验证码错误         |
| 5002   | 验证码已过期       |

---

## 十、接口使用示例

### 10.1 完整登录流程
```javascript
// 1. 发送邮箱验证码
POST /auth/captcha/send
{
  "email": "user@example.com",
  "type": "REGISTER"
}

// 2. 用户注册
POST /auth/register
{
  "username": "testuser",
  "password": "password123",
  "nickname": "测试用户",
  "email": "user@example.com",
  "captcha": "123456"
}

// 3. 用户登录
POST /auth/login
{
  "username": "testuser",
  "password": "password123"
}

// 4. 使用Token访问其他接口
GET /user/profile
Headers: { "Authorization": "Bearer eyJhbGciOiJIUzI1NiIs..." }
```

### 10.2 聊天流程示例
```javascript
// 1. 建立WebSocket连接
const ws = new WebSocket('ws://localhost:8088/ws?token=eyJhbGciOiJIUzI1NiIs...');

// 2. 发送消息
ws.send(JSON.stringify({
  type: 'CHAT_MESSAGE',
  data: {
    receiverId: 2,
    messageType: 'TEXT',
    content: '你好',
    clientMessageId: 'client-uuid-123'
  },
  timestamp: Date.now(),
  messageId: 'uuid-123'
}));

// 3. 接收消息
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  if (message.type === 'NEW_MESSAGE') {
    console.log('收到新消息:', message.data);
  }
};
```

---

## 十一、导入到Apifox的说明

1. **创建新项目**：在Apifox中创建新项目
2. **导入方式**：选择"导入" -> "OpenAPI/Swagger"
3. **数据格式**：可以将本文档转换为OpenAPI 3.0格式导入
4. **环境配置**：
   - 开发环境: `http://localhost:8088/api`
   - 测试环境: `http://test.example.com/api`
   - 生产环境: `https://api.example.com`
5. **全局Header**：设置`Authorization: Bearer {{token}}`
6. **全局变量**：设置`token`变量用于测试

