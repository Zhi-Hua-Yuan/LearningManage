# PR5 成员退出与移除验收记录

日期：2026-08-28
范围：成员主动退出、成员移除、未完成任务原子解除分配

## 1. 交付内容

- 新增 `POST /api/team/{teamId}/leave`：有效 `MEMBER`、`ADMIN` 可主动退出；`OWNER` 被拒绝。
- 新增 `POST /api/team/{teamId}/member/remove`：`OWNER` 可移除 `ADMIN`/`MEMBER`，`ADMIN` 仅可移除 `MEMBER`。
- 退出和移除均在单一事务中完成：锁定成员关系、锁定目标团队项目中的未完成任务、解除受理人、写 `UNASSIGN` 审计日志，最后逻辑删除成员关系。
- 仅处理状态为 `0` 的未完成任务；状态 `1/2/3` 的已完成任务保留原受理人历史。
- 用户重新加入团队只恢复成员关系，不自动恢复历史任务分配。

## 2. 本地验证

| 验证项 | 结果 |
|---|---|
| PR5 定向单元测试 | PASS：5 项通过，失败 0、错误 0 |
| Maven 全量测试 | PASS：125 项通过，失败 0、错误 0、跳过 0 |
| 编译 | PASS |
| `git diff --check` | PASS |

覆盖场景：

1. MEMBER 主动退出并解除未完成任务；
2. OWNER 主动退出被拒绝；
3. ADMIN 移除 MEMBER 并解除未完成任务；
4. ADMIN 移除 ADMIN 被拒绝。

## 3. 事务与并发边界

- 服务方法使用 `@Transactional(rollbackFor = Exception.class)`，成员关系、任务更新和审计日志任一步失败均回滚。
- 任务查询使用 `FOR UPDATE`，更新带当前受理人、状态和逻辑删除条件，避免覆盖并发变更。
- 当前测试为服务层事务编排单元测试；真实 MySQL 并发压力与受保护 CI 证据留待阶段验收门禁补充。

## 4. 受保护 CI 实跑

PR：[Stage 1: permissions and task assignment audit #40](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/40)

Workflow run：[Backend CI 33090864163](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33090864163)

| Gate | 结果 |
|---|---|
| Guard and migration immutability | PASS |
| Maven verification and tested artifact | PASS（125 项测试） |
| Flyway empty database gate | PASS |
| Flyway existing database gate | PASS |
| Docker runtime and migration gate | PASS |

本次运行仅使用 CI 临时 MySQL 容器和受保护凭据，未接触生产数据库。

## 5. 未在本记录中宣称的内容

任务列表作用域查询、TEAM 周复盘发布/共享、AI 批量资源权限校验仍属于后续 PR6/PR7 工作包。
