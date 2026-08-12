-- 用户自建 AI 助手的本地备注，只对助手所有者可见
ALTER TABLE `ai_assistant`
  ADD COLUMN `remark` varchar(50) DEFAULT NULL COMMENT '助手所有者的本地备注' AFTER `name`;
