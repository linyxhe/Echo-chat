-- AI Gateway：为 user.role 增加 BOT 角色，并创建固定 username 的 "AI 助手" 系统用户。
-- bot 不写死 id（InnoDB 不允许把 AUTO_INCREMENT 重置到低于当前 max(id)，显式高 id 会让下一个真实用户拿到巨大 id），
-- 后端按 username='ai_assistant' 解析其 id（见 com.echo.ai.BotUserService）。

ALTER TABLE `user`
  MODIFY COLUMN `role` enum('USER','ADMIN','BOT') DEFAULT 'USER' COMMENT '角色';

-- password_hash 为一次性生成的 BCrypt 哈希（随机密码，bot 永不登录，仅满足 NOT NULL）。
INSERT INTO `user`
  (`username`, `nickname`, `password_hash`, `email`, `role`)
VALUES
  ('ai_assistant', 'AI 助手',
   '$2a$10$Thd3nLx1r93r8qEMyX2aHOI7Wxfk9m5ZX5IByumeDdCByZkxtr1y2',
   'ai@echo.local', 'BOT');
