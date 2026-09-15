# Agent 状态机与 Tool Calling 图

## 1. 异步 Run 生命周期

```mermaid
stateDiagram-v2
    [*] --> PENDING: 提交请求并持久化 Run
    PENDING --> RUNNING: Worker claim / attempt+1 / execution token
    PENDING --> CANCELED: 用户在领取前取消

    RUNNING --> RUNNING: 心跳续租
    RUNNING --> RUNNING: 租约过期且未超最大 attempt，新 Worker 接管
    RUNNING --> SUCCEEDED: 分析完成并生成可确认草稿
    RUNNING --> PARTIAL: 使用降级或部分证据生成草稿
    RUNNING --> FAILED: 不可恢复失败或租约重试耗尽
    RUNNING --> TIMED_OUT: 超过整体运行时限
    RUNNING --> CANCELED: 检测到取消请求后协作式停止

    SUCCEEDED --> [*]
    PARTIAL --> [*]
    FAILED --> [*]
    TIMED_OUT --> [*]
    CANCELED --> [*]
```

`SUCCEEDED`、`PARTIAL`、`FAILED`、`TIMED_OUT`、`CANCELED` 都是不可逆终态。状态更新使用当前状态与 `execution_token` 条件更新；租约失效的旧 Worker 不能写进度、草稿或终态。

## 2. 提交、执行与确认

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant API as AgentController
    participant PS as PermissionService
    participant RUN as ai_agent_run
    participant W as Agent Worker
    participant O as AgentOrchestrator
    participant TP as AgentToolPolicy
    participant TE as AgentToolExecutor
    participant TOOL as 只读 AgentTool
    participant DB as MySQL
    participant PIPE as AiInvocationPipeline
    participant D as AI Draft
    participant R as Analysis Report

    U->>API: POST project-risk/team-workload + clientRequestId
    API->>PS: 校验项目可见或团队负载分析权
    PS-->>API: 当前服务端权限事实
    API->>RUN: 以 user+scene+clientRequestId 幂等创建 PENDING
    API-->>U: runId + PENDING

    W->>RUN: SKIP LOCKED 领取，写租约与 execution token
    W->>O: 使用 Run 中持久化的 actor/target 执行
    Note over W,O: Worker 不使用 HTTP UserHolder
    O->>DB: 记录 startDataVersion

    loop 最多 4 个不同的已注册 Tool
        O->>PIPE: Tool-capable Chat Round
        PIPE-->>O: assistant.tool_calls
        O->>TP: 校验场景白名单、必需 Tool、去重和次数
        TP-->>O: allow/deny
        O->>TE: 强类型解析参数 + 固定 Run target
        TE->>TOOL: ToolExecutionContext
        TOOL->>PS: Tool 内部再次鉴权
        TOOL->>DB: 只读查询
        DB-->>TOOL: 结构化事实
        TOOL-->>TE: 限长 Tool Result
        TE->>RUN: 写 Tool 日志与进度，token-fenced
        TE-->>O: 不可信 Tool 消息
    end

    O->>PIPE: 生成结构化分析
    O->>DB: 检查 endDataVersion
    alt 数据版本未变化且无降级
        O-->>W: SUCCEEDED 结果
    else 依赖降级但数据版本稳定
        O-->>W: PARTIAL + 可审计降级原因
    else 分析期间数据版本变化
        O-->>W: PARTIAL + stale 数据版本，后续确认会拒绝
    end
    W->>D: 在终态事务中创建 AI 草稿
    W->>RUN: CAS 写终态、draftId、Usage 和 Trace

    U->>API: POST /report/confirm
    API->>PS: 重查 Run 所有权、目标权限、数据版本和引用哈希
    API->>D: 行锁 + Schema Handler + 幂等确认
    D->>R: 创建唯一正式报告
    R-->>U: 报告结果
```

## 3. 项目风险 Tool Calling

```mermaid
flowchart TD
    Start["PROJECT_RISK Run"] --> Mode{"tool-calling.enabled?"}
    Mode -- "是" --> Model["Qwen Tool Calling"]
    Model --> Policy{"Tool Policy 允许?"}
    Policy -- "否" --> Reject["拒绝未注册/重复/超限 Tool"]
    Policy -- "是" --> Args["强类型参数校验\nprojectId 不能由模型改写"]
    Args --> Auth["Tool 内 requireProjectView"]
    Auth --> Read["执行只读查询"]
    Read --> Required{"已调用 queryTaskStats\n和 queryOverdueTasks?"}
    Required -- "否" --> Correct["最多一次纠正提示"] --> Model
    Required -- "是" --> Final["生成风险项与引用"]
    Model -- "协议/模型失败" --> Fixed["固定只读工作流"]
    Mode -- "否" --> Fixed
    Fixed --> Stats["queryTaskStats"]
    Stats --> Overdue["queryOverdueTasks"]
    Overdue --> History{"RAG 历史可用?"}
    History -- "是" --> Retrieve["retrieveProjectHistory"] --> Final
    History -- "否" --> Final
    Final --> Draft["PROJECT_RISK_REPORT 草稿"]
```

项目风险允许的工具：

```text
queryProjectTasks
queryOverdueTasks
queryTaskStats
retrieveProjectHistory
```

必需工具是 `queryTaskStats` 和 `queryOverdueTasks`。历史检索不可用时仍可使用结构化任务事实完成降级分析。

## 4. 团队负载固定工作流

团队负载只允许 OWNER/ADMIN 发起，固定调用：

```text
queryTeamMemberWorkload
queryMemberOverdueTasks
```

随后使用两条隔离生成链路：

```mermaid
flowchart LR
    Facts["成员级结构化事实"] --> ManagerPrompt["管理版 Prompt"]
    ManagerPrompt --> Manager["managerSummary + recommendations"]
    Facts --> Aggregate["服务端匿名聚合\n不含姓名/用户ID/任务标题"]
    Aggregate --> PublicPrompt["公开版 Prompt"]
    PublicPrompt --> Leak{"出现成员别名?"}
    Leak -- "是" --> Deterministic["确定性公开摘要"]
    Leak -- "否" --> Public["publicSummary"]
    Facts --> Snapshot["memberMetrics 快照"]
    Manager --> Draft["TEAM_WORKLOAD_REPORT 草稿"]
    Public --> Draft
    Deterministic --> Draft
    Snapshot --> Draft
```

报告读取时，OWNER/ADMIN 获得管理摘要、全部成员指标和建议；MEMBER 只获得公开摘要及后端筛选出的本人指标。

## 5. 安全不变量

- Run 的 actor、projectId 和 teamId 来自持久化记录，不接受模型重选目标。
- 工具注册表中没有删除、修改、分配或批量写入能力。
- 每个 Tool 内再次执行权限检查，模型 Prompt 不能绕过。
- 同一 Tool 不允许在一次 Run 中重复调用，总次数最多 4。
- Tool 输出作为不可信文本返回模型，不能成为 System 指令。
- PENDING 可立即取消；RUNNING 在当前只读操作结束后协作式取消。
- 只有成功或部分成功的 Run 可以产生草稿；正式报告仍需用户确认。
