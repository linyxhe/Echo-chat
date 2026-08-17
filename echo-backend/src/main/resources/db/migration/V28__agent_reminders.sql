CREATE TABLE `agent_reminder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `assistant_id` bigint DEFAULT NULL,
  `content` varchar(500) NOT NULL,
  `scheduled_at` datetime NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `source_confirmation_id` bigint NOT NULL,
  `fired_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_reminder_confirmation` (`source_confirmation_id`),
  KEY `idx_agent_reminder_due` (`status`, `scheduled_at`),
  KEY `idx_agent_reminder_user` (`user_id`, `scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户确认创建的 AI 站内提醒';
