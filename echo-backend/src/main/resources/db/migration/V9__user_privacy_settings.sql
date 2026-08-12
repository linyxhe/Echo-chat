-- 每用户隐私开关（默认关闭：最隐私）。关闭后他人看不到你的在线状态/收不到你的已读回执。
ALTER TABLE `user`
  ADD COLUMN `show_online_status` tinyint(1) DEFAULT 0 COMMENT '是否展示在线状态' AFTER `updated_at`,
  ADD COLUMN `show_read_receipts` tinyint(1) DEFAULT 0 COMMENT '是否展示已读回执' AFTER `show_online_status`;
