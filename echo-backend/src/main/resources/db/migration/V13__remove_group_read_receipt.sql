-- 删除群消息已读人数/已读回执功能及其历史字段。
ALTER TABLE `chat_group_member`
  DROP COLUMN `visible_read_message_id`;
