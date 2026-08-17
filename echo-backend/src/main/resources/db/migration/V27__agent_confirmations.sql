-- Agent 写入类能力必须先生成待确认项；模型不能绕过此表直接写入用户数据。
CREATE TABLE `agent_confirmation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(96) NOT NULL,
  `user_id` bigint NOT NULL,
  `assistant_id` bigint DEFAULT NULL,
  `bot_user_id` bigint NOT NULL,
  `stream_id` varchar(128) NOT NULL,
  `action_type` varchar(32) NOT NULL,
  `payload` text NOT NULL,
  `summary` varchar(500) NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `expires_at` datetime NOT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_confirmation_token` (`token`),
  KEY `idx_agent_confirmation_user_status` (`user_id`, `status`, `expires_at`),
  KEY `idx_agent_confirmation_stream` (`user_id`, `stream_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent 待用户确认的写入提议';

CREATE TABLE `agent_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `assistant_id` bigint DEFAULT NULL,
  `content` varchar(1000) NOT NULL,
  `reason` varchar(300) DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_memory_user_assistant` (`user_id`, `assistant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户确认保存的 AI 助手记忆';

CREATE TABLE `agent_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `assistant_id` bigint DEFAULT NULL,
  `content` text NOT NULL,
  `title` varchar(120) DEFAULT NULL,
  `source_confirmation_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_draft_confirmation` (`source_confirmation_id`),
  KEY `idx_agent_draft_user_assistant` (`user_id`, `assistant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户确认保存的 AI 消息草稿';
