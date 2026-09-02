# PR7 WP7-E1-1.3 敏感内存资产生命周期验收

状态：`PASS / COMPLETED（本地策略与合同）`

日期：2026-09-03

## 1. 工作范围

本工作包完成 `S7-MEM-001`～`S7-MEM-011` 的生命周期 owner、获取点、派生状态、reset 覆盖、session reset 触发器和 stale response 策略冻结。

本工作包不接入主动登出、401、账号切换、focus refresh，也不关闭 `S1-R-013`。

## 2. 实现证据

前端策略：

```text
learning-manage-frontend/scripts/storage-asset-policy.mjs
```

前端不变量测试：

```text
learning-manage-frontend/scripts/storage-asset-policy.test.mjs
```

生命周期合同：

```text
docs/stage1/frontend/pr7-wp7e1-1-3-sensitive-memory-lifecycle-contract.md
```

## 3. 验收结果

| 验收项 | 结果 |
|---|---|
| 11 项 `S7-MEM-*` 均登记 lifecycle owner | PASS |
| 获取点和派生状态完整登记 | PASS |
| reset 入口、覆盖字段和当前状态登记 | PASS |
| `SESSION_END`、`ACTOR_CHANGE` 触发器完整 | PASS |
| stale guard 策略和 token 完整 | PASS |
| `MEMORY_ONLY` 不允许持久化 | PASS |
| session integration 未提前宣称完成 | PASS |
| 26 项资产 ID 和 44 operation 合同保持不变 | PASS |

## 4. 自动化门禁

```text
npm run test:storage-policy
```

结果：`4 tests / 0 failures / 0 errors`

测试覆盖：

- 26 项资产完整且 ID 唯一；
- 所有资产分类闭合；
- 11 项内存资产均为 `MEMORY_ONLY`；
- 每项内存资产均声明 owner、获取点、派生面、reset 和 stale guard；
- 每项均包含 `SESSION_END` 与 `ACTOR_CHANGE`；
- 基础设施元数据与业务资源策略保持分离。

完整前端回归门禁：

| 命令 | 结果 |
|---|---|
| `npm run test:ci` | PASS；52 files / 384 tests |
| `npm run test:coverage` | PASS；52 files / 384 tests；Statements 83.73%，Branches 75.07% |
| `npm run lint:ci` | PASS；oxlint 0 warnings / 0 errors，eslint PASS |
| `npm run type-check` | PASS |
| `npm run build` | PASS |
| `npm run contract:test` | PASS；3 subtests |
| `npm run contract:verify` | PASS；44 operations，sha256 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |

## 5. 未关闭事项

- `PR7-T-042` 仍需后续运行态不落盘证据；
- `PR7-T-043` 由 E2 接入 logout/401；
- `PR7-T-044` 由 E2/E3 提供多账号和迟到响应证据；
- `PR7-T-045` 由 E3 实现 focus refresh；
- `S1-R-013` 继续保持 `OPEN`。

下一主目标：`WP7-E1-1.4 全源码 storage policy 静态门禁`。
