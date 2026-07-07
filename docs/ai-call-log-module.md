# AI 调用记录模块交付文档

## 一、模块目标

AI 调用记录模块用于记录后端真实发起的大模型调用，支持后续排查 AI 调用失败、解析失败、耗时异常和不同 AI 场景的稳定性。

本模块记录的是“AI 调用本身”的状态，不等同于接口最终是否成功。部分接口具备 fallback 能力，即使 AI 调用失败，接口仍可能返回成功响应。

## 二、数据表说明

初始化脚本：

```text
sql/init_ai_call_log.sql
```

表名：

```text
ai_call_log
```

核心字段：

| 字段 | 说明 |
|---|---|
| `id` | 主键 ID |
| `user_id` | 用户 ID，用于当前用户数据隔离 |
| `scene` | AI 调用场景 |
| `model_name` | 实际调用模型名称 |
| `prompt_type` | Prompt 类型或业务策略 |
| `request_text` | 请求内容 |
| `response_text` | 响应内容 |
| `status` | 调用状态 |
| `error_message` | 错误信息 |
| `cost_time_ms` | 调用耗时，单位毫秒 |
| `retry_count` | 重试次数 |
| `create_time` | 创建时间 |
| `update_time` | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `idx_user_scene_time` | 支持按用户、场景、时间查询 |
| `idx_status_time` | 支持按状态、时间查询 |

## 三、状态枚举说明

枚举类：

```text
src/main/java/com/spt/learningmanage/constant/AiCallLogStatusEnum.java
```

状态定义：

| 状态 | 值 | 说明 |
|---|---:|---|
| `RUNNING` | 0 | 调用中 |
| `SUCCESS` | 1 | AI 调用成功，且返回结果通过解析和业务校验 |
| `FAILED` | 2 | AI 调用阶段失败，例如配置、网络、模型接口异常 |
| `PARSE_FAILED` | 3 | AI 有返回，但 JSON 解析或业务校验失败 |
| `TIMEOUT` | 4 | 超时，当前为预留状态 |

## 四、已接入 AI 场景

| scene | 入口接口 | promptType | 失败行为 |
|---|---|---|---|
| `task-breakdown` | `POST /ai/breakdown/preview`、兼容旧接口 `POST /ai/breakdown` | `default` / `detailed` | 抛出业务错误 |
| `weekly-polish` | `POST /ai/polish`、`POST /ai/polish/preview` | `weekly-polish` | 抛出业务错误 |
| `today-order` | `POST /ai/today-order/recommend` | 请求策略 `strategy` | 返回 fallback 结果 |
| `daily-review-rename` | `POST /ai/daily-review/suggest-rename` | 请求策略 `strategy` | 返回 fallback 结果 |
| `list-replan` | `POST /ai/list/replan/preview` | `preview` | 返回 fallback 预览 |

## 五、调用记录写入规则

通用规则：

- 只有真实调用大模型时才创建 `ai_call_log`。
- 调用前创建 `RUNNING` 记录。
- AI 调用异常时更新为 `FAILED`。
- AI 调用成功但解析或业务校验失败时更新为 `PARSE_FAILED`。
- AI 调用成功且结果可用时更新为 `SUCCESS`。
- `cost_time_ms` 记录从调用前到最终状态更新前的耗时。
- 日志写入失败不阻断主业务流程。

不写入记录的情况：

- 今日任务推荐没有待办任务。
- 日报回顾改名没有未完成任务。
- 清单重排预览没有待重排任务。
- 周总结润色在没有有效任务时走固定兜底文案。
- 草稿确认、取消、详情查询等非 AI 调用操作。

## 六、fallback 场景日志口径

以下场景具备 fallback 能力：

```text
today-order
daily-review-rename
list-replan
```

这些接口中，AI 失败后接口仍会返回本地规则生成的结果。因此：

- `ai_call_log.status = FAILED` 表示 AI 调用失败，不表示接口失败。
- `ai_call_log.status = PARSE_FAILED` 表示 AI 返回不可用，不表示接口失败。
- 接口响应中的 fallback 结果仍属于业务成功响应。

## 七、查询接口说明

Controller：

```text
src/main/java/com/spt/learningmanage/controller/AiCallLogController.java
```

分页查询：

```text
GET /ai/call-log/list
```

