# PR6 WP6-B：周复盘隐私读写开发记录

状态：`IMPLEMENTED（等待 WP6-C～E 集成验收）`

日期：2026-08-30

## 1. 本工作包交付范围

- 新增周复盘保存、更新和团队查询 Request DTO；
- 新增作者完整详情 VO、团队共享 VO 及作者/重点项目摘要 VO；
- `/review/current`、`/review/save`、`/review/update`、`/review/{id}`、`/review/history` 改为 DTO/VO 合同；
- 新增 `GET /review/team` 团队共享查询接口；
- `visibilityScope` 缺省按 `PRIVATE` 处理；`TEAM` 保存要求团队、共享摘要和当前团队查看权限；
- 团队查询 SQL 只投影共享字段，明确排除 `reflection`、`next_plan` 和私人任务关联，并显式过滤 `wr.is_delete = 0`；
- 新增 `WeeklyReviewTask` 实体，为 WP6-C 关联写入预留，但本工作包不启用资源关联。

## 2. 安全边界

1. 作者详情通过 `PermissionService.requireWeeklyReviewFullView` 判断，返回完整私有字段；
2. 团队共享查询先执行 `requireTeamView`，SQL 再以 `team_id` 和 `BINARY visibility_scope = BINARY 'TEAM'` 双重过滤；
3. `WeeklyReviewSharedVO` 不定义私人正文、私人任务列表或私人项目详情；
4. PRIVATE 指定 `teamId`、TEAM 缺失团队或共享摘要、以及任何项目/任务关联请求均拒绝；
5. 已完成的项目/任务统计和关联校验仍保留在 WP6-D/WP6-C，未在本工作包提前改变统计口径。

## 3. 验证证据

### 3.1 编译

```text
.\mvnw.cmd -q -DskipTests compile
结果：PASS
```

### 3.2 WP6-B 聚焦测试

```text
.\mvnw.cmd -q -Dtest=WeeklyReviewVisibilityScopeEnumTest,WeeklyReviewSharedVOContractTest,WeeklyReviewMapperPrivacyContractTest,WeeklyReviewServiceImplTest test
结果：PASS
```

覆盖内容：可见性枚举白名单、共享 VO 私有字段排除、共享查询 SQL 投影/TEAM 过滤、PRIVATE/TEAM 保存边界和作者完整读取。

### 3.3 全量测试环境说明

全量 `mvn test` 已启动并执行；现有 MySQL 集成测试因测试配置仍使用占位符 `${TEST_DB_USERNAME}`，报 `Access denied`，未能建立数据库连接。该失败属于测试环境凭据阻塞，需在 WP6-E 使用真实测试数据库凭据重跑；不将其计为 WP6-B 业务断言失败。

## 4. 未在 WP6-B 宣称完成的内容

- `weekly_review_task` 的批量项目/任务归属校验与事务写入（WP6-C）；
- 完成任务统计改为 `assignee_user_id` 及重点项目口径修正（WP6-D）；
- AI 业务 ID 授权回归、真实 MySQL/权限集成验收及 S1-A-006/S1-A-008 状态更新（WP6-D/E）。
