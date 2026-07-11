# Redis AI 限流模块交付文档

> 面向前端联调的统一错误码与限流处理约定见 [AI 接口文档](api/README.md)；任务拆解草稿接口见 [AI 草稿接口文档](api/ai-draft-api.md)。本文件保留限流算法、配置、运维和排查说明。

## 一、模块目标

Redis AI 限流模块用于限制单个登录用户在指定时间窗口内对 AI 能力的调用次数，避免短时间重复调用造成模型成本失控、服务资源被占用或下游模型接口被过度请求。

本模块的限流维度为：

```text
登录用户 + AI 调用场景 + 固定时间窗口
```

模块仅保护会实际触发大模型调用的 AI `POST` 接口，不限制草稿确认、取消、详情查询等本地业务操作。

## 二、实现结构

执行链路：

```text
客户端请求
  -> LoginInterceptor 校验登录并写入 UserHolder
  -> AiRateLimitInterceptor 识别 AI 调用场景
  -> RateLimitServiceImpl 读取场景限流规则
  -> Redis Lua 脚本原子递增计数器
  -> 未超限：进入 AiController 执行业务
  -> 已超限：抛出 RATE_LIMIT_ERROR，由全局异常处理器返回统一响应
```

核心文件：

| 文件 | 职责 |
|---|---|
| `pom.xml` | 引入 `spring-boot-starter-data-redis` 依赖 |
| `config/RateLimitProperties.java` | 绑定 `rate-limit.ai` 配置，包括总开关、容错策略和场景规则 |
| `interceptor/AiRateLimitInterceptor.java` | 根据请求路径识别 AI 场景，并以当前登录用户作为限流主体 |
| `service/RateLimitService.java` | 定义 AI 限流服务入口 |
| `service/impl/RateLimitServiceImpl.java` | 实现固定窗口计数、Lua 原子执行、规则解析和 Redis 异常容错 |
| `config/WebMvcConfig.java` | 注册登录拦截器和 AI 限流拦截器，确保登录校验先执行 |
| `exception/ErrorCode.java` | 定义 `RATE_LIMIT_ERROR(42900, "请求过于频繁")` |
| `application-*.yml` | 提供开发、测试、生产环境的 Redis 与限流规则配置 |

## 三、限流算法与 Redis Key

当前实现使用固定窗口计数器。

窗口编号计算方式：

```text
windowBucket = 当前 Unix 时间戳（秒） / windowSeconds
```

Redis Key 格式：

```text
rate_limit:ai:{userId}:{scene}:{windowBucket}
```

示例：

```text
rate_limit:ai:1001:task-breakdown:29201940
```

Redis Lua 脚本在一次原子操作中完成以下动作：

1. 对当前窗口的 Key 执行 `INCR`。
2. 当计数首次创建时设置过期时间。
3. 返回当前计数，由业务代码与场景上限比较。

Key 的过期时间为：

```text
windowSeconds + 5 秒
```

其中 5 秒为过期缓冲，避免边界时间附近出现遗留计数。窗口编号不会因请求而刷新，因此计数会在下一个固定窗口自然恢复。

说明：超额请求同样会增加当前窗口的计数，但不会延长窗口的有效时间。

## 四、已接入场景

| 请求方法 | 接口 | scene | 说明 |
|---|---|---|---|
| `POST` | `/ai/breakdown` | `task-breakdown` | 兼容旧版任务拆解入口 |
| `POST` | `/ai/breakdown/preview` | `task-breakdown` | 任务拆解预览 |
| `POST` | `/ai/polish` | `weekly-polish` | 兼容旧版周总结润色入口 |
| `POST` | `/ai/polish/preview` | `weekly-polish` | 周总结润色预览 |
| `POST` | `/ai/today-order/recommend` | `today-order` | 今日任务推荐顺序 |
| `POST` | `/ai/daily-review/suggest-rename` | `daily-review-rename` | 日报回顾改名建议 |
| `POST` | `/ai/list/replan/preview` | `list-replan` | 清单任务重排预览 |

