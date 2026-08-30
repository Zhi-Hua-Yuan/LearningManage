# PR6 WP6-C1：WeeklyReviewTask Mapper 开发记录（2026-08-31）

## 范围

C1 只实现 `weekly_review_task` 关联表的持久化原语：按复盘批量读取、按复盘删除、批量插入。关联业务、权限判断、复盘保存流程仍由后续 C2/C3 启用。

## 实现

- 新增 `WeeklyReviewTaskMapper` 与 XML 映射。
- 查询显式列出 `id/weekly_review_id/task_id/create_time`，空 review ID 集合返回空结果，并固定 `weekly_review_id ASC, id ASC` 排序。
- 删除严格限定 `weekly_review_id`；批量插入使用绑定参数，依赖 V2 唯一键 `(weekly_review_id, task_id)` 保证重复关联失败。
- 修正 WP6-B 共享查询：V1/V2 的 `weekly_review` 没有 `is_delete` 列，移除错误的 `wr.is_delete = 0` 条件；用户表仍保留 `u.is_delete = 0` 过滤。

## 验收证据

- `WeeklyReviewTaskMapperContractTest`：namespace、列投影、空集合保护、参数绑定、删除范围、批量形状与私有字段排除。
- `WeeklyReviewTaskMapperMySqlTest`：真实 V2 数据库下验证批量读取、稳定排序、空输入、批量写入、跨复盘复用任务、按复盘隔离删除及唯一键冲突。
- 数据库迁移保持 V2，未新增迁移文件。

## 非目标

本 C1 不修改 `WeeklyReviewServiceImpl` 的关联拒绝逻辑，不引入 DTO/VO 变更，不改变 PRIVATE/TEAM 业务语义。
