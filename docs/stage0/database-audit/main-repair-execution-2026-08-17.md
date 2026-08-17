# 阶段 B 主库孤儿数据逻辑删除执行记录

执行日期：2026-08-17 Asia/Shanghai  
目标数据库：`learning_manage`  
执行状态：成功提交并完成验证  
授权：用户已明确确认进入阶段 B

## 1. 执行前门禁

- 新主库完整备份 SHA-256：`7677A5450CBB945C2B2669C80944EEF9E3B34971B71E20B2C7AC641028FC78AF`。
- 新主库结构备份 SHA-256：`B2E4A0E880626D3A0AADF29C43363E0B9B5810FA66903FCC1D74CCE943640650`。
- 备份已恢复到隔离库并通过全部 20 张表的 `mysqlcheck`。
- 应用端口 `8123` 在执行前未监听，未发现本地应用写入竞争。
- 执行前主库候选：活跃孤儿项目 1、活跃孤儿任务 2。
- 目标项目和任务指纹、旧值、创建时间及更新时间与审计基线一致。

## 2. 执行内容

执行脚本：`sql/repair/stage0/main/01_apply_orphan_logical_delete_main.sql`

脚本包含：

- 数据库名称保护：必须是 `learning_manage`。
- 显式写入授权保护：`@stage0_main_write_authorized=1`。
- 候选数量保护：项目必须为 1、任务必须为 2。
- 事务保护和影响行数保护：项目必须影响 1 行、任务必须影响 2 行。
- 仅逻辑删除，不执行物理 `DELETE`。

执行结果：

- 修复时间：`2026-08-17 15:16:49`。
- 项目影响行数：1。
- 任务影响行数：2。
- 事务提交成功。

## 3. 主库验证

验证脚本：`sql/repair/stage0/main/02_verify_orphan_logical_delete_main.sql`

| 检查项 | 结果 |
|---|---:|
| 活跃孤儿项目 | 0 |
| 活跃孤儿任务 | 0 |
| 活跃孤儿里程碑 | 0 |
| 活跃孤儿团队成员 | 0 |
| 目标项目已逻辑删除 | 1 |
| 两个目标任务已逻辑删除 | 2 |
| 原本已删除任务保持不变 | 1 |
| 非法逻辑删除标志 | 0 |
| 非法任务状态 | 0 |
| 非法任务优先级 | 0 |

主库物理行数保持不变：用户 28、项目 35、里程碑 37、任务 122、周复盘 3。

修复后完整只读审计结果：

- 所有关键表行数与执行前一致。
- 所有重复键检查为 0。
- 所有状态和范围合法性检查为 0。
- `project.user_id`、`task.project_id`、`milestone.project_id`、团队成员双向关联孤儿数均为 0。

修复后执行 `mysqlcheck --check learning_manage`，全部表检查通过。

## 4. 回归验证

后端回归测试：56 个测试全部通过，失败 0、错误 0、跳过 0。

本次没有在主库启动应用执行写入型 API 冒烟，以避免额外测试数据进入主库；应用 API 冒烟已在隔离恢复库完成，主库本次完成了数据库级完整审计、表检查和后端回归测试。

## 5. 证据文件

证据均保存在忽略目录：

```text
D:\ajavacode\LearningManage\.codex-tmp\stage0-main-repair-20260817-150213\main-apply-20260817.tsv
D:\ajavacode\LearningManage\.codex-tmp\stage0-main-repair-20260817-150213\main-verify-20260817.tsv
D:\ajavacode\LearningManage\.codex-tmp\stage0-main-repair-20260817-150213\main-postrepair-audit.tsv
D:\ajavacode\LearningManage\.codex-tmp\stage0-main-repair-20260817-150213\main-mysqlcheck.txt
```

| 证据 | SHA-256 |
|---|---|
| `main-apply-20260817.tsv` | `A493E434A3FFA2A1197CFC55282233BDB2FAE44460E1C8D26A17465086EFC40D` |
| `main-verify-20260817.tsv` | `998FED0A2BB5A560A7C44B9810C39510C7399ADAD231DA7FA96002FF12503A0A` |
| `main-postrepair-audit.tsv` | `C042F3881FE635C4FBBC728B9EDEF511910C9CAC96AB2DC063B77359905C69D5` |
| `main-mysqlcheck.txt` | `735C79785E8C7EA5C42EFB3CD3EE3807B3A507C6A5FE77CA72795D88C63048DC` |

## 6. 结论

阶段 B 主库修复已成功完成。目标孤儿数据已从活跃业务数据中逻辑隔离，主库结构、物理行数、RBAC 数据及其他稳定质量规则未受到非预期影响。

回滚脚本仍保留在 `sql/repair/stage0/main/03_rollback_orphan_logical_delete_main.sql`，本次未执行回滚，因为修复和验证均符合预期。
