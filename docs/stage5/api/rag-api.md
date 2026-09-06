# Stage 5 RAG API

## Ask

```http
POST /api/ai/rag/ask
Authorization: Bearer <token>
Content-Type: application/json

{"question":"这个项目最近为什么推进缓慢？","projectId":1001}
```

The question is required and limited to 1,000 characters. `projectId` is required and must identify an active project visible to the requester.

Success data:

```json
{
  "requestId": "opaque UUID",
  "status": "ACTIVE",
  "answer": "两个任务仍未完成 [S1]。",
  "insufficientEvidence": false,
  "degraded": false,
  "degradationReason": null,
  "knowledgeAsOf": "2026-09-06T13:30:00",
  "sources": [{
    "citationId": "S1",
    "sourceType": "TASK",
    "sourceId": 2001,
    "title": "任务标题",
    "score": 0.82,
    "vectorScore": 0.75,
    "rerankScore": 0.82,
    "updatedAt": "2026-09-06T12:00:00"
  }]
}
```

## Read result

```http
GET /api/ai/rag/result/{requestId}
```

The result is user-owned and project-scoped. Every read rechecks access and citation versions. An expired or invalidated result returns its dedicated business error and no body. A stale result returns `status=STALE`, `answer=null`, and citation metadata so the UI can request regeneration.

## Business errors

```text
32001 RAG_DISABLED
32002 KNOWLEDGE_INDEX_NOT_READY
32003 RAG_DEPENDENCY_UNAVAILABLE
32004 RAG_RESULT_NOT_FOUND
32005 RAG_RESULT_INVALIDATED
32006 RAG_RESULT_EXPIRED
32007 RAG_CITATION_INVALID
32008 RAG_SOURCE_CHANGED
32009 RERANK_UNAVAILABLE
```
