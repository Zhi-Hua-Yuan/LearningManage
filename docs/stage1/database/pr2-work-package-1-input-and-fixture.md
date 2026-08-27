# PR2 工作包 1：迁移输入与测试样本合同

状态：`FROZEN`；工作包 1 实施输入（已通过本地验收）

日期：2026-08-24

上游合同：

- [V2 数据字典与迁移合同](v2-data-dictionary.md)
- [ADR-001：任务创建人与受理人语义](../architecture/ADR-001-task-identity-and-assignment.md)
- [ADR-002：周复盘可见性与团队共享](../architecture/ADR-002-weekly-review-visibility.md)
- [阶段 1 风险登记表](../risk/stage1-risk-register.md)

## 1. 目的

本文件冻结 PR2 工作包 1 的迁移输入、存量数据判定规则、preflight 检查目录和测试样本矩阵，使工作包 2 编写正式 V2 迁移时不再临时决定以下问题：

- V1 当前受理人如何计算；
- 活跃任务和历史任务采用什么成员有效性规则；
- 哪些存量冲突必须在迁移前阻断；
- `INITIAL_ASSIGN` 回填日志如何生成稳定主键；
- 合法和非法 fixture 必须覆盖哪些数据组合；
- 后续迁移结果应如何做机器对账。

本文件是 V2 数据字典的实施细化，不修改或替代已冻结的阶段 1 ADR。若后续实现与上游合同冲突，应新增 ADR 或显式修订实施方案，不能静默改变本文件中的业务语义。

## 2. 工作包边界

工作包 1 只交付：

1. 本迁移输入与测试样本合同；
2. 只读 V2 preflight SQL；
3. 合法 V1 存量数据 fixture；
4. 合法 fixture 的机器可读预期结果；
5. 未知系统角色、孤儿受理人和非团队成员受理人的负向 fixture；
6. preflight 与 fixture 的静态测试；
7. 工作包 1 本地验收记录。

工作包 1 不创建正式 `V2__stage1_business_semantics_and_permissions.sql`，不修改 V1、Java 业务代码、CI、数据库、部署配置或已发布迁移清单，不连接正式数据库。

## 3. 统一派生值

### 3.1 最终受理人

所有 preflight、fixture 预期、V2 回填和迁移后对账必须使用同一个派生规则：

```text
finalAssigneeUserId = COALESCE(task.assignee_id, task.user_id)
```

含义：

- V1 已有显式 `assignee_id` 时保留该值；
- V1 `assignee_id` 为空时，将创建人 `task.user_id` 作为初始受理人；
- 不允许 preflight、V2 SQL 和 post-verify 分别实现不同的 fallback 规则；
- 不允许用项目所有者、团队 OWNER 或其他默认成员覆盖上述冻结规则；
- 若派生结果与资源关系冲突，preflight 必须失败，不能静默修复。

### 3.2 活跃未完成任务

活跃未完成任务定义为：

```text
task.is_delete = 0 AND task.status = 0
```

这类任务表达当前责任，必须同时满足：

1. 创建人用户记录存在；
2. 最终受理人用户记录存在且 `user.is_delete = 0`；
3. 项目存在且未删除；
4. 个人项目的最终受理人等于项目所有者；
5. 团队项目的团队存在且未删除；
6. 团队项目的最终受理人是当前有效成员，即存在 `team_member.is_delete = 0` 的关系；
7. 里程碑非空时，里程碑存在且属于同一项目。

任一条件不满足时，preflight 必须报告非零异常并阻断 V2，不得把受理人改为其他成员或置空后继续迁移。

### 3.3 历史任务

历史任务定义为满足以下任一条件的任务：

```text
task.status IN (1, 2, 3) OR task.is_delete = 1
```

历史任务保留完成或删除时的责任事实，采用以下规则：

1. 创建人用户记录必须存在，但可以已经逻辑删除；
2. 最终受理人用户记录必须存在，但可以已经逻辑删除；
3. 个人项目的最终受理人仍须等于项目所有者；
4. 团队项目的最终受理人必须存在对应 `team_member` 历史关系，但允许该关系 `is_delete = 1`；
5. 从未加入目标团队的外部用户不能作为历史团队任务受理人；
6. 项目、团队或里程碑的逻辑删除不抹除历史关联，但物理记录必须存在且关系可解释。

该规则与阶段 1 成员退出合同一致：退出时只解除未完成任务，已完成任务保留历史受理人。

### 3.4 创建人语义

