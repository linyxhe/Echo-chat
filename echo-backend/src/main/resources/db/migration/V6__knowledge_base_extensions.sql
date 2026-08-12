-- 知识库扩展：索引失败原因（异步索引用）。
ALTER TABLE `kb_document`
  ADD COLUMN `error_message` varchar(1000) DEFAULT NULL COMMENT '索引失败原因' AFTER `status`;
