-- AI Gateway 调用审计与基础用量统计
CREATE TABLE `ai_usage_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '发起调用的用户',
  `assistant_id` bigint DEFAULT NULL COMMENT '自定义助手 ID，系统助手为空',
  `bot_user_id` bigint DEFAULT NULL COMMENT '目标 BOT 用户 ID',
  `stream_id` varchar(100) DEFAULT NULL,
  `model_name` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL COMMENT 'SUCCESS/ERROR/CANCELLED/REPLAYED/FALLBACK',
  `input_chars` int NOT NULL DEFAULT 0,
  `output_chars` int NOT NULL DEFAULT 0,
  `input_tokens` int DEFAULT NULL,
  `output_tokens` int DEFAULT NULL,
  `total_tokens` int DEFAULT NULL,
  `first_token_ms` bigint DEFAULT NULL,
  `latency_ms` bigint DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_usage_user_created` (`user_id`, `created_at`),
  KEY `idx_ai_usage_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Gateway 调用审计';
