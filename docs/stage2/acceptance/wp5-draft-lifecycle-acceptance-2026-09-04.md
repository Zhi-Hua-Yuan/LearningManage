# WP5 AI 草稿生命周期验收记录

状态：`IMPLEMENTED / CANDIDATE CI PENDING`
日期：2026-09-04

## 已完成实现

- 通用 `AiDraftConfirmationService`、Handler 注册中心和强类型确认上下文。
- 任务拆解、周复盘润色两个版本 1 Handler。
- 草稿行锁、草稿级重放、终态 CAS 和事务不变量保护。
- 过期转换先提交、后返回业务错误。
- 草稿创建持久化 `schemaVersion` 和生成调用 `traceId`。
- 清单重排行锁、确认/取消/定时过期事务守卫、任务快照 CAS 和 Trace 持久化。
- 四个稳定草稿领域错误码。
- 场景服务不再直接写正式数据、确认日志或草稿确认终态。
- V1/V2/V3 未修改，未新增 V4。

## 自动化覆盖

- 同 operationId 与不同 operationId 草稿级重放。
- 首次确认日志、businessId、Trace 和终态写入。
- Handler 异常回滚。
- 过期转换提交后返回错误。
- 不支持 Schema、未知场景、重复 Handler 注册和上下文错误。
- 取消幂等及 CAS 失败后重读赢家。
- 清单重排成功、过期、终态竞争、快照失效和部分写入回滚行为。
- 静态写路径与无 V4 范围门禁。
- 隔离 MySQL 上 20 路并发确认，只允许一个首次业务结果。

## 当前验证结果

- Java 主源码与全部测试源码完成干净重编译。
- WP5 单元、架构、场景委托与 Spring 上下文测试通过。
- 非 MySQL 测试集 `614/614` 通过，Failures 0、Errors 0、Skipped 0。
- PR #114 首次 Runner 已实际执行 `674/674` 且测试本身全部通过：非 MySQL 614、MySQL 60；首次门禁仅因预估计数 673 少 1 而停止，现已按报告修正。
- 三项 WP5 MySQL 测试已纳入测试集：20 路草稿确认、重排确认/取消竞争、重排快照失效原子回滚；本机因未注入 `TEST_DB_USERNAME/TEST_DB_PASSWORD` 未执行成功，
  必须由候选 CI 的隔离 V3 MySQL 完成最终判定。

## 候选 CI 必须证明

1. 后端既有测试和 WP5 新测试全部通过，测试数不得减少。
2. 20 路并发确认产生 1 个项目、1 条确认日志和一个共同 businessId；重排竞争只有一个终态，快照失效无部分写入。
3. 前端 459 项、legacy 37、frontend 44/44 和 runtime 65 契约无回归。
4. Flyway 空库/升级及 V1/V2/V3 不可变检查通过。
5. Docker Stub 的任务拆解预览、确认、重放、取消链路通过。

上述候选 CI 通过前，`S2-A-009` 和 `S2-R-004` 保持待关闭状态。
