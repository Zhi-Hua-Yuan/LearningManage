# AI 任务拆解草稿接口

本接口用于完成“输入学习目标 → AI 生成草稿 → 用户确认 → 创建项目、阶段和任务”的闭环。正式前端页面应使用草稿流程，不应使用旧版接口后自行逐条创建项目、阶段和任务。

通用约定见 [AI 接口文档](README.md)。

## 草稿状态

| status | statusText | 含义 | 可执行操作 |
|---:|---|---|---|
| `0` | `预览中` | 草稿已生成，等待用户决定。 | 查询、确认、取消 |
| `1` | `已确认` | 已完成项目、阶段和任务的创建。 | 查询 |
| `2` | `已取消` | 用户已放弃该草稿。 | 查询 |
| `3` | `已过期` | 草稿超过有效期。 | 查询 |

当前草稿默认有效期为 20 分钟，但客户端必须以接口返回的 `expireAt` 作为判断依据。服务端是状态的最终权威：即使前端本地倒计时未结束，确认或取消接口返回终态时也必须重新查询草稿详情并按服务端状态展示。

## 1. 生成任务拆解草稿

```text
POST /api/ai/breakdown/preview
```

调用大模型并创建一个 `task-breakdown` 场景的草稿。该接口受 Redis AI 限流保护。

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `target` | string | 是 | 学习目标，不能为空，最长 100 个字符。 |
| `description` | string | 否 | 补充描述，例如当前基础、偏好或约束。 |
| `duration` | string | 是 | 期望周期，例如 `12周`、`3个月`，不能为空。 |
| `detailed` | boolean | 否 | `true` 使用详细拆解 Prompt；未传或 `false` 使用默认拆解。 |

```json
{
  "target": "三个月内通过英语六级",
  "description": "当前词汇和听力较弱，希望系统提升",
  "duration": "12周",
  "detailed": false
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "draftId": "8efc7e50c0ca4f2a9da5d7a6098e1a11",
    "expireAt": "2026-07-11T10:30:00",
    "milestones": [
      {
        "name": "第一阶段：夯实词汇与听力基础",
        "tasks": [
          {
            "name": "完成每日词汇学习",
            "priority": 3,
            "dueDate": "2026-07-18"
          }
        ]
      }
    ]
  }
}
```

### 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `draftId` | string | 草稿唯一标识，后续详情、确认和取消都使用该值。 |
| `expireAt` | string | 草稿过期时间。 |
| `milestones[].name` | string | 阶段名称。 |
| `milestones[].tasks[].name` | string | 任务名称。 |
| `milestones[].tasks[].priority` | integer | 优先级：`0` 稍后、`1` 低、`2` 中、`3` 高。 |
| `milestones[].tasks[].dueDate` | string | 截止日期，格式为 `yyyy-MM-dd`。 |

### 失败处理

- `42900`：已触发 `task-breakdown` 限流；不要自动重试。
- `30001`～`30004`：AI 调用失败；前端保留用户已填写的表单，不创建本地“伪草稿”。
- 网络层超时：请求可能已到达服务端。若页面已经得到 `draftId`，优先进入详情；若没有 `draftId`，不要用相同输入自动连续提交。

## 2. 查询草稿详情

```text
GET /api/ai/draft/{draftId}
```

从服务端获取草稿的最新状态和完整内容。页面刷新、返回草稿详情页或确认/取消失败后，都应调用此接口恢复真实状态。

