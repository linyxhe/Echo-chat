-- 消息已读时间：标记已读时写入，前端展示「已读 HH:mm」。
ALTER TABLE `message`
  ADD COLUMN `read_at` datetime DEFAULT NULL COMMENT '已读时间' AFTER `is_read`;
