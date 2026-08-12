-- 用户自定义 AI 助手私有知识库：文档同时绑定创建者与助手，空值表示管理员公共知识库。
ALTER TABLE `kb_document`
  ADD COLUMN `owner_id` bigint DEFAULT NULL COMMENT '私有文档所有者用户ID' AFTER `created_by`,
  ADD COLUMN `assistant_id` bigint DEFAULT NULL COMMENT '私有文档绑定的 AI 助手ID' AFTER `owner_id`;

ALTER TABLE `kb_document`
  ADD KEY `idx_kb_document_private_scope` (`owner_id`, `assistant_id`, `status`);
