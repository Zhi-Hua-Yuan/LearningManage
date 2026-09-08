# 已确认缺陷

> 本文件保留修复前的复现事实、影响和根因。7 项缺陷的当前状态与回归证据见
> [修复结果](resolution.md)。

## BUG-AUDIT-001 — P1 — 任务创建与成员终止稳定死锁

状态：`CONFIRMED / RELEASE_BLOCKER`

影响：团队成员退出或被移除时，如果 OWNER 同时创建并指派任务给该成员，成员终止
请求会抛出 `DeadlockLoserDataAccessException`。接口可能返回 500，违反“并发操作只有
约定业务结果、不能泄漏数据库死锁”的验收要求。

证据：

- 全量 MySQL 分区：61 项中 2 项 Error。
- `TaskMembershipTerminationConcurrencyMySqlTest` 单独重复 3 次，3/3 失败。
- `TeamMembershipTerminationConcurrencyMySqlTest` 单独重复 3 次，3/3 失败。
- 两条失败分别为 `createAssignedTaskVsMemberLeaveHasOnlyContractualOutcomes` 和
  `createAssignedTaskVsMemberRemoveNeverLeavesInactiveAssignee`。
- MySQL 报错发生在 `TeamMemberMapper.selectActiveMembersForUpdate(... FOR UPDATE)`。

初步根因：

- 任务创建先通过 `TaskAssigneePolicyImpl` 锁 `team_member`，随后
  `BusinessDataVersionServiceImpl` 更新项目和团队版本，最后尝试取得 `team` 行锁。
- 成员退出/移除先在 `TeamMembershipTerminationServiceImpl` 锁 `team`，再锁
  `team_member`。
- 两条路径形成 `team_member → team` 与 `team → team_member` 的反向锁序。

建议：统一团队写路径锁序为 `team → team_member → task`，并评估数据库死锁的有限事务
重试；不能仅让测试接受死锁。修复后两个并发类必须连续多轮 100% 通过。

## BUG-AUDIT-002 — P1 — 业务异常 HTTP 状态契约失效

状态：`CONFIRMED / RELEASE_BLOCKER`

实际结果：

| 场景 | 业务码 | 约定 HTTP | 实际 HTTP |
|---|---:|---:|---:|
| 未登录访问 `/user/me` | 40100 | 401 | 200 |
| 注册参数错误 | 40000 | 400 | 200 |
| 普通用户访问管理员接口 | 40300 | 403 | 200 |
| AI Stub 停止后请求拆解 | 30001 | 503 | 200 |

根因：`GlobalExceptionHandler.handleBusinessException` 固定使用
`@ResponseStatus(HttpStatus.OK)`，没有根据错误类型映射状态码。现有部分 Controller 测试
还把错误响应的 HTTP 200 固化成了断言。

影响：反向代理、浏览器外客户端、重试器、监控与安全审计无法依赖 HTTP 语义；503 被
当作成功还会阻止基础设施级降级与重试策略。

复现：前端 `e2e/api-semantics.spec.ts`。该用例暂用 `test.fail` 标记已知缺陷。

建议：集中建立 `ErrorCode → HttpStatus` 映射并保留现有 JSON envelope；同步修正
OpenAPI 和 Controller 测试。权限存在性隐藏策略可以继续返回 403，但 HTTP 层不能是 200。

## BUG-AUDIT-003 — P1 — 同账号并发注册返回系统错误

状态：`CONFIRMED / RELEASE_BLOCKER`

复现结果：

- 12 个并发请求：1 个成功，11 个 HTTP 500 / 业务码 50000。
- 后续 3 轮、每轮 8 个并发请求：每轮均为 1 个成功、7 个 500。
- 固定账号复核：1 个成功、7 个 500，数据库最终正确保留 1 行。

初步根因：`UserServiceImpl.register` 先执行 `selectCount`，完成 BCrypt 后再插入；并发请求
会同时通过查重，数据库唯一键拒绝后续插入，但代码没有捕获 `DuplicateKeyException`，
最终落入系统异常处理。

影响：用户双击、客户端重试或并发注册会产生可避免的系统错误和错误日志，核心注册
流程不满足幂等/冲突语义。

