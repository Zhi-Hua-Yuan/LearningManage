# 阶段 1：业务语义与统一权限

状态：PR1～PR5 已合并并完成合同收口；PR5 的成员终止、事务回滚、任务变更竞争和最终并发门禁已完成验收；PR6/WP6-A 设计已合并，WP6-C1 关联 Mapper 已实现

建立日期：2026-08-23

适用仓库：`Zhi-Hua-Yuan/LearningManage`、`Zhi-Hua-Yuan/learning-manage-frontend`

## 1. 阶段目标

阶段 1 将当前以 `userId` 直接过滤资源的单用户模型，升级为具有明确创建人、受理人、团队角色、资源权限和周复盘隐私边界的多人协作底座。

目标业务链路：

```text
团队负责人创建项目和任务
→ 将任务分配给有效团队成员
→ 成员在权限范围内推进任务
→ 分配、转派和解除分配全程留痕
→ 成员退出时解除其未完成任务
→ 用户保存私人周复盘
→ 仅向指定团队共享单独填写的摘要
→ 所有普通接口、统计接口和 AI 入口使用统一权限判断
```

阶段 1 是后续 AI 调用治理、知识索引、权限感知 RAG 和 Agent Tool 二次鉴权的前置依赖。

## 2. PR1 范围

PR1 只冻结设计输入，不修改 Java、前端、数据库迁移、CI、运行配置或部署环境：

1. [阶段 1 需求合同](requirements/stage1-requirements-contract.md)
2. [权限矩阵](authorization/permission-matrix.md)
3. [ADR-001：任务创建人与受理人语义](architecture/ADR-001-task-identity-and-assignment.md)
4. [ADR-002：周复盘可见性与团队共享](architecture/ADR-002-weekly-review-visibility.md)
5. [ADR-003：系统角色、团队角色与租户 RBAC 边界](architecture/ADR-003-role-boundaries.md)
6. [ADR-004：统一 PermissionService](architecture/ADR-004-permission-service.md)
7. [V2 数据字典与迁移合同](database/v2-data-dictionary.md)
8. [API 兼容合同](api/api-compatibility-contract.md)
9. [阶段 1 风险登记表](risk/stage1-risk-register.md)
10. [机器可读验收合同](acceptance/stage1-acceptance-contract.json)
11. [PR1 设计验收记录](acceptance/pr1-design-acceptance-2026-08-23.md)

## 3. PR2 已交付范围

PR2 只交付 V2 数据语义及数据库发布门禁，不引入 `PermissionService`、任务业务接口、周复盘业务实现或前端改动：

1. V2 正式 Flyway migration、25 项 preflight 与 12 项 post-verify；
2. 冻结 V1→V2 fixture、预期对账与 3 个负向 preflight 样本；
3. 空库安装、legacy 升级、重复 migrate、checksum 和 Flyway history Gate；
4. [V2 备份与恢复运行手册](database/v2-recovery-runbook.md)及隔离恢复演练 Gate；
5. 注册默认角色写入从旧值 `user` 兼容为 V2 允许值 `USER`；
6. backend CI 与 release gate 接线及 PR2 验收证据。

PR3 才实现 `SystemRole` 和统一 `PermissionService`。综合实现分支或包含 PR3～PR6 代码的 PR 不得作为 PR2 合并。

