# PR7 / WP7-C1 最终验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-08-31

## 1. 验收结论

WP7-C1 已完成并完成前端受保护合并。C1 建立了任务 `TaskModel`、capability fail-closed、任务缓存 v2 安全降级、网络权限重验和精确 action capability 事件守卫基础。负责人选择、CAS 冲突和分配历史交互仍由 WP7-C2～C5 实现，WP7-C 整体继续保持 `PENDING`。

## 2. 前端合并证据

| 项目 | 结果 |
|---|---|
| 前端仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| 前端 PR | [#18](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/18) |
| PR head 提交 | `dbb407ce6a798c0e84a887d7b7a4de65baa1f8c2` |
| merge SHA | `2b43df5513b95064f3c6f1b7208ff4d33ae537a8` |
| PR CI run | [33381904864](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33381904864) |
| post-merge CI run | [33382066150](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33382066150) |
| post-merge CI 结果 | `PASS` |

## 3. C1 交付范围

- `TaskList.vue` 统一使用共享 `TaskModel`，任务响应经过运行时规范化。
- 缺失或非法 capability 整体按 deny-all 处理。
- 新增精确 action → capability 策略，写入口在乐观修改、请求 ID 生成和 API 调用前完成守卫。
- 任务缓存 schema 升级为 v2；capability 在缓存写入和读取两端强制 deny-all。
- 缓存命中后继续执行网络权限重验，网络失败时缓存任务保持只读。
- 写操作使用任务列表中的最新任务对象鉴权。
- `40300` 后立即降权并刷新任务；删除延迟提交失败时先回滚，再恢复最新权限。

## 4. 本地门禁

| 门禁 | 结果 |
|---|---|
| `npm run type-check` | `PASS` |
| `npm run test:ci` | `17 files / 106 tests PASS` |
| `npm run test:coverage` | `72.67% statements / 60.72% branches / 67.19% functions / 77.10% lines` |
| `npm run lint:ci` | `0 warnings / 0 errors` |
| `npm run contract:test` | `3/3 PASS` |
| `npm run contract:verify` | `44 operations PASS` |
| `npm run build` | `PASS` |
| `git diff --check` | `PASS` |

## 5. 合同与边界

- API operation 数量保持 `44`。
- 前端合同 SHA-256 保持为 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6`。
- 未新增后端 API、数据库迁移或新的前端 operation。
- PR7-T-010～027 的完整交互验收留给 WP7-C 后续工作包；C1 只关闭模型、缓存和事件守卫基础。
- `S1-R-013` 不在 C1 关闭，继续由 WP7-E 负责全局缓存与会话治理。

## 6. 后续目标

WP7-C1 关闭后，下一主目标切换为 WP7-C2：capability 驱动的任务操作界面与负责人入口接线。WP7-C、WP7-D、WP7-E 和 WP7-F 仍按各自合同继续执行。
