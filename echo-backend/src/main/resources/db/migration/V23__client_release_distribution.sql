CREATE TABLE `client_release` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `platform` varchar(20) NOT NULL COMMENT 'WINDOWS/ANDROID',
  `version` varchar(64) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `storage_key` varchar(255) NOT NULL,
  `file_size` bigint NOT NULL,
  `sha256` char(64) NOT NULL,
  `release_notes` varchar(1000) DEFAULT NULL,
  `published` tinyint(1) NOT NULL DEFAULT 0,
  `published_at` datetime DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_client_release_platform_published` (`platform`, `published`, `published_at`),
  KEY `idx_client_release_creator` (`created_by`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户端安装包发布记录';
