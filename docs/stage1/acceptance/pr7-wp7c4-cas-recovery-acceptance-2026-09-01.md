# PR7 / WP7-C4-5 CAS 冲突恢复验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-01

## 1. 验收结论

WP7-C4-5 已完成前端任务负责人分配的 CAS 冲突与不确定结果恢复。冲突或网络/服务端不确定结果不会自动重复提交；前端先刷新服务端事实，保留用户目标与原因，并要求用户显式重新确认。若刷新后发现目标已经由其他请求应用，则仅更新本地事实并关闭对话框，不发送第二次分配请求。

## 2. 前端合并证据

| 项目 | 结果 |
|---|---|
| 前端仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| 实现分支 | `pr7-c4-5-cas-conflict-recovery` |
| PR | [#25](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/25) |
| PR head 提交 | `5b8d9334751fb9f0e105a9ef5ddf4fe1f2a4fa14` |
| merge SHA | `67b6454af0f9a3e509cbd4242eaad413ef6ee6ed` |
| PR CI run | [33478521536](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33478521536) |
| post-merge CI run | [33479137728](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33479137728) |
| post-merge CI 结果 | `PASS` |

## 3. 交付范围

- 区分 CAS 冲突、网络/服务端不确定结果、已提交后刷新失败三类恢复状态；
- 恢复期间冻结上下文切换和能力失效清理，避免旧草稿覆盖新事实；
- 失败刷新只清理项目任务缓存并重新读取当前任务与能力，不自动重放 POST；
- 刷新后重新基线化 `expectedAssigneeUserId`，保留目标负责人和 reason；
- 目标已被其他请求应用时，直接采用最新事实并关闭，不产生重复分配；
- 任务失效、权限撤销和恢复刷新失败均 fail-closed，并提供可理解的重试/重新确认入口；
- `40300` 不触发登出，`40400` 关闭已失效任务详情；
- reason 按纯文本展示，用户名为空时降级为用户 ID，不请求额外敏感字段。

## 4. 本地门禁

| 门禁 | 结果 |
|---|---|
| `npm run test:ci` | `30 files / 212 tests PASS` |
| `npm run test:coverage` | `78.47% statements / 67.45% branches / 75.79% functions / 82.02% lines` |
| `npm run type-check` | `PASS` |
| `npm run lint:ci` | `0 warnings / 0 errors` |
| `npm run contract:test` | `3/3 PASS` |
| `npm run contract:verify` | `44 operations PASS` |
| `npm run build` | `PASS` |
| `git diff --check` | `PASS` |

## 5. 重点场景与合同边界

- CAS 冲突重新读取最新负责人后必须显式重新确认；
- 不确定结果刷新发现目标已应用时不允许第二次 POST；
- 恢复重试只刷新事实，不隐式重放写请求；
- 目标负责人离开团队后保留草稿目标但禁用提交；
- 任务失效、权限变化和恢复错误均不泄漏旧权限事实；
- 未新增或修改前端 API operation、后端接口、数据库迁移或 44-operation 合同；
- 合同 SHA-256 保持为 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6`。

## 6. 后续目标

WP7-C4-5 关闭后，WP7-C4 的负责人分配、转派、解除和 CAS 恢复链路完成。WP7-C 整体仍保持 `PENDING`，下一主目标切换为 C5：负责人变更历史与相关状态交互。WP7-D、WP7-E 和 WP7-F 继续保持 `PENDING`。