### 路径参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `draftId` | string | 生成草稿接口返回的草稿 ID。 |

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "draftId": "8efc7e50c0ca4f2a9da5d7a6098e1a11",
    "scene": "task-breakdown",
    "status": 0,
    "statusText": "预览中",
    "payloadJson": "{\"target\":\"三个月内通过英语六级\",\"description\":\"当前词汇和听力较弱\",\"duration\":\"12周\",\"detailed\":false,\"milestones\":[{\"name\":\"第一阶段：夯实词汇与听力基础\",\"tasks\":[{\"name\":\"完成每日词汇学习\",\"priority\":3,\"dueDate\":\"2026-07-18\"}]}]}",
    "expireAt": "2026-07-11T10:30:00",
    "confirmedAt": null,
    "canceledAt": null
  }
}
```

### 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `scene` | string | 草稿所属场景。任务拆解页面应校验其为 `task-breakdown`。 |
| `status` | integer | 草稿状态，见本文档的状态表。 |
| `statusText` | string | 服务端返回的状态文本，用于展示。 |
| `payloadJson` | string | 草稿完整内容的 JSON 字符串；前端应使用 `JSON.parse` 解析后展示。 |
| `expireAt` | string | 过期时间。 |
| `confirmedAt` | string/null | 确认时间；未确认时为 `null`。 |
| `canceledAt` | string/null | 取消时间；未取消时为 `null`。 |

`payloadJson` 中的任务拆解草稿包含 `target`、`description`、`duration`、`detailed` 和 `milestones` 字段；`milestones` 及任务字段与生成接口中的结构一致。

## 3. 确认任务拆解草稿

```text
POST /api/ai/breakdown/confirm
```

确认一个未过期的任务拆解草稿。服务端在同一事务中创建项目、阶段和任务，并将草稿改为“已确认”。该接口不会再次调用 AI。

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `draftId` | string | 是 | 待确认的 `task-breakdown` 草稿 ID。 |
| `operationId` | string | 是 | 客户端生成的幂等操作 ID。首次提交后必须保存；因网络问题重试时必须复用同一值。 |
| `projectName` | string | 否 | 创建的项目名称；未传时使用草稿中的 `target`。 |
| `projectGoal` | string | 否 | 创建的项目目标；未传时使用草稿中的 `description`。 |

```json
{
  "draftId": "8efc7e50c0ca4f2a9da5d7a6098e1a11",
  "operationId": "2b0d01ce-35b4-4daa-bfdc-6c087ef8fdba",
  "projectName": "英语六级冲刺计划",
  "projectGoal": "三个月内通过英语六级"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "success": true,
    "idempotentReplay": false,
    "businessId": 10001
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | boolean | 确认成功时为 `true`。 |
| `idempotentReplay` | boolean | `false` 表示首次确认；`true` 表示相同 `draftId + operationId` 的重复提交，未重复创建数据。 |
| `businessId` | number | 创建成功后的项目 ID。 |

### 幂等与状态规则

1. 同一用户、同一 `draftId`、同一 `operationId` 的重复请求会返回同一个 `businessId`，并将 `idempotentReplay` 设为 `true`。
2. 客户端在确认请求进行中要锁定确认和取消按钮。
3. 若请求已发送但客户端未收到响应，先复用原 `operationId` 重试，或重新查询草稿详情；不得生成新的 `operationId` 后盲目提交。
4. 已确认、已取消或已过期草稿不能作为新的确认操作处理；前端收到失败响应后应重新读取草稿详情并展示终态。

## 4. 取消草稿

```text
POST /api/ai/draft/cancel
```

取消一个仍处于“预览中”的草稿。取消不创建项目、阶段和任务，也不会再次调用 AI。

### 请求体

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `draftId` | string | 是 | 要取消的草稿 ID。 |

```json
{
  "draftId": "8efc7e50c0ca4f2a9da5d7a6098e1a11"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": true
}
```

取消成功后，前端应重新查询详情或直接按最新服务端状态展示为“已取消”。对已经取消的草稿再次调用取消接口会继续返回 `true`，可视为幂等取消；已确认、已过期或不存在的草稿不能取消，应以接口响应和后续详情查询为准。

## 旧版兼容接口

```text
POST /api/ai/breakdown
```

该接口已标记为 `Deprecated`，仅为旧客户端兼容保留。它只返回阶段和任务列表，不提供草稿 ID、过期状态和确认幂等能力。新页面不得使用该接口，也不得在浏览器端循环创建项目、阶段和任务。
