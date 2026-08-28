# PR3 WP4-D：业务 Service、Stats 与 AI 权限接入验收记录

日期：2026-08-28  
工作区：`codex/stage1-pr3-permission-service`  
范围：在 WP4-A/B/C 的权限内核、真实 Mapper 集成和批量查询能力之上，接入现有业务 Service、Stats 与 AI 入口，并补齐任务能力提示与项目恢复的生命周期语义。

## 1. 交付内容

| 项目 | 结果 |
|---|---|
| Project | 个人/团队查看、创建、更新、归档、删除、恢复、重排统一经过 `PermissionService`；恢复读取使用原始已删除事实 |
| Task | 创建、详情、列表、内容更新、状态变更、删除统一鉴权；按实际字段变化选择 content/reorganize/view 动作；列表/详情批量解析 `capabilities` |
| Milestone | 创建、列表、更新、删除按所属项目权限判定，去除创建人过滤导致的团队项目误拒绝 |
| Team | 创建团队、加入团队、成员列表、角色更新统一校验有效系统角色与团队成员生命周期 |
| WeeklyReview | 详情、更新、删除及读写入口统一经过作者/可见性权限判定 |
| Stats | 统计入口先校验有效登录主体 |
| AI | 外部 taskId/projectId 在模型调用前批量校验；越权、非法或不存在 ID 整体拒绝；清单重排要求项目管理权限 |
| 生命周期 | 项目恢复使用 raw deleted 查询；任务/里程碑级联恢复 SQL 保留团队项目语义；进度计算改为项目范围 |
| API | `TaskVo` 新增服务端计算的 `capabilities`，不接受客户端能力值作为授权事实 |
| 数据库 | 无 Flyway migration 变更 |

## 2. 验证证据

| 门禁 | 结果 |
|---|---|
| Maven 编译（JDK 17） | PASS — `BUILD SUCCESS` |
| WP4-D 定向单元测试 | PASS — 69 tests，0 failures/errors/skipped（Permission、Task、AI 回归） |
| 完整非 MySQL 测试集 | PASS — 185 tests，0 failures/errors/skipped |
| `git diff --check` | PASS |
| Flyway migration diff | NONE |
| CI 测试计数 | PASS — 两份 workflow 门槛为 193，PR #44 的 Maven verification 实际通过 |

本机完整测试中 8 个 MySQL 集成用例因未提供 `${TEST_DB_USERNAME}` 等数据库凭据无法连接；PR #44 CI 已在托管 MySQL 环境完成正式复核，未跳过集成测试。

正式 CI 证据：[Run 33172016005](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33172016005)

- Guard and migration immutability：PASS
- Maven verification and tested artifact：PASS（193 tests）
- Flyway empty database gate：PASS
- Flyway existing database gate：PASS
- Docker runtime and migration gate：PASS

## 3. 权限安全结论

1. 业务层不再以客户端角色、创建人或 `capabilities` 作为授权事实；每次写操作均重新调用 `PermissionService`。
2. 团队项目不再使用 `user_id` 作为资源范围过滤，团队成员由服务端团队角色和成员生命周期事实判定。
3. AI 外部 ID 采用 fail-closed 策略，批量校验任一失败即整体拒绝；草稿确认路径继续保留事务内二次校验边界。
4. 现阶段未实现任务分配、成员退出/移除并发治理和周复盘共享 VO；这些继续由 PR4～PR6 按冻结合同交付。

## 4. 验收结论

WP4-D 的代码接入、能力提示、恢复语义、本地回归和受保护 CI 验证均已完成，结论为 **PASS**。PR #44 的全部必需门禁通过，可进入 PR3 合并评审。
