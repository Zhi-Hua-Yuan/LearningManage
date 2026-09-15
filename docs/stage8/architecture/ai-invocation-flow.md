# AI 调用流程图

## 1. 统一调用链路

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant C as Controller
    participant S as AI 场景服务
    participant P as PermissionService
    participant T as PromptTemplateResolver
    participant A as AiInvocationPipeline
    participant L as AiCallLogService
    participant M as AiModelClient
    participant Z as AiContentSanitizer
    participant R as 并发舱壁/熔断器
    participant Q as Qwen Provider
    participant D as 草稿/业务结果

    U->>C: 请求 + JWT + X-Trace-Id
    C->>S: 校验后的 DTO
    S->>P: 校验 actor 与业务资源
    P-->>S: 服务端权限事实
    S->>S: 准备最小必要业务上下文
    S->>A: AiExecutionCommand + ResponseProcessor + 可选 Fallback
    A->>T: 按 PromptCode 解析模板
    T-->>A: Prompt ID / 版本 / System Prompt
    A->>L: 创建 RUNNING 调用日志
    Note over A,L: 日志失败只告警，不改变正常业务结果
    A->>M: chat(AiChatCommand)
    M->>Z: 发送供应商前脱敏

    alt 检出禁止发送的敏感内容
        Z-->>M: BLOCKED
        M-->>A: CONTENT_BLOCKED
    else 内容允许发送
        Z-->>M: SAFE 或 REDACTED
        M->>R: 获取并发许可并检查模型熔断器
        alt 本地并发已满或熔断打开
            R-->>M: 快速失败
        else 允许调用
            M->>Q: 主模型 /chat/completions
            alt 主模型成功
                Q-->>M: content/tool_calls/finish_reason/usage/requestId
            else 符合回退条件且总期限未耗尽
                M->>Q: 最多一次兜底模型调用
                Q-->>M: 兜底模型响应
            else 不可恢复失败
                Q-->>M: timeout/429/5xx/auth/protocol error
            end
        end
    end

    M-->>A: AiChatResult 或规范化 AiInvocationException
    alt 模型调用与响应处理成功
        A->>A: ResponseProcessor 解析和结构校验
        A->>L: SUCCESS + Usage + Token + 成本 + Trace
        A-->>S: AiExecutionResult
    else 调用/解析/业务校验失败且有规则降级
        A->>A: 执行确定性 Fallback
        A->>L: SUCCESS + degraded + failureType
        A-->>S: 降级结果与原因
    else 无可用降级或降级自身失败
        A->>L: TIMEOUT / PARSE_FAILED / FAILED
        A-->>S: 安全业务错误
    end

    S->>D: 生成预览、草稿或只读回答
    D-->>C: 业务结果 + Trace ID
    C-->>U: BaseResponse + X-Trace-Id
```

## 2. Tool Calling 扩展点

普通文本场景发送 `SYSTEM + USER` 消息、空 Tool 列表和 `toolChoice=none`。Agent 模型轮次通过同一 Pipeline 使用：

```text
AiChatRoundExecutionCommand
→ SYSTEM + 多轮消息 + 已注册 Tool Definition
→ AiModelClient.chat
→ assistant.tool_calls
→ 服务端 Tool Policy 和 DTO 校验
→ role=tool 结果回填下一轮
```

ModelClient 会拒绝以下响应：

- 请求未声明 Tool，但模型返回 Tool Call。
- `toolChoice=none` 时返回 Tool Call。
- 返回的函数名不在本轮声明列表中。
- 强制某个函数时返回了其他函数。

业务 Tool 是否允许执行由 Agent 层再次判断，ModelClient 只保证供应商协议边界。

## 3. 正文、Trace 和成本策略

| 场景 | 正文日志策略 | 说明 |
|---|---|---|
| 普通 AI 场景 | `DEFAULT` | 脱敏、截断后保存允许的诊断正文和哈希 |
| RAG | `METADATA_ONLY` | 不保存问题、证据和答案正文 |
| Agent 模型轮次 | `METADATA_ONLY` | 只记录 Run、轮次、模型、Usage 和状态 |

- Trace 优先使用命令显式值，其次使用 HTTP/MDC 上下文，否则生成随机值。
- Usage 缺失或价格目录不完整时成本保持未知，不伪造为 0。
- 主模型和兜底模型按各自 Usage 与价格版本分别计价后聚合。
- 请求正文默认上限 8,000 字符，错误摘要默认上限 2,000 字符。
- 正式业务写入不属于 Pipeline；任务拆解和 Agent 报告都先生成草稿。

## 4. 失败分类

```mermaid
flowchart LR
    Failure["AI 调用异常"] --> Type{"失败类型"}
    Type -- "网络 / 超时 / 429 / 5xx / 协议异常" --> Retry["计入熔断\n允许主模型到兜底模型的一次回退"]
    Type -- "配置 / 认证 / 参数" --> NoRetry["不重试\n返回安全错误"]
    Type -- "内容被拦截" --> Block["不调用供应商\nAI_CONTENT_BLOCKED"]
    Type -- "解析 / 业务校验" --> SceneFallback["场景服务确定性降级"]
    Type -- "本地并发不足" --> Busy["快速失败\nAI_CONCURRENCY_LIMIT"]
```
