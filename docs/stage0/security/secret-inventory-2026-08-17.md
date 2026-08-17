# 阶段 0.3-A 敏感配置盘点

盘点日期：2026-08-17 Asia/Shanghai  
范围：当前工作区、Spring 配置、Docker Compose、JWT 实现和配置调用方

## 发现

| 类型 | 原位置 | 阶段 0.3-A 处理 |
|---|---|---|
| MySQL 开发密码 | `application-dev.yml` | 改为 `DB_PASSWORD` |
| MySQL 测试密码 | `application-test.yml` | 改为 `TEST_DB_PASSWORD` |
| MySQL 生产密码默认值 | `application-prod.yml` | 改为无默认值的 `DB_PASSWORD` |
| Docker MySQL 密码 | `deploy/docker-compose.yml` | 改为必填环境变量 |
| JWT 签名密钥 | `JwtUtils` 静态常量 | 改为 `JWT_SECRET` 和 `JwtTokenService` |
| 阿里云 API Key 默认值/配置值 | `application*.yml` | 改为 `ALIYUN_API_KEY` |
| Redis 密码 | Profile 配置 | 改为环境变量，开发可为空 |
| Qdrant | 当前无代码调用方 | 仅在 `.env.example` 预留，不设启动门禁 |

## 说明

本文件不保存任何真实凭据。真实凭据是否曾进入 Git 历史、是否需要轮换，属于阶段 0.3-B 的独立确认事项。

当前工作区扫描未再发现原始开发数据库密码、原始 JWT 常量、生产 API Key 或占位默认值。历史提交仍包含此前被跟踪的开发/测试数据库密码和 JWT 常量，阶段 0.3-B 必须轮换对应凭据；本阶段不改写 Git 历史。
