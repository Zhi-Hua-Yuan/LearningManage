# WP6 安全验证报告

状态：`LOCAL PASS / CANDIDATE GITLEAKS PENDING`

## 已验证控制

- 嵌套 JSON、普通文本键值、Authorization、JWT、Cookie 和数据库 URI 凭据会被确定性脱敏。
- 脱敏具有幂等性；无键值语义的普通 token 单词不会被误删。
- SHA-256 基于脱敏后、截断前的完整正文；正文与错误分别执行 8000/2000 字符上限。
- 模型 Transport 捕获测试确认供应商只能接收到脱敏内容。
- MySQL 集成测试确认请求、响应和错误正文不保存原始测试凭据，状态、哈希和截断标记一致。
- ArchUnit/源码边界测试确认 Controller 不依赖 `AiCallLogMapper`，且 Mapper 只由 `AiCallLogServiceImpl` 使用。
- 日志列表、详情和统计查询继续强制使用当前 `userId`。
- 仓库级 Gitleaks 继续由 Backend CI 和 Release Gate 执行。

对应自动化测试：

- `DefaultAiContentSanitizerTest`
- `AiModelClientImplTest`
- `AiCallLogServiceImplTest`
- `AiCallLogPipelineMySqlTest`
- `AiLogAccessArchitectureTest`
- `TraceIdFilterTest`

正式结论需要候选 CI 的 Gitleaks、Linux 测试、Docker Stub 和产物扫描全部通过后封存。

