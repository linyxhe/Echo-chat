-- 受控 Agent 审计：不保存用户提问、知识库正文、访问令牌或完整工具参数。
CREATE TABLE `agent_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `assistant_id` bigint DEFAULT NULL,
  `bot_user_id` bigint NOT NULL,
  `stream_id` varchar(128) NOT NULL,
  `status` varchar(32) NOT NULL,
  `step_count` int NOT NULL DEFAULT 0,
  `tool_call_count` int NOT NULL DEFAULT 0,
  `failure_reason` varchar(500) DEFAULT NULL,
  `started_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_run_user_created` (`user_id`, `started_at`),
  KEY `idx_agent_run_assistant_created` (`assistant_id`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控 Agent 运行审计';

CREATE TABLE `agent_tool_call` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` bigint NOT NULL,
  `sequence_no` int NOT NULL,
  `tool_name` varchar(64) NOT NULL,
  `risk_level` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL,
  `arguments_redacted` varchar(500) DEFAULT NULL,
  `result_summary` varchar(500) DEFAULT NULL,
  `duration_ms` bigint DEFAULT NULL,
  `error_code` varchar(64) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_tool_call_run` (`run_id`, `sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控 Agent 工具调用审计';
