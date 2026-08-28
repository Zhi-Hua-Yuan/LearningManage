# PR4 WP4-A：任务身份模型验收记录

日期：2026-08-28
基线：`develop@99d7343`
工作包：任务身份模型正式落地

## 交付内容

- `Task.createdByUserId` 显式映射物理列 `task.user_id`；
- `Task` 增加 `assignedByUserId`、`assignedAt` 映射；
- `TaskVo` 移除模糊的 `userId`，增加创建人与分配身份字段；
- Task 专属生产调用点和测试 fixture 完成机械改名；
- 保留当前周复盘创建人口径，未提前实现 PR6 的统计修正；
- 未修改 V1/V2 迁移，未新增接口、迁移或分配业务逻辑。

## 变更文件

- `src/main/java/com/spt/learningmanage/model/entity/Task.java`
- `src/main/java/com/spt/learningmanage/model/vo/task/TaskVo.java`
- `src/main/java/com/spt/learningmanage/service/impl/TaskServiceImpl.java`
- `src/main/java/com/spt/learningmanage/service/impl/AiServiceImpl.java`
- `src/main/java/com/spt/learningmanage/service/impl/WeeklyReviewServiceImpl.java`
- `src/test/java/com/spt/learningmanage/model/entity/TaskIdentityMappingTest.java`
- `src/test/java/com/spt/learningmanage/model/vo/task/TaskVoContractTest.java`

## 验证结果

| Gate | 结果 |
|---|---|
| `mvnw.cmd -DskipTests compile` | PASS |
| WP4-A 针对性测试 | PASS，22 tests，0 failures/errors |
| 完整 Surefire invocation | 368 tests；源码相关测试 PASS |
| Task 旧 accessor 残留扫描 | PASS，无 `Task::getUserId`、`task.getUserId()`、`task.setUserId()` |
| V1 SHA-256 | PASS，保持 `E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9` |
| V2 SHA-256 | PASS，保持 `B40BD46F7CB303F8ED5B79AC86F78AE9078E78F8F3C26C91AAFA89F758683FE1` |

完整测试在本机有 8 个既有 MySQL 集成测试因未配置 CI 专用的 `TEST_DB_USERNAME` 而无法建立连接；这些测试报的是数据库认证错误，不是代码断言失败。CI workflow 的期望测试数已按 Surefire 实际 invocation 从 366 更新为 368，待 CI MySQL 容器环境复核。

## 合同状态

- `S1-A-003`：继续 `PENDING`，待 PR4 全部初始分配、转派和历史闭环完成后更新；
- `S1-R-014`：继续 `OPEN`，由 WP4-C 的 reason 安全规则关闭；
- `S1-R-003`：继续 `OPEN`，成员退出竞争验收留给 PR5；
- `S1-R-008`、`S1-R-012`：保持 OPEN，分别由后续工作包处理。
