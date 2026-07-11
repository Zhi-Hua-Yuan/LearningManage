# AI 调用记录接口

本接口用于查询当前登录用户发起的真实 AI 调用记录。草稿详情、确认和取消属于本地业务操作，不会产生新的 AI 调用记录。

通用约定见 [AI 接口文档](README.md)。

## 枚举说明

### 调用状态

| status | statusText | 含义 |
|---:|---|---|
| `0` | `调用中` | 已创建记录，调用尚未结束。 |
| `1` | `成功` | AI 调用成功，结果通过解析和业务校验。 |
| `2` | `调用失败` | 配置、网络或模型调用等失败。 |
| `3` | `解析失败` | AI 有响应，但 JSON 解析或业务校验失败。 |
| `4` | `超时` | AI 调用超时。 |

### Prompt 元数据

| 字段 | 含义 |
|---|---|
| `promptType` | Prompt 模板编码或类型，例如 `task-breakdown.default`。 |
| `promptTemplateId` | 实际使用的数据库 Prompt 模板 ID；内置模板可为 `null`。 |
| `promptVersion` | 实际使用的 Prompt 版本。 |
| `promptSource` | 模板来源，当前取值为 `database` 或 `builtin`。 |

## 1. 分页查询调用记录

```text
GET /api/ai/call-log/list
```

### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `scene` | string | 否 | - | AI 场景，例如 `task-breakdown`。 |
| `status` | integer | 否 | - | 调用状态，取值见状态表。 |
| `modelName` | string | 否 | - | 实际模型名称。 |
| `promptType` | string | 否 | - | Prompt 模板编码或类型。 |
| `startTime` | string | 否 | - | 起始时间，ISO-8601 本地日期时间。 |
| `endTime` | string | 否 | - | 结束时间，ISO-8601 本地日期时间。 |
| `current` | integer | 否 | `1` | 当前页码。 |
| `size` | integer | 否 | `10` | 每页数量，最大 100。 |

请求示例：

```text
GET /api/ai/call-log/list?scene=task-breakdown&status=1&current=1&size=10
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "records": [
      {
        "id": 501,
        "scene": "task-breakdown",
        "modelName": "qwen-plus",
        "promptType": "task-breakdown.default",
        "promptTemplateId": null,
        "promptVersion": 1,
        "promptSource": "builtin",
        "requestPreview": "{...}",
        "responsePreview": "[{...}]",
        "status": 1,
        "statusText": "成功",
        "errorMessage": null,
        "costTimeMs": 1280,
        "retryCount": 0,
        "createTime": "2026-07-11T10:10:00",
        "updateTime": "2026-07-11T10:10:02"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

列表接口只返回 `requestPreview` 和 `responsePreview`，适合表格或列表展示；需要完整文本时再调用详情接口。

## 2. 查询调用记录详情

```text
GET /api/ai/call-log/get/{id}
```

### 路径参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | number | AI 调用记录 ID，必须为正整数。 |

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 501,
    "userId": 1001,
    "scene": "task-breakdown",
    "modelName": "qwen-plus",
    "promptType": "task-breakdown.default",
    "promptTemplateId": null,
    "promptVersion": 1,
    "promptSource": "builtin",
    "requestText": "{\"systemPrompt\":\"...\",\"userPrompt\":\"...\"}",
    "requestTextTruncated": false,
    "responseText": "[{\"name\":\"第一阶段\",\"tasks\":[]} ]",
    "responseTextTruncated": false,
    "status": 1,
    "statusText": "成功",
    "errorMessage": null,
    "costTimeMs": 1280,
    "retryCount": 0,
    "createTime": "2026-07-11T10:10:00",
    "updateTime": "2026-07-11T10:10:02"
  }
}
```

`requestTextTruncated` 和 `responseTextTruncated` 为 `true` 时，表示服务端为保护接口响应大小已截断对应文本。调用记录仅允许当前登录用户查询；不存在或不属于当前用户的记录会返回业务错误。

## 3. 查询调用记录统计

```text
GET /api/ai/call-log/stats
```

### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `scene` | string | 否 | 按 AI 场景过滤。 |
| `startTime` | string | 否 | 起始时间，ISO-8601 本地日期时间。 |
| `endTime` | string | 否 | 结束时间，ISO-8601 本地日期时间。 |

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "totalCount": 12,
    "runningCount": 0,
    "successCount": 10,
    "failedCount": 1,
    "parseFailedCount": 1,
    "timeoutCount": 0,
    "successRate": 83.33,
    "avgCostTimeMs": 1100,
    "maxCostTimeMs": 2100,
    "minCostTimeMs": 550,
    "sceneStats": [
      {
        "scene": "task-breakdown",
        "totalCount": 12,
        "runningCount": 0,
        "successCount": 10,
        "failedCount": 1,
        "parseFailedCount": 1,
        "timeoutCount": 0,
        "successRate": 83.33,
        "avgCostTimeMs": 1100
      }
    ],
    "statusStats": [
      {
        "status": 1,
        "statusText": "成功",
        "count": 10
      }
    ]
  }
}
```

统计范围只包含当前登录用户的记录。`successRate` 的计算方式为 `successCount / totalCount * 100`；没有数据时计数和成功率均为 `0`。耗时指标仅统计 `costTimeMs` 非空的记录。

