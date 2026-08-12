ALTER TABLE `chat_group_member`
  ADD COLUMN `visible_read_message_id` bigint DEFAULT NULL COMMENT '公开给群成员的已读游标；仅开启已读回执时更新';