支持参数：

| 参数 | 说明 |
|---|---|
| `scene` | 调用场景 |
| `status` | 调用状态 |
| `modelName` | 模型名称 |
| `promptType` | Prompt 类型 |
| `startTime` | 开始时间，ISO 日期时间 |
| `endTime` | 结束时间，ISO 日期时间 |
| `current` | 当前页，默认 1 |
| `size` | 每页数量，默认 10，最大 100 |

详情查询：

```text
GET /ai/call-log/get/{id}
```

查询规则：

- 只查询当前登录用户自己的记录。
- 列表接口返回 `requestPreview` / `responsePreview`。
- 详情接口返回 `requestText` / `responseText`，并提供截断标记。
- 非法 `status` 返回参数错误。
- 不存在或无权限的记录返回参数错误。

## 八、统计接口说明

统计接口：

```text
GET /ai/call-log/stats
```

支持参数：

| 参数 | 说明 |
|---|---|
| `scene` | 调用场景 |
| `startTime` | 开始时间，ISO 日期时间 |
| `endTime` | 结束时间，ISO 日期时间 |

返回指标：

| 指标 | 说明 |
|---|---|
| `totalCount` | 总调用数 |
| `runningCount` | 调用中数量 |
| `successCount` | 成功数量 |
| `failedCount` | 调用失败数量 |
| `parseFailedCount` | 解析失败数量 |
| `timeoutCount` | 超时数量 |
| `successRate` | 成功率，百分比，保留两位小数 |
| `avgCostTimeMs` | 平均耗时 |
| `maxCostTimeMs` | 最大耗时 |
| `minCostTimeMs` | 最小耗时 |
| `sceneStats` | 按场景统计 |
| `statusStats` | 按状态统计 |

统计规则：

- 只统计当前登录用户自己的记录。
- `successRate = successCount / totalCount * 100`。
- 耗时统计只计算 `cost_time_ms` 非空的记录。
- 没有数据时数量为 0，成功率为 `0.00`。

## 九、暂不接入场景说明

以下方法当前不接入 `ai_call_log`：

| 方法 | 原因 |
|---|---|
| `replanListTasks(Long listId)` | 当前无 Controller、Service、定时任务或测试调用入口，属于不可达的内部遗留/预留方法 |
| `chat(String systemPrompt, String userPrompt)` | 当前无业务调用入口，属于通用 AI 对话能力，暂不污染业务统计 |

后续如果开放对应接口，需要先设计明确的 `scene` 和 `promptType`，再接入调用记录。

## 十、测试验证清单

自动化测试：

```bash
.\mvnw.cmd test
```

场景验证：

- `task-breakdown` 成功时记录 `SUCCESS`。
- `weekly-polish` 成功时记录 `SUCCESS`。
- `today-order` 成功时记录 `SUCCESS`，AI 失败时记录 `FAILED` 并返回 fallback。
- `daily-review-rename` 成功时记录 `SUCCESS`，AI 失败时记录 `FAILED` 并返回 fallback。
- `list-replan` 成功时记录 `SUCCESS`，AI 失败时记录 `FAILED` 并返回 fallback。
- AI 返回非法 JSON 或业务校验失败时记录 `PARSE_FAILED`。
- 没有真实 AI 调用时不新增记录。

查询验证：

- `GET /ai/call-log/list` 可分页查询。
- 可按 `scene` 筛选。
- 可按 `status` 筛选。
- 可按时间范围筛选。
- `GET /ai/call-log/get/{id}` 可查看详情。
- 非当前用户记录不可访问。

统计验证：

- `GET /ai/call-log/stats` 返回总览统计。
- 可按 `scene` 统计。
- 可按时间范围统计。
- `startTime > endTime` 返回参数错误。
- `sceneStats` 和 `statusStats` 与数据库记录一致。

## 十一、后续扩展建议

- 增加 Redis 限流，按用户和 AI 场景限制调用频率。
- 增加 `TIMEOUT` 的真实识别和更新逻辑。
- 如果调用记录量变大，将统计逻辑从内存聚合优化为 SQL 聚合。
- 如果希望日志不受主事务回滚影响，可为调用记录更新引入独立事务。
- 后续前端可基于查询和统计接口制作 AI 调用观察面板。
