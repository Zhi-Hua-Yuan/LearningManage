# Phase 0 baseline 冻结需求

状态：`IMPLEMENTED / AWAITING_PROTECTED_MERGE_AND_RELEASE_GATE`

## 目标

在 Spring Boot 与 Spring AI 迁移前，冻结同一对前后端 SHA 可复现的 Stage 8 baseline。产品代码只允许通过受保护 PR 进入 `develop`，Release Gate 必须绑定两个仓库的精确 SHA。

## 验收合同

1. 保留 Long/string wire contract、Prompt 版本和既有 Flyway V1～V8，已发布迁移 checksum 不变。
2. 新增 `RESOURCE_STATE_CONFLICT(40901)`，只用于可确定的并发条件更新失败；审计、查询和无法解释的内部失败仍返回 500。
3. shared、dev、prod 中 Knowledge Worker、RAG、Agent、Agent Worker、Tool Calling 默认开启，并可由环境变量显式关闭。
4. Cleanup 与 Cleanup Schedule 默认关闭；正式执行继续要求 SYSTEM_ADMIN、匹配的成功 Dry Run、预计数量复核和审计。
5. Tool Calling 不注册全局模型 Tool，只向单次 Agent Run 注入场景白名单；参数校验、重新鉴权、超时、最多四次、重复/未知/写 Tool 拒绝和 Draft-only 边界保持不变。
6. 默认开启门禁证明 Knowledge Worker 消费、RAG、Agent Worker 和 Tool Calling 可运行；AI 依赖状态不进入核心 readiness。
7. 保留 method/path existence gate，并以 `oasdiff 1.28.0` 加固定归档校验和执行完整 OpenAPI breaking-change gate；报告 SHA 写入候选 Manifest。
8. Stage 8 架构、部署、生产 Bugfix 和事实边界文档与代码一致；`docs/interview/` 保持忽略，误生成 `stop` 文件不存在。
9. 只有两个 PR 合入 `develop` 且同一对 SHA 的 Release Gate 全绿后，才允许创建同名 annotated Tag/Release：`stage8-pre-spring-ai-v1.0.0`。

## 声明边界

- Stage 7 已发布；受保护的真实 Qwen 可观测性验证被用户显式豁免，未运行，不能写成通过。
- 历史 `3/3`、`9/9` 仅表示模型协议验证，不表示业务语义质量评测。
- 100 条 Outbox 事件由 4 路并发 Knowledge Worker 执行线程处理。
- 当前权限模型为“系统角色 + 团队角色 + 资源权限”；预留租户 RBAC 表未接入运行时。

