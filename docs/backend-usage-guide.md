# 后端运行与 AI 依赖使用说明

本文档说明 LearningManage 后端的本地运行、生产配置原则、SQL 初始化顺序，以及 Redis、AI、Prompt 模板和 AI 相关数据表的使用约定。

接口请求与响应字段见 [AI 接口文档](api/README.md)，草稿联调结果见 [Sprint 3 AI 草稿联调验收记录](sprint/Sprint3_AI草稿联调验收记录.md)。

## 1. 运行环境

| 依赖 | 建议版本 | 用途 |
|---|---|---|
| JDK | 17 | 运行 Spring Boot 服务。 |
| MySQL | 8.x | 保存业务数据、AI 草稿、Prompt 模板和调用记录。 |
| Redis | 6.x 及以上 | AI 调用限流计数。 |
| Maven Wrapper | 仓库内置 | 构建、测试和启动，无需额外安装 Maven。 |

默认服务地址：

```text
Base URL:  http://localhost:8123/api
健康检查:  GET http://localhost:8123/api/health
接口文档:  http://localhost:8123/api/doc.html
```

Windows 启动命令：

```powershell
.\mvnw.cmd spring-boot:run
```

Linux 或 macOS 启动命令：

```bash
./mvnw spring-boot:run
```

指定生产 Profile：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

启动后应先访问健康检查接口，再启动前端进行联调。

## 2. 配置原则与环境变量

配置文件分层如下：

| 文件 | 作用 |
|---|---|
| `application.yml` | 公共端口、上下文路径、MyBatis Plus 与 AI 通用配置。 |
| `application-dev.yml` | 本地开发环境配置。 |
| `application-test.yml` | 测试环境配置，使用独立测试数据库和 Redis 数据库。 |
| `application-prod.yml` | 生产环境配置，应完全由环境变量或密钥管理服务注入敏感数据。 |

生产环境应使用以下环境变量，不应将真实值写入 Git：

| 分类 | 环境变量 | 说明 |
|---|---|---|
| MySQL | `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD` | 数据库连接。 |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_DATABASE`、`REDIS_PASSWORD` | Redis 连接。 |
| AI Key | `ALIYUN_API_KEY` | 阿里云百炼 / DashScope 的 API Key。 |
| AI 模型 | `AI_BREAKDOWN_MODEL`、`AI_POLISH_MODEL`、`AI_FALLBACK_MODEL` | 按场景指定模型；未设置时使用配置中的默认模型。 |
| AI 超时 | `AI_CONNECT_TIMEOUT_MS`、`AI_READ_TIMEOUT_MS` | AI 连接和读取超时，单位为毫秒。 |
| 限流 | `AI_RATE_LIMIT_ENABLED`、`AI_RATE_LIMIT_FAIL_OPEN` | AI 限流总开关和 Redis 故障策略。 |
| 限流额度 | `AI_RATE_LIMIT_*_WINDOW_SECONDS`、`AI_RATE_LIMIT_*_MAX_REQUESTS` | 各 AI 场景的固定窗口和最大请求数。 |

### 配置安全要求

- 不要在 `application-*.yml`、README、接口示例、日志或提交记录中写入真实密码、JWT、Redis 密码或 AI Key。
- 密钥疑似泄露时，应先在服务商侧轮换密钥，再删除仓库中的明文配置并改用环境变量。
- 本地开发也建议通过操作系统环境变量或未提交的本地覆盖配置提供敏感值。

## 3. 数据库初始化

### 3.1 首次初始化

首次在空 MySQL 数据库中部署时，先创建数据库，例如：

```sql
CREATE DATABASE learning_manage
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

然后按以下业务依赖顺序执行 `sql/` 下的脚本：

