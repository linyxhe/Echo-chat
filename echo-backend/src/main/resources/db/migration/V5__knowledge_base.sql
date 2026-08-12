-- 知识库：文档元数据 + 分片（含 embedding JSON）。embedding 由本地 BGE 中文模型生成，持久化避免重启重算。

CREATE TABLE `kb_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `filename` varchar(255) NOT NULL COMMENT '原始文件名',
  `content_type` varchar(255) DEFAULT NULL,
  `content` longtext COMMENT '抽取出的原文文本',
  `chunk_count` int NOT NULL DEFAULT 0 COMMENT '分片数',
  `status` varchar(20) NOT NULL DEFAULT 'READY' COMMENT 'PENDING/READY/FAILED',
  `created_by` bigint DEFAULT NULL COMMENT '上传者 user id',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_document_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库文档';

CREATE TABLE `kb_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` bigint NOT NULL COMMENT '所属文档',
  `chunk_index` int NOT NULL COMMENT '文档内分片序号（0 起）',
  `content` text NOT NULL COMMENT '分片文本',
  `embedding` text NOT NULL COMMENT 'float[] 的 JSON 数组字符串',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_chunk_doc_index` (`document_id`, `chunk_index`),
  KEY `idx_kb_chunk_doc` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库分片与向量';
