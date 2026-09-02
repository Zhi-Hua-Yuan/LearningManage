# WP7-E1-1.2 Cache Scope Freeze Acceptance

日期：2026-09-02
状态：`PASS / LOCAL_VALIDATED / UNMERGED / LOCAL_CI_PASS`

## 1. 验收范围

本工作包完成 E1-1.1 盘点资产的目标 scope 冻结，包含 15 个持久化资产和 11 个敏感内存资产。本工作包不修改运行时缓存 key，不接入 actor 隔离、401、登出或 focus 刷新。

## 2. 交付证据

- 人工合同：[pr7-wp7e1-1-2-storage-scope-contract.md](../frontend/pr7-wp7e1-1-2-storage-scope-contract.md)
- 机器策略：前端 `scripts/storage-asset-policy.mjs`
- 策略测试：前端 `scripts/storage-asset-policy.test.mjs`
- 前置盘点：[pr7-wp7e1-1-storage-inventory.md](../frontend/pr7-wp7e1-1-storage-inventory.md)
- 扫描证据：[wp7-e1-1-storage-scan.json](../evidence/wp7-e1-1-2/wp7-e1-1-storage-scan.json)
- 扫描文本：[wp7-e1-1-storage-scan.txt](../evidence/wp7-e1-1-2/wp7-e1-1-storage-scan.txt)

## 3. 验收结果

| 门禁 | 结果 |
|---|---|
| 26 个资产 ID 完整且不重复 | PASS |
| Scope、敏感等级和后续实现目标全部闭合 | PASS |
| `MEMORY_ONLY` 不可持久化 | PASS |
| 全局偏好与基础设施元数据独立 | PASS |
| actor 要求、会话清理、版本清理和旧数据策略全部明确 | PASS |
| `npm run test:storage-policy` | PASS；4 tests / 0 failures |
| storage scan | PASS；56 条命中（localStorage 16 / sessionStorage 4 / cacheHelper 36） |
| `npm run test:ci` | PASS；52 files / 384 tests |
| `npm run lint:ci` | PASS；oxlint 0 warnings / 0 errors，eslint PASS |
| `npm run type-check` | PASS |
| `npm run build` | PASS |
| `npm run contract:test` | PASS；3 subtests |
| `npm run contract:verify` | PASS；44 operations，sha256 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |

## 4. 明确未完成项

- actor-scoped key 尚未实现；
- 旧业务 key 尚未删除；
- cacheVersion 尚未拆分资源清理和偏好保留；
- logout/401 尚未接入统一清理；
- 多账号迟到响应和 focus 刷新尚未实现。

因此本次不关闭 `PR7-T-042～045`，不关闭 `S1-R-013`。

## 5. 状态更新

```text
WP7-E1-1.1：PASS / COMPLETED / MERGED / CI_PASS
WP7-E1-1.2：PASS / LOCAL_VALIDATED / UNMERGED / LOCAL_CI_PASS
WP7-E1-1：IN_PROGRESS
WP7-E：IN_PROGRESS
S1-R-013：OPEN
下一主目标：WP7-E1-1.3
```
