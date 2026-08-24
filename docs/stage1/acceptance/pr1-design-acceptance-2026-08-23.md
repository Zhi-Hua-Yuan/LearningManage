# 阶段 1 PR1 设计验收记录

状态：本地设计候选已生成，等待受保护 PR 评审与 CI 证据

日期：2026-08-23

## 1. PR1 目标

在不修改运行代码、数据库、CI 或部署配置的前提下，冻结 PR2～PR8 所需的业务语义、权限规则、V2 数据合同、API 兼容边界和阶段验收门禁。

## 2. 设计输入核对

| 输入 | 结果 | 说明 |
|---|---|---|
| V1 `task.assignee_id` | CONFIRMED | V1 已存在列和索引，ADR-001 采用 rename，不新增双列 |
| Java `Task.userId` | CONFIRMED | 当前被广泛用于归属和权限；阶段 1 固定为创建人语义 |
| `weekly_review` 当前结构 | CONFIRMED | 缺少可见范围、团队目标和稳定关联 |
| 团队退出/移除能力 | CONFIRMED | 当前 Service/Controller 未提供完整链路 |
| `user.user_role` | CONFIRMED | 当前注册写入小写 `user` |
| 租户 RBAC 调用方 | CONFIRMED_ABSENT | V1 结构存在，当前 Java 无对应业务调用方 |
| 前端 API 基线 | CONFIRMED | 阶段 0 证据为 37 个唯一 operation、缺失 0 |
| 后端测试基线 | CONFIRMED | 设计时 CI 精确计数为 84；实现 PR 增加测试后必须按证据更新 |
| Flyway history 基线 | CONFIRMED | 当前 CI 期望 1；PR2 发布 V2 时改为 2 |

## 3. PR1 交付矩阵

| ID | 交付物 | 状态 |
|---|---|---|
| PR1-D-001 | 阶段 1 入口与范围 | PASS |
| PR1-D-002 | 可测试的需求合同 | PASS |
| PR1-D-003 | 项目/任务/团队/复盘/AI 权限矩阵 | PASS |
| PR1-D-004 | 任务身份与分配 ADR | PASS |
| PR1-D-005 | 周复盘可见性 ADR | PASS |
| PR1-D-006 | 角色边界 ADR | PASS |
| PR1-D-007 | PermissionService ADR | PASS |
| PR1-D-008 | V2 数据字典、回填、验证和恢复合同 | PASS |
| PR1-D-009 | API 兼容与新增 operation 合同 | PASS |
| PR1-D-010 | 风险登记表 | PASS |
| PR1-D-011 | 机器可读阶段验收合同及 Schema | PASS |

上述 PASS 仅表示 PR1 设计交付物完整，不代表阶段 1 功能或 V2 迁移已经实现。

## 4. 关键冻结候选

1. `task.user_id` 保留物理列并定义为创建人；Java/API 使用 `createdByUserId`。
2. V1 `assignee_id` 在 V2 重命名为 `assignee_user_id`，不保留同义双列。
3. 分配、转派、解除和成员退出写不可变历史。
4. TEAM 周复盘首版定向一个 `team_id`，团队只读取独立 `sharedSummary`。
5. `user.user_role` 规范为 `USER/SYSTEM_ADMIN`，不新增 `system_role`，不启用租户 RBAC。
6. `SYSTEM_ADMIN` 不默认绕过私人内容和业务资源权限。
7. 权限以显式 `PermissionService` 为唯一入口，并提供批量能力阻止 N+1。
8. 原 37 个前端 operation 是兼容子集，不是阶段 1 固定总数。

## 5. PR1 验证要求

本地候选检查项：

- JSON 与 JSON Schema 均可解析；
- 机器合同中的所有设计输入路径存在；
- ADR 编号唯一；
- 需求、权限矩阵、数据字典和 API 合同中的核心枚举一致；
- Markdown 不包含有效凭据、数据库备份或真实私人正文；
- `git diff --check` 通过；
- `docs/stage1/` 以外没有由 PR1 引入的修改。

### 本地执行结果

执行时间：2026-08-24 Asia/Shanghai

| 检查 | 结果 | 摘要 |
|---|---|---|
| 文档边界 | PASS | 新增 `docs/stage1/` 下 13 个文件；未发现其他 PR1 修改 |
| Markdown 相对链接 | PASS | 所有本地相对目标存在 |
| JSON 解析 | PASS | 合同与 Schema 均可解析 |
| 机器合同结构 | PASS | 9 个设计输入、12 个唯一 Gate，基线 37/0 |
| 尾随空白 | PASS | 0 处 |
| 凭据模式扫描 | PASS | 未发现私钥、Bearer Token、AK/SK 模式 |
| 后端回归测试 | PASS | Maven Surefire 20 个报告、84 项测试，失败 0、错误 0、跳过 0 |

工作区在 PR1 开始前已存在 `deploy/docker-compose.release-gate.yml` 的未提交修改；该文件不属于 PR1，未被本次工作修改或纳入上述设计交付物。创建正式 PR 时必须只选择 `docs/stage1/`。

远程 PR 合并前还需：

- Backend CI 必需检查通过；
- Files changed 复核仅包含授权文档；
- ADR 评审无未解决阻塞意见；
- 合并后将设计状态从评审候选解释为冻结输入，不回写伪造的远程 Run 证据。

## 6. PR2 进入条件

只有 PR1 经受保护 PR 合并后，PR2 才开始编写 `V2__stage1_business_semantics_and_permissions.sql`。PR2 必须直接引用本目录中的 ADR、数据字典、风险 ID 和机器验收 Gate，不重新发明字段语义。

## 7. 范围声明

PR1 不授权连接或修改正式数据库、执行 Flyway、修改 V1、变更凭据、部署环境、创建 Tag/Release 或直接写入受保护分支。