PR2 已通过受保护 PR [#41](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/41) 合并到 `develop`：

- 合并时间：2026-08-28 14:22:22（Asia/Shanghai）；
- Merge commit：`e6189a40fab25079105c8f86734116988fc45b47`；
- 合并后 Backend CI：`33147698207`，全部必需 Job 通过；
- V2 状态：`PUBLISHED / IMMUTABLE`。

完整证据见 [PR2 合并收口记录](acceptance/pr2-merge-closure-2026-08-28.md)。

当前 PR3 工作包证据：

- [WP1：SystemRole](acceptance/pr3-wp1-system-role-acceptance-2026-08-28.md)
- [WP2：权限基础类型](acceptance/pr3-wp2-permission-foundation-acceptance-2026-08-28.md)
- [WP3：权限查询与单条判定](acceptance/pr3-wp3-permission-query-single-decision-acceptance-2026-08-28.md)
- [WP4-A：权限内核加固](acceptance/pr3-wp4a-permission-core-hardening-acceptance-2026-08-28.md)
- [WP4-B：PermissionQueryMapper MySQL 集成](acceptance/pr3-wp4b-permission-mapper-mysql-integration-acceptance-2026-08-28.md)
- [WP4-C：批量权限与 N+1 治理](acceptance/pr3-wp4c-permission-batch-n-plus-one-acceptance-2026-08-28.md)
- [WP4-D：业务 Service、Stats 与 AI 权限接入](acceptance/pr3-wp4d-business-service-permission-integration-acceptance-2026-08-28.md)
- [PR3：最终合同验收](acceptance/pr3-final-acceptance-2026-08-28.md)
- [PR3：合并收口](acceptance/pr3-merge-closure-2026-08-28.md)

当前 PR4 工作包证据：

- [WP4-A：任务身份模型](acceptance/pr4-wp4a-task-identity-acceptance-2026-08-28.md)
- [WP4-B：统一创建与初始分配](acceptance/pr4-wp4b-initial-assignment-acceptance-2026-08-28.md)
- [WP4-C：任务负责人变更与并发安全](acceptance/pr4-wp4c-task-assignment-acceptance-2026-08-29.md)
- [WP4-D1：负责人历史查询契约冻结](acceptance/pr4-wp4d1-assignment-history-contract-acceptance-2026-08-29.md)
- [WP4-D2-A：负责人历史分页 Mapper](acceptance/pr4-wp4d2a-assignment-history-mapper-acceptance-2026-08-29.md)
- [WP4-D2-B：负责人历史查询 Service](acceptance/pr4-wp4d2b-assignment-history-service-acceptance-2026-08-29.md)
- [WP4-D2-C：负责人历史查询 Controller/API](acceptance/pr4-wp4d2c-assignment-history-controller-acceptance-2026-08-29.md)
- [WP4-D2-E：负责人 CAS、事务与审计对账验收](acceptance/pr4-wp4d2e-assignment-consistency-acceptance-2026-08-29.md)
- [PR4：最终合同验收](acceptance/pr4-final-acceptance-2026-08-29.md)
- [PR4：合并收口](acceptance/pr4-merge-closure-2026-08-29.md)

当前 PR5 工作包设计证据：

- [WP5-A：成员关系终止合同与并发设计](acceptance/pr5-wp5a-membership-termination-design-acceptance-2026-08-29.md)
- [WP5-D1：负责人资格锁协议开发记录](acceptance/pr5-wp5d1-assignee-lock-development-record-2026-08-29.md)
- [WP5-D2：任务写路径锁顺序开发记录](acceptance/pr5-wp5d2-task-mutation-lock-order-development-record-2026-08-29.md)
- [WP5-D3：completed → TODO 负责人资格保护开发记录](acceptance/pr5-wp5d3-task-reopen-assignee-guard-development-record-2026-08-29.md)
- [WP5-D4：任务变更与成员终止并发测试开发记录](acceptance/pr5-wp5d4-task-membership-concurrency-development-record-2026-08-29.md)
- [WP5-E：成员终止事务回滚与对账开发记录](acceptance/pr5-wp5e-membership-termination-transaction-reconciliation-development-record-2026-08-29.md)
- [WP5-F：最终并发门禁开发记录](acceptance/pr5-wp5f-final-concurrency-gate-development-record-2026-08-29.md)
- [WP5-F：最终并发门禁验收记录](acceptance/pr5-wp5f-final-concurrency-gate-acceptance-2026-08-30.md)
- [PR5：最终合同验收](acceptance/pr5-final-acceptance-2026-08-30.md)
- [PR5：合并收口](acceptance/pr5-merge-closure-2026-08-30.md)
- [PR5：团队成员关系终止合同](api/pr5-team-membership-termination-contract.md)
- [ADR-005：成员关系终止与任务分配并发协议](architecture/ADR-005-membership-termination-concurrency.md)

当前 PR5 WP5-B 实现证据：

- [WP5-B：成员终止 Mapper 实现验收记录](acceptance/pr5-wp5b-membership-termination-mapper-acceptance-2026-08-29.md)

当前 PR5 WP5-C 实现证据：

- [WP5-C：成员终止 Service/API 实现验收记录](acceptance/pr5-wp5c-membership-termination-service-acceptance-2026-08-29.md)

当前 PR6 设计证据：

- [WP6-A：周复盘隐私与统计/AI 授权设计计划](acceptance/pr6-wp6a-weekly-review-privacy-design-plan-2026-08-30.md)

当前 PR6 实现证据：

- [WP6-B：周复盘隐私读写开发记录](acceptance/pr6-wp6b-weekly-review-privacy-development-record-2026-08-30.md)
- [WP6-C1：WeeklyReviewTask Mapper 开发记录](acceptance/pr6-wp6c-c1-weekly-review-task-mapper-development-record-2026-08-31.md)

## 4. 决策状态规则

- PR 评审期间，ADR 和阶段合同状态为 `PROPOSED` / `DRAFT`。
- PR1 合并到受保护分支后，ADR 状态视为 `ACCEPTED`，机器合同状态进入 `FROZEN`。
- 若合并前变更决策，必须同步修改需求、矩阵、数据字典、API 合同和机器合同。
- 若合并后变更决策，必须新增 ADR 或显式 supersede 原 ADR，不能静默改写历史结论。
- PR2 发布 `V2__*.sql` 后，V2 迁移文件不可修改；后续修正只能使用新版本迁移。

## 5. 实施顺序

```text
PR1 设计冻结                   completed
→ PR2 V2 迁移与数据库门禁      completed
→ PR3 SystemRole 与 PermissionService  completed
→ PR4 任务分配与历史            completed（PR #49 已合并）
→ PR5 成员退出和移除            completed（WP5-A/B/C/D/E/F accepted；S1-A-004 PASS；S1-R-003 CLOSED；ADR-005 ACCEPTED）
→ PR6 周复盘隐私模型            in_progress（WP6-A 设计冻结候选）
→ PR7 前端任务分配与复盘隐私    pending
→ PR8 跨仓验收、证据、Tag 与 Release  pending
```

每个时点只允许一个阶段 1 主目标处于 `in_progress`。阶段 1 不并行引入 Qdrant、RAG 或 Agent。

## 6. 权威来源

发生冲突时，按以下顺序处理：

1. 已合并的阶段 1 ADR；
2. 阶段 1 需求合同和权限矩阵；
3. V2 数据字典与 API 兼容合同；
4. 阶段 0 已发布 V1 结构及验收合同；
5. 当前代码实现；
6. 历史初始化 SQL 和旧接口文档。

当前代码不能覆盖已冻结的阶段 1 业务语义；代码与合同冲突时，应在对应实施 PR 中修改代码并补测试。

## 7. 范围边界

阶段 1 不包含：

- AI 模型协议升级和 `AiServiceImpl` 拆分；
- Embedding、Qdrant、知识索引、RAG 或 Agent；
- Redis 权限缓存；
- 激活 V1 中尚未使用的租户 RBAC 五表；
- 全量 REST 路径重构；
- 生产数据库迁移、部署或正式凭据操作。

现有 AI 接口读取项目、任务或复盘时必须接入统一权限，但不在阶段 1 重构模型调用管线。
