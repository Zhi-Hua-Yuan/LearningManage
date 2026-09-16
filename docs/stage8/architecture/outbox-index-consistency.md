# Outbox 与索引一致性图

## 1. 写入与异步对账

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户/系统任务
    participant B as 业务 Service
    participant DB as MySQL
    participant E as ai_knowledge_index_event
    participant W as Knowledge Worker
    participant Q as Event Queue
    participant SL as Source Lock
    participant F as Document Factory
    participant EM as EmbeddingClient
    participant VS as Qdrant
    participant DOC as ai_knowledge_document

    U->>B: 新增/修改/删除任务或周复盘
    B->>DB: BEGIN
    B->>DB: 写业务事实与数据版本
    B->>E: 同事务插入 PENDING 事件
    B->>DB: COMMIT
    Note over B,E: 业务数据和唤醒事件要么同时成功，要么同时回滚

    W->>Q: claimReady
    Q->>E: SELECT FOR UPDATE SKIP LOCKED
    E-->>Q: PENDING/到期 RETRY_WAIT 事件
    Q->>E: PROCESSING + claim_token + lease
    Q-->>W: 已领取事件
    W->>SL: 获取 sourceType+sourceId 独占租约
    SL-->>W: source lease token
    W->>DB: 重读来源、项目、权限和当前数据版本
    W->>F: 生成当前 desired documents
    F->>F: norm-v1 + chunk-v1 + contentHash + payloadHash

    alt 当前不应存在文档
        W->>VS: delete by obsolete documentKey/source
        W->>DOC: 标记 DELETED/SKIPPED
    else contentHash 与 payloadHash 均未变化
        W->>DOC: 保持 INDEXED，跳过外部调用
    else 仅 payloadHash 变化
        W->>VS: 覆盖 Payload，不重新生成向量
        W->>DOC: 更新 indexedPayloadHash
    else contentHash 变化
        W->>EM: 文档 Embedding，1024 维
        EM-->>W: vectors + usage
        W->>VS: 按确定性 point ID 幂等 upsert
        W->>DOC: 更新哈希、切片数和 INDEXED
    end

    W->>DB: 外部写入后再次读取来源与权限
    alt 内容或权限在处理期间变化
        W->>E: 新增纠正事件
        W->>E: 抛出可重试 STALE_SOURCE，当前事件进入 RETRY_WAIT
    else 状态未变化且 token 仍有效
        W->>E: CAS 更新 SUCCESS
    end
    W->>SL: 释放来源租约
```

## 2. 事件状态机

```mermaid
stateDiagram-v2
    [*] --> PENDING: 业务事务提交事件
    PENDING --> PROCESSING: Worker 领取并写 claim token
    RETRY_WAIT --> PROCESSING: 到达 next_attempt_at 后重新领取
    PROCESSING --> SUCCESS: 当前状态对账完成
    PROCESSING --> RETRY_WAIT: 网络/限流/超时等瞬时失败
    PROCESSING --> DEAD: 配置/协议/维度错误或重试耗尽
    PROCESSING --> PROCESSING: 租约过期后由新 token 接管
    DEAD --> PENDING: SYSTEM_ADMIN 显式重放
    SUCCESS --> [*]
```

旧 Worker 的 `claim_token` 已失效时，不能更新事件终态。来源在处理期间变化时，纠正事件和当前事件的重试共同保证后续再次对账，而不是把旧写入标记为已经收敛。`worker-enabled=false` 只停止消费，不停止业务事务写 Outbox，因此重新启用后不会漏掉期间变化。

## 3. 文档状态与哈希决策

```mermaid
flowchart TD
    Read["读取 MySQL 当前事实"] --> Desired{"是否应存在知识文档?"}
    Desired -- "否" --> Delete["删除旧 Points\n文档 DELETED/SKIPPED"]
    Desired -- "是" --> Hash["生成 contentHash 与 payloadHash"]
    Hash --> Compare{"与 indexed hash 比较"}
    Compare -- "都相同" --> Noop["零外部写入\n确认 INDEXED"]
    Compare -- "仅 payload 不同" --> Payload["只覆盖 Qdrant Payload"]
    Compare -- "content 不同" --> Embed["重新 Embedding\n幂等 Upsert"]
    Delete --> Verify["处理后重读事实"]
    Noop --> Verify
    Payload --> Verify
    Embed --> Verify
    Verify --> Changed{"版本/权限是否变化?"}
    Changed -- "是" --> Correct["插入纠正事件"]
    Changed -- "否" --> Converged["Token-fenced SUCCESS"]
```

哈希定义：

```text
contentHash = SHA-256(normalizerVersion + chunkingVersion + canonicalText)
payloadHash = SHA-256(canonical JSON payload)
sourceVersion = contentHash + ":" + payloadHash
```

## 4. 可见性和内容边界

| 来源 | 生成的知识文档 |
|---|---|
| 个人项目任务 | 项目所有者 PRIVATE 文档 |
| 团队项目任务 | 团队范围 TEAM 文档 |
| PRIVATE 周复盘 | 作者仍有项目权限时生成作者 PRIVATE 文档 |
| TEAM 周复盘 | 作者 PRIVATE 文档 + 只含 `sharedSummary` 的 TEAM 文档 |
| 删除/失效来源 | 不存在 ACTIVE 文档，删除旧 Points |

任务语义正文只包含标题与描述；状态、优先级、截止日、受理人和权限范围进入 Payload。这样仅运营字段变化时无需支付新的 Embedding 成本。

## 5. Backfill 与恢复

- INITIAL、REBUILD 和增量事件最终都进入同一套对账逻辑，避免出现第二种写向量语义。
- Backfill 使用可恢复的 Keyset 游标和独立 Run；父 Run 等待所有子事件达到终态。
- Qdrant 不是备份。Collection 丢失或模型/维度升级时，从 MySQL 重新生成并通过 Alias 切换。
- DEAD 事件不被生命周期任务自动删除，必须由 SYSTEM_ADMIN 排查后重放。
