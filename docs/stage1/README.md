# 阶段 1：业务语义与统一权限

状态：PR5 成员退出与移除实现完成；本地与受保护 CI 验收通过

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
12. [PR2 工作包 1：迁移输入与测试样本合同](database/pr2-work-package-1-input-and-fixture.md)
13. [PR2 工作包 1 本地验收记录](acceptance/pr2-work-package-1-acceptance-2026-08-24.md)
14. [PR2 工作包 2 V2 迁移实跑验收记录](acceptance/pr2-work-package-2-acceptance-2026-08-27.md)
15. [PR2 工作包 3：CI 数据库门禁本地验收记录](acceptance/pr2-work-package-3-acceptance-2026-08-27.md)
16. [PR3 权限核心本地验收记录](acceptance/pr3-permission-core-acceptance-2026-08-27.md)
17. [PR4 任务分配与历史审计本地验收记录](acceptance/pr4-task-assignment-acceptance-2026-08-27.md)
18. [PR5 成员退出与移除本地验收记录](acceptance/pr5-team-member-lifecycle-acceptance-2026-08-27.md)

## 3. 决策状态规则

- PR 评审期间，ADR 和阶段合同状态为 `PROPOSED` / `DRAFT`。
- PR1 合并到受保护分支后，ADR 状态视为 `ACCEPTED`，机器合同状态进入 `FROZEN`。
- 若合并前变更决策，必须同步修改需求、矩阵、数据字典、API 合同和机器合同。
- 若合并后变更决策，必须新增 ADR 或显式 supersede 原 ADR，不能静默改写历史结论。
- PR2 发布 `V2__*.sql` 后，V2 迁移文件不可修改；后续修正只能使用新版本迁移。

## 4. 实施顺序

```text
PR1 设计冻结
→ PR2 V2 迁移与数据库门禁
→ PR3 SystemRole 与 PermissionService
→ PR4 任务分配与历史
→ PR5 成员退出和移除
→ PR6 周复盘隐私模型
→ PR7 前端任务分配与复盘隐私
→ PR8 跨仓验收、证据、Tag 与 Release
```

每个时点只允许一个阶段 1 主目标处于 `in_progress`。阶段 1 不并行引入 Qdrant、RAG 或 Agent。

## 5. 权威来源

发生冲突时，按以下顺序处理：

1. 已合并的阶段 1 ADR；
2. 阶段 1 需求合同和权限矩阵；
3. V2 数据字典与 API 兼容合同；
4. 阶段 0 已发布 V1 结构及验收合同；
5. 当前代码实现；
6. 历史初始化 SQL 和旧接口文档。

当前代码不能覆盖已冻结的阶段 1 业务语义；代码与合同冲突时，应在对应实施 PR 中修改代码并补测试。

## 6. 范围边界

阶段 1 不包含：

- AI 模型协议升级和 `AiServiceImpl` 拆分；
- Embedding、Qdrant、知识索引、RAG 或 Agent；
- Redis 权限缓存；
- 激活 V1 中尚未使用的租户 RBAC 五表；
- 全量 REST 路径重构；
- 生产数据库迁移、部署或正式凭据操作。

现有 AI 接口读取项目、任务或复盘时必须接入统一权限，但不在阶段 1 重构模型调用管线。
