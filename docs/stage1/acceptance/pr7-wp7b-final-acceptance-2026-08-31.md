# PR7 / WP7-B 最终验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-08-31

## 1. 验收结论

WP7-B 已完成。B1～B4 的类型、API、协作上下文 Store、团队项目导航和路由恢复，以及 B5 的任务写路径和请求类型收口均已实现；前端受保护 PR 已合并，合并后的 Frontend CI 已通过。

WP7-B 不包含任务 capability 交互、负责人选择/转派历史、周复盘隐私界面或 WP7-E 全局缓存治理，这些继续由 WP7-C～E 负责。

## 2. 前端合并证据

| 项目 | 结果 |
|---|---|
| 前端仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| 前端 PR | [#17](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/17) |
| PR head SHA | `7a98e4cfbeab44a621e369d4a5d169d5245dc7d5` |
| merge SHA | `d8b60dca602268bc1cba72f74cb1fd6e62215a98` |
| PR CI run | `33374343077` |
| post-merge CI run | [33374541206](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33374541206) |
| post-merge CI 结果 | `PASS` |

## 3. 合同与功能证据

- Stage 0 的 37 个 operation 全部保留；PR7 新增 7 个 operation；当前总数精确为 44。
- 前端合同 SHA-256：`4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6`。
- `/task/status/change` 是唯一状态变更路径；`/task/update` 不携带 `status`。
- 任务内容和重组请求使用显式字段白名单，不展开完整 Task。
- 任务创建和周复盘请求类型与冻结字段合同一致。
- 40300/40101 不清理登录状态；团队资源和成员不写未隔离持久缓存。

## 4. 本地门禁

| 门禁 | 结果 |
|---|---|
| `npm run contract:test` | `3/3 PASS` |
| `npm run contract:verify` | `44 operations PASS` |
| `npm run test:ci` | `14 files / 92 tests PASS` |
| `npm run test:coverage` | `69.38% statements / 59.11% branches` |
| `npm run type-check` | `PASS` |
| `npm run lint:ci` | `0 warnings / 0 errors` |
| `npm run build` | `PASS` |
| `git diff --check` | `PASS` |

## 5. 关闭范围与后续工作

WP7-B 关闭后，下一主目标切换为 WP7-C：任务 capabilities、负责人选择、CAS 冲突和分配历史交互。WP7-D、WP7-E 和 WP7-F 仍保持 `PENDING`。

