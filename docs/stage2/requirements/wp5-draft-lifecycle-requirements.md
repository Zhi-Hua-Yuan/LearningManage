# WP5 需求与实施清单：AI 草稿生命周期与写入安全

状态：`PASS`
冻结日期：2026-09-04
基线：`stage2-wp4-v1.0.0` / `c20cfa7`

## 冻结需求

| ID | 要求 | 实现 | 状态 |
|---|---|---|---|
| WP5-F-001 | 同一用户的同一草稿只产生一次正式写入，与 operationId 无关 | 草稿行锁、`(user_id,draft_id)` 日志查询和唯一约束 | DONE |
| WP5-F-002 | 业务写入、确认日志和确认终态处于同一事务 | `AiDraftConfirmationService` + `TransactionTemplate` | DONE |
| WP5-F-003 | 场景与 Schema 版本通过注册 Handler 路由 | `AiDraftHandlerRegistry` | DONE |
| WP5-F-004 | 确认时重新验证登录、资源、权限和当前业务状态 | 两个场景 Handler 与重排确认回调 | DONE |
| WP5-F-005 | 确认、取消、过期竞争只能产生一个终态 | 行锁、PREVIEW 条件 CAS、终态重读 | DONE |
| WP5-F-006 | 清单重排保持原 API，但拒绝失效任务快照和部分更新 | 操作行锁、任务旧值/更新时间 CAS、整体事务回滚 | DONE |
| WP5-F-007 | 草稿和确认日志贯穿模型调用 Trace | 创建命令、草稿、确认日志和重排操作 Trace | DONE |
| WP5-F-008 | 不修改 V1/V2/V3，不新增 V4，不改变公共成功响应 | 迁移校验和、架构测试与 API 候选门禁 | DONE |

## 实施任务

- [x] WP5-A：冻结状态机、事务、Handler、幂等和兼容合同。
- [x] WP5-B：实现通用确认内核、行锁、CAS、Clock 和草稿创建命令。
- [x] WP5-C：迁移任务拆解正式写入到注册 Handler。
- [x] WP5-D：迁移周复盘润色写入，并在锁定资源后重验权限。
- [x] WP5-E：实现清单重排行锁、过期、任务快照 CAS 和原子回滚。
- [x] WP5-F：加入单元、架构、Spring 上下文及隔离 MySQL 并发测试。
- [x] WP5-F：候选 CI、跨仓 API、Docker 和 Manifest 封存。
- [x] WP5-F：Annotated Tag 和 GitHub Release 封存（`stage2-wp5-v1.0.0`）。

最终候选基于受保护 `develop` 的后端 `80f357b357bf98e18f4bdd1e53392aede002e8cf`
与前端 `2ef907f292fbbacecf8a68f7d24c4701a555aa8a`，跨仓 run `33889047346`
全部通过后关闭 `S2-A-009` 与 `S2-R-004`。
