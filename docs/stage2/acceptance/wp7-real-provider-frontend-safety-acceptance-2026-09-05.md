# WP7 真实模型与前端 AI 安全闭环验收记录

状态：`PASS`

基线：`stage2-wp6-v1.0.0`，后端起点 `88e09bb`

## 实施结论

- 独立 Maven Profile 和仅手动触发的 GitHub Actions 工作流已建立，普通 CI 不连接真实供应商。
- `stage2-real-provider` Environment 限制为受保护分支，密钥只由 Environment Secret 注入；工作流权限为 `contents: read`，供应商 URL 固定为 DashScope HTTPS 地址。
- 真实 `qwen-plus` 连续三轮完成中文文本、强制 `stage2_protocol_probe` Tool Call 和 Tool Result 回传，共 9 个场景。
- DashScope 强制 Tool Call 实测返回结构化 `tool_calls` 时可能仍给出 `finish_reason=stop`；兼容范围仅限“Tool Call 已通过强类型校验”的 `stop`，其他不一致仍拒绝。
- 前端统一从 `X-Trace-Id` Header 提取 TraceId，统一映射 AI 错误动作，保留失败上下文且不自动重试。
- AI 自由文本统一由 `SafeAiText` 或 Vue 文本节点渲染，静态门禁禁止 `v-html`、`innerHTML` 和 `insertAdjacentHTML`。
- 公共 REST API 和数据库均未变化，Flyway 头保持 V3。

## 真实供应商证据

- 工作流：[run 33942527673](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33942527673)
- 后端提交：`11d2f6604cc4f32fc7605627427c04e642d07000`
- 模型：`qwen-plus`
- 结果：3/3 轮、9/9 场景通过。
- Usage：输入 999、输出 84、总计 1083 Token。
- 估算成本：`0.00096720 CNY`，价格版本 `qwen-plus-2025-12-01-cn-beijing-0-128k-list-price-2026-09-05`。
- 原始请求 ID 仅保存 SHA-256 哈希；证据不含 API Key、Authorization、完整 Prompt、完整响应或原始请求 ID。

## 前端与跨仓证据

- 前端实现 PR：[learning-manage-frontend#54](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/54)，合并提交 `ff896ea7e297eb4865a3540552a9333641e278c5`。
- 前端 CI [run 33910827041](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33910827041) 通过，最终前端测试为 484/484。
- 跨仓 Release Gate [run 33942635736](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33942635736) 10/10 Job 通过。
- 后端 710/710、前端 484/484；Flyway 空库/存量库升级、Docker 全栈、制品扫描均通过。
- legacy 37 个 operation 保持；前端 44 个 operation 与运行时 65 个 operation 全部匹配，缺失 0。

## 验收结论

WP7 达到完成条件，`S2-R-003` 关闭。`S2-A-012` 与 `S2-R-008` 继续保留到 WP8；阶段 2 状态仍为 `FROZEN`，不提前发布 `stage2-v1.0.0`。
