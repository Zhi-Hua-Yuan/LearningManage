# PR7 / WP7-B1 共享类型与规范化验收记录

状态：`IMPLEMENTED / LOCAL_VALIDATION_PASS`

日期：2026-08-31

## 1. 验收结论

WP7-B1 已完成。前端建立了 API Wire 类型、页面上下文类型、任务权限类型、任务分配/状态/历史 DTO、周复盘共享白名单类型，以及 ID、分页、角色、项目范围和 capability 的安全规范化函数。

B1 未新增 API 调用、未修改请求路径或 HTTP 方法、未创建 Store、未修改页面运行逻辑，仍处于 WP7-B 的类型基础设施阶段。

## 2. 基线与变更

| 项目 | 值 |
|---|---|
| 前端仓库 | `Zhi-Hua-Yuan/learning-manage-frontend` |
| 前端基线 | `cdff8f777843ab18f0c01c08d5f2ac7a82ec23e9` |
| 原有 operation | `37` |
| 原有 operation SHA-256 | `39CA49E63C1D1F3C6F7D232180F57B20A668B14573AC6C2792C65C4A53F69035` |
| 新增运行时 operation | `0` |

主要交付物：

- `src/types/common.ts`
- `src/types/user.ts`
- `src/types/team.ts`
- `src/types/project.ts`
- `src/types/task.ts`
- `src/types/review.ts`
- `src/types/normalization.ts`
- `src/types/normalization.test.ts`
- `src/types/type-contracts.ts`

同时将 `src/api/task.ts`、`src/api/project.ts`、`src/api/milestone.ts` 的重复 `EntityId` 定义迁移到共享类型。

## 3. 安全边界

- ID 在 API 边界允许 `number|string`，规范化后统一为字符串；超出安全整数范围的 JavaScript number 被拒绝。
- 缺失、字段缺失或类型非法的任务 capability 全部按拒绝处理。
- 未知团队角色和项目范围不会隐式提升为有效角色或权限。
- `SharedWeeklyReview` 显式白名单不包含 `reflection`、`nextPlan`、`taskIds`。
- `AssignTaskPayload.expectedAssigneeUserId` 为必填属性，允许显式传 `null`。
- 任务内容更新 DTO 不包含 `status`。

## 4. 门禁证据

| 门禁 | 结果 | 证据 |
|---|---|---|
| TypeScript 类型检查 | `PASS` | `npm run type-check` |
| B1 规范化测试 | `PASS` | 8 tests passed |
| 全量 Vitest | `PASS` | 8 files / 51 tests passed |
| API 合同测试 | `PASS` | 3 subtests passed |
| API 合同校验 | `PASS` | 37 operations；SHA-256 保持 `39ca49e63c1d1f3c6f7d232180f57b20a668b14573ac6c2792c65c4a53f69035` |
| Oxlint / ESLint | `PASS` | 0 warnings / 0 errors |
| 页面本地存储与任务缓存回归检查 | `PASS` | 两项脚本通过 |
| 生产构建 | `PASS` | Vite build 完成 |

## 5. 后续准入

WP7-B2 可以开始实现 7 个新增 API 函数。B2 必须复用本记录中的 Wire 类型和规范化边界，不得在 API 文件中重新声明 ID、权限或共享复盘类型。
