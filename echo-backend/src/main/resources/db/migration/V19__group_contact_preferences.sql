-- 群聊联系人视图：备注、隐藏、置顶和当前用户的历史清空游标
ALTER TABLE `chat_group_member`
  ADD COLUMN `remark` varchar(100) DEFAULT NULL COMMENT '当前用户的群备注' AFTER `user_id`,
  ADD COLUMN `is_archived` tinyint(1) NOT NULL DEFAULT 0 COMMENT '当前用户是否从消息列表隐藏' AFTER `remark`,
  ADD COLUMN `is_pinned` tinyint(1) NOT NULL DEFAULT 1 COMMENT '当前用户是否置顶' AFTER `is_archived`,
  ADD COLUMN `history_cleared_at` datetime DEFAULT NULL COMMENT '当前用户清空聊天记录的时间' AFTER `last_read_message_id`;
