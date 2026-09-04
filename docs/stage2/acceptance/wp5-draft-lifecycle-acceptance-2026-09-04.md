# WP5 AI 草稿生命周期验收记录

状态：`PASS`
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

## 最终验证结果

- Java 主源码与全部测试源码完成干净重编译。
- WP5 单元、架构、场景委托与 Spring 上下文测试通过。
- 非 MySQL 测试集 `614/614` 通过，Failures 0、Errors 0、Skipped 0。
- PR [#114](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/114) 已合并，候选实现提交为 `80f357b357bf98e18f4bdd1e53392aede002e8cf`。
- 合并后 Backend CI `33888407204` 的 Maven、Flyway 空库/存量库和 Docker 五项门禁全部通过。
- 跨仓候选 [run 33889047346](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33889047346) 的 10 个 Job 全部通过。
- 后端 `674/674` 通过：非 MySQL 614、MySQL 60；前端 `459/459` 通过。
- 三项 WP5 MySQL 场景通过：20 路草稿确认只产生一次正式写入，重排确认/取消竞争只有一个终态，重排快照失效时整体回滚。
- 前端 44 个 operation 与运行时 65 个 operation 匹配 `44/44`，缺失 0；legacy 37 未发生破坏性变化。
- Flyway 空库、存量库升级、V1/V2/V3 不可变与无 V4 检查全部通过。
- Docker Stub 的预览、取消、确认和幂等重放通过，正式写入计数为项目 1、里程碑 2、任务 4。

## 候选绑定

- Candidate ID：`stage2-wp5-20260904-merge-80f357b`
- 后端 SHA：`80f357b357bf98e18f4bdd1e53392aede002e8cf`
- 前端 SHA：`2ef907f292fbbacecf8a68f7d24c4701a555aa8a`
- Candidate Manifest SHA-256：`78BD22DCEF2E60CC8B1EAC8355288E8BDD0826A6A1862BC8FB1184094BC1C98C`
- API 比对报告 SHA-256：`5234569B647EDB5C74D321686E01D22D574EA0C4E5412D3BB3808C8A84B0B300`
- 全栈 AI 证据 SHA-256：`84C0F5EFA21AC3D9368ABC4CB4639242B2A3860129E53C6268F7541717B74C5B`

据此，`S2-A-009` 已关闭为 `PASS`，`S2-R-004` 已关闭为 `CLOSED`。WP5 不代表阶段 2 整体完成，`S2-A-010`～`S2-A-012` 继续保留至 WP6/WP8。
