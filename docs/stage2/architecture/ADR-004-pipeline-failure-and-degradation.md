# ADR-004：Pipeline 2.0 失败分类与降级终态

状态：`ACCEPTED`
日期：2026-09-04

## 背景

WP2 已提供供应商中立的 Chat 协议，但旧 Pipeline 仍调用 `invoke(...)`，部分场景自行维护日志和降级，导致模型回退、规则降级和最终状态无法可靠区分。

## 决策

1. 所有生产 AI 场景只通过 `AiInvocationPipeline` 调用模型，Pipeline 统一使用 `AiModelClient.chat(...)`。
2. Pipeline 负责 Prompt、Trace、协议元数据、失败分类和日志生命周期；场景通过回调提供响应处理器和可选确定性降级算法。
3. 模型回退使用 `fallback_used/fallback_reason`；规则降级使用 `degraded/failure_type/degradation_reason`，两组状态相互独立。模型客户端显式携带请求模型、实际模型和主模型失败原因，禁止从重试次数推断回退。
4. 日志通过 `AiCallLogCompletionCommand` 从 `RUNNING` 以 CAS 方式进入唯一终态。
5. `SUCCESS + degraded=1` 表示 AI 失败但业务通过规则结果继续；原失败类型和安全错误信息仍保留，降级耗时计入调用总耗时。
6. 通用内部 chat 仅能使用固定 `legacy-chat` 场景、不带 Tool 的 `executeRaw`；没有登录用户时允许不创建用户日志，以保持既有内部兼容行为。

## 失败分类

模型协议错误映射为 `CONFIG/AUTH/RATE_LIMIT/NETWORK/TIMEOUT/UPSTREAM_REJECTED/UPSTREAM_SERVER/PROTOCOL/INTERNAL`；其中 WP2 的 `UPSTREAM_REJECTED` 保持兼容，Pipeline 根据可选 HTTP 状态码将 401/403 精确归类为 `AUTH`。场景处理错误映射为 `RESPONSE_PARSE` 或 `BUSINESS_VALIDATION`。

## Pipeline 2.0 时序

```mermaid
sequenceDiagram
    participant S as AI 场景服务
    participant P as AiInvocationPipeline
    participant L as AiCallLogService
    participant M as AiModelClient
    S->>P: execute(command, processor, fallback?)
    P->>P: 解析 Prompt / 校验 Trace
    P->>L: createRunningLog
    P->>M: chat(SYSTEM + USER, toolChoice=none)
    M-->>P: content + usage + model/fallback metadata
    P->>P: 协议校验、结构解析、业务校验
    alt AI 结果可用
        P->>L: complete(SUCCESS) CAS
        P-->>S: 业务结果 + 调用元数据
    else AI 结果不可用且允许规则降级
        P->>P: 执行确定性 fallback
        P->>L: complete(SUCCESS, degraded=1) CAS
        P-->>S: 降级结果 + 原失败类型
    else 无降级或降级失败
        P->>L: complete(FAILED/PARSE_FAILED/TIMEOUT) CAS
        P-->>S: 抛出分类异常
    end
```

## 日志失败状态图

```mermaid
stateDiagram-v2
    [*] --> RUNNING
    RUNNING --> SUCCESS: 主模型或备用模型结果有效
    RUNNING --> SUCCESS: 规则降级成功 / degraded=1
    RUNNING --> TIMEOUT: 最终失败为 TIMEOUT
    RUNNING --> PARSE_FAILED: PROTOCOL / RESPONSE_PARSE / BUSINESS_VALIDATION
    RUNNING --> FAILED: 其他最终失败
    SUCCESS --> [*]
    TIMEOUT --> [*]
    PARSE_FAILED --> [*]
    FAILED --> [*]
```

所有终态转换均带 `status=RUNNING` 条件；重复完成返回 `false`，不会覆盖首次终态。

## 边界

WP3 不注册 Tool，不实现成本、日志正文治理、HTTP Trace Filter 或 Resilience4j。场景服务拆分在 WP4 完成。
