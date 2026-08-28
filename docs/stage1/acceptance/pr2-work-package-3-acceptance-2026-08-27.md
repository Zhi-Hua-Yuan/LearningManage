# PR2 工作包 3 本地验收记录

日期：2026-08-27
范围：阶段 1 CI 数据库门禁与负向 preflight 隔离验证

## 1. 交付内容

- 空库门禁升级为 V1+V2 全链路：首次迁移执行 2 个版本，验证 22 张业务表、23 张总表、2 条 Flyway history，并执行 12 项 V2 post-verify。
- 既有 V1 库门禁导入冻结 V1 schema 与阶段 1 seed fixture，先执行 25 项 V2 preflight，再 baseline V1、迁移 V2，并核对任务分配日志、角色归一化、周复盘默认隐私和行数对账。
- 新增 3 个隔离负向数据库，分别覆盖未知系统角色、孤立受理人和非团队成员受理人；每个样本要求恰好 1 个 preflight 失败，且不得创建 V2 列、关系表或 Flyway history。
- backend CI 与 release gate 均接入负向门禁，并将运行时 history 基线更新为 2。

## 2. 本地验证

| 验证项 | 结果 |
|---|---|
| Maven 测试 | PASS：104 项通过，失败 0、错误 0、跳过 0（独立 PR2 候选复验） |
| CI 脚本静态合同 | PASS：脚本清单、LF、安全前置 guard、无 clean/repair 均通过 |
| SQL 静态合同 | PASS：V2 migration、preflight、post-verify、fixture 合同通过 |
| 工作流接线 | PASS：backend-ci/release-gate 均包含负向 preflight，history=2 |
| diff 检查 | PASS：`git diff --check` 无输出 |

## 3. 未在本记录中宣称的内容

本记录不替代受保护分支上的 GitHub Actions 实跑证据，也不宣称生产数据库或 Java 权限服务已经完成。恢复演练和角色写入兼容性由独立 PR2 候选的后续验收记录覆盖；`SystemRole` 与统一 `PermissionService` 保留到 PR3。
