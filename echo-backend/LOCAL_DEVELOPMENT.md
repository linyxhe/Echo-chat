# 后端本地开发说明

## 首次配置

1. 将 `src/main/resources/application-local.yml.example` 复制为同目录的 `application-local.yml`。
2. 在 `application-local.yml` 填写本机 MySQL、Redis、邮件和 JWT 配置。
3. 不要提交 `application-local.yml`；该文件已被根目录 `.gitignore` 忽略。

`JWT_SECRET` 必须是仅供本地使用的随机长字符串。模型 API Key 等未来敏感配置同样只放入本地配置或环境变量，不能写入仓库。

## 数据库迁移

应用使用 Flyway 管理 MySQL 表结构，迁移目录为 `src/main/resources/db/migration/`。

- 新建空数据库后，启动应用会执行 `V1__baseline_schema.sql` 创建现有基线表。
- 已由旧版 `echo_chat.sql` 初始化的数据库，首次启动会记录 Flyway 基线版本 `1`，不会重建或删除现有表和数据。
- 后续所有结构变更必须新建递增迁移，例如 `V2__add_file_metadata.sql`；不要修改已经执行过的迁移文件，也不要继续编辑 `echo_chat.sql`。

## 验证与运行

```powershell
cd echo-backend
mvn test
mvn spring-boot:run
```

测试使用 `test` Profile，不会运行 Flyway，也不读取本地私有配置。正式本地运行使用默认的 `local` Profile，并会在连接 MySQL 后执行或校验 Flyway 迁移。