| 顺序 | 脚本 | 是否必需 | 说明 |
|---:|---|---|---|
| 1 | `init_user.sql` | 是 | 用户基础表。 |
| 2 | `init_team.sql` | 按需 | 团队功能的团队表。 |
| 3 | `init_team_member.sql` | 按需 | 团队成员关系表，应在团队表后执行。 |
| 4 | `init_project.sql` | 是 | 项目表。 |
| 5 | `init_milestone.sql` | 是 | 项目阶段表。 |
| 6 | `init_task.sql` | 是 | 项目任务表。 |
| 7 | `init_weekly_review.sql` | 按需 | 周总结表。 |
| 8 | `init_task_status_idempotency.sql` | 按需 | 任务状态更新幂等记录。 |
| 9 | `init_task_title_rename_log.sql` | 按需 | 日报回顾任务改名日志。 |
| 10 | `init_prompt_template.sql` | 使用 AI 时必需 | Prompt 模板表及内置 V1 模板数据。 |
| 11 | `init_ai_draft.sql` | 使用 AI 草稿时必需 | AI 草稿表。 |
| 12 | `init_ai_draft_confirm_log.sql` | 使用 AI 草稿时必需 | AI 草稿确认幂等日志。 |
| 13 | `init_ai_call_log.sql` | 使用 AI 时必需 | AI 调用记录表。 |
| 14 | `init_ai_replan_operation.sql` | 使用 AI 重排时必需 | 清单任务重排操作表。 |
| 15 | `init_ai_replan_item.sql` | 使用 AI 重排时必需 | 清单任务重排明细表。 |

当前脚本未通过数据库外键强制依赖顺序，但上述顺序反映实际业务依赖，便于后续排查和数据回查。

### 3.2 已有数据库升级

- 不要对已有数据库直接重复执行全部初始化脚本。
- 部分历史脚本没有 `IF NOT EXISTS`，重复执行可能失败；部署前应先备份数据库，并检查表、索引和字段是否已经存在。
- 结构变更应使用独立、可审查的迁移 SQL；执行后记录执行环境、时间和结果。
- `init_prompt_template.sql` 中使用 `INSERT IGNORE` 初始化缺失的 V1 模板，不会覆盖已存在的人工维护版本。

## 4. Redis 与 AI 限流

Redis 仅用于当前 AI 限流计数，不需要手工预置限流 Key。启动前可使用：

```bash
redis-cli ping
```

返回 `PONG` 表示 Redis 可连接。

当前限流使用固定窗口计数，Key 格式为：

```text
rate_limit:ai:{userId}:{scene}:{windowBucket}
```

开发/生产默认场景额度如下：

| scene | 时间窗口 | 最大请求数 |
|---|---:|---:|
| `task-breakdown` | 60 秒 | 3 |
| `weekly-polish` | 60 秒 | 5 |
| `today-order` | 60 秒 | 10 |
| `daily-review-rename` | 60 秒 | 5 |
| `list-replan` | 60 秒 | 3 |

受限流保护的是会真实调用大模型的 AI `POST` 接口，例如任务拆解预览 `POST /ai/breakdown/preview`。草稿详情、确认和取消不会再次调用模型，也不受该规则限制。

超过额度时，后端返回业务码 `42900`。项目的业务异常可能仍使用 HTTP `200 OK`，前端必须读取响应体中的 `code`。

### Redis 故障策略

- `rate-limit.ai.fail-open=true`：Redis 限流检查异常时记录告警并放行 AI 请求，优先保证功能可用。
- `rate-limit.ai.fail-open=false`：Redis 不可用时拒绝请求，优先避免限流失效造成 AI 成本失控。

生产环境应根据可用性与成本控制需求选择该策略，并监控 Redis 连接异常日志。

如需清理本地限流 Key，只清理由本模块创建的前缀：

```powershell
redis-cli --scan --pattern "rate_limit:ai:*" | ForEach-Object { redis-cli DEL $_ }
```

不要使用 `FLUSHALL` 或 `FLUSHDB`，以免误删其他 Redis 数据。

更多实现和排查信息见 [Redis AI 限流模块文档](redis-rate-limit-module.md)。

## 5. AI Key 与模型配置

AI 服务采用 DashScope OpenAI 兼容接口。至少需要提供：

```text
ALIYUN_API_KEY
```

应用会根据场景选择 `breakdown-model`、`polish-model`、`fallback-model` 或默认 `model`。AI 调用超时和模型错误会映射为以下业务错误码：

| code | 含义 |
|---:|---|
| `30001` | AI 服务暂时不可用。 |
| `30002` | AI 服务响应超时。 |
| `30003` | AI 返回结果格式异常。 |
| `30004` | AI 服务配置异常。 |

AI 调用前后的记录由 `ai_call_log` 保存；日志写入失败不会阻断主业务，但应通过日志和监控及时排查。

## 6. Prompt 模板管理规则

Prompt 数据存储于 `prompt_template` 表。核心字段如下：

