# WP7 Release 记录

状态：`PASS`

## 发布绑定

| 项目 | 值 |
|---|---|
| 工作包 | WP7 真实模型验证与前端 AI 安全闭环 |
| 后端实现 PR | [#119](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/119)、[#120](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/120)、[#121](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/121)、[#122](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/122) |
| 前端实现 PR | [#54](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/54) |
| 后端候选提交 | `11d2f6604cc4f32fc7605627427c04e642d07000` |
| 前端候选提交 | `ff896ea7e297eb4865a3540552a9333641e278c5` |
| 真实模型验证 | [run 33942527673](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33942527673) |
| 跨仓候选门禁 | [run 33942635736](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33942635736) |
| 候选名称 | `stage2-wp7-20260905-11d2f66` |
| Tag | `stage2-wp7-v1.0.0` |
| Release | [stage2-wp7-v1.0.0](https://github.com/Zhi-Hua-Yuan/LearningManage/releases/tag/stage2-wp7-v1.0.0) |

## 门禁结果

- 真实 `qwen-plus` 3/3 轮、9/9 场景通过，文本、Tool Call、Tool Result、Usage 与供应商请求 ID 协议均得到验证。
- 后端 710/710、前端 484/484 通过。
- TraceId、30001～30011/42900/网络错误映射、上下文保留、无自动重试和纯文本安全渲染均通过前端测试及静态门禁。
- Release Gate 10/10 Job 通过；Flyway V1/V2/V3 未修改且没有 V4。
- Docker 全栈、API 44/44 匹配、legacy 37 保持、AI Stub 草稿闭环和制品敏感信息扫描通过。
- `S2-R-003` 关闭；`S2-A-012` 与 `S2-R-008` 留给 WP8。

## 证据摘要

- Repository real-provider report SHA-256：`7288F2C5B81BE2133763A22263EEC66512D6B82A3F901EBD8E1E6F4D4FE5E528`
- Workflow source report SHA-256：`1060AF9FEF9CE592A7F2F4E578E2814937737FED82B73B7A7227DC8B21C29C39`
- Candidate Manifest SHA-256：`5AB6D15936BB0C1A2C0334E67D9112FDCDAE31122796F425B1083FC472B05955`
- Backend JAR SHA-256：`1A50F6001B73F1025E2408D6E8E71D3860E1F7B0FABEFCA62090E67C318EFE88`
- API 比对报告 SHA-256：`AF13A31207019571A9FCFF5C61E4FECB0195831C88A03BE8AA073A31C4CFB5DE`
- 全栈 AI 证据 SHA-256：`CB4EC8D490A505ABA868840605C317B7AA71CF5463D252FEC4C55B7753C80FF6`

## 范围说明

本 Release 只封存阶段 2 的 WP7，不代表阶段 2 整体完成。WP7 未新增公共 API、数据库迁移、RAG、Embedding、Qdrant 或 Agent；真实模型验证工作流不参与生产运行，前端改动可独立回滚。
