-- 知识库文档分类（管理端组织/搜索用；bot 检索暂不按分类过滤）。
ALTER TABLE `kb_document`
  ADD COLUMN `category` varchar(100) DEFAULT NULL COMMENT '文档分类' AFTER `content_type`;
