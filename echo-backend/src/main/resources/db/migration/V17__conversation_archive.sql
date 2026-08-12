-- 会话列表隐藏：只作用于当前用户的会话视角，不删除消息数据
ALTER TABLE `conversation`
  ADD COLUMN `is_archived` tinyint(1) NOT NULL DEFAULT 0 COMMENT '当前用户是否隐藏会话' AFTER `unread_count`;

ALTER TABLE `conversation`
  ADD KEY `idx_conversation_archived` (`user1_id`, `is_archived`, `updated_at`);
