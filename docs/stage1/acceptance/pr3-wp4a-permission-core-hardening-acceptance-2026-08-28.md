# PR3 WP4-A：权限内核加固验收记录

状态：`PASS`

日期：2026-08-28

## 1. 验收范围

本工作包在 WP3 单条权限判定骨架之上，补齐 actor 生命周期校验、团队 OWNER 一致性和周复盘作者生命周期语义。WP4-A 不接入 Controller/业务 Service，不实现批量权限 API，不新增 Flyway migration，也不执行真实 MySQL 集成测试。

输入合同：

- [ADR-002：周复盘可见性与团队共享](../architecture/ADR-002-weekly-review-visibility.md)
- [ADR-003：系统角色、团队角色与租户 RBAC 边界](../architecture/ADR-003-role-boundaries.md)
- [ADR-004：统一 PermissionService](../architecture/ADR-004-permission-service.md)
- [阶段 1 权限矩阵](../authorization/permission-matrix.md)
- [PR3 WP3 验收记录](pr3-wp3-permission-query-single-decision-acceptance-2026-08-28.md)

## 2. 实现结果

| 项目 | 结果 |
|---|---|
| actor 最小事实查询 | `ActorPermissionRow` 和 `selectActorPermissionRow` 只读取用户 ID、系统角色和逻辑删除标记 |
| actor 有效性 | 未登录、非正 ID、用户不存在、用户已删除均拒绝；未知或非规范系统角色拒绝；`SYSTEM_ADMIN` 不获得资源旁路 |
| OWNER 一致性 | `team_member.role=OWNER` 必须与 `team.owner_id` 双向一致；异常或缺失事实失败关闭 |
| 周复盘作者生命周期 | 作者完整查看、更新、删除不再依赖团队或成员关系有效；非作者共享摘要仍要求有效团队和成员关系 |
| 私密字段边界 | 权限查询不读取复盘正文、共享摘要、密码或 Token |
| 数据库迁移 | 无新增或修改 Flyway migration |

## 3. 测试证据

定向测试：

```text
.\mvnw.cmd '-Dtest=PermissionQueryMapperContractTest,PermissionServiceImplTest,PermissionModelContractTest,SystemRoleEnumTest' test
```

结果：Tests run 60，Failures 0，Errors 0，Skipped 0，`BUILD SUCCESS`。

全量测试：

```text
.\mvnw.cmd test
```

结果：Tests run 171，Failures 0，Errors 0，Skipped 0，`BUILD SUCCESS`；CI 测试门槛已同步为 `171`，未降低门槛。

新增回归覆盖：

- 18 个公开 `require*` 入口在 actor 已删除时均先返回 `40100`，不查询资源事实；
- `null`、小写旧值和未知 `user_role` 均不进入资源授权；
- OWNER 与 `team.owner_id` 不一致时，项目/任务/团队成员权限均拒绝；
- 团队删除或作者退出后，作者仍保有自己的完整周复盘视图；
- 团队删除或成员失效后，非作者不能读取 TEAM 摘要；
- `PRIVATE + team_id` 等结构冲突失败关闭。

## 4. 查询与兼容性边界

- 单条权限调用最多执行一次 actor 查询和一次资源查询；
- Mapper 仍只返回原始事实，授权决策仍集中在 `PermissionServiceImpl`；
- 不新增 endpoint，不改变 `PermissionService` 对外方法签名；
- 错误码保持 `40100`、`40300`、`40000` 的既有语义；
- `SYSTEM_ADMIN` 仅作为系统角色事实参与验证，不自动绕过资源关系或私人复盘边界。

## 5. 未实现范围

- 真实 MySQL Mapper 集成测试；
- 批量权限 API 和 100 资源常数级查询 Gate；
- Controller、业务 Service、Stats、AI 入口接入；
- 并发重新鉴权、权限缓存和审计事件。

## 6. 结论

WP4-A 的 actor 生命周期校验、OWNER 一致性、周复盘作者生命周期、定向回归、全量回归、差异检查及迁移不可变性证据均已通过，本地验收为 `PASS`。下一步可进入 WP4-B 真实数据库 Mapper 集成测试。
