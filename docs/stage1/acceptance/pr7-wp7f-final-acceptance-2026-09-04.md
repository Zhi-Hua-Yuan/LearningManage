# WP7-F：PR7 最终测试、契约与验收记录

日期：2026-09-04
结论：`PASS / COMPLETED / CI_PASS`

## 1. 候选冻结

本次验收使用跨仓库候选 `wp7-f-20260904-001`：

| 仓库 | commit |
|---|---|
| LearningManage | `4fa217fcdc8ea11c13aa463a7d95cb680171863f` |
| learning-manage-frontend | `2ef907f292fbbacecf8a68f7d24c4701a555aa8a` |

候选由 [Cross-repository release gate run 33790707384](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33790707384) 冻结，开始和结束时两个 develop 分支 SHA 均未漂移。

## 2. 前端门禁

前端干净候选和保护 CI 均通过：

| 门禁 | 结果 |
|---|---|
| `npm ci --no-audit --fund=false` | PASS（CI） |
| `npm run contract:test` | PASS，3 tests |
| `npm run contract:verify` | PASS，44 operations |
| `npm run test:storage-policy` | PASS，14 tests |
| `npm run test:ci` | PASS，58 files / 459 tests |
| `npm run test:coverage` | PASS，58 files / 459 tests |
| `npm run lint:ci` | PASS，storage 55 accesses，oxlint/eslint 无错误 |
| `npm run type-check` | PASS |
| `npm run build` | PASS，766 modules transformed |

覆盖率：Statements 84.83%、Branches 75.13%、Functions 85.71%、Lines 88.49%。

本机原前端目录的 `npm ci` 曾因 Windows `.vite-temp` 文件锁定返回 `EPERM`；未删除该目录，随后从同一 SHA 创建干净 worktree，`npm ci`、Vitest、lint、type-check 和 build 均通过。最终安装门禁以 CI run 为权威。

## 3. 44 operation 契约

前端导出契约满足：

```text
schemaVersion = 1
basePath = /api
total operations = 44
legacy operations = 37
new PR7 operations = 7
duplicate operations = 0
```

契约 SHA-256：

```text
4F8CB8D3B92252E4375B49DD102E7CDE75F819827713060D6E521BED19F0B2F6
```

7 个新增 operation 全部存在：

```text
POST /task/assign
GET  /task/{taskId}/assignment-history
GET  /team/my
GET  /team/{teamId}/members
GET  /project/team/list
GET  /review/team
POST /task/status/change
```

## 4. 运行时 OpenAPI 比对

运行时比对由 `verify-runtime-api-contract.sh` 在隔离 MySQL、Docker 全栈环境中执行：

| 指标 | 结果 |
|---|---:|
| Runtime OpenAPI | 3.0.1 |
| 前端 operations | 44 |
| Runtime operations | 65 |
| matched operations | 44 |
| missing operations | 0 |
| 比对状态 | PASS |

运行时文档 SHA-256：`B0E898DD8B6535BBA962C5B267E30E7961299B17D9BBCF1515A7865F82674FD2`。比对报告 SHA-256：`595D70320FD4023DB37853734EA8B4CDAEEA614122D35CCF67EC8C1E6AD2EE03`。

## 5. 后端与全栈门禁

权威 CI 结果：

- Maven verification：564 tests，0 failures，0 errors，0 skipped；
- Flyway empty database：PASS；
- Flyway existing database：PASS；
- Docker backend runtime：PASS；
- Nginx/frontend proxy：PASS；
- AI breakdown、cancel、confirm、幂等重放：PASS；
- 全栈 AI 确认结果：1 project、2 milestones、4 tasks。

所有 job 均为 success，job 明细和 artifact 名称见 [WP7-F 机器证据](../evidence/wp7-f/final-acceptance.json) 和 [CI artifact 绑定](../evidence/wp7-f/ci-artifacts.json)。

## 6. 场景矩阵和风险

PR7 测试矩阵共 38 个场景，API、能力权限、任务分配并发、周复盘隐私、缓存和会话五组均为 PASS，详见 [test-matrix-results.json](../evidence/wp7-f/test-matrix-results.json)。

`S1-R-013` 已在 WP7-E3-4 关闭。`S1-R-010` 的 PR7 证据已经补齐，但按照阶段合同保留为 `OPEN`，最终关闭仍归 PR8 的权威跨仓 Candidate Manifest 和 release gate；不能在本记录中提前关闭阶段级风险。

## 7. 收口结论

WP7-F 的退出条件全部满足：前端全量门禁通过、44 operation 与运行时 OpenAPI 匹配且缺失为 0、后端真实 MySQL 回归通过、Flyway 与 Docker 门禁通过、候选 SHA 未漂移、CI artifacts 可追溯。

因此 PR7 可标记为 `PASS / COMPLETED`，下一主目标为 PR8。阶段1正式 tag/release 及 `S1-A-009～012` 仍保持 PR8 范围。
