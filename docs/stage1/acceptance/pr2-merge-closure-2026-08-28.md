# PR2 合并收口记录

状态：`PASS`

日期：2026-08-28

## 1. 合并事实

- PR：[#41](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/41)
- 标题：`feat(stage1): PR2 V2 migration and database release gates`
- Head：`codex/stage1-pr2-v2-migration`
- Base：`develop`
- 合并时间：2026-08-28 14:22:22（Asia/Shanghai）
- Merge commit：`e6189a40fab25079105c8f86734116988fc45b47`

远端 `develop` 已指向上述 Merge commit，PR2 至此完成受保护合并。

## 2. PR 检查结果

PR Backend CI Run：[`33147067189`](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33147067189)

| Job | 结果 |
|---|---|
| Guard and migration immutability | PASS |
| Maven verification and tested artifact | PASS |
| Flyway empty database gate | PASS |
| Flyway existing database gate | PASS |
| Docker runtime and migration gate | PASS |

## 3. develop 合并后验证

合并后 Backend CI Run：[`33147698207`](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33147698207)

- 触发事件：`push`
- Head SHA：`e6189a40fab25079105c8f86734116988fc45b47`
- 最终结论：`success`
- 5 个必需 Job 全部通过
- Maven 精确测试数：104
- Flyway history：2

该结果证明通过检查的是合并后的 `develop` 提交，而不只是 PR head。

## 4. 已发布数据库状态

- `V1__baseline_schema.sql`：保持已发布字节不变；
- `V2__stage1_business_semantics_and_permissions.sql`：已发布；
- 空库路径：V1 + V2 安装成功；
- 存量路径：V1 baseline 后升级 V2 成功；
- V2 preflight、post-verify、负向 fixture 和 checksum Gate 均通过；
- 隔离恢复演练证明迁移前备份可恢复为 V1 状态。

V2 自 PR2 合并后进入 `PUBLISHED / IMMUTABLE` 状态。后续修正只能新增迁移，不能修改 V2 文件。

## 5. PR2 阶段 Gate

| Gate | 结果 |
|---|---|
| S1-A-001 空库安装与 V1→V2 升级 | PASS |
| S1-A-002 V1/V2 发布迁移不可变 | PASS |
| S1-R-001 唯一受理人字段 | CLOSED |
| S1-R-002 未知系统角色阻断 | CLOSED |
| S1-R-005 周复盘共享目标 | CLOSED |
| S1-R-011 迁移恢复路径 | CLOSED |

## 6. 范围声明

PR2 没有实现 `SystemRoleEnum`、`PermissionService`、任务分配业务、成员退出、周复盘隐私 VO 或前端能力。以上内容继续按阶段 1 顺序交付。

本记录只保存合并标识、Gate 结论和公开工作流链接，不保存凭据、备份正文或真实私人数据。

## 7. PR3 进入结论

PR2 的代码、迁移、恢复和合并后 CI 门禁全部通过。阶段 1 当前唯一 `in_progress` 主目标切换为 PR3：实现 `SystemRole` 与统一 `PermissionService`。
