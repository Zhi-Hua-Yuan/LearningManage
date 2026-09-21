# Phase 3 TODO

## 后端

- [x] 增加 Embedding adapter selector 和启动校验。
- [x] 增加文档/Query Spring AI `EmbeddingModel` 及旧实现回退。
- [x] 保留 request ID、Usage、dimension 和 provider metadata。
- [x] 增加 RAG SSE endpoint、阶段事件、取消检查和有界执行器。
- [x] 将 actorId 显式传入异步 RAG 执行。
- [x] 增加 `CANCELED` Query Log 状态和流式指标。
- [ ] 在受保护环境验证真实 Document/Query Embedding 和 Qwen Stream metadata。

## 前端

- [x] 增加 POST SSE parser 和 Bearer Token。
- [x] 增加阶段提示、取消按钮和组件卸载 AbortController。
- [x] 仅在 `accepted` 前对能力/连接失败回退同步接口。
- [x] 保留原结果、Citation 和安全文本渲染。
- [ ] 完成浏览器 E2E、代理缓冲和断线演练。

## Gate

- [x] 后端定向回归。
- [x] 前端定向回归和 type-check。
- [x] 后端非 MySQL 全量门禁。
- [x] 前端 Vitest、lint、build、contract（E2E 除外）门禁。
- [ ] 隔离 MySQL、Flyway、Redis、Qdrant 和 Docker 验证。
