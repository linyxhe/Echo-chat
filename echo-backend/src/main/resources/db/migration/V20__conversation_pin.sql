-- 好友/AI 会话置顶状态
ALTER TABLE `conversation`
  ADD COLUMN `is_pinned` tinyint(1) NOT NULL DEFAULT 1 COMMENT '当前用户是否置顶' AFTER `is_archived`;

ALTER TABLE `conversation`
  ADD KEY `idx_conversation_pinned` (`user1_id`, `is_archived`, `is_pinned`, `updated_at`);
