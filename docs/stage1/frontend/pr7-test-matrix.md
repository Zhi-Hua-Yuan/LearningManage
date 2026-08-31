# PR7 前端测试与验收矩阵

状态：`FROZEN（WP7-A 已合并）`

日期：2026-08-31

## 1. 必需命令

PR7 实现和最终验收至少执行：

```text
npm run contract:test
npm run contract:verify
npm run test:ci
npm run test:coverage
npm run lint:ci
npm run type-check
npm run build
```

组件测试应显式使用受支持的 Vue 测试工具；如果引入 `@vue/test-utils`，必须锁定版本、提交 lockfile 并由依赖审查覆盖。

本矩阵共 38 个测试场景；编号按领域分段，因此 `PR7-T-001`～`PR7-T-045` 之间存在保留号段。

## 2. API 与契约

| ID | 场景 | 预期 |
|---|---|---|
| PR7-T-001 | 导出阶段 0 基线 operation | 原 37 项全部仍存在 |
| PR7-T-002 | 导出 PR7 operation | 新增 7 项，预期总数 44，未解析 0 |
| PR7-T-003 | 分配请求当前负责人为空 | JSON 显式包含 `expectedAssigneeUserId:null` |
| PR7-T-004 | 状态变更 | 调用 `/task/status/change`，不向 `/task/update` 发送 status |
| PR7-T-005 | 团队共享查询 | 使用 `/review/team` 和分页参数 |
| PR7-T-006 | 团队项目/成员 | path、method 和动态参数规范化正确 |

## 3. 任务能力

| ID | 场景 | 预期 |
|---|---|---|
| PR7-T-010 | capability 缺失/非法 | 所有写控件默认关闭 |
| PR7-T-011 | `canEditContent=true` | 只启用标题、描述、截止日期 |
| PR7-T-012 | `canChangeStatus=true` | 使用独立状态接口 |
| PR7-T-013 | `canReorganize=true` | 只启用优先级、里程碑 |
| PR7-T-014 | `canAssign=true` | 启用负责人变更 |
| PR7-T-015 | `canDelete=true` | 启用删除 |
| PR7-T-016 | MEMBER 为负责人 | 可编辑内容/状态，不可重组、转派、删除 |
| PR7-T-017 | MEMBER 非负责人 | 任务只读 |

## 4. 分配与并发

| ID | 场景 | 预期 |
|---|---|---|
| PR7-T-020 | 团队成员加载 | 只展示最新有效成员和未分配选项 |
| PR7-T-021 | `changed=true` | 清缓存并刷新任务、capability、历史 |
| PR7-T-022 | `changed=false` | 幂等成功，无重复本地副作用 |
| PR7-T-023 | `50001` | 撤销乐观结果，刷新后重新确认 |
| PR7-T-024 | `40300` | 不退出登录，刷新任务权限 |
| PR7-T-025 | 用户名为空 | 安全降级展示 ID，不请求敏感字段 |
| PR7-T-026 | reason 含 HTML | 按纯文本展示 |
| PR7-T-027 | 状态网络重试 | 复用同一 clientRequestId |

## 5. 周复盘隐私

| ID | 场景 | 预期 |
|---|---|---|
| PR7-T-030 | 新复盘 | 默认 PRIVATE |
| PR7-T-031 | PRIVATE 保存 | `teamId=null`，不要求共享摘要 |
| PR7-T-032 | TEAM 缺团队 | 阻止提交 |
| PR7-T-033 | TEAM 空白摘要 | 阻止提交 |
| PR7-T-034 | TEAM A 切 TEAM B | 清空 A 的重点项目和任务 |
| PR7-T-035 | 选择第 501 个任务 | 阻止并提示 500 上限 |
| PR7-T-036 | 团队共享类型 | 无 reflection、nextPlan、taskIds |
| PR7-T-037 | 团队共享卡片 | 无编辑、删除、私人正文导出 |
| PR7-T-038 | 服务端统计返回 | 前端不覆盖 completedTaskCount/focusProjectName |
| PR7-T-039 | 失权关联被过滤 | 前端不恢复旧 ID |

## 6. 缓存与会话

| ID | 场景 | 预期 |
|---|---|---|
| PR7-T-040 | 分配成功 | 项目任务与聚合任务缓存均失效 |
| PR7-T-041 | 最新任务响应 | 旧负责人和 capability 被替换 |
| PR7-T-042 | 团队成员/历史/共享摘要 | 不写未隔离 localStorage |
| PR7-T-043 | 主动退出/401 | 清理受保护缓存 |
| PR7-T-044 | 用户 A 后登录用户 B | 不复用 A 的团队任务和 capability |
| PR7-T-045 | 页面重新聚焦 | 刷新当前项目任务列表并替换打开任务的旧事实 |

## 7. 回归与人工可用性

- 个人项目列表、任务创建、编辑、删除保持可用；
- 原周复盘 PRIVATE 保存、历史、编辑、删除保持可用；
- AI 拆解、今日排序、重排和周复盘润色入口无接口回退；
- 桌面和窄屏下负责人选择、历史面板和可见性表单可操作；
- 键盘能够操作 PRIVATE/TEAM、团队选择、负责人选择和确认弹窗；
- 不以颜色作为唯一权限或可见性提示；
- 页面不使用未经清洗的 `v-html` 渲染任务、reason、私人正文或共享摘要。

## 8. 工作包门禁

| 工作包 | 最低门禁 |
|---|---|
| WP7-B | PR7-T-001～006，类型检查、API/Store 单测 |
| WP7-C | PR7-T-010～027，任务组件测试、缓存聚焦测试 |
| WP7-D | PR7-T-030～039，隐私字段白名单测试 |
| WP7-E | PR7-T-040～045，原 37 operation 回归、全量测试 |
| WP7-F | 全部命令、契约 Artifact、受保护合并和 merge closure |
