# PR7 WP7-E2-3 主动登出与重新登录验收

日期：2026-09-03  
状态：`PASS / COMPLETED / MERGED / CI_PASS`

受保护合并：前端 PR [#49](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/49)，merge commit `04127319c3829e98c8b0331f4beff7a364a37083`；backend 证据 PR [#96](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/96)，merge commit `c4f1fe09b331c142f563b280cdcf1b6a426e721e`。

Develop post-merge CI：前端 run `33755415089`、backend run `33755426428`，均为 `completed / success`。

## 1. 范围

主动登出必须先在本地终止认证会话并清理受保护状态，再以 best-effort 调用后端 `/user/logout`；重新登录建立新的 actor-scoped cache namespace。后端登出请求失败不能恢复旧 token，也不能阻断登录页跳转。

## 2. 实现证据

- 前端实现基线：`289e515`（immediate logout and re-login）;
- `BasicLayout.executeLogout` 捕获旧 token，先调用 `terminateAuthenticatedSession('USER_LOGOUT')`，随后路由到登录页并 best-effort 请求后端;
- `LoginView` 通过 `establishAuthenticatedSession(token)` 建立新会话;
- `/user/logout` 使用 `authFailureMode: 'LOCAL'`，后端 401 不触发第二次全局过期处理。

## 3. 自动化验证

| 门禁 | 结果 |
|---|---|
| 全量 Vitest | `58 files / 447 tests passed` |
| logout API captured credential | `PASS` |
| immediate cleanup before backend logout | `PASS` |
| TypeScript / production build | `PASS` / `PASS` |

## 4. 验收结论

E2-3 的本地主流程和失败回退已验证；远端受保护合并和 post-merge CI 待证据 PR 收口。

```text
WP7-E2-3：PASS / COMPLETED / MERGED / CI_PASS
S1-R-013：OPEN（等待 E2-4、E3 证据）
```
