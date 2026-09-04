# WP1 V2→V3 Schema Diff 摘要

状态：`LOCAL_VERIFIED / CI_PENDING`

## 新增对象

- 表：`ai_draft_confirm_log_archive`。
- `ai_call_log`：23 个可观测性、Usage/成本、Trace、失败/降级、脱敏/截断/哈希字段。
- `ai_draft`：`schema_version`、`trace_id`。
- `ai_draft_confirm_log`：`trace_id`。
- `ai_replan_operation`：`trace_id`。

合计新增 27 个业务表字段。未新增运行时 Mapper、Controller 或公共 API。

## 索引与约束变化

- 新增 `idx_ai_call_log_trace`、`idx_ai_call_log_provider_request`、`idx_ai_call_log_model_time`。
- 新增 `idx_ai_draft_trace`、`idx_ai_replan_operation_trace`。
- 新建唯一索引 `uk_ai_confirm_user_draft(user_id,draft_id)` 后，删除旧索引 `uk_user_draft_op(user_id,draft_id,operation_id)`。
- 归档表以 `source_log_id` 为主键，并增加用户/草稿和归档时间索引。
- AI 调用日志增加脱敏状态、布尔标志、非负 Usage 和非负成本检查约束。

## 兼容性

- `model_name` 保留且继续表示实际模型，只更新字段注释。
- `operation_id`、历史正文和全部原业务表字段保留。
- 存量 `requested_model=model_name`，存量草稿 `schema_version=1`。
- 未知 Token、价格、成本、Trace 和哈希保持 `null`；历史正文状态为 `LEGACY_UNKNOWN`。
- V2 业务表数 22；V3 新增归档表后为 23；Flyway history 不计入业务表。
