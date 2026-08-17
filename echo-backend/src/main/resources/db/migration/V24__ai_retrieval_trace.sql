-- RAG 可观测性：只记录命中数量与最高分，禁止将文档正文或用户问题写入审计表。
ALTER TABLE `message`
  ADD COLUMN `ai_sources` text DEFAULT NULL COMMENT 'AI 回答采用的知识库来源摘要 JSON' AFTER `file_size`;

ALTER TABLE `ai_usage_log`
  ADD COLUMN `kb_private_hits` int DEFAULT NULL COMMENT '私有知识库命中数' AFTER `error_message`,
  ADD COLUMN `kb_public_hits` int DEFAULT NULL COMMENT '公共知识库命中数' AFTER `kb_private_hits`,
  ADD COLUMN `kb_max_score` double DEFAULT NULL COMMENT '本次检索最高相似度分数' AFTER `kb_public_hits`;