复现：前端 `e2e/known-bugs.spec.ts` 的 `BUG-AUDIT-003`。

建议：以唯一键为最终事实，捕获重复键并返回统一的账号已存在冲突；避免仅扩大前置查询
窗口或依赖前端禁用按钮。

## BUG-AUDIT-004 — P2 — 并发项目创建产生重复排序号

状态：`CONFIRMED`

复现结果：同一用户并发创建 20 个项目，20 个请求全部成功，但数据库只有 2 个不同的
`order_no`：`0` 有 10 条，`1` 有 10 条。

根因：`ProjectServiceImpl.getNextPersonalProjectOrderNo` 和
`getNextTeamProjectOrderNo` 使用未加锁的 `MAX/order by desc + 1` 模式，数据库也没有对应
的唯一约束。

影响：项目排序出现并列值，列表顺序取决于数据库非稳定返回顺序；个人项目和团队项目
都使用相同模式。

复现：前端 `e2e/known-bugs.spec.ts` 的 `BUG-AUDIT-004`。

建议：使用受锁定的父级序列、独立顺序分配器或带冲突重试的唯一约束；列表排序同时增加
稳定的 ID 次序作为防御性兜底。

## BUG-AUDIT-005 — P1 — 删除末尾里程碑后不能创建新里程碑

状态：`CONFIRMED / RELEASE_BLOCKER`

最小复现：

1. 新建项目。
2. 新建排序号 1、2 的两个里程碑。
3. 逻辑删除第二个里程碑。
4. 新建“替代阶段”。

实际：第四步返回 HTTP 500 / 业务码 50000。期望：成功创建新里程碑。

根因：`getNextOrderNo` 只读取未删除记录，所以再次生成排序号 2；但数据库唯一键
`uk_milestone_project_order_no(project_id, order_no)` 仍被逻辑删除行占用。

复现：前端 `e2e/known-bugs.spec.ts` 的 `BUG-AUDIT-005`。

建议：选择一个一致策略：排序号对历史行单调递增，或迁移唯一键使逻辑删除记录不占用
活动排序空间；同时捕获重复键，避免暴露 500。

## BUG-AUDIT-006 — P1 — 后端 CI 固定测试计数已过期

状态：`CONFIRMED / RELEASE_BLOCKER`

当前同一套 CI 选择器的新鲜结果：

- 非 MySQL `*Test`：809 项。
- MySQL `*MySqlTest`：61 项。
- 合计：870 项。

但 `.github/workflows/backend-ci.yml` 和 `.github/workflows/release-gate.yml` 仍固定
`CI_EXPECTED_TEST_COUNT: '864'`，`FlywayCiScriptStaticTest` 也把 864 固化为预期。

影响：即使修复 `BUG-AUDIT-001` 后所有测试通过，CI 仍会因 `870 != 864` 失败。

建议：确认新增 6 项测试属于预期后，同步更新两个工作流与静态合同；长期可同时记录精确
测试清单哈希，避免只维护易过期的单个数字。

## BUG-AUDIT-007 — P1 — 前端生产依赖存在已知漏洞

状态：`CONFIRMED / RELEASE_BLOCKER`

使用官方 npm registry 执行 `npm audit --omit=dev`，得到 6 个依赖级条目：4 个高危、
2 个中危，均显示存在修复版本。

重点路径：

- 直接依赖 `axios@1.15.0`：高危集合，包含原型污染相关请求篡改、认证绕过、头注入和
  资源耗尽条目。
- 直接依赖 `echarts@6.0.0`：中危 XSS 条目。
- Axios 传递依赖 `form-data@4.0.5`、`follow-redirects@1.15.11`。
- 构建/编译依赖树中的 `postcss@8.5.8`、`nanoid@3.3.11`。

说明：本轮确认的是供应链漏洞状态，没有声称所有条目都能通过当前浏览器代码直接利用；
Axios 和 ECharts 均被产品直接使用，因此发布前仍应升级并做请求拦截器、图表标签/提示框
及生产构建回归。

建议：在独立修复批次升级到审计工具给出的安全版本，运行 `npm ci`、完整 Vitest、
Playwright、生产构建和再次 `npm audit --omit=dev`。本轮未运行 `npm audit fix`。
