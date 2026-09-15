# RAG 检索和引用图

## 1. 权限感知检索主流程

```mermaid
flowchart TD
    Ask["POST /api/ai/rag/ask\nquestion + projectId"] --> Gate{"RAG 开关与索引就绪?"}
    Gate -- "否" --> Unavailable["返回明确业务错误\n不调用模型"]
    Gate -- "是" --> Auth1["① PermissionService\nrequireProjectView"]
    Auth1 --> QueryEmbed["查询 Embedding\ntext_type=query / 1024 维"]
    QueryEmbed --> Filter["Qdrant 权限过滤\nproject + visibility + owner/team"]
    Filter --> Top20["向量召回 Top 20"]
    Top20 --> Auth2["② 批量来源鉴权"]
    Auth2 --> Hydrate["按 sourceType/sourceId\n从 MySQL 重建当前文档与切片"]
    Hydrate --> Version{"sourceVersion/哈希匹配?"}
    Version -- "否" --> Discard["丢弃候选\n写纠正 Outbox 事件"]
    Version -- "是" --> Rerank["qwen3-rerank\n选 Top 8"]
    Discard --> Enough
    Rerank --> Enough{"有授权且有效的证据?"}
    Enough -- "否" --> Abstain["确定性返回\ninsufficientEvidence=true\n不调用 Chat"]
    Enough -- "是" --> Evidence["服务端分配 S1～S8\n构造不可信 JSON 证据包"]
    Evidence --> Chat["AiInvocationPipeline\nMETADATA_ONLY 日志"]
    Chat --> Output["模型返回 answer\ninsufficientEvidence\ncitations"]
    Output --> Validate{"引用集合 = 正文标记\n且每个 ID 都存在?"}
    Validate -- "首次失败" --> Repair["允许一次格式修复"]
    Repair --> Validate2{"再次校验"}
    Validate2 -- "失败" --> SafeError["安全失败\n不保存结果"]
    Validate -- "通过" --> Auth3["③ 持久化前最终校验\n项目权限 + 来源权限 + 哈希"]
    Validate2 -- "通过" --> Auth3
    Auth3 --> Changed{"权限或来源是否变化?"}
    Changed -- "是" --> Stale["拒绝 ACTIVE 落库\n返回失效/重新生成语义"]
    Changed -- "否" --> Persist["事务保存 Query/Result/Sources"]
    Persist --> Answer["返回答案、S 引用、分数和 knowledgeAsOf"]
```

## 2. 三道权限防线

| 防线 | 时点 | 作用 |
|---|---|---|
| 第一道 | 任何供应商调用前 | 确认用户当前可查看项目，越权问题不会发送到外部服务 |
| 第二道 | Qdrant 召回后 | 批量校验任务/复盘权限，并从 MySQL 重建正文；Qdrant Payload 不作为业务事实 |
| 第三道 | 保存与返回前 | 防止模型生成期间发生退团、删除、改权或内容更新 |

个人项目只查询 `PRIVATE + ownerUserId`。团队项目查询当前团队的 `TEAM` 文档，以及请求者本人在该项目下的 `PRIVATE` 文档。

## 3. 证据与引用合同

服务端给最终证据分配临时编号，模型只能引用编号，不能提交业务 ID：

```json
{
  "evidence": [
    {"id": "S1", "title": "任务标题", "text": "不可信证据正文"},
    {"id": "S2", "title": "第 36 周共享复盘", "text": "不可信证据正文"}
  ]
}
```

模型必须返回：

```json
{
  "answer": "主要延期来自两个未完成任务 [S1]，共享复盘也记录了阻塞 [S2]。",
  "insufficientEvidence": false,
  "citations": ["S1", "S2"]
}
```

后端验证：

- `citations` 集合必须与答案中的 `[Sx]` 标记完全一致。
- 每个编号必须存在于最终 Top 8 证据中。
- 业务 `sourceId` 由服务端从编号映射，模型无法伪造。
- 没有授权候选时不调用 Chat，直接返回依据不足。
- RAG 调用日志只保存编号、哈希、Usage 和状态，不保存问题、证据或答案正文。

## 4. 结果生命周期

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: 引用与最终权限校验通过
    ACTIVE --> STALE: 来源内容或数据版本变化
    ACTIVE --> INVALIDATED: 来源删除或权限撤销
    ACTIVE --> EXPIRED: 超过正文保留期
    STALE --> INVALIDATED: 后续确认来源删除/失权
    STALE --> EXPIRED: 超过保留期
    INVALIDATED --> [*]
    EXPIRED --> [*]
```

`GET /api/ai/rag/result/{requestId}` 每次重新检查结果所有者、项目权限和引用版本：

- `ACTIVE`：返回答案和当前仍有效的引用。
- `STALE`：返回状态与引用元数据，答案为 `null`，提示重新生成。
- `INVALIDATED`、`EXPIRED`：返回专用业务错误，不返回正文。

## 5. 降级边界

- Rerank 不可用时可以按向量顺序返回，并明确 `degraded=true`。
- Embedding 或 Qdrant 不可用时不能绕过检索生成答案。
- 引用格式修复最多一次，第二次失败不持久化结果。
- 当前版本不包含聊天历史、问题改写、文件摄取或混合关键词检索。