`task.user_id` 始终表示创建人，不因转派、退出、用户逻辑删除或项目状态变化而改写。preflight 只验证创建人记录存在，不要求历史创建人仍是当前有效团队成员。

## 4. `INITIAL_ASSIGN` 日志回填规则

V2 为每个最终受理人非空的存量任务写且只写一条 `INITIAL_ASSIGN` 日志。

回填映射固定为：

| 日志字段 | 来源 |
|---|---|
| `id` | `task.id` |
| `task_id` | `task.id` |
| `from_assignee_user_id` | `NULL` |
| `to_assignee_user_id` | `finalAssigneeUserId` |
| `assigned_by_user_id` | `task.user_id` |
| `action` | `INITIAL_ASSIGN` |
| `reason` | `NULL` |
| `create_time` | `task.create_time` |

`INITIAL_ASSIGN` 日志主键使用 `task.id`，原因如下：

- 新表不存在历史日志主键冲突；
- 一项存量任务最多生成一条初始日志；
- 回填结果与执行时间、数据库自增状态无关；
- 任务和初始日志可以直接按 ID 对账；
- 后续真实分配日志仍由应用使用 Snowflake ID 生成。

工作包 2 不得将初始日志改为随机 ID、当前时间生成 ID、`AUTO_INCREMENT` 或依赖执行顺序的行号。若最终受理人为空则不写初始日志，但按照冻结回填规则，合法 V1 任务应能派生出非空最终受理人。

## 5. Preflight 输出合同

目标文件：

```text
sql/flyway/stage1/01_preflight_v2.sql
```

preflight 必须是只读、单结果集、确定性 SQL。每项检查输出：

| 字段 | 说明 |
|---|---|
| `check_id` | 稳定且唯一的检查编号 |
| `check_name` | 不包含业务正文的简短检查名称 |
| `violation_count` | 异常记录数量 |
| `status` | `PASS` 或 `FAIL` |

结果按 `check_id` 升序排列。任何 `violation_count > 0` 都应产生 `FAIL`；后续 CI 包装脚本以任一 `FAIL` 作为拒绝迁移条件。

preflight 不执行 `INSERT`、`UPDATE`、`DELETE`、DDL、存储过程、触发器、权限变更或数据库控制语句，也不输出密码、账号、用户名、任务标题、任务描述、周复盘正文、AI 正文或连接配置。

## 6. Preflight 检查目录

### 6.1 V1 结构

| ID | 检查 | 失败含义 |
|---|---|---|
| `V2-P-001` | V1 的 20 个业务表完整存在 | 目标不是已发布 V1 结构 |
| `V2-P-002` | `task.assignee_id` 恰好存在一列 | 受理人输入列缺失或重复 |
| `V2-P-003` | `idx_task_assignee_id` 恰好存在一个 | V1 索引发生漂移 |
| `V2-P-004` | `task.assignee_user_id` 尚不存在 | 目标可能部分执行过 V2 |
| `V2-P-005` | `task_assignment_log` 和 `weekly_review_task` 尚不存在 | 目标可能部分执行过 V2 |
| `V2-P-006` | V2 周复盘字段尚不存在 | 目标可能部分执行过 V2 |

V1 文件 checksum 和 Flyway history 由发布迁移不可变测试及 Flyway `validate` 继续验证；preflight SQL 负责数据库当前结构的关键对象检查。

### 6.2 系统角色

| ID | 检查 | 允许值或规则 |
|---|---|---|
| `V2-P-010` | 未知 `user_role` | 仅允许 `user/admin/USER/SYSTEM_ADMIN` |
| `V2-P-011` | `user_role` 为空或空白 | 数量必须为 0 |
| `V2-P-012` | `user_role` 带前后空格 | 数量必须为 0 |

角色比较必须使用大小写严格语义，例如 `BINARY user_role`，不能依赖当前 `utf8mb4_0900_ai_ci` 排序规则。

### 6.3 任务基础关系

| ID | 检查 | 失败含义 |
|---|---|---|
| `V2-P-020` | 任务创建人记录不存在 | 创建事实无法解释 |
| `V2-P-021` | 最终受理人记录不存在 | 当前或历史责任无法解释 |
| `V2-P-022` | 任务项目记录不存在 | 任务资源范围无法判断 |
| `V2-P-023` | 非空里程碑记录不存在 | 任务里程碑关联损坏 |
| `V2-P-024` | 里程碑不属于任务项目 | 跨项目里程碑冲突 |
| `V2-P-025` | 活跃未完成任务属于已删除项目 | 当前责任挂在失效项目下 |
| `V2-P-026` | 活跃团队任务所属团队不存在或已删除 | 当前团队范围失效 |

