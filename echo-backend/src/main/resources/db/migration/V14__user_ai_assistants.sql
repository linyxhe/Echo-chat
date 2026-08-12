-- 用户自定义 AI 助手：每个助手绑定一个 BOT 用户，复用现有消息/会话链路。
CREATE TABLE IF NOT EXISTS `ai_assistant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '助手ID',
  `owner_id` bigint NOT NULL COMMENT '创建者用户ID',
  `bot_user_id` bigint NOT NULL COMMENT '绑定的 BOT 用户ID',
  `name` varchar(50) NOT NULL COMMENT '助手名称',
  `assistant_type` varchar(30) NOT NULL DEFAULT 'GENERAL' COMMENT '助手类型',
  `persona` text COMMENT '系统角色设定',
  `knowledge_category` varchar(100) DEFAULT NULL COMMENT 'RAG 知识库分类；为空表示全部分类',
  `default_operations` text COMMENT '默认行为约束',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_assistant_bot_user` (`bot_user_id`),
  KEY `idx_ai_assistant_owner_status` (`owner_id`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户自定义 AI 助手';
