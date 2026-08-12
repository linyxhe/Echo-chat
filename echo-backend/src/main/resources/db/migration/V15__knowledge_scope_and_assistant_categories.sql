-- 知识库范围增强：管理员可以决定文档是否允许用户 AI 助手检索；助手支持多分类范围。
ALTER TABLE `kb_document`
  ADD COLUMN `ai_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否允许用户 AI 助手检索' AFTER `category`;

ALTER TABLE `ai_assistant`
  ADD COLUMN `knowledge_categories` text DEFAULT NULL COMMENT 'RAG 知识库分类范围 JSON 数组；为空表示全部可用分类' AFTER `knowledge_category`;
