# 阶段 1 风险登记表

状态：阶段 1 执行中；PR6 已完成；PR7/WP7-A、WP7-B、WP7-C1～WP7-C5、WP7-D3、WP7-D4-1、WP7-D4-2、WP7-D4-3、WP7-D5-1～D5-4、WP7-D 已合并；当前主目标为 WP7-E

| ID | 状态 | 风险 | 触发条件 | 缓解措施 | 关闭证据 | 目标 PR |
|---|---|---|---|---|---|---|
| S1-R-001 | CLOSED | V1 已有 `assignee_id`，误新增同义列形成双真相 | V2 DDL 同时出现两个受理人列 | ADR-001 固定 rename；结构测试断言唯一受理人列 | V2 schema manifest 与 MySQL 验证；PR2 工作包 2/4 记录 | PR2 |
| S1-R-002 | CLOSED | 存量未知 `user_role` 被错误归类 | preflight 发现非允许值 | 未知值阻断迁移，先人工分类 | 25 项 preflight 与未知角色负向样本全部 PASS；PR2 工作包 3 记录 | PR2 |
| S1-R-003 | CLOSED | 分配与成员退出竞争留下失效受理人 | 两事务并发操作同一成员/任务 | 行锁或 CAS；同事务解除任务与失效成员 | PR5 最终验收记录；WP5-D/F 真实 MySQL 并发测试；CI run 33262101089；失效受理人数量为 0 | PR4/PR5 |
| S1-R-004 | CLOSED | TEAM 周复盘泄漏私人正文 | 复用实体或同一 VO 返回 | DTO/VO 分离；共享 VO 类型不含私人字段 | PR6 C5：共享 VO JSON 脱敏、MySQL 共享查询排除 PRIVATE、Backend CI run 33350232099 | PR6 |
| S1-R-005 | CLOSED | 复盘没有共享目标导致跨团队泄漏 | TEAM 记录 teamId 为空或关联跨团队任务 | ADR-002 增加 team_id；保存时批量校验 | V2 post-verify 复盘隐私与关联检查；PR2 工作包 2/4 记录 | PR2/PR6 |
| S1-R-006 | ACCEPTED_LIMITATION | 首版一份周复盘只能定向一个团队 | 用户需要向多个团队发布不同摘要 | 明确产品限制；未来用 share 表扩展时新增 ADR/迁移 | 文档和 UI 提示 | 后续阶段 |
| S1-R-007 | CLOSED | 权限检查逐条查询导致 N+1 | 任务列表、统计、AI 批量 ID | PermissionService 提供批量方法；查询次数门禁 | PR3 WP4-C：100 资源真实 MySQL 查询次数为固定 actor 1 次 + task 1 次；CI run 33167613189 | PR3 |
| S1-R-008 | CLOSED | 普通接口已鉴权但 AI 入口可绕过 | AI 请求携带越权 projectId/taskId | C4 已补齐自动候选过滤、显式 ID 全量校验、周复盘润色草稿确认重授权 | PR6 C5 AI 授权回归；越权/不存在 ID 在模型调用前拒绝；Backend CI run 33350232099 | PR6 |
| S1-R-009 | CLOSED | SYSTEM_ADMIN 被实现为全局内容读取后门 | 代码按角色无条件 return allow | ADR-003 禁止默认绕过；PR3 冻结权限矩阵覆盖 SYSTEM_ADMIN 的项目、任务、团队和复盘拒绝组合 | `PermissionMatrixParameterizedTest` 173 项通过，unauthorizedAllowedCount=0；PR3 最终合同验收记录 | PR3 |
| S1-R-010 | OPEN | 旧 37 个前端 operation 被破坏 | 修改旧路径、method 或必填字段 | WP7-A 已合并并固定 37 operation 数量与合同 SHA-256；PR7 预计新增 7 个、总计 44 个；PR7/PR8 执行兼容子集和运行时 OpenAPI 门禁 | PR #66 合并、WP7-A 机器合同；最终前端导出与跨仓 OpenAPI 报告待补 | PR7/PR8 |
| S1-R-011 | CLOSED | Flyway V2 失败后无法安全恢复 | DDL/回填中断或发布后发现问题 | 迁移前备份；隔离库恢复演练；发布后只前向修复 | PR2 工作包 4 恢复演练：备份、迁移、恢复、V1 对账全部 PASS | PR2 |
| S1-R-012 | CLOSED | 创建人与受理人口径混用导致统计错误 | 周复盘统计仍按 `task.user_id` 查询执行者 | C4 已将周复盘完成数/重点项目统一改为 `assignee_user_id`，并新增 Mapper SQL 合同与 MySQL 回归 | PR6 C5/C4 周统计真实 MySQL 回归；Backend CI run 33350232099；564 项测试通过 | PR6 |
| S1-R-013 | OPEN | 前端缓存保留旧受理人或旧能力 | 管理员从其他会话转派任务 | 受理人、任务列表、成员和能力采用内存态与统一失效链；401、登出和身份切换清空敏感状态；服务端始终重鉴权 | WP7-A 合并并冻结缓存合同；WP7-C 已证明任务级刷新、stale response 和当前页面状态隔离；WP7-D3 已证明关联候选 stale response 与失权 ID 不恢复；WP7-D4-3 已证明 AI 任务上下文 stale response、授权失败刷新和 malformed response 正文保护；WP7-E1-1.1～1.5 已完成资产盘点、scope/lifecycle 冻结、storage policy 门禁和 13 项差距路由；全局缓存、401/登出和多账号运行时证据待 WP7-E1-2～E3 | PR7 |
| S1-R-014 | CLOSED | 分配历史包含敏感理由或越权用户信息 | reason 写入敏感正文或外部用户查询历史 | reason 限长和内容规则；历史沿用 TASK_VIEW 权限 | WP4-C reason 规则、D2-B 权限前置、D2-C HTTP 隐私字段与拒绝响应测试；Backend CI Run 33199646633 全部通过 | PR4 |

## 使用规则

- `OPEN` 风险必须在目标 PR 中关闭或重新分类，不能在 PR8 被汇总为普通 PASS。
- `ACCEPTED_LIMITATION` 是明确产品边界，不代表安全控制可省略。
- 新发现的权限、迁移、隐私或兼容风险必须追加 ID，不复用或删除历史 ID。
- 本表不记录密码、Token、数据库备份内容或真实私人复盘正文。
