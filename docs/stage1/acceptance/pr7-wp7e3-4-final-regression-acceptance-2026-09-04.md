# PR7 / WP7-E3-4 回归、证据与风险关闭验收

日期：2026-09-04  
状态：PASS / COMPLETED / MERGED / CI_PASS

## 1. 收口范围

E3-4 不新增业务接口或缓存语义，只对 E3-1～E3-3 的会话、全局缓存、跨页面刷新和多账号隔离实现做最终回归，并形成可追溯证据：

- 分配后项目任务、聚合任务、成员和 capability 缓存失效；
- 401、主动登出、重新登录和账号切换不复用旧用户敏感内存；
- focus/visibility 刷新去重、single-flight、fail-closed capability、最新任务替换和删除详情收口；
- 团队项目跨页面访问裁剪、失权任务过滤、同 ID 多账号隔离和迟到响应保护；
- 原有 37 个 operation 与新增 operation 合同保持稳定。

本次没有修改 E3-1～E3-3 的业务实现；前端基线为 `fa5d0e4`（`fix(session): close E3-3 task focus refresh races`）。该基线已通过前端受保护 PR [#53](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/53) 合并，Merge SHA 为 `2ef907f292fbbacecf8a68f7d24c4701a555aa8a`，post-merge CI run `33783390301` 三项必需 Job 全部成功。

## 2. 自动化回归证据

| 门禁 | 结果 |
|---|---|
| E3 聚焦/会话聚焦测试 | 3 files / 54 passed |
| 前端全量 `npm run test:ci` | 58 files / 459 passed |
| 覆盖率 | statements 84.83%；branches 75.13%；functions 85.71%；lines 88.49% |
| `npm run contract:test` | 3 passed |
| `npm run contract:verify` | 44 operations valid；SHA-256 `4f8cb8d3…f0b2f6` |
| `npm run test:storage-policy` | 14 passed |
| `npm run lint:ci` | PASS；55 个 production storage access 有策略覆盖；Oxlint 0 warning/error |
| `npm run type-check` | PASS |
| `npm run build` | PASS；766 modules transformed |
| 后端非 MySQL Maven 回归 | 511 passed / 0 failures / 0 errors / 0 skipped |

聚焦测试文件为：`TaskList.assignment-dialog.test.ts`、`collaboration.test.ts`、`sessionLifecycle.test.ts`。它们覆盖窗口 focus/visibility、刷新 single-flight、capability fail-closed、团队强制重验、最新任务替换、失权/删除详情收口、脏编辑排队、会话重置和同 ID 多账号隔离。

## 3. 环境性阻塞与分类

另外执行了本地完整 `mvn test`：`563 tests / 0 failures / 52 errors / 0 skipped`。52 项全部在 Spring SQL fixture 建立连接阶段失败，错误为：

```text
Access denied for user '${TEST_DB_USERNAME}'@'localhost'
```

这属于本机没有注入测试数据库凭据的环境前置失败，不是 E3 业务断言失败。去除 `*MySqlTest` 后的 511 项后端回归全部通过；真实 MySQL 集成门禁仍必须在受保护 CI 的隔离数据库中执行。

## 4. 测试矩阵与风险结论

| 项目 | 结论 |
|---|---|
| `PR7-T-040`～`PR7-T-045` | PASS |
| `S7-GAP-010` 团队项目跨页面访问裁剪 | RUNTIME_CLOSED |
| `S7-GAP-011` capability reset/stale guard | RUNTIME_CLOSED |
| `S1-R-013` 前端缓存保留旧受理人或旧能力 | CLOSED；前端 PR #53、backend PR #100 及两端 post-merge CI 均成功 |

E3-4 的回归、证据和风险关闭责任已完成。前端 PR #53 的 post-merge CI 为 `33783390301`，backend PR #100 的 post-merge CI 为 `33785800984`，两者均成功。WP7-F 继续负责跨仓最终验收、Artifact 和 release 收口。

## 5. 证据索引

- [机器证据 JSON](../evidence/wp7-e3-4/final-regression.json)
- [机器证据文本](../evidence/wp7-e3-4/final-regression.txt)
- [E3-3 页面聚焦验收](pr7-wp7e3-3-focus-refresh-acceptance-2026-09-04.md)
- [PR7 测试矩阵](../frontend/pr7-test-matrix.md)

```text
WP7-E3-4：PASS / COMPLETED / MERGED / CI_PASS
WP7-E3：PASS / COMPLETED / MERGED / CI_PASS
S7-GAP-010：RUNTIME_CLOSED
S7-GAP-011：RUNTIME_CLOSED
S1-R-013：CLOSED
下一主目标：WP7-F / PR7 最终验收
```
