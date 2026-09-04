# WP3 AI 调用管线验收

状态：`LOCAL_PASS / FULL_CI_PENDING`
日期：2026-09-04

## 已验收

| 项目 | 结果 | 证据 |
|---|---|---|
| Pipeline 使用 Chat 协议 | PASS | `AiInvocationPipelineTest` |
| Trace 与协议元数据传播 | PASS | `AiInvocationPipelineTest` |
| 模型失败分类 | PASS | `AiInvocationPipelineTest` |
| 解析与业务校验分类 | PASS | `AiInvocationPipelineTest`、`AiExecutionContractTest` |
| 模型回退与规则降级分离 | PASS | `AiInvocationPipelineTest` |
| 日志唯一终态 CAS | PASS | `AiCallLogServiceImplTest` |
| 模型调用唯一入口 | PASS | `AiInvocationArchitectureTest`、调用点扫描 |
| 主/备模型最终失败元数据 | PASS | 最终失败类型、实际模型与主模型回退原因分别记录 |
| 规则降级耗时与原因 | PASS | 总耗时包含降级执行；原因写入 `error_message` |
| WP3 核心测试 | 35/35 | Pipeline、失败映射、执行合同、CAS、架构规则 |
| WP2 Chat 协议回归 | 38/38 | Validator、Mapper、Parser、模型客户端 |
| 非 MySQL 全量回归 | 572/572 | `mvnw -Dtest=!**/*MySqlTest test` |
| WP3 MySQL 集成测试 | 4/4 PASS（本地 Docker；候选 CI 待复核） | 成功、模型回退、规则降级、终态 CAS |
| 公共 API 变化 | 0 | 仅内部类型和实现变更 |
| 已发布迁移变化 | 0 | V1/V2/V3 SHA-256 复核 |
| 验收 JSON Schema/等价合同检查 | PASS | 本地 Python Schema 校验与 PowerShell 语义检查 |

## 当前边界

- WP3 不代表真实供应商验证、成本治理、日志脱敏或韧性门禁完成。
- WP3 不完成 `AiServiceImpl` 场景拆分，该事项继续由 WP4 负责。
- 本机已在隔离 Docker MySQL 执行全部 57 个 MySQL 测试（含 4 个 WP3 新增测试）；候选 CI 仍需复核相同结果。
- 本机已构建后端镜像并启动隔离容器，`GET /api/health` 返回 HTTP 200；候选 CI 仍需复核完整 Docker 门禁。
- 本机 WSL 缺少 `jq`，原 Bash 验收脚本和 WP2 Stub 脚本无法本地重跑；Schema 与合同语义已等价校验，WP2 的 16 场景沿用 WP2 已封存 PASS 证据。
- 完整 MySQL、Docker、原生 Bash 门禁和跨仓契约结果由候选 CI 补充，完成前不发布 WP3 Release。
- `S2-A-007` 和 `S2-R-002` 在候选 CI 完成前分别保持 `PENDING` 和 `OPEN`；CI 预期总测试数为 629。