### 6.4 任务受理人

| ID | 检查 | 失败含义 |
|---|---|---|
| `V2-P-030` | 活跃未完成任务受理人用户已删除 | 当前责任人失效 |
| `V2-P-031` | 个人任务最终受理人不是项目所有者 | 违反个人项目受理规则 |
| `V2-P-032` | 活跃团队任务受理人不是有效团队成员 | 当前团队责任越界 |
| `V2-P-033` | 历史团队任务受理人从未加入目标团队 | 历史责任无法解释 |

`V2-P-032` 只检查活跃未完成任务，并要求 `team_member.is_delete = 0`。`V2-P-033` 只检查历史任务，允许命中逻辑删除的成员关系，但必须存在团队成员历史记录。成员资格检查应在用户记录存在的前提下执行，避免一个孤儿受理人重复触发多个次生检查。

### 6.5 周复盘

| ID | 检查 | 合法规则 |
|---|---|---|
| `V2-P-040` | 周复盘作者不存在 | 作者记录必须存在 |
| `V2-P-041` | `year` 或 `week_no` 不合法 | `year > 0`，`week_no` 为 1～53 |
| `V2-P-042` | `end_date < start_date` | 结束日期不得早于开始日期 |
| `V2-P-043` | `user_id/year/week_no` 重复 | 同一用户同一周最多一条 |
| `V2-P-044` | 当前周复盘唯一索引缺失 | V1 唯一关系必须存在 |

工作包 1 不重新计算历史记录的 ISO Week，不根据日期自动修改 `year/week_no`。

## 7. 合法 V1 Fixture 数据矩阵

目标文件：

```text
src/test/resources/db/stage1/v1_to_v2_seed.sql
```

fixture 只向 `user`、`team`、`team_member`、`project`、`milestone`、`task` 和 `weekly_review` 插入固定数据，不写 Flyway history、租户 RBAC、AI、幂等或日志表。

### 7.1 用户

| 用户 ID | V1 `user_role` | V2 预期角色 | 用途 |
|---:|---|---|---|
| `1101` | `user` | `USER` | 个人项目所有者、团队 OWNER |
| `1102` | `admin` | `SYSTEM_ADMIN` | 旧管理员值迁移 |
| `1103` | `USER` | `USER` | 当前有效团队 MEMBER |
| `1104` | `SYSTEM_ADMIN` | `SYSTEM_ADMIN` | 已规范管理员值保持不变 |
| `1105` | `USER` | `USER` | 已退出团队的历史 MEMBER |

fixture 密码字段统一使用不可登录占位文本 `not-a-real-password-hash`，账号和用户名使用 `stage1_v2_*` 虚构值。

### 7.2 团队与成员

| 记录 | 定义 |
|---|---|
| 团队 `2101` | 所有者为 `1101` |
| 成员关系 `3101` | `1101`，`OWNER`，有效 |
| 成员关系 `3102` | `1103`，`MEMBER`，有效 |
| 成员关系 `3103` | `1105`，`MEMBER`，已逻辑删除 |

### 7.3 项目与里程碑

| 记录 | 定义 |
|---|---|
| 项目 `4101` | `1101` 的个人项目 |
| 项目 `4102` | 团队 `2101` 的团队项目，创建人为 `1101` |
| 里程碑 `5101` | 属于个人项目 `4101` |
| 里程碑 `5102` | 属于团队项目 `4102` |

### 7.4 任务

| 任务 ID | 类型 | V1 状态 | V1 `assignee_id` | V2 最终受理人 | 覆盖目标 |
|---:|---|---|---:|---:|---|
| `6101` | 个人任务 | 活跃未完成 | `NULL` | `1101` | NULL fallback 到创建人 |
| `6102` | 团队任务 | 活跃未完成 | `NULL` | `1101` | 团队 OWNER 作为创建人 fallback |
| `6103` | 团队任务 | 活跃未完成 | `1103` | `1103` | 保留有效显式受理人 |
| `6104` | 团队任务 | 已完成 | `1105` | `1105` | 保留已退出成员的完成历史 |
| `6105` | 团队任务 | 已逻辑删除 | `1105` | `1105` | 删除任务仍参与历史回填 |

五个任务均应生成一条 `INITIAL_ASSIGN`，日志 ID 分别为 `6101`～`6105`。

### 7.5 周复盘

| 复盘 ID | 作者 | V2 预期 |
|---:|---:|---|
| `7101` | `1101` | `PRIVATE`，无团队、项目和共享摘要 |
| `7102` | `1103` | `PRIVATE`，无团队、项目和共享摘要 |

