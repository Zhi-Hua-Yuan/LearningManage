# 阶段 2 风险登记表

状态：`WP8 CANDIDATE PASS / STAGE SEALED PENDING`
建立日期：2026-09-04

| ID | 状态 | 风险 | 触发条件 | 缓解措施 | 目标工作包 |
|---|---|---|---|---|---|
| S2-R-001 | CLOSED | 阶段 2 分支未从包含阶段 1 最终证据的受保护 develop 创建 | 本地分支不是最新受保护基线 | WP3 收口分支从合并后 `develop=e76e2b5` 创建，阶段 1 候选 `5057158` 仍为当前 HEAD 祖先 | WP0/WP1 |
| S2-R-002 | CLOSED | 部分场景绕过 Pipeline 直接调用模型 | 静态扫描发现新增或遗留直接调用，或 MySQL 终态测试失败 | WP3 已迁移全部生产调用；精确 ArchUnit、源码扫描、4 个 MySQL 集成测试及合并后 Backend CI run 33867556286 全部通过，业务直连模型调用为 0 | WP3 |
| S2-R-003 | CLOSED | Qwen/OpenAI-compatible 响应对 Tool Calls、Usage 或请求 ID 的格式差异 | 真实模型与 Stub 字段不一致 | WP7 真实 `qwen-plus` run `33942527673` 连续 3 轮、9 个场景通过；确认 DashScope 强制 Tool Call 可在合法 `tool_calls` 下返回 `finish_reason=stop`，仅对此组合做窄兼容并保留原始 finish reason 证据；Usage 与请求 ID 哈希完整，敏感正文和凭据未进入证据 | WP7 |
| S2-R-004 | CLOSED | 同一草稿使用不同 operationId 重复落库 | 旧确认日志唯一约束不足 | WP5 已实现草稿行锁、草稿级重放、Handler 权限重验、CAS 和单事务写入；候选 run `33889047346` 的 20 路 MySQL 并发确认只产生一次正式写入，重排竞争与原子回滚同时通过 | WP5 |
| S2-R-005 | CLOSED | AI 请求/响应日志泄漏私人内容或凭据 | 脱敏发生在持久化之后 | WP6 统一脱敏、先哈希后截断和唯一日志写入口已通过候选 Gitleaks、MySQL 与 JAR/dist 产物扫描；Release Gate `33903357653` 通过 | WP6 |
| S2-R-006 | CLOSED | 重试和模型回退造成不可控成本 | 网络抖动、429 或上游 5xx | WP6 限制主/兜底各一次并按实际模型聚合 Usage/成本；候选故障注入和成本验证通过 | WP3/WP6 |
| S2-R-007 | CLOSED | 熔断或 Bulkhead 配置错误阻塞核心业务 | 外部模型不可用或并发耗尽 | WP6 全局 Semaphore Bulkhead、模型级熔断、总期限和启动校验已通过候选 Docker/CI | WP6 |
| S2-R-008 | CLOSED | 场景拆分导致旧 API 或前端行为回归 | 门面签名、响应字段或错误语义变化 | WP8 最终候选 run `33950053176` 在后端 SHA `e92c115`、前端 SHA `ff896ea` 上通过 11 项门禁：legacy 37 保留、前端 44/44 匹配、运行时 65、V1～V3 迁移、Docker AI Stub 草稿闭环与幂等重放均通过 | WP4/WP7/WP8 |
| S2-R-009 | CLOSED | AI 日志查询暴露系统管理员不应读取的项目内容 | 运维查询绕过资源权限 | WP6 Mapper 依赖静态阻断和用户范围过滤已通过候选架构门禁；未新增全量正文读取接口 | WP6/WP8 |

## 使用规则

- `OPEN` 风险必须在目标工作包关闭、降级为明确限制或进入阶段验收的已接受风险。
- 不删除历史风险 ID，不把未验证项写成 PASS。
- 本文件不保存密码、Token、API Key、完整 Prompt 或业务正文。
