CREATE TABLE `agent_tool_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assistant_id` bigint NOT NULL,
  `tool_name` varchar(64) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_tool_grant` (`assistant_id`, `tool_name`),
  KEY `idx_agent_tool_grant_assistant` (`assistant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户自建 AI 助手的工具授权';
