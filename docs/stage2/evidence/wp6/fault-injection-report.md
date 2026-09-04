# WP6 韧性故障注入报告

状态：`PASS / CANDIDATE GATE PASS`

使用可控 Transport 和程序式 Resilience4j 验证了以下场景：

| 场景 | 结果 |
|---|---|
| 主模型 5xx 后切换兜底模型 | 每个模型各调用一次 |
| 配置、认证或调用参数错误 | 不调用兜底模型，不计入熔断 |
| 全局 Bulkhead 满载 | 立即返回并发限制，不调用兜底模型 |
| 主模型熔断打开 | 不访问主供应商；兜底模型熔断状态独立 |
| OPEN → HALF_OPEN → CLOSED | 探测成功后恢复 |
| 逻辑总期限耗尽 | 不发起下一次外部请求 |
| 规则降级 | 终态记录 `degraded=true` 和唯一失败类型 |
| 已知失败/回退 Usage | 按实际模型计价并聚合 |

默认配置为 20 并发、0 ms 等待、20 个滑动窗口、最小 10 次、50% 失败/慢调用阈值、30 秒慢调用与 OPEN 等待、HALF_OPEN 3 次探测。实现没有同模型自动重试，因此逻辑调用外部请求硬上限为 2。

对应自动化测试：`AiResilientCallExecutorTest`、`AiModelClientImplTest`、`AiInvocationPipelineTest`、`RateLimitServiceImplTest`。

候选 Release Gate `33903357653` 已在合并提交
`88e09bb2c9487ba04a2245355fcdc59152ad8639` 上完成后端回归、Docker 全栈和 AI Stub
闭环验证，以上故障注入控制与调用次数、成本聚合约束均通过。
