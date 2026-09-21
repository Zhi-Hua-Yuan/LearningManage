# Phase 2 TODO

- [x] 建立严格 `AiStructuredOutputDecoder`，生成 Schema format 并统一错误分类。
- [x] 扩展 `AiInvocationPipeline`，保持 Feature Gate、脱敏、超时、主备、限流、熔断和审计边界不变。
- [x] 迁移 today-order，并保留原有过滤、默认值、范围与 fallback 规则。
- [x] 迁移 daily-review-rename，并保留无效任务过滤、最大修改数与 fallback 规则。
- [x] 迁移 weekly-polish，并保留非空业务校验和 Draft payload 兼容性。
- [x] 迁移 list-replan，并保留待办任务范围、日期窗口、去重和 fallback 规则。
- [x] 迁移 task-breakdown，使用 `TaskBreakdownStructuredResponse` 包装 milestones，并保持旧 Draft 结构。
- [x] 为结构变化新增 Prompt 版本与可复现 fixture，不覆盖历史模板。
- [x] 增加 Parse/Schema/Business Validation、Authorization、Resource State Conflict 的可观测性区分。
- [x] 增加解码器、指标和五场景回归测试。
- [ ] 真实 Qwen provider-native Structured Output 验证（后续受保护环境执行）。