以下接口不在当前限流范围内：

- 任务拆解、周总结润色、清单重排的确认接口。
- AI 草稿取消和详情查询接口。
- 清单重排取消接口。
- 其他未配置到 `AiRateLimitInterceptor` 场景映射表中的请求。

## 五、配置说明

配置前缀：

```yaml
rate-limit:
  ai:
```

通用配置项：

| 配置项 | 说明 | 当前默认值 |
|---|---|---:|
| `enabled` | AI 限流总开关 | `true` |
| `fail-open` | Redis 限流检查失败时是否放行 | `true` |
| `default-rule.window-seconds` | 未单独配置场景的窗口时长 | `60` 秒 |
| `default-rule.max-requests` | 未单独配置场景的最大请求数 | `5` 次 |

当前测试与生产基准规则：

| scene | 时间窗口 | 最大请求数 |
|---|---:|---:|
| `task-breakdown` | 60 秒 | 3 次 |
| `weekly-polish` | 60 秒 | 5 次 |
| `today-order` | 60 秒 | 10 次 |
| `daily-review-rename` | 60 秒 | 5 次 |
| `list-replan` | 60 秒 | 3 次 |

开发环境可以为了联调或压测临时调低额度；发布前应以生产环境变量的值为准。生产环境支持以下配置项覆盖：

```text
AI_RATE_LIMIT_ENABLED
AI_RATE_LIMIT_FAIL_OPEN
AI_RATE_LIMIT_WINDOW_SECONDS
AI_RATE_LIMIT_MAX_REQUESTS
AI_RATE_LIMIT_TASK_BREAKDOWN_WINDOW_SECONDS
AI_RATE_LIMIT_TASK_BREAKDOWN_MAX_REQUESTS
AI_RATE_LIMIT_WEEKLY_POLISH_WINDOW_SECONDS
AI_RATE_LIMIT_WEEKLY_POLISH_MAX_REQUESTS
AI_RATE_LIMIT_TODAY_ORDER_WINDOW_SECONDS
AI_RATE_LIMIT_TODAY_ORDER_MAX_REQUESTS
AI_RATE_LIMIT_DAILY_REVIEW_RENAME_WINDOW_SECONDS
AI_RATE_LIMIT_DAILY_REVIEW_RENAME_MAX_REQUESTS
AI_RATE_LIMIT_LIST_REPLAN_WINDOW_SECONDS
AI_RATE_LIMIT_LIST_REPLAN_MAX_REQUESTS
```

Redis 环境约定：

| 环境 | Redis 数据库 | 说明 |
|---|---:|---|
| 开发环境 | `0` | 本地 Redis，默认 `localhost:6379` |
| 测试环境 | `1` | 与开发环境计数数据隔离 |
| 生产环境 | 由 `REDIS_DATABASE` 指定，默认 `0` | Redis 地址、端口、密码均支持环境变量注入 |

配置修改后需要重启应用才会生效。

## 六、异常与容错行为

### 1. 触发限流

同一用户在同一场景的当前窗口内调用次数超过配置上限时，服务抛出：

```text
ErrorCode.RATE_LIMIT_ERROR
业务码：42900
```

全局异常处理器会返回项目统一响应结构。例如：

```json
{
  "code": 42900,
  "message": "AI 调用过于频繁，请稍后再试",
  "data": null
}
```

当前项目的 `BusinessException` 统一按 HTTP `200 OK` 返回，客户端应以响应体中的 `code = 42900` 作为限流判断依据。

### 2. Redis 不可用

当 Redis 执行限流脚本异常时：

- `fail-open=true`：记录告警日志并放行请求，优先保证 AI 主业务可用。
- `fail-open=false`：返回操作失败，避免 Redis 故障期间绕过限流。

当前配置为 `fail-open=true`。该策略适合优先保障功能可用的场景，但线上需要监控 Redis 可用性，避免限流长期失效而未被发现。

