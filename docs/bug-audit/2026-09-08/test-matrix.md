# 测试矩阵

> 本文件记录原始审计基线结果；修复后的最终矩阵见[修复结果](resolution.md)。

状态定义：`PASS`、`FAIL`、`BLOCKED`、`NOT_APPLICABLE`。

## 后端、数据库与 AI

| 项目 | 状态 | 本轮结果 |
|---|---|---|
| 非 MySQL 回归 | PASS | 809 tests，0 failures，0 errors |
| MySQL 集成/并发分区 | FAIL | 61 tests，2 errors；对应 `BUG-AUDIT-001` |
| 两个失败并发类重复验证 | FAIL | 每个类 3/3 次稳定失败 |
| V8 清理生命周期 | PASS | 5 tests |
| Stage 4 知识索引 E2E | PASS | 干净专用数据库上 5 tests |
| Stage 5 权限感知 RAG E2E | PASS | 3 tests |
| Stage 6 Agent E2E | PASS | 4 tests |
| 10 万知识 ID/哈希稳定性 | PASS | 已包含于 809 项回归 |
| 100 条 Outbox→Qdrant 新鲜度 | PASS | Stage 4 E2E 的 60 秒 P95 门槛通过 |
| 后端打包 | PASS | 当前 SHA JAR 构建成功 |
| Stage 7 静态脚本原命令 | BLOCKED | Windows `python3` App Alias 无执行权限 |
| Stage 7 等价静态校验 | PASS | 合同、敏感标签规则、6 个 Grafana dashboard 均通过 |

Stage 4 第一次在已被浏览器用例写入的数据库上得到“预期 2、实际 8”；换成全新专用
数据库后 5/5 通过，因此归类为测试数据污染，不是产品缺陷。

## Flyway 与恢复

| 项目 | 状态 | 本轮结果 |
|---|---|---|
| 空库 V1→V8 | PASS | 8 个迁移，38 个业务表、39 个总表、二次迁移 0 项 |
| 存量库 V1 baseline→V8 | PASS | 7 个后续迁移，后置验证全部通过 |
| V2 负向预检 | PASS | 3/3 非法存量状态被拒绝 |
| V2 备份恢复 | PASS | 结构与数据恢复验证通过 |
| V3 负向预检 | PASS | 13/13 非法状态被拒绝 |
| V3 等价重复与恢复 | PASS | 去重、结构备份和恢复验证通过 |
| 应用账号 DDL 拒绝 | PASS | 空库验收脚本验证通过 |

迁移过程中 MySQL 8.0.41 多次提示 `BINARY expr` 已弃用；当前执行成功，作为未来版本
兼容风险记录。

## 前端

| 项目 | 状态 | 本轮结果 |
|---|---|---|
| Vitest | PASS | 71 files，515 tests |
| 覆盖率门槛 | PASS | statements 84.36%，branches 74.91%，functions 84.89%，lines 87.94% |
| 只读 lint/守卫 | PASS | ESLint、Oxlint、存储、缓存、AI 渲染均通过 |
| 存储策略专项 | PASS | 14 tests |
| TypeScript | PASS | `vue-tsc --build` |
| 前端 API 契约生成 | PASS | 71 operations，确定性 SHA-256 通过 |
| 生产构建 | PASS | Vite 7.3.2，784 modules |
| npm 生产依赖审计 | FAIL | 4 high、2 moderate；对应 `BUG-AUDIT-007` |

覆盖率薄弱但未直接形成缺陷的区域包括 `useTheme.ts`、`projectCache.ts`、
`useUndoDelete.ts` 和部分 API 包装器。本轮通过浏览器主题持久化、跨账号权限与缓存链路
补充了运行时证据。

## Playwright 浏览器 E2E

最终执行 30 项：Playwright 汇总为 28 passed、2 failed。

| 浏览器/视口 | 状态 | 结果 |
|---|---|---|
| Chromium desktop | PASS | 13/13；其中 4 项为已知缺陷预期失败 |
| Chromium Pixel 7 | PASS | 13/13；其中 4 项为已知缺陷预期失败 |
| WebKit desktop smoke | PASS | 2/2 |
| Firefox desktop smoke | BLOCKED | 2 项在启动前失败，Windows side-by-side 配置错误 |

正常通过的浏览器链路覆盖：

- 未登录路由守卫；
- 注册、登录、刷新恢复、退出与 Token 清理；
- 项目/任务创建及刷新恢复；
- 跨用户项目读写拒绝；
- 普通用户管理员接口拒绝；
- 团队创建/加入、TEAM 周复盘隐私白名单、成员退出、任务解绑；
- AI 草稿预览、服务端恢复、取消、取消后确认拒绝且不创建项目；
- 仪表盘、周复盘、AI 规划、RAG、Agent、团队、设置七个页面；
- 深浅主题立即生效和刷新持久化。

预期失败用例覆盖 `BUG-AUDIT-002` 至 `BUG-AUDIT-005`。它们在当前版本必须失败；
修复后会因“意外通过”提醒移除 `test.fail` 并转为普通回归断言。

## Docker 与故障注入

| 项目 | 状态 | 结果 |
|---|---|---|
| 当前 SHA Stage 7 九容器栈 | PASS | 所有容器健康 |
| 运行时 health/OpenAPI | PASS | business、liveness、readiness；95 个运行时接口 |
| RAG/Agent/指标 smoke | PASS | 总耗时 3,253 ms |
| Redis 停止/恢复 | PASS | readiness 保持 UP；AI health 降级后恢复 |
| Qdrant 停止/恢复 | PASS | readiness 保持 UP；AI health 降级后恢复 |
| AI Stub 停止/恢复 | PASS | 核心 health code=0；AI 返回 30001；Stub 可恢复 |
| Prometheus 停止/恢复 | PASS | business health 与 readiness 保持 UP |
| Tempo 停止/恢复 | PASS | business health 与 readiness 保持 UP |
| 独立 Worker 进程停止 | NOT_APPLICABLE | Worker 内嵌后端；租约/旧 token 场景由单元和集成测试覆盖 |

AI 依赖健康缓存按 30 秒刷新；Redis 恢复状态在第二个刷新周期内回到 UP。

## 规模验证

环境：Windows NT 10.0.26200 X64，Intel Core i5-13500H，16 logical processors，
31.7 GB RAM，MySQL 8.0.41 Docker。

| 数据/查询 | 状态 | 结果 |
|---|---|---|
| 10,000 用户和团队成员 | PASS | 插入 672 ms |
| 100,000 任务 | PASS | 插入 3,027 ms |
| 10,000 周复盘 | PASS | 插入 454 ms |
| 100,000 AI 调用日志 | PASS | 插入 4,100 ms |
| 任务页 Top 100 | PASS | `ANALYZE TABLE` 后 210 ms，扫描 100,000 行；保留性能风险 |
| 逾期任务统计 | PASS | 137 ms，扫描 100,000 行；保留性能风险 |
| 团队复盘 Top 20 | PASS | 36.8 ms，扫描 10,000 行后排序 |
| AI 日志 24 小时计数 | PASS | 38.5 ms，覆盖索引扫描约 99,931 行 |
| 100,000 个 1024 维 Qdrant 点 | BLOCKED | 未执行满量向量写入；见残余风险 |

数据库规模结果没有违反原计划中已明确的 AI SLO，但任务列表和逾期统计缺少能同时覆盖
过滤与排序的索引，建议在修复阶段建立可重复的 API 级性能基线后再决定是否加索引。