fixture 应为两个复盘填入明确的虚构 `reflection` 和 `next_plan`，用于证明 V2 不会把私人正文复制到 `shared_summary`。

### 7.6 固定性要求

- 所有主键使用本文件约定的固定值；
- 所有日期和时间使用固定字面量，不使用 `CURRENT_TIMESTAMP`、`NOW()` 或数据库随机函数；
- 所有字符串是无隐私的测试文本；
- fixture 不依赖插入后的自增值；
- fixture 可重复导入到一个全新的 V1 测试库，但不要求在同一数据库重复执行。

## 8. 机器可读预期结果

目标文件：

```text
src/test/resources/db/stage1/v1_to_v2_expected.json
```

JSON 至少包含：

1. `schemaVersion=1`；
2. V1 fixture 各表预期行数；
3. 五个用户的 V2 角色；
4. 五个任务的最终受理人、最近分配人、最近分配时间和初始日志 ID；
5. 两个周复盘的 PRIVATE 结果；
6. `initialAssignmentLogs=5`；
7. `weeklyReviewTaskLinks=0`。

expected JSON 必须列出全部任务和复盘，不能只保存示例。工作包 2 和工作包 3 将以该文件作为迁移后机器对账输入。

## 9. 负向 Fixture

负向 fixture 分文件保存，每个文件只构造一种主要失败原因，并在合法 fixture 之上执行。

### 9.1 未知系统角色

目标文件：

```text
src/test/resources/db/stage1/negative/unknown_system_role.sql
```

只增加一个 `user_role='AUDITOR'` 的虚构用户，预期 `V2-P-010` 为 1，不引入其他资源关系错误。

### 9.2 孤儿受理人

目标文件：

```text
src/test/resources/db/stage1/negative/orphan_assignee.sql
```

增加一个创建人、项目和里程碑均合法，但 `assignee_id` 指向不存在用户的任务，预期 `V2-P-021` 为 1。成员资格检查必须先确认用户存在，避免该记录重复计入 `V2-P-032` 或 `V2-P-033`。

### 9.3 非团队成员受理人

目标文件：

```text
src/test/resources/db/stage1/negative/team_assignee_not_member.sql
```

增加一个存在且未删除、但从未加入团队 `2101` 的用户，并将一项活跃未完成团队任务显式分配给该用户，预期 `V2-P-032` 为 1。

负向 fixture 只用于隔离测试数据库，不属于迁移脚本，不得用于修复或填充真实环境。

## 10. 静态测试输入

工作包 1 计划新增：

```text
FlywayV2PreflightStaticTest
FlywayV2SeedFixtureStaticTest
```

preflight 静态测试负责验证：

- SQL 只读；
- 检查编号完整、唯一并有序；
- 输出字段合同存在；
- 最终受理人使用统一 `COALESCE` 规则；
- 角色检查采用大小写严格比较；
- SQL 不读取敏感正文。

fixture 静态测试负责验证：

- 插入表在允许列表内；
- 不包含 DDL、数据库控制语句和真实凭据；
- 固定用户、团队、成员、项目、里程碑、任务和复盘 ID 完整且唯一；
- 四种角色输入全部覆盖；
- 五类任务样本全部覆盖；
- expected JSON 可解析且与 fixture ID、数量和派生结果一致；
- 每个预期初始日志 ID 等于对应任务 ID；
- 所有预期周复盘均为 PRIVATE。

正式 `FlywayV2MigrationStaticTest` 在工作包 2 与 V2 SQL 同时新增，工作包 1 不创建必然因迁移文件尚不存在而失败的测试。

## 11. 工作包 1 验收条件

进入工作包 2 前必须满足：

- 本文件中的派生规则、活跃/历史判定和日志 ID 规则无歧义；
- preflight 为单结果集只读 SQL；
- 所有检查编号唯一且静态测试覆盖；
- 合法 fixture 与 expected JSON 完整一致；
- 三个负向 fixture 各自具有明确预期失败编号；
- 不包含真实凭据、私人正文或生产数据；
- 新增静态测试与原有测试全部通过；
- V1 文件保持 byte-identical；
- 未创建或注册 V2 发布迁移；
- 未修改 CI、Java 业务代码、数据库或部署配置；
- 工作区原有 `deploy/docker-compose.release-gate.yml` 修改未被纳入本工作包。

工作包 1 的 PASS 只表示迁移输入和测试样本已经冻结，不表示 V2 可执行、数据库升级成功或阶段 1 数据库 Gate 已通过。
