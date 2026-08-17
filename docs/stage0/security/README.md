# 阶段 0.3-A 密钥治理与配置外置

本阶段只完成代码、配置和启动门禁改造，不轮换真实数据库、JWT、Redis 或模型凭据。

## 本地启动

Spring Boot 不会自动读取 `.env` 文件。请将 `.env.example` 复制为本机专用文件，或在 IDE / PowerShell 的运行配置中设置环境变量。

PowerShell 示例：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_PASSWORD = 'your_local_database_password'
$env:JWT_SECRET = 'a-local-secret-with-at-least-32-characters'
$env:ALIYUN_API_KEY = 'your_local_api_key'
.\mvnw.cmd spring-boot:run
```

测试环境至少需要设置 `TEST_DB_PASSWORD`。测试专用 JWT 密钥使用 `application-test.yml` 中标注的非真实占位值，不得用于生产。

阶段 0.3-A 部署到已有运行环境时，`JWT_SECRET` 应先从受控凭据存储注入当前运行实例使用的旧密钥，以保持现有 Token 兼容；不要把旧密钥重新写回仓库。阶段 0.3-B 再单独确认密钥轮换和 Token 全部失效。

## 生产启动门禁

生产 Profile 必须提供 `DB_PASSWORD`、`JWT_SECRET` 和 `ALIYUN_API_KEY`。缺失、过短或使用占位值时，应用会在启动阶段失败。日志不得打印这些变量或完整 Token。

## 凭据轮换

真实凭据轮换属于阶段 0.3-B，需要在阶段 0.3-A 验收后单独确认。当前代码改造不会主动让现有 Token 失效，也不会修改数据库账号。
