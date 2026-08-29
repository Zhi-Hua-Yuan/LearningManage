# PR5 WP5-C：成员终止 Service/API 实现验收记录

日期：2026-08-29（Asia/Shanghai）
状态：`DEVELOPMENT_ALLOWED / ACCEPTANCE_PENDING`

## 1. 实施范围

WP5-C 基于 WP5-B Mapper 原语实现两条冻结业务入口：

- `POST /api/team/{teamId}/leave`：当前登录用户主动退出；
- `POST /api/team/member/remove`：OWNER/ADMIN 移除成员。

两条入口均由独立 public `@Transactional(rollbackFor = Exception.class)` 方法承载，事务顺序固定为：

```text
锁定有效 team_member 行
→ 使用锁内成员快照进行二次权限判断
→ 锁定目标成员的未完成受理任务
→ 批量解除任务并写入 MEMBER_LEFT / MEMBER_REMOVED 审计
→ 以关系 ID、团队、用户、角色和 active 状态执行 CAS 失效
```

返回 `teamId`、`memberUserId`、`action`、`unassignedTaskCount`、`terminatedAt`；任务更新数、审计日志数和返回计数保持一致，操作时间在任务、日志和成员失效记录之间复用。

## 2. 权限与安全边界

- 主动退出允许 ADMIN/MEMBER，OWNER 拒绝；
- OWNER 可移除 ADMIN/MEMBER，ADMIN 只能移除 MEMBER；
- 禁止自移除、移除 OWNER、重复操作已失效关系；
- 普通权限预检查失败时不触发成员锁、任务更新或审计写入；
- 锁内策略拒绝缺失、跨团队、逻辑删除、已失效或非法角色的成员快照；
- Forbidden 响应只返回统一 `40300`，不泄漏目标成员角色或状态。

WP5-C 不包含普通分配、初始分配、completed→TODO、前端、Flyway、AI 或完整并发业务门禁；后者留给 WP5-E/F。

## 3. 测试证据

### 已通过（无需数据库）

执行：

```text
.\mvnw.cmd test "-Dtest=TeamMembershipTerminationPolicyTest,TeamMembershipTerminationServiceImplTest,TeamMembershipTerminationControllerTest"
```

结果：`15 tests / 0 failures / 0 errors`。

覆盖内容：

- OWNER/ADMIN/MEMBER 权限矩阵与主动退出/管理员移除差异；
- 缺失、错误团队、失效成员和重复操作拒绝；
- 锁内任务计数、批量更新、审计字段和 CAS 调用顺序；
- 任务 CAS 条数不一致、审计写入失败时停止后续状态变更；
- 独立 public 事务方法及 `rollbackFor = Exception.class` 反射契约；
- API 路由、响应字段、参数绑定、未登录和统一 Forbidden 响应。

### 待完成（真实 MySQL）

`TeamMembershipTerminationTransactionMySqlTest` 使用 V2 隔离 fixture，验证真实事务中审计异常会回滚任务解除和成员失效。该测试与 WP5-B 共用 `application-test.yml` 的 `${TEST_DB_USERNAME}` / `${TEST_DB_PASSWORD}`；凭据未配置前不得宣称 WP5-C 或 WP5-B 的真实 MySQL 门禁通过。

## 4. 当前结论

```text
WP5-A：accepted
WP5-B：implemented，MYSQL_GATE_BLOCKED
WP5-C：development allowed，acceptance pending
PR5：in_progress
```

补齐隔离 MySQL 凭据后，应先执行 WP5-B Mapper/锁等待测试，再执行 WP5-C 事务回滚测试和完整 Maven 回归；WP5-E/F 完成后才能进行 PR5 最终合并验收。
