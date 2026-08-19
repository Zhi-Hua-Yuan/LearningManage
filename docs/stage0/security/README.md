# 阶段 0.3 密钥治理与配置外置

阶段 0.3-A 负责代码、配置和启动门禁改造；阶段 0.3-B 负责本机真实凭据切换。MySQL Root 密码本次按已记录的本机风险接受决定不轮换，Redis 和 Qdrant 仍不在本阶段处理范围内。

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

## 阶段 0.3-B1

2026-08-18 已完成本机和当前仓库范围内的凭据轮换前置检查：

- [凭据清单](credential-inventory-2026-08-18.md)
- [轮换就绪报告](credential-rotation-readiness-2026-08-18.md)
- [阶段 0.3-B2 运行手册](credential-rotation-runbook.md)

B1 未创建账号、修改权限、轮换密钥、停用凭据或重启应用。B2 仍需确认外部部署范围、Redis 处理方式、凭据存储位置和阿里云 Key 所有权。

## 阶段 0.3-B2-0 / B2-1

- [B2-0 门禁记录](b2-0-preflight-2026-08-18.md)
- [B2-1 账号创建与最小权限验证记录](b2-1-account-creation-2026-08-18.md)

B2-1 已完成本机业务账号、独立测试账号和最小权限验证。Root、JWT、阿里云 API Key 和 Redis 尚未轮换。

## 阶段 0.3-B2-3

- [B2-3 JWT 密钥轮换与验证记录](b2-3-jwt-rotation-2026-08-18.md)

B2-3 已完成本机 JWT 密钥轮换。新密钥已写入真实 Windows 用户级环境变量，隔离测试库上的登录、Token 鉴权、篡改 Token 拒绝和缺少 Token 拒绝均通过；61 项自动化测试全部通过。随后 B2-4 已完成阿里云 API Key 切换和最低成本验证，旧 Key 已停用。

## 阶段 0.3-B2-4

- [B2-4 阿里云 API Key 切换记录](b2-4-aliyun-api-key-2026-08-19.md)

B2-4 已完成。新 Key 验证成功，旧 Key 已停用；健康检查、注册、登录和最低成本 AI 调用均通过。验证期间 8123、8124 端口均已清理，主库未修改，61 项自动化测试保持 0 failures / 0 errors。

## 阶段 0.3-B2-5

- [B2-5 MySQL Root 密码轮换风险接受记录](b2-5-mysql-root-rotation-waiver-2026-08-19.md)
- [B2-5.1 Root 轮换前门禁记录](b2-5-1-root-rotation-preflight-2026-08-19.md)

经用户确认，本机 MySQL Root 密码本次不轮换，作为本机范围内的已接受残余风险记录。Root 仍限制为 `root@localhost`，应用和测试已使用独立最小权限账号，Root 不参与应用运行。该决定不适用于云服务器、Docker、CI 或任何对外暴露的数据库环境。

## 阶段 0.3-B 当前结论

- MySQL 应用账号：已完成。
- MySQL 测试账号：已完成。
- JWT 密钥：已完成轮换。
- 阿里云 API Key：已完成轮换，旧 Key 已停用。
- MySQL Root 密码：本次不轮换，已接受本机残余风险。
- Redis：按 Redis 5 兼容性结论延期。
- Qdrant：当前无使用方，不适用。
