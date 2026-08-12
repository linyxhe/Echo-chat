-- 群主控制是否需要验证后才能加入群聊；默认开启，保持现有安全策略
ALTER TABLE `chat_group`
  ADD COLUMN `join_verification_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '加入群聊是否需要群主验证' AFTER `owner_id`;
