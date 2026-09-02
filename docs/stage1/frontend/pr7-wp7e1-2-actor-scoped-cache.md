# WP7-E1-2 Actor-scoped Cache Implementation

状态：`PASS / COMPLETED / MERGED / CI_PASS`

日期：2026-09-03

前置：WP7-E1-1.1～1.5 已通过受保护 PR 合并并完成 post-merge CI。

## 1. 目标与边界

本工作包将 E1-1.5 路由到 E1-2 的 CACHE-005～012 个人资源和业务草稿缓存改为 actor-scoped key，并在 actor 未确定时 fail closed。

本工作包不删除旧无账号 key，不接入全局 logout/401 清理，也不修改 API、数据库或 operation 合同；旧 key 清理属于 E1-3，会话生命周期清理属于 E2。

## 2. 实现

- 新增 active actor cache context，由 collaboration store 在当前用户建立、账号切换和上下文清理时同步设置/清除；
- 受保护缓存统一追加 `:actor-{encodeURIComponent(actorId)}` 命名空间；
- 覆盖 selected project、project list、project progress、project task list、aggregate task list、AI planner draft、today AI order 和 list replan state；
- actor 缺失时所有受保护缓存读操作返回空值，写/删操作不产生 storage side effect；
- 旧无账号业务 key 不再被读取、复制或升级；theme 等 `GLOBAL_PREFERENCE` 保持原有全局 key；
- 任务缓存全量清理只匹配当前 actor 的 key，不触碰其他 actor 的缓存。

示例 key：

```text
tick_selectedProjectId:actor-1
tick:cache:project-list:status-0:v1:actor-1
tick:cache:task-list:v1:101:actor-1
tick_aiPlannerDraft_v1:actor-1
```

## 3. 自动化验证

- actor 缺失 fail-closed：PASS；
- actor 1/2 顺序登录隔离：PASS；
- 旧无账号 key 不升级：PASS；
- 协作 store actor 建立/切换/认证失败清理：PASS；
- 全量 Vitest：53 files / 388 tests passed；
- storage policy：14 tests passed；
- storage policy scanner：52 production accesses covered；
- task cache consistency、Oxlint、ESLint：PASS；
- TypeScript type-check：PASS；
- production build：PASS；
- API operation contract：44 operations，SHA-256 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6`。

## 4. 受保护合并证据

前端 [PR #43](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/43) 已通过 `protect-develop-v1` 后 squash 合并：

- Merge SHA：`134994685f53287f4c4919259894dfb4d4c86180`；
- PR CI run：`33665054286`；
- develop post-merge CI：[run `33665308048`](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33665308048)；
- post-merge jobs：Guard `100365470531`；tests/static `100365532366`；production build `100365931045`；
- 三项门禁均为 `completed / success`。

## 5. 未关闭事项

- E1-3：删除旧无账号业务 key，并修正 backend version 清理边界；
- E2：接入 logout、401、token clear、actor change 的统一缓存和内存 reset；
- E3：补齐跨页面 focus、capability 和 stale response 运行时保护；
- `S1-R-013` 继续保持 `OPEN`。