## 七、测试验证结果

本阶段已完成后端手工整体验证，结果符合预期。

| 验证项 | 预期结果 | 结果 |
|---|---|---|
| 额度内调用 | 请求正常进入 AI 业务流程 | 通过 |
| 达到并超过场景额度 | 返回业务码 `42900` | 通过 |
| 同用户、同场景重复调用 | 使用同一个 Redis 计数器 | 通过 |
| 不同用户调用 | 计数互相隔离 | 通过 |
| 不同 AI 场景调用 | 计数互相隔离 | 通过 |
| 窗口到期后再次调用 | 使用新窗口计数并恢复放行 | 通过 |
| 未映射的 AI 本地操作 | 不触发 AI 限流 | 通过 |
| Redis 异常且 `fail-open=true` | 限流模块放行，主业务不被限流阻断 | 通过 |
| 关闭 `enabled` | 不执行限流检查 | 通过 |

建议后续补充自动化回归测试，重点覆盖 Lua 计数逻辑、场景规则解析、用户隔离和拦截器路径映射。

## 八、运维与排查

查看当前限流 Key：

```powershell
redis-cli --scan --pattern "rate_limit:ai:*"
```

查看指定 Key 的计数和剩余存活时间：

```powershell
redis-cli GET "rate_limit:ai:1001:task-breakdown:窗口编号"
redis-cli TTL "rate_limit:ai:1001:task-breakdown:窗口编号"
```

本地联调需要清理限流记录时，只删除本模块前缀 Key：

```powershell
redis-cli --scan --pattern "rate_limit:ai:*" | ForEach-Object { redis-cli DEL $_ }
```

不要使用 `FLUSHALL` 或 `FLUSHDB` 清理限流计数，以免误删其他业务的 Redis 数据。

排查顺序：

1. 确认请求已登录，且 `UserHolder` 中存在正确的用户 ID。
2. 确认请求方法为 `POST`，请求路径已配置到场景映射表。
3. 确认 `rate-limit.ai.enabled=true`，对应场景规则没有被关闭。
4. 检查 Redis 连通性、Key 计数与 TTL。
5. 检查应用日志中是否存在 Redis 限流检查异常。

## 九、回滚方案

需要紧急暂停 AI 限流时，在目标环境设置：

```text
AI_RATE_LIMIT_ENABLED=false
```

重启应用后，AI 限流拦截器仍会执行路径识别，但不会读取或写入 Redis 计数器。

现有 Redis 限流 Key 会在过期时间到达后自动删除，无需清理。若确需人工清理，只能按 `rate_limit:ai:*` 前缀定向删除。

## 十、已知限制与后续建议

- 当前算法为固定窗口。窗口边界附近可能出现短时间内两段窗口连续放行的情况；若未来需要更平滑、更严格的控制，可升级为滑动窗口或令牌桶。
- 当前返回体提供业务码 `42900`，未附带剩余额度和重试时间响应头；前端需要更精细的交互时可补充 `X-RateLimit-Remaining` 和 `Retry-After`。
- 当前 Redis 异常时的放行策略依赖应用日志发现问题，后续可增加限流拒绝次数、Redis 限流异常次数等监控指标。
- 正式生产发布前，所有 AI 密钥和数据库、Redis 密码都应通过环境变量或密钥管理服务注入，不应以明文形式提交到版本库；已暴露的密钥应立即轮换。

## 十一、交付结论

Redis AI 限流模块已完成以下交付：

- Redis 基础接入与多环境配置。
- 基于 Lua 原子递增的固定窗口限流服务。
- 按当前登录用户和 AI 场景隔离的限流拦截器。
- 场景化额度配置、总开关与 Redis 故障容错。
- 统一的 `RATE_LIMIT_ERROR(42900)` 业务错误返回。
- 后端手工整体测试验证与本文档交付。

至此，Redis AI 限流模块的核心开发、验证和文档交付均已完成。
