# ADR-001：任务创建人与受理人语义

状态：`ACCEPTED`

日期：2026-08-23

## 背景

当前 Java `Task.userId` 被用于创建、列表、更新、删除、统计和 AI 资源过滤。V1 数据库同时存在未被 Java 映射的 `task.assignee_id`。若 V2 直接新增第二个受理人字段，会形成两个含义相同但状态可能不同的列。

## 决策

1. `task.user_id` 的物理列名在阶段 1 保持不变，业务语义冻结为“创建人”。
2. Java 实体使用 `createdByUserId` 并通过 `@TableField("user_id")` 映射；外部 VO 不再把该字段命名为 `userId`。
3. V2 将 `assignee_id` 重命名为 `assignee_user_id`，不新增并存列。
4. V2 增加 `assigned_by_user_id`、`assigned_at`，表示最近一次实际分配。
5. 所有实际受理人变化写入不可变 `task_assignment_log`。
6. 普通任务更新接口不接受受理人变更；分配使用独立命令和权限动作。
7. `expectedAssigneeUserId` 用于并发保护；实现可选择行锁或带旧值条件的 CAS。

## 存量回填

```text
assignee_user_id    = COALESCE(旧 assignee_id, user_id)
assigned_by_user_id = user_id
assigned_at         = create_time
```

该规则表示没有显式历史受理人的任务在创建时由创建人负责，具有确定性且可验证。

## 备选方案

### 新增 `assignee_user_id` 并保留 `assignee_id`

拒绝。双列会产生同步、回填、查询和索引歧义。

### 将 `task.user_id` 物理重命名为 `created_by_user_id`

阶段 1 暂不采用。该列被现有 Java、SQL、索引、AI 和统计广泛使用，物理重命名会显著扩大 V2 切换面。先在 Java/API 语义层消除歧义，后续如需物理收缩再单独迁移。

### 只保留当前受理人，不记录历史

拒绝。无法解释成员退出、任务转派和后续 Agent 分析中的责任变化。

## 影响

- 任务查询必须明确按创建人还是受理人过滤。
- 周完成统计改为受理人维度。
- AI 场景不能继续假设 `task.user_id` 是当前任务执行者。
- V2 必须重建或重命名 `idx_task_assignee_id`。
- PR4 必须增加并发转派和日志原子性测试。

## 验收

- 数据库只有一个当前受理人列；
- 非法受理人不能落库；
- 创建人不因转派变化；
- 实际受理人变化与日志一一对应；
- 并发转派不会静默覆盖。
