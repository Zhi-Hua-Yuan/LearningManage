# PR7 周复盘隐私界面合同

状态：`DRAFT（WP7-A 设计冻结候选，本地静态验收通过）`

日期：2026-08-31

## 1. 类型隔离

前端必须定义两个不相互继承的类型：

### 作者完整类型

允许字段：

```text
id, authorUserId, year, weekNo, startDate, endDate,
completedTaskCount, visibilityScope, teamId,
focusProjectId, focusProjectName, sharedSummary,
reflection, nextPlan, taskIds, createTime, updateTime
```

### 团队共享类型

只允许：

```text
id, author{id,username}, year, weekNo, startDate, endDate,
focusProject{id,name}, sharedSummary, createTime, updateTime
```

团队共享类型和组件禁止定义、读取、导出或条件探测：

```text
reflection, nextPlan, taskIds, 私人项目详情
```

## 2. PRIVATE 状态

- 新建复盘默认 `PRIVATE`；
- 不要求选择团队；
- `teamId` 显式提交为 `null`；
- 不要求 `sharedSummary`；
- 作者完整正文只出现在“我的复盘”；
- PRIVATE 记录绝不出现在团队共享列表。

## 3. TEAM 状态

- 必须选择一个当前有效团队；
- `sharedSummary` trim 后必须非空；
- 首版只允许一个团队；
- 重点项目和所有关联任务必须来自所选团队；
- 切换目标团队时清空原 `focusProjectId` 和 `taskIds`；
- 作者退出团队或失权时，保留本地未提交正文，但不能继续提交该 TEAM 目标；
- 固定提示：`仅向所选团队共享摘要。本周复盘、下周计划和关联任务仍然只有你自己可见。`

## 4. 状态切换

### PRIVATE → TEAM

```text
选择 TEAM
→ 选择一个有效团队
→ 输入独立共享摘要
→ 仅从该团队选择重点项目和任务
```

### TEAM → PRIVATE

- 清空 `teamId`；
- 清空跨团队限定的 `focusProjectId/taskIds`，或重新按当前可访问范围校验；
- 共享摘要可以作为未发布表单草稿保留，但 PRIVATE 请求不得把它解释为已共享；
- 界面明确显示当前不会向团队发布。

### TEAM A → TEAM B

- 必须清空 A 的重点项目和任务；
- 重新加载 B 的项目和任务；
- 不允许把 A 的关联 ID 带入 B 的保存请求。

## 5. 关联选择

| 可见性 | 重点项目 | 关联任务 |
|---|---|---|
| PRIVATE | 作者当前可访问的个人或团队项目 | 作者当前可访问的任务 |
| TEAM | 所选团队项目 | 所选团队任务 |

共同规则：

- 一个重点项目；
- 任务去重后最多 500 条；
- 达到 500 条后禁用继续选择并显示限制；
- 编辑历史记录时以后端返回的当前可读关联为准；
- 后端已过滤的失权项目或任务不在前端恢复；
- 选择器不把任务正文带入团队共享卡片。

## 6. 作者与团队视图

页面区分：

```text
我的复盘：完整详情、编辑、删除、作者导出
团队动态：共享摘要卡片，只读
```

团队动态：

- 调用 `/review/team`；
- 不调用 `/review/{id}` 获取非作者完整详情；
- 不显示编辑、删除或私人正文导出；
- `sharedSummary` 按纯文本渲染；
- 当前成员失权后清空团队列表和相关内存状态。

## 7. 统计与 AI

- `completedTaskCount`、`focusProjectName` 使用后端返回结果，前端不得覆盖；
- 如保留客户端完成率，只能统计 `assigneeUserId == currentUserId` 的任务；
- 不得把“当前可查看的全部团队任务”当作“本人执行任务”；
- AI 周复盘润色优先使用作者显式选择的 `taskIds`；
- 无显式选择时只能使用本周已完成、当前可读且负责人为当前用户的任务；
- AI 权限错误后刷新候选任务，不部分忽略非法 ID 后重试。

## 8. 文案和可访问性

- PRIVATE/TEAM 使用可键盘操作的单选或分段控件；
- TEAM 团队选择和共享摘要错误与字段关联；
- 共享摘要显示剩余字数或上限提示；
- 保存确认弹窗明确当前可见性；
- 不使用仅颜色区分 PRIVATE 与 TEAM；
- 私人正文、共享摘要和历史 reason 均禁止未经清洗的 `v-html`。
