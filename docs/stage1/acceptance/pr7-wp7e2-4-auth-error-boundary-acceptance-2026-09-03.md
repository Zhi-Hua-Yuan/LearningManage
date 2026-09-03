# PR7 WP7-E2-4 认证错误边界与 401/403 回归验收

日期：2026-09-03  
状态：`PASS / COMPLETED / MERGED / CI_PASS`

受保护合并：前端 PR [#49](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/49)，merge commit `04127319c3829e98c8b0331f4beff7a364a37083`；backend 证据 PR [#96](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/96)，merge commit `c4f1fe09b331c142f563b280cdcf1b6a426e721e`。

Develop post-merge CI：前端 run `33755415089`、backend run `33755426428`，均为 `completed / success`。

## 1. 范围

统一请求层按 HTTP 状态优先、业务码补充的规则区分认证失效与权限拒绝：

- HTTP 401、业务码 `40100`（无冲突时）进入认证失效处理；
- HTTP 403、业务码 `40101/40300` 进入权限拒绝处理，不清理或跳转登录；
- HTML 401/403 响应按同一规则分类；
- public/local-auth 请求（登录、登出）不会误触发全局会话终止；
- 认证失效处理幂等，重复 401 只产生一次清理和跳转；
- 主动登出先清理本地会话后，后端 `/user/logout` 返回 401 仍只作为本地请求失败，不重复 toast/跳转。

## 2. 实现与测试变更

- `src/utils/request.test.ts` 扩展 PR7-T-043：覆盖冻结资产清单中的 actor-scoped localStorage key、AI operation sessionStorage key、reset handler、token 及必须保留的主题/布局/backend metadata；
- 新增主动登出与后端 401 竞态对照测试，断言不重复清理、toast 或 router 跳转；
- `src/utils/sessionLifecycle.test.ts` 扩展显式终止会话的敏感内存清理和全局偏好保留断言；
- 生产实现沿用 `classifyApiError`、`handleAuthenticationRequired`、`authFailureMode: 'LOCAL'` 和 `terminateAuthenticatedSession`，未改变 API 路径或响应合同。

## 3. 自动化验证证据

| 门禁 | 结果 |
|---|---|
| E2-4 focused Vitest | `2 files / 24 tests passed` |
| 全量 Vitest | `58 files / 447 tests passed` |
| coverage | Statements `84.76%`；Branches `75.12%`；Functions `85.66%`；Lines `88.43%` |
| storage asset scan | `55`（localStorage `17`、sessionStorage `7`、cache helper `31`） |
| storage policy tests | `14 passed` |
| task cache consistency | `PASS` |
| API contract test / verify | `3 passed` / `44 operations`，SHA-256 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |
| Oxlint / ESLint | `0 warnings / 0 errors` / `PASS` |
| TypeScript | `PASS` |
| production build | `PASS`（766 modules transformed） |

## 4. 验收结论与边界

E2-4 实现、测试、受保护合并和 develop post-merge CI 全部通过。跨账号切换、focus refresh、迟到响应及 `S1-R-013` 最终关闭属于 E3，未在本工作包提前宣称完成。

```text
PR7-T-043：PASS（本地）
WP7-E2-4：PASS / COMPLETED / MERGED / CI_PASS
WP7-E2：PASS / COMPLETED / MERGED / CI_PASS
S1-R-013：OPEN
下一主目标：WP7-E3
```
