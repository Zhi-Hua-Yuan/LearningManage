# 阶段 0.3-B1 凭据轮换就绪报告

执行时间：2026-08-18 Asia/Shanghai  
基线提交：`909e89a chore(stage0): 完成密钥治理与配置外置`  
执行状态：本机和当前仓库范围 B1 已完成；进入 B2 前仍有人工确认项

## 1. 安全边界

B1 只执行读取、备份、隔离恢复、测试和文档记录。未创建或删除数据库账号，未修改权限或密码，未修改 Redis，未轮换 JWT/API Key，也未重启应用。

## 2. 数据库备份与恢复

证据目录：

```text
D:\ajavacode\LearningManage\.codex-tmp\stage0-credential-rotation-20260818-153937
```

隔离恢复库：`learning_manage_stage03b1_restore_20260818_153937`

| 文件 | 字节数 | SHA-256 |
|---|---:|---|
| `learning_manage-full.sql` | 133107 | `29A1CA52DB30D1132083D324AF2721217FF9583B43F7479639D59577FFEA3432` |
| `learning_manage-schema.sql` | 27971 | `F19C30AA58E4E34256352A0327DD448BE7C0E460CE2D8B9A824B06ABC2D800CB` |
| `restore-mysqlcheck.txt` | 1327 | `608B01848BD98EE891BB750F469F89E3B0932C5768108DA27698A63302C9D343` |
| `restore-counts.tsv` | 162 | `D278168128B481086CF9B8358638FD6DB4669FB5517739F1F5E54C14B42788CF` |

主库和恢复库均为 20 张表；用户 28、项目 35、里程碑 37、任务 122、周复盘 3，行数一致；恢复库全部 20 张表检查通过。隔离恢复库目前保留，删除需要单独确认。

## 3. 数据库账号就绪情况

- 当前认证账号为 `root@localhost`。
- `learning_manage_app` 数量为 0。
- Root 账号具有全局权限和授权能力。
- 当前开发配置仍允许用户名默认到 `root`。
- 历史中的旧数据库凭据仍能认证，风险确认成立。

B2 必须先创建并验证业务账号，再切换应用，最后轮换 Root；不能先修改 Root 密码。

## 4. Redis、JWT 和 AI

### Redis

Redis 5.0.14 可无认证访问且不支持 ACL。B2 不能直接使用 Redis 6 ACL 方案。建议把 Redis 认证作为独立变更：优先升级 Redis 6+；如果继续使用 Redis 5，必须通过持久化配置启用 `requirepass` 并准备重启和回滚。

### JWT

JWT 已由 `JwtProperties` 和 `JwtTokenService` 管理，生产密钥长度及占位值门禁有效。当前没有运行实例，也没有检测到 `JWT_SECRET`；原静态密钥存在于 Git 历史。B2 可以生成全新密钥，但必须确认所有旧 Token 统一失效。

### 阿里云 API Key

当前环境没有 `ALIYUN_API_KEY`。仓库历史中的 AI 配置属于占位默认值，未确认真实 Key 泄露。是否轮换需要用户确认外部有效 Key 是否存在以及是否可以创建新 Key。

## 5. 自动化和安全检查

- Maven 测试：61 个测试，失败 0、错误 0、跳过 0。
- JWT 和生产密钥策略测试包含在 61 个测试中。
- 当前跟踪树未确认有效凭据；两个规则命中经复核均为误报。
- Gitleaks 未安装，这是工具覆盖缺口。
- Docker daemon 未启动，未执行容器级验证。
- 端口 8123 未监听，因此没有运行中应用可做健康检查或 Token 行为验证。

## 6. B2 准入结论

| 子项 | 状态 | 条件 |
|---|---|---|
| MySQL 业务账号创建与切换 | 条件通过 | 确认凭据存储位置和维护窗口 |
| MySQL Root 轮换 | 暂不可执行 | 必须先完成业务账号切换 |
| JWT 轮换 | 条件通过 | 确认旧 Token 全部失效及实例范围 |
| Redis 轮换 | 需决策 | Redis 5 不支持 ACL，选择升级或 `requirepass` |
| 阿里云 Key 轮换 | 需用户操作/确认 | 确认外部 Key 和控制台权限 |
| Qdrant | 不适用 | 当前无使用方 |

本机和当前仓库范围内的 B1 工作已经完成。整体 B2 暂时为“有条件准入”，不是自动执行授权。

## 7. 进入 B2 前需要确认

1. 当前项目是否只在本机运行，还是还有服务器、Docker、CI 或其他实例。
2. 新凭据保存到用户级环境变量、受保护的 `.env.local`，还是外部 Secret 管理器。
3. 是否接受下一次启动时所有旧 JWT Token 失效。
4. Redis 5 本阶段暂缓认证，还是单独安排升级/`requirepass`。
5. 是否存在真实阿里云 Key，以及是否允许创建新 Key并执行一次最低成本验证调用。
