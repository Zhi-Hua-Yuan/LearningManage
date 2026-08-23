# ADR-004：统一 PermissionService

状态：`PROPOSED`

日期：2026-08-23

## 背景

当前各 Service 通过 `UserHolder`、`user_id`、`team_member` 和 `TeamRoleEnum` 分别判断权限。团队任务、周复盘共享和 AI 批量 ID 出现后，分散判断容易产生规则漂移、N+1 和旁路。

## 决策

1. 建立显式调用的 `PermissionService`，阶段 1 不使用注解/AOP 作为主要授权机制。
2. 服务方法取得当前 actor 后，将 actorId 和资源 ID 显式传给权限服务。
3. 权限服务返回 `ProjectAccessScope` 等可信上下文，而不只返回布尔值。
4. 权限拒绝抛出 `PermissionDeniedException`，稳定映射业务码 `40300`。
5. 提供批量项目范围、任务可读 ID 和 VO capabilities 解析方法。
6. Controller 参数校验、前端按钮和缓存值都不能替代服务端授权。
7. AI 预览和确认分别鉴权；确认事务必须重新校验当前权限。

## 建议接口

```java
ProjectAccessScope requireProjectView(Long actorId, Long projectId);
ProjectAccessScope requireProjectManage(Long actorId, Long projectId);
void requireTaskView(Long actorId, Long taskId);
void requireTaskEditContent(Long actorId, Long taskId);
void requireTaskChangeStatus(Long actorId, Long taskId);
void requireTaskReorganize(Long actorId, Long taskId);
void requireTaskAssign(Long actorId, Long taskId);
void requireTaskDelete(Long actorId, Long taskId);
void requireWeeklyReviewFullView(Long actorId, Long reviewId);
void requireWeeklyReviewSharedView(Long actorId, Long reviewId);
Map<Long, ProjectAccessScope> resolveProjectScopes(Long actorId, Collection<Long> projectIds);
Set<Long> filterReadableTaskIds(Long actorId, Collection<Long> taskIds);
```

## 调用边界

必须接入：

- `ProjectService`、`MilestoneService`、`TaskService`；
- `TeamService`、`WeeklyReviewService`、`StatsService`；
- 当前所有接收项目/任务/复盘 ID 的 AI Service 方法；
- 分配历史和 TEAM 周复盘列表。

Mapper 不自行读取 `UserHolder`；权限事实由应用服务和 PermissionService 组织。

## N+1 策略

- 页面已有任务列表时，以其 `projectId` 集合一次解析项目范围；
- 批量 taskId 通过 join/IN 查询一次获取任务、项目和成员关系；
- 用户显示名批量查询，不逐条 `selectById`；
- `capabilities` 使用同一批范围计算，不为每个 VO 再查数据库。

## 备选方案

### 继续在各 Service 内复制判断

拒绝。无法保证普通接口、统计和 AI 入口使用同一规则。

### 只依赖前端隐藏按钮

拒绝。客户端不可信，且无法保护直接 API 和 AI Tool 调用。

### 立即迁移到 Spring Security 方法注解

延期。资源授权需要加载项目、团队成员和任务受理人，先用显式服务固定语义；身份认证框架迁移可独立进行。

## 验收

- 权限矩阵参数化测试全部通过；
- 越权业务 ID 放行数为 0；
- 100 个资源没有逐条权限查询；
- 所有现有 AI 资源入口没有旁路；
- SYSTEM_ADMIN 不会因系统角色被默认放行私人内容。
