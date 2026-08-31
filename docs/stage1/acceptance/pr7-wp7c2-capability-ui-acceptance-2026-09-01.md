# PR7 / WP7-C2 Capability UI 与任务事实展示验收记录

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-01

## 1. 验收结论

WP7-C2 已完成并完成前端受保护合并。本工作包将任务 capability 接入任务页面操作守卫，完成负责人事实展示和 stale interaction 清理。负责人候选、初始负责人选择、CAS 分配及负责人历史由 WP7-C3～C5 继续负责。

## 2. 前端合并证据

| 项目 | 结果 |
|---|---|
| 前端仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| 前端分支 | `pr7-c2-1-capability-ui` |
| PR | [#19](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/19) |
| PR head 提交 | `8fae08c588dca1686fc52385e4acb4bd8138ff9a` |
| merge SHA | `647a61a0bd1fd6d0b8b2770bdac9e852edd65546` |
| PR CI run | [33402079771](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33402079771) |
| post-merge CI run | [33404034903](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33404034903) |
| post-merge CI 结果 | `PASS` |

## 3. C2 交付范围

- 任务标题、描述、截止日期由 `canEditContent` 控制；
- 状态控件由 `canChangeStatus` 控制；
- 优先级、里程碑由 `canReorganize` 控制；
- 负责人事实和负责人权限由 `canAssign` 展示；
- 删除控件由 `canDelete` 控制；
- 控件禁用之外，事件处理器保留 capability 二次守卫；
- capability 变化导致的失权会清理对应弹层、菜单和过期交互；
- 当前负责人、创建人、分配人和离队负责人使用安全降级展示；
- 任务切换会清理旧任务的编辑、状态和菜单交互。

## 4. 本地门禁

| 门禁 | 结果 |
|---|---|
| `npm run test:ci` | `20 files / 124 tests PASS` |
| `npm run test:coverage` | `74.75% statements / 62.67% branches / 71.15% functions / 78.99% lines` |
| `npm run type-check` | `PASS` |
| `npm run lint:ci` | `PASS` |
| `npm run contract:test` | `PASS` |
| `npm run contract:verify` | `44 operations PASS` |
| `npm run build` | `PASS` |
| `git diff --check` | `PASS` |

## 5. 合同与边界

- operation 数量保持 `44`；
- 合同 SHA-256 保持为 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6`；
- 未修改后端接口、数据库迁移或新增前端 operation；
- C2 不提前实现团队成员选择、初始负责人创建、负责人 CAS 或历史抽屉；
- `S1-R-013` 继续保持开放，等待 WP7-E 的全局缓存与跨页面回归验收。

## 6. 后续目标

WP7-C2 关闭后进入 WP7-C3：成员候选懒加载和创建任务初始负责人选择。
