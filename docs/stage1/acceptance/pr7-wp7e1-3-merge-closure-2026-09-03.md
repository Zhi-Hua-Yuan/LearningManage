# PR7 WP7-E1-3 旧缓存与 backend-version 失效合并收口

日期：2026-09-03

状态：`PASS / COMPLETED / MERGED / CI_PASS`

## 1. 收口范围

E1-3 完成旧无账号业务缓存删除和 backend cache-version 精确失效边界收紧。旧 key 不再读取、升级或迁移；backend version 变化只清理 actor-scoped 的服务端业务资源缓存，不触碰会话、用户偏好、AI 草稿、元数据和 `CACHE-015`。

本工作包不接入主动登出、HTTP 401 或账号切换清理；这些运行时生命周期仍由 WP7-E2 负责，页面重新聚焦刷新由 WP7-E3 负责。

## 2. 实现与本地验证

| 项目 | 结果 |
|---|---|
| 旧无账号业务 key | CACHE-005～012 删除；不读取、不升级、不迁移 |
| backend version 清理 | 仅匹配 7 类 actor-scoped 后端业务 key；移除全量 `tick_` / `tick:` 前缀清理 |
| 多账号清理 | 可同时清理多个 actor 的业务资源缓存 |
| 保留项 | token、theme、sidebarWidth、detailWidth、AI planner draft、backend version 元数据、CACHE-015 |
| storage 枚举异常 | fail-closed，不中断业务请求 |
| 聚焦测试 | 3 files / 38 tests passed |
| 全量 Vitest | 54 files / 414 tests passed |
| 覆盖率 | Statements 84.25%；Branches 75.00%；Functions 84.64%；Lines 88.15% |
| API 合同 | 44 operations；SHA-256 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |
| 静态与构建 | contract test/verify、lint、cache-view lint、type-check、production build、diff check 全部通过 |

## 3. 前端受保护 PR 与合并后 CI

| 项目 | 值 |
|---|---|
| PR | [#45](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/45) |
| 标题 | `WP7-E1-3：删除旧无账号缓存并收紧 backend-version 失效范围` |
| Merge SHA | `01ddeacca9c82b28868ff3cfe9925fd6cd893b32` |
| PR CI | 三项 required checks 全部 `success` |
| develop post-merge CI | [run 33710958405](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33710958405) |
| post-merge 结论 | `completed / success` |

## 4. 风险与下一步

- `S7-GAP-005`（旧无账号业务 key）和 `S7-GAP-006`（全量 tick 前缀清理）已由 E1-3 实现并验证。
- `S1-R-013` 继续保持 `OPEN`；全局缓存、401、登出和多账号运行时关闭证据仍需 E2/E3。
- 下一主目标切换为 `WP7-E2：主动登出与 401 受保护状态清理`。

```text
WP7-E1-1：PASS / COMPLETED / MERGED / CI_PASS
WP7-E1-2：PASS / COMPLETED / MERGED / CI_PASS
WP7-E1-3：PASS / COMPLETED / MERGED / CI_PASS
WP7-E1：IN_PROGRESS
WP7-E2：PENDING
WP7-E3：PENDING
S1-R-013：OPEN
下一主目标：WP7-E2
```
