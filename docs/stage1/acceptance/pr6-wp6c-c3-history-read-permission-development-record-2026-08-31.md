# PR6 WP6-C3：周复盘历史读取权限开发记录

状态：`IMPLEMENTED（等待全量 CI 与真实 MySQL 验收）`

日期：2026-08-31

## 1. 交付范围

- 新增 `WeeklyReviewReadAssociationResolver`，统一处理详情、当前周和历史复盘的关联读取授权；
- 读取 `weekly_review_task` 后重新执行当前任务权限过滤，不信任保存时的权限结果；
- 读取重点项目时重新解析当前项目访问范围，项目失权或删除时同时清空项目 ID 和名称；
- 复盘正文权限与关联资源权限分离，作者退出团队后仍能读取自己的完整正文；
- 历史复盘关联使用一次批量关联查询和批量权限查询，不按复盘或资源产生 N+1；
- 资源量超过权限服务单批上限时按 500 条分片，保持批量查询边界；
- 团队共享查询只返回当前仍有效、且属于目标团队的重点项目安全摘要；
- 读取过程不删除或修复存量失效关联，避免隐式写操作；
- 未修改 V1/V2 Flyway 文件。

## 2. 关键不变量

1. 无权读取复盘本身时，在查询关联前拒绝请求；
2. 已有复盘关联资源失权时，只裁剪关联，不让作者复盘正文读取失败；
3. `taskIds` 保持数据库确定顺序，只包含当前可读任务；
4. 重点项目当前不可读时，`focusProjectId` 和 `focusProjectName` 均为 `null`；
5. 关联数据异常（未知复盘、非法 ID、重复关系）抛出系统异常，不伪装成普通越权；
6. TEAM 共享 VO 不包含 `reflection`、`nextPlan` 或任务列表；
7. 大历史列表按批次调用权限服务，不逐条调用单资源权限接口。

## 3. 验证结果

### 3.1 编译

```text
.\\mvnw.cmd test -DskipTests
结果：PASS
```

### 3.2 C3 聚焦测试

```text
.\\mvnw.cmd test '-Dtest=WeeklyReviewReadAssociationResolverTest,WeeklyReviewServiceImplTest,WeeklyReviewAssociationValidatorTest,WeeklyReviewMapperPrivacyContractTest'
结果：19/19 PASS
```

覆盖：当前任务过滤、项目失权裁剪、多复盘批量组装、500 条分片、关联数据异常、详情接入、历史一次解析和共享 SQL 安全投影。

### 3.3 全量测试

本机全量执行统计为 `534`（其中 46 个 MySQL 集成测试因占位账号未计入完整执行）；C3 新增 8 个测试，按此前 CI 隔离 MySQL 比本机多计 1 个测试的已知差异，门禁候选值更新为 `537`。最终以 GitHub CI 的真实计数和结果为准。

## 4. 数据库与后续边界

- 无新增迁移，Flyway 仍使用已发布的 V1/V2；
- 不在读取时删除失效 `weekly_review_task` 关联；
- WP6-D 继续负责统计执行者口径和 AI ID 授权；
- WP6-E 负责真实 MySQL、回滚、并发和最终验收证据；
- `S1-A-006`、`S1-A-008` 在 WP6-D/WP6-E 完成前继续保持 `PENDING`。
