# 阶段 0 主库修复前置准备记录

执行日期：2026-08-17 Asia/Shanghai  
目标主库：`learning_manage`  
阶段状态：阶段 A 通过；主库未执行任何写操作

## 1. 新备份

备份目录：

```text
D:\ajavacode\LearningManage\.codex-tmp\stage0-main-repair-20260817-150213
```

| 文件 | 字节数 | SHA-256 |
|---|---:|---|
| `learning_manage-full.sql` | 133090 | `7677A5450CBB945C2B2669C80944EEF9E3B34971B71E20B2C7AC641028FC78AF` |
| `learning_manage-schema.sql` | 27971 | `B2E4A0E880626D3A0AADF29C43363E0B9B5810FA66903FCC1D74CCE943640650` |

备份使用 MySQL 8.0.41、单事务、包含触发器、事件、存储过程和函数选项生成。

## 2. 恢复验证

备份已恢复到新隔离库：

```text
learning_manage_stage0a_restore_20260817_150213
```

主库与恢复库关键表行数一致：

| 表 | 主库 | 恢复库 |
|---|---:|---:|
| `user` | 28 | 28 |
| `team` | 4 | 4 |
| `team_member` | 11 | 11 |
| `project` | 35 | 35 |
| `milestone` | 37 | 37 |
| `task` | 122 | 122 |
| `weekly_review` | 3 | 3 |
| `ai_call_log` | 3 | 3 |
| `ai_draft` | 5 | 5 |

`mysqlcheck --check learning_manage_stage0a_restore_20260817_150213` 检查全部 20 张表均返回 `OK`。

## 3. 主库只读预检查

完整只读审计脚本：`sql/audit/stage0_readonly_audit.sql`。主库和恢复库均执行成功。

主库结果：

- 数据库版本：MySQL 8.0.41。
- RBAC：`tenant=1`、`role=3`、`permission=15`、`role_permission=27`、`user_role=0`。
- 活跃孤儿项目：1。
- 活跃孤儿任务：2。
- 活跃孤儿里程碑：0。
- 活跃孤儿团队成员：0。
- 非法状态、非法优先级、重复账号、重复邀请码和重复周复盘键：均为 0。
- 目标项目和两个目标任务的指纹、旧状态、创建时间和更新时间与恢复库演练基线一致。

原始只读证据保存在忽略目录：

```text
D:\ajavacode\LearningManage\.codex-tmp\stage0-main-repair-20260817-150213\main-readonly-preflight.tsv
D:\ajavacode\LearningManage\.codex-tmp\stage0-main-repair-20260817-150213\restore-readonly-preflight.tsv
```

主库证据 SHA-256：`1E0543D7E11C87C2165E79FFA8A6F7F61D88B8330BBAFFCE42B776B212CC9CA0`。  
恢复库证据 SHA-256：`51DDDAA4F35ED230B443F63FC423B6F25B699BB3EE358E3DA11099373A147F46`。

## 4. 主库专用脚本

以下脚本已生成，但尚未执行：

- `sql/repair/stage0/main/01_apply_orphan_logical_delete_main.sql`
- `sql/repair/stage0/main/02_verify_orphan_logical_delete_main.sql`
- `sql/repair/stage0/main/03_rollback_orphan_logical_delete_main.sql`

修复和回滚脚本都要求调用方显式设置 `@stage0_main_write_authorized=1`，默认会中止；同时包含数据库名称、候选数量、旧值和影响行数保护。

## 5. 阶段 A 结论

阶段 A 通过：备份可恢复、恢复库表检查通过、主库只读预检查与演练基线一致、主库没有写入。

下一步是人工审阅主库专用脚本，并在确认维护窗口和主库写入授权后，才进入阶段 B。当前不能直接执行主库修复脚本。
