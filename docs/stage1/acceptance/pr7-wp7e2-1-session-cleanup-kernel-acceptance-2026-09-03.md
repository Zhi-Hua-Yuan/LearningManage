# PR7 WP7-E2-1 统一受保护状态清理内核验收

日期：2026-09-03  
状态：`PASS / MERGED / CI_PASS`

受保护合并：前端 PR [#46](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/46)，merge commit `48af8bb687962c54de0c1840bc362152aa89a9c7`；后端证据 PR [#94](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/94)，merge commit `fbd612eb00719298718509f69a9f2b80c5d5be12`。

Post-merge CI：前端 run `33729041995`、后端 run `33729072385`，全部门禁通过。

## 1. 范围

本工作包实现统一的受保护会话清理内核，覆盖：

- actor-scoped 业务缓存和旧无账号业务 key 的清理匹配；
- AI 草稿确认 operationId 的 actor-scoped sessionStorage key；
- 无 active actor 时 operationId 读写 fail closed；
- reset handler 注册、执行、异常隔离和幂等；
- token 清理原语；
- global preference 与 backend cache infrastructure metadata 保留。

本工作包未接入主动 logout、HTTP 401/403、Pinia Store、页面状态和 router；这些属于 WP7-E2-2/E2-3。focus refresh、跨账号迟到响应和完整 `S1-R-013` 关闭仍属于 WP7-E3。

## 2. 主要实现

前端新增：

- `src/utils/sessionStorageCleanup.ts`
- `src/utils/sessionLifecycle.ts`
- `src/utils/sessionOperation.ts`
- 对应的三个 Vitest 测试文件

`AiDraftDetail.vue` 不再直接生成无账号 sessionStorage key，改为通过 `sessionOperation.ts` 使用：

```text
ai:draft:confirm-operation:{draftId}:actor-{actorId}
```

清理器删除全部 actor-scoped 业务资源、旧无账号业务 key 和 AI confirm operation key，但保留主题、布局尺寸、backend version 和 reload lock。

## 3. 自动化验证

| 门禁 | 结果 |
|---|---|
| E2-1 targeted Vitest | `3 files / 10 tests passed` |
| 全量 Vitest | `57 files / 424 tests passed` |
| coverage | Statements `84.48%`；Branches `75.21%`；Functions `85.02%`；Lines `88.35%` |
| storage asset scan | `55 production accesses` |
| storage policy | `55/55 covered` |
| storage policy tests | `14 passed` |
| task cache consistency | `PASS` |
| Oxlint | `0 warnings / 0 errors` |
| ESLint | `PASS` |
| TypeScript | `PASS` |
| API contract | `44 operations`，SHA-256 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6` |
| production build | `PASS` |

## 4. 验收结论

- `S7-CACHE-015` 的机器策略 owner 已更新为 `src/utils/sessionOperation.ts`；
- 所有新增 storage 访问均被策略门禁覆盖；
- 未修改 API、数据库和 operation 合同；
- E2-1 的清理内核和 operationId 隔离验证通过；
- 未提前关闭 `PR7-T-043`，因为 logout/401 接线尚未进入本工作包；
- `S1-R-013` 继续保持 `OPEN`。

```text
WP7-E2-1：PASS / MERGED / CI_PASS
WP7-E2：PASS / COMPLETED / MERGED / CI_PASS
S1-R-013：OPEN
下一主目标：WP7-E3
```
