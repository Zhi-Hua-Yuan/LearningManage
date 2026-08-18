# 阶段 0.3-B1 凭据清单

盘点时间：2026-08-18 Asia/Shanghai  
范围：当前仓库、本机 MySQL、本机 Redis、当前进程/用户/系统环境变量、Docker 和 CI 配置  
限制：无法从本机自动确认仓库之外的服务器、云平台和其他应用实例

本文件不保存密码、JWT、Token 或 API Key 正文，也不保存可用于离线猜测弱密码的哈希。

## 1. 环境矩阵

| 环境 | 发现状态 | B1 结论 |
|---|---|---|
| 本机 Spring Boot | 端口 8123 未监听 | 当前没有运行实例 |
| 本机 MySQL | `localhost:3306` 可达，MySQL 8.0.41 | 已纳入备份和权限检查 |
| 本机 Redis | `localhost:6379` 可达，Redis 5.0.14 standalone | 无认证即可 `PING`，不支持 ACL 命令 |
| Docker Compose | 配置文件存在 | Docker CLI 可用，但 daemon 未启动 |
| CI | 未发现 `.github/workflows` | 当前仓库没有 CI 工作流 |
| 外部服务器/云环境 | 仓库中无可确认信息 | 需要用户确认是否存在 |

当前进程、用户级和系统级环境变量中，未检测到 `DB_*`、`TEST_DB_*`、`REDIS_*`、`JWT_SECRET`、`ALIYUN_API_KEY` 或 Qdrant 凭据。仓库根目录也不存在 `.env`、`.env.local` 或 `.env.*.local`。

## 2. 凭据清单

| 类型 | 当前状态 | 风险 | B2 动作 |
|---|---|---|---|
| MySQL Root | 只有 `root@localhost`；插件为 `mysql_native_password`；账号未过期、未锁定，拥有全局可授权权限 | 高 | 先创建业务账号并切换应用，再轮换 Root |
| MySQL 业务账号 | `learning_manage_app` 不存在 | 高 | 创建最小权限账号，应用停止使用 Root |
| 测试数据库账号 | 测试仍可使用历史测试配置中的凭据连接 | 高 | 与开发数据库凭据一起轮换，并保持测试库隔离 |
| Redis | Redis 5.0.14，无认证连接可用，不支持 Redis 6 ACL | 中；若暴露到非可信网络则为高 | 决定升级到 Redis 6+，或单独设计持久化 `requirepass` 方案 |
| JWT | 代码已改为 `JWT_SECRET`；当前没有运行实例和已注入密钥 | 高 | 在下一次启动前生成新密钥，接受旧 Token 失效 |
| 阿里云 API Key | 当前环境未设置；Git 历史中的仓库配置为占位默认值 | 待确认 | 用户确认是否存在外部有效 Key及其所有权，再由控制台轮换 |
| Qdrant | 无代码调用方，环境变量未设置 | 不适用 | 本阶段不处理 |
| Docker Secrets | Compose 要求从环境注入数据库、JWT 和 AI 凭据 | 待部署 | B2 前选择受控 `.env.local` 或 Secret 存储 |
| CI Secrets | 无 CI 工作流 | 不适用 | 建立 CI 时再配置 |

## 3. Git 历史风险

- 开发和测试数据库密码曾被提交到 Git 历史。
- B1 使用历史中的旧开发凭据进行了不回显连接验证，旧凭据目前仍能认证。
- 原静态 JWT 密钥仍存在于 Git 历史。
- AI 配置历史只发现环境变量占位默认值，没有在当前仓库证据中确认真实供应商 Key。
- 当前跟踪树经过规则扫描和人工复核，未发现有效密码、私钥、JWT Token 或供应商 Key。
- Gitleaks 当前未安装，因此 B1 采用仓库规则扫描；后续 CI 应补充专业扫描工具。

Git 历史不在 B1 中改写。因为数据库旧凭据仍有效，B2 必须轮换；JWT 在下一次部署时必须使用新密钥。

## 4. 存储状态

当前没有发现已建立的本地受控凭据文件或系统环境变量。进入 B2 前必须选择并准备一种方式：

- 本机用户级环境变量；或
- 已被 Git 忽略且限制访问权限的 `.env.local`；或
- 操作系统/部署平台 Secret；或
- 密码管理器。

不得把新凭据写入 `.env.example`、Spring YAML、运行手册、Git 提交信息或终端输出。