| 字段 | 说明 |
|---|---|
| `template_code` | Prompt 编码，例如 `task-breakdown.default`。 |
| `scene` | 业务场景，例如 `task-breakdown`。 |
| `version` | 模板版本号，正整数递增。 |
| `template_content` | 系统 Prompt 正文。 |
| `enabled` | 是否启用：`1` 启用，`0` 停用。 |
| `is_delete` | 逻辑删除标记。 |

模板解析规则：

1. 后端优先查询数据库中与目标 `template_code` 匹配且 `enabled=1` 的模板。
2. 同一 `template_code` 必须恰好存在一个启用版本；存在 0 个或多个启用版本时，后端回退到内置模板。
3. 启用版本的 `template_code`、`scene`、`version` 和 `template_content` 不合法时，后端也回退到内置模板。
4. 查询数据库失败时，后端记录异常并回退到内置模板，避免 Prompt 配置问题阻断 AI 主流程。

发布新版本的建议步骤：

1. 新增更高版本，初始设置为 `enabled=0`。
2. 在测试环境验证对应 AI 场景和 `ai_call_log` 中记录的 Prompt 元数据。
3. 停用旧版本，再启用新版本，保证同一编码始终只有一个启用版本。
4. 观察调用结果、失败率和耗时；如需回滚，恢复旧版本启用状态并停用新版本。

Prompt 使用情况可通过 `ai_call_log` 中的 `prompt_type`、`prompt_template_id`、`prompt_version` 和 `prompt_source` 追踪。

## 7. AI 草稿与调用记录数据表

### 7.1 `ai_draft`

`ai_draft` 保存 AI 生成后、用户确认前的草稿，不是最终项目数据。

| 字段/状态 | 用途 |
|---|---|
| `draft_id` | 对外暴露的草稿 ID，用于详情、确认和取消。 |
| `scene` | 草稿所属 AI 场景，例如 `task-breakdown`。 |
| `payload_json` | 草稿完整内容；任务拆解场景包含目标、周期、描述和阶段任务列表。 |
| `status=0` | 预览中，可确认或取消。 |
| `status=1` | 已确认，已完成对应业务落库。 |
| `status=2` | 已取消，不会创建业务数据。 |
| `status=3` | 已过期，不允许继续确认。 |
| `expire_at` | 草稿到期时间；服务端以该值判定是否过期。 |

### 7.2 `ai_draft_confirm_log`

该表保存 `user_id + draft_id + operation_id` 的确认幂等记录和最终 `business_id`。确认请求因网络超时重试时必须复用原 `operationId`，才能保证不会重复创建项目、阶段和任务。

### 7.3 `ai_call_log`

`ai_call_log` 记录真实发起的大模型调用，用于问题排查、成本观察和 Prompt 版本追踪；它不等同于接口最终是否成功。

| 字段/状态 | 用途 |
|---|---|
| `scene` | AI 调用场景。 |
| `model_name` | 实际调用的模型。 |
| `prompt_*` | Prompt 类型、模板 ID、版本和来源。 |
| `request_text` / `response_text` | AI 请求和响应内容，用于排查，访问时应注意敏感信息。 |
| `status=0` | 调用中。 |
| `status=1` | 调用成功且结果通过解析和业务校验。 |
| `status=2` | 模型调用失败。 |
| `status=3` | AI 有响应，但解析或业务校验失败。 |
| `status=4` | 调用超时。 |
| `cost_time_ms` / `retry_count` | 调用耗时和重试次数。 |

草稿详情、确认和取消都是本地业务操作，不会新增 `ai_call_log`；生成 AI 草稿等真实模型调用才会写入该表。

更多字段与查询接口见 [AI 调用记录接口文档](api/ai-call-log-api.md) 和 [AI 调用记录模块文档](ai-call-log-module.md)。

## 8. 启动后检查清单

1. `GET /api/health` 返回成功。
2. 可以登录并正常携带 `Authorization: Bearer <token>` 请求受保护接口。
3. Redis `PING` 正常，且限流配置符合当前环境。
4. AI Key 通过环境变量注入，日志中没有输出完整密钥。
5. `prompt_template` 中每个业务使用的模板编码只有一个 `enabled=1` 版本。
6. 生成 AI 草稿后，`ai_draft` 和 `ai_call_log` 均有预期记录；确认后 `ai_draft_confirm_log`、`project`、`milestone`、`task` 数据一致。

