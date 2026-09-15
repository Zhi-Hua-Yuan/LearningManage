# LearningManage 部署与日常维护手册

> 文档状态：部署准备稿。它说明“仓库已经具备什么”和“目标环境应怎样操作”，不是上线成功报告。
> 最终分析基线：后端 `develop` @ `13e916f0363e88d21b5053e7555f36ccb605d0ca`；前端 `develop` @ `37fbb504e111b72d88d589367f86fd0b9648e812`。
> 最终复核时间：2026-09-10 21:00:32 +08:00。目标服务器、付费模型、备份与公网入口均未在本轮连接或验证。

## 0. 证据口径

本文使用四种口径，不能相互替代：

| 口径 | 含义 |
|---|---|
| 当前实现 | 上述后端 HEAD 或前端 HEAD 中可直接定位到的源码、配置、脚本 |
| 部署方案 | 用户提供的京东云低资源方案或本文提出的最小候选；不代表已执行 |
| 历史验证 | 仓库已有的本地隔离环境、Stage Gate 或历史发布记录；不代表本次目标服务器 |
| 目标结果 | 本次目标服务器上的脱敏命令输出和人工验收；目前统一为“待执行/未验证” |

主要证据入口：

- 生产资产说明：[`deploy/README.prod.md`](../../deploy/README.prod.md)
- 生产 Compose：[`deploy/docker-compose.prod.yml`](../../deploy/docker-compose.prod.yml)
- 后端配置：[`application.yml`](../../src/main/resources/application.yml)、[`application-prod.yml`](../../src/main/resources/application-prod.yml)
- 发布门禁：[`release-gate.yml`](../../.github/workflows/release-gate.yml)
- Stage 7 历史本地证据：[`local-verification-2026-09-07.md`](../stage7/evidence/local-verification-2026-09-07.md)
- 实际上线记录：[`DEPLOYMENT_RECORD.md`](DEPLOYMENT_RECORD.md)

## A. 部署范围与架构

### A.1 本次最小候选

Phase 0 候选部署 Frontend、Backend、MySQL、Redis、Qdrant；Knowledge Worker、RAG、Agent、Agent Worker 和受控 Tool Calling 默认开启，Cleanup 保持关闭。Prometheus、Tempo、Grafana 只在验收或排障时通过 `observability` profile 启动。

Qdrant 是当前 Compose 的启动依赖：Backend 对 MySQL、Redis、Qdrant 都声明了 `service_healthy`。显式关闭 RAG 目前不等于可以不启动 Qdrant；如需改变这一点，必须修改 Compose 并重新通过发布门禁，不能临时删服务。

Knowledge、Agent和Cleanup Worker不是独立容器，而是Backend JVM内由Spring调度的任务；各自再检查功能开关。Frontend、Backend、MySQL、Redis、Qdrant则是同一台目标主机上的独立容器。

推荐先采用“受邀演示、合成数据、不开匿名付费 AI”的开放范围。是否允许公众自行注册仍待用户确认；当前代码和宿主机 Nginx 都允许注册，只进行了限流。

### A.2 文字拓扑

```text
备案前浏览器
  -> SSH tunnel localhost:18080
  -> 目标机 127.0.0.1:18080
  -> Frontend Nginx 容器 frontend:80
  -> /api/* -> Backend 容器 backend:8123

备案并启用 HTTPS 后
Internet
  -> 京东云防火墙
  -> UFW 80/443
  -> 宿主机 Nginx :443（TLS、限流、文档封锁）
  -> 127.0.0.1:18080
  -> Frontend Nginx frontend:80（SPA、静态缓存、API 代理）
  -> /api/* -> Backend 容器 backend:8123

Backend
  -> mysql:3306
  -> redis:6379
  -> qdrant:6333
  -> Qwen / Embedding / Rerank HTTPS API

按需观测
  Prometheus -> backend:9123/actuator/prometheus
  Backend tracing -> tempo:4318
  Grafana -> prometheus / tempo
  SSH tunnel localhost:13000 -> 目标机 127.0.0.1:13000 -> grafana:3000
```

### A.3 服务与端口

| Compose 服务 | 主要依赖 | 容器内部端口 | 宿主机映射 | 访问范围 | 持久化位置 |
|---|---|---:|---|---|---|
| `frontend` | `backend` healthy | 80 | `127.0.0.1:18080:80` | 宿主机回环；备案前走 SSH 隧道，备案后由宿主机 Nginx 转发 | 静态文件已烘焙进镜像，无运行时数据卷 |
| `backend` | `mysql`、`redis`、`qdrant` healthy；外部模型 API | 8123、9123 | 无；仅 `expose` | 共享其任一Docker网络的项目容器可达；宿主机和公网不可直达 | 业务状态在 MySQL/Redis/Qdrant；容器根文件系统只读 |
| `mysql` | 无 | 3306 | 无 | `data-internal` | 命名卷 `mysql-data` |
| `redis` | 无 | 6379 | 无 | `data-internal`，ACL 用户 `learning_app` | 命名卷 `redis-data`，AOF everysec |
| `qdrant` | 无 | 6333 | 无 | `data-internal`，API Key 鉴权 | 命名卷 `qdrant-data` |
| `migrator` | `mysql` healthy | 无 Web 端口 | 无 | `admin` profile、`data-internal`、一次性进程 | 无独立卷；复用 Backend JAR 中的 Flyway migration |
| `prometheus` | `backend` healthy | 9090 | 无 | `observability` profile、`observability-internal` | `prometheus-data`；3 天/512MB 上限 |
| `tempo` | 无 | 3200、4318 | 无 | `observability` profile、`observability-internal` | `tempo-data`；24 小时保留 |
| `grafana` | Prometheus、Tempo | 3000 | `127.0.0.1:13000:3000` | 仅宿主机回环/SSH 隧道 | `grafana-data` |

注意：浏览器中的 `/api/...`、宿主机的 `127.0.0.1:18080`、容器服务名 `frontend:80` 和 `backend:8123` 是四个不同层次，不能互换。容器中的 `localhost` 只指向该容器自身。Docker网络本身不能按端口做ACL；Backend加入三个网络后，8123和9123都可能被共享任一网络的项目容器寻址，只是当前配置仅让Frontend使用8123、Prometheus使用9123。真正确定的边界是二者都没有宿主机端口映射。

## B. 构建与版本追踪

### B.1 后端

- Maven 打包入口是仓库根目录的 [`pom.xml`](../../pom.xml)，Spring Boot 产物名为 `target/LearningManage-0.0.1-SNAPSHOT.jar`。
- 根目录 [`Dockerfile`](../../Dockerfile)不编译源码，只复制已经存在的上述 JAR；因此 `docker compose up --build` 不能从全新克隆自动完成 Maven 打包。
- 生产使用 [`deploy/Dockerfile.backend.prod`](../../deploy/Dockerfile.backend.prod)，从 Release Bundle 的 `backend/app.jar` 组装镜像，并用完整 Backend SHA 写入 OCI revision label。
- 目标服务器不运行完整 Maven 构建。JAR 必须来自同一次成功的跨仓 Release Gate，并通过 `backend.jar.sha256`校验。

### B.2 前端

- 前端仓库位置：`D:/ajavacode/learning-manage-frontend`；当前可确认版本为 `37fbb504e111b72d88d589367f86fd0b9648e812`。
- `npm run build` 先运行 `vue-tsc`，再运行 `vite build`；Vite 未重写 `build.outDir`，产物目录是前端仓库的 `dist/`。
- Axios 的生产 base URL 固定为同源 `/api`。`VITE_DEV_PROXY_TARGET`只影响 Vite 开发服务器，不决定生产 API 地址。
- 前端本地 `dist/`被 Git 忽略，且没有 `dist.sha256`或 revision 标记，不能证明对应当前 HEAD。
- 后端仓库的 `deploy/dist/`最后一次提交来自 `ad0fd5ea4d3dee52dfd03adaf5af86b83a0780f8`（2026-04-18），与当前前端产物不一致，属于遗留产物，禁止用于本次生产候选。
- 生产使用 [`deploy/Dockerfile.frontend.prod`](../../deploy/Dockerfile.frontend.prod)，只复制 Release Gate 下载并验证过的 `frontend/dist/`。

### B.3 一次候选如何绑定

1. 后端和前端都必须是远端可检出的完整 40 位 SHA。
2. 从受保护的后端 `develop` 运行同一次 [`release-gate.yml`](../../.github/workflows/release-gate.yml)，输入 Backend SHA、Frontend SHA 和 candidate ID。
3. 保存经过测试的 JAR、前端 `dist`、两者校验文件、API 契约、全栈证据、候选 manifest 和生产 manifest。
4. 在可信工作机运行 `assemble-release-bundle.sh`；脚本只从不可变后端 Git SHA 导出生产白名单，不复制遗留 Compose 或 `deploy/dist`。
5. Bundle 目录固定为 `<BACKEND_SHA>-<FRONTEND_SHA>`，上传后再次校验 `SHA256SUMS`。
6. 第三方镜像必须使用 `name:tag@sha256:<digest>`；自建 Backend/Frontend 镜像分别使用完整 Git SHA 标签和 OCI revision label。

当前生产资产已通过 PR #157 进入 `develop`，但 HEAD 尚不是可部署 Release Candidate：本轮没有运行或读取到一次针对 `13e916f...`和所选前端 SHA 的成功 Release Gate 产物，也没有包含三个关键修复的新 Git tag。

### B.4 密钥进入产物的边界

生产 Dockerfile只复制 JAR、验证后的 `dist`和非敏感 Nginx配置；密钥通过服务器 `/etc/learning-manage`下的受保护文件在运行时注入。构建前仍需执行仓库密钥扫描和产物扫描。不能把 `.env`、证书私钥、Cookie、Token、真实日志或数据库导出放入 Bundle。

## C. 配置与秘密管理

### C.1 配置优先级

Spring 先加载 `application.yml`，`SPRING_PROFILES_ACTIVE=prod`后再用 `application-prod.yml`覆盖；Compose 又为关键项显式提供环境变量。因此判断生产行为时必须同时看三层，不能只读模板。

一个重要例子：`application-prod.yml`默认要求 Qdrant HTTPS；生产 Compose明确设置 `QDRANT_REQUIRE_SECURE_TRANSPORT=false`，实际采用“同机 internal bridge + API Key + 不发布端口”的残余风险例外。该例外只适用于单机；跨主机必须恢复 TLS 和 secure transport guard。

### C.2 必要变量清单

| 类别 | 变量 | 用途 | 来源 | 敏感 | 安全默认值/当前边界 |
|---|---|---|---|---|---|
| 版本 | `BACKEND_SHA`、`FRONTEND_SHA`、`BACKEND_IMAGE`、`FRONTEND_IMAGE` | 绑定前后端候选 | Release manifest | 否 | 无，必须与 Bundle 一致 |
| 第三方镜像 | `RUNTIME_IMAGE`、`NGINX_IMAGE`、`MYSQL_IMAGE`、`REDIS_IMAGE`、`QDRANT_IMAGE`、`PROMETHEUS_IMAGE`、`TEMPO_IMAGE`、`GRAFANA_IMAGE` | 固定镜像身份 | 成功 Gate 清单 | 否 | 必须带 digest，不能用浮动标签 |
| 数据库 | `DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`、`MYSQL_ROOT_PASSWORD_FILE` | 应用 DML 和初始化 | 受保护配置/独立密钥文件 | 密码敏感 | 应用账号固定为 DML；root 不进入应用容器 |
| 迁移 | `FLYWAY_DB_USERNAME`、`FLYWAY_DB_PASSWORD` | 一次性 Flyway 管理 | 临时 `migration.env` | 是 | 用户固定为 `learning_manage_migrator`；用后删除 |
| Redis | `REDIS_DATABASE`、`REDIS_PASSWORD` | AI 场景限流/ACL | `learning.env` | 密码敏感 | default 用户关闭；prod `fail-open=false` |
| 会话 | `JWT_SECRET`、`JWT_EXPIRE_SECONDS` | JWT 签名和过期时间 | `learning.env` | Secret 敏感 | Secret 至少 32 位且不得复用；默认 86400 秒 |
| Chat | `ALIYUN_API_KEY`、`AI_BASE_URL`、`AI_MODEL`、`AI_BREAKDOWN_MODEL`、`AI_POLISH_MODEL`、`AI_FALLBACK_MODEL` | 普通 AI 场景 | 服务端受保护配置 | Key 敏感 | Key 不进入浏览器 |
| AI 超时/并发 | `AI_CONNECT_TIMEOUT_MS`、`AI_READ_TIMEOUT_MS`、`AI_TOTAL_TIMEOUT_MS`、`AI_MAX_CONCURRENT_CALLS`、`AI_MAX_WAIT_MILLIS` | 连接、读取、总超时和并发 | 低资源配置 | 否 | 5s/60s/120s/4/0 |
| Embedding | `AI_EMBEDDING_BASE_URL`、`AI_EMBEDDING_QUERY_BASE_URL`、`AI_EMBEDDING_API_KEY`、`AI_EMBEDDING_MODEL`、`AI_EMBEDDING_DIMENSION`、`AI_EMBEDDING_BATCH_SIZE` | 知识索引向量化 | 服务端受保护配置 | Key 敏感 | Worker默认开启；维度1024、批量10 |
| Rerank | `AI_RERANK_BASE_URL`、`AI_RERANK_API_KEY`、`AI_RERANK_MODEL`、`AI_RERANK_MAX_CONCURRENT_CALLS` | RAG重排 | 服务端受保护配置 | Key 敏感 | RAG默认开启；并发1 |
| Qdrant | `QDRANT_API_KEY`、`QDRANT_COLLECTION`、`QDRANT_ALIAS` | 向量鉴权和版本切换 | `learning.env` | Key 敏感 | 不发布端口；collection与alias必须不同 |
| RAG | `AI_RAG_ENABLED`、`AI_RAG_REQUIRE_BACKFILL`、`AI_RAG_QUESTION_HMAC_SECRET`及 Top-K/阈值项 | RAG开关、就绪门槛、问题摘要 | `learning.env` | HMAC 敏感 | 默认开启；强制完成 backfill 后才可回答 |
| Worker | `AI_KNOWLEDGE_WORKER_ENABLED`、claim/worker/embedding/vector并发与lease项 | 索引队列消费者 | `learning.env` | 否 | 默认开启；低资源并发1/1/2 |
| Agent | `AI_AGENT_ENABLED`、`AI_AGENT_WORKER_ENABLED`、`AI_AGENT_TOOL_CALLING_ENABLED`、batch/concurrency/lease/timeout项 | API、消费者和Tool Calling分别控制 | `learning.env` | 否 | 三项默认开启；最大并发1；仅注入场景白名单只读Tool |
| Cleanup | `AI_CLEANUP_ENABLED`、`AI_CLEANUP_SCHEDULE_ENABLED`及保留/批量/租约项 | 数据生命周期 | `learning.env` | 否 | 默认关闭；schedule不能先于功能启用 |
| 管理面 | `MANAGEMENT_TRACING_ENABLED`、`MANAGEMENT_TRACING_SAMPLING`、`OTEL_EXPORTER_OTLP_ENDPOINT`、`GRAFANA_ADMIN_PASSWORD_FILE` | 指标、Trace、Grafana | base/observability env和密钥文件 | Grafana密码敏感 | tracing默认关；采样0.02；Grafana仅回环 |
| 费用观测 | `AI_PRICE_VERSION`、`AI_PRICE_CURRENCY`、模型单价、`AI_DAILY_COST_SOFT_LIMIT`、`AI_DAILY_COST_HARD_LIMIT` | 估算指标和告警阈值 | 经确认的价格表 | 否 | 空值代表未配置；当前没有调用前费用硬阻断 |
| 备份 | `BACKUP_AGE_RECIPIENT`、`OSS_BUCKET_URI`、`S3CMD_CONFIG`、`BACKUP_DIRECTORY` | 加密和对象存储 | `backup.env`/受限配置 | s3凭据敏感 | age只存公钥；私钥离线保存 |

### C.3 文件与脱敏纪律

- `/etc/learning-manage/learning.env`、`observability.env`、`backup.env`、`s3cfg`：`root:lmdeploy`、`0640`。
- MySQL root和Grafana密码文件：`lmdeploy:lmdeploy`、`0600`，供 Compose只读挂载。
- `migration.env`只在迁移窗口创建；固定生产路径由脚本退出时删除。
- 各数据库、Redis、JWT、RAG HMAC、Qdrant、Grafana密钥必须独立生成；`verify-production-secrets.sh`会拒绝模板值、短值、空白和复用。
- 不把完整 `docker compose config`、`docker inspect`、`.env`或日志贴到聊天/工单；只记录必要且脱敏的状态、SHA、端口和计数。
- `AI_DAILY_COST_HARD_LIMIT`当前只参与配置合法性、指标和告警。源码没有在模型调用前读取它并拒绝请求，不能称为“费用硬熔断”。

## D. 网络与访问

### D.1 请求路径

前端请求使用同源 `/api`。容器 Nginx的 `location /api/`使用 `proxy_pass http://backend:8123`且不附加替换 URI，因此 `/api/user/login`仍以完整路径到达 Backend；Backend自身 context path也是 `/api`。SPA通过 `try_files $uri $uri/ /index.html`支持刷新非根路由。

静态资产 `/assets/`可长期缓存，`index.html`使用 `no-store`。接口文档路径在宿主机和前端容器两层返回404。

### D.2 HTTPS与端口边界

- 备案前：用户方案要求云防火墙与UFW只开放22，通过SSH隧道访问 `127.0.0.1:18080`；本轮未验证目标服务器规则。
- 备案后：宿主机Nginx终止TLS，80只做ACME和跳转，443转发到回环18080。当前没有真实域名和证书验证证据，不能写“HTTPS已启用”。
- Backend 8123、MySQL 3306、Redis 6379、Qdrant 6333、Actuator 9123、Prometheus和Tempo均无宿主机映射。
- Grafana只映射 `127.0.0.1:13000`，仍需SSH隧道。
- 登录、注册、AI分别有宿主机IP限流；应用AI限流按用户ID和场景写入Redis。两者目的不同，且都不是全局费用预算。

### D.3 超时

- 模型客户端：连接5秒、读取60秒、总时限120秒。
- 容器Nginx：普通API 30秒；`/api/ai/`发送/读取150秒。
- 宿主机AI代理：150秒。

外层超时覆盖应用总时限，当前顺序合理。仓库没有证明本次生产接口使用SSE、WebSocket或流式响应，因此手册不添加相关代理配置。

## E. 数据初始化、迁移与持久化

### E.1 当前 Schema

当前代码要求Flyway V1～V8，权威文件位于 `src/main/resources/db/migration/`，校验和清单位于 [`published-migrations.sha256`](../../src/test/resources/flyway/published-migrations.sha256)。已发布文件只能追加，不能修改既有内容。

### E.2 全新空库（本次计划路径）

1. 启动MySQL并确认目标库为空。
2. 使用root仅创建固定的migrator/app账户及最小权限。
3. 用临时 `migration.env`执行 `info -> validate(允许pending) -> migrate -> validate -> info`。
4. 验证history总数、成功数、V1～V8 SQL成功数均为8。
5. 使用app账户验证DML连接，并确认CREATE TABLE失败。
6. 删除临时迁移环境；Backend保持 `FLYWAY_ENABLED=false`。

不得对空库执行baseline、repair或clean。

### E.3 已有数据升级

已有Flyway history的数据库必须先备份并在隔离实例恢复，再执行validate和前向migration。没有history但已有旧结构的接管属于另一条高风险流程，需要显式baseline授权和专门审计；它不适用于本次“全新空库”计划。不得用自动baseline、改历史migration或清库掩盖差异。

### E.4 持久化与恢复来源

| 数据 | 容器重建后 | 业务恢复方式 | 说明 |
|---|---|---|---|
| MySQL业务、Outbox、Agent Run、索引元数据 | `mysql-data`应保留 | 已验证的加密逻辑备份 | 命名卷不是备份 |
| Redis限流/AOF状态 | `redis-data`应保留 | 通常允许重建/自然过期 | 不是业务事实主源 |
| Qdrant向量 | `qdrant-data`应保留 | 从MySQL通过REBUILD重建 | 派生索引，不作为唯一业务备份 |
| Prometheus指标 | `prometheus-data`应保留 | 通常不做业务恢复 | 3天/512MB有限保留 |
| Tempo Trace | `tempo-data`应保留 | 通常不做业务恢复 | 24小时有限保留 |
| Grafana状态 | `grafana-data`应保留 | 配置可从仓库provisioning重建 | 不等同于告警送达记录 |

业务写入会产生知识索引意图；权限、删除、成员终止或源版本变化可能使已有向量过期。RAG命中后还会回到MySQL重新hydration和鉴权；发现陈旧候选时，当前修复通过独立新事务持久化纠偏事件。关闭Worker会停止消费，但不会阻止V4以后已经接入写路径继续记录Outbox事件。

## F. 启动与功能启用顺序

### F.1 Phase 0 baseline 首次部署

1. **Bundle验证**：校验目录名、manifest、JAR/dist、V1～V8和镜像digest。
2. **镜像组装**：只从验证产物构建SHA标签的Backend/Frontend镜像。
3. **数据依赖启动**：MySQL、Redis、Qdrant。
4. **依赖健康**：MySQL ping、Redis ACL PING、Qdrant TCP探针通过。
5. **账户与迁移**：仅首次空库执行provision和V1～V8。
6. **Backend/Frontend启动**：Backend readiness通过后再启动Frontend。
7. **默认开启 smoke**：健康、页面、登录、基础业务、Knowledge/RAG/Agent/Tool Calling 和 Draft 边界。
8. **原子切换**：全部通过后才将 `/opt/learning-manage/current`指向新release。

`depends_on: service_healthy`只证明对应容器探针通过，不证明完整业务、真实模型、RAG或Agent可用。Backend readiness当前包含应用状态和核心数据库，不包含AI依赖；AI应另查内部 `/actuator/health/ai`。`/api/health`本身只返回固定应用存活响应，也不能单独证明数据库或模型可用。

具体而言，MySQL探针是root `mysqladmin ping`，Redis探针是受限ACL用户PING，Qdrant探针只检查6333 TCP可连接；它们分别不能证明V1～V8已迁移、所有Redis业务命令可用或Qdrant鉴权、集合和alias正确。

### F.2 状态层次

| 层次 | 判定 |
|---|---|
| Running | Docker进程仍在，不代表应用健康 |
| 容器healthy | 该服务定义的有限探针通过 |
| 基础业务可用 | 登录、项目、任务等合成数据smoke通过 |
| 知识索引就绪 | Worker开启；指定REBUILD成功；backlog=0、DEAD=0；维度和alias正确 |
| RAG可用 | backfill就绪、真实/获批模型链路、引用、权限、STALE与降级测试通过 |
| Agent可用 | Agent API与Worker都开启；Run可领取、心跳、完成；Draft→Confirm→Report通过 |
| Tool Calling可用 | 在Agent可用基础上，Tool开关、白名单、权限、次数和超时验证通过 |

### F.3 默认开启与故障关闭

- `AI_KNOWLEDGE_WORKER_ENABLED`、`AI_RAG_ENABLED`、`AI_AGENT_ENABLED`、`AI_AGENT_WORKER_ENABLED` 和 `AI_AGENT_TOOL_CALLING_ENABLED` 默认均为 `true`。
- 默认开启不会绕过 backfill、权限、Citation、Tool 白名单、调用次数、超时或 Draft 确认边界。
- Cleanup 仍默认关闭；首次启用时 schedule 保持关闭，先 Dry Run、复核预计数量并由 SYSTEM_ADMIN 批准正式执行。
- 故障时按 Tool Calling → Agent Worker → Agent → RAG → Knowledge Worker 的顺序显式设为 `false`；每次只重建 Backend，不重启 MySQL、Redis 或 Qdrant。

## G. 目标服务器验证清单

统一使用合成数据和受邀测试账号。下表“实际证据”目前均未在目标服务器取得；后续只把脱敏摘要写入上线记录。

| 项目 | 前置条件 | 操作 | 预期结果 | 实际证据 | 未验证范围 |
|---|---|---|---|---|---|
| 页面访问 | baseline healthy；备案前SSH隧道或备案后HTTPS | 打开登录页并刷新一个非根路由 | 静态资源200、刷新不404 | 待执行 | 不代表API可用 |
| 登录 | 合成账号存在 | 正确/错误密码各一次 | 正确登录；错误凭据不泄露细节 | 待执行 | 未授权公众注册 |
| 项目与任务 | 已登录 | 创建、查询、更新、归档/恢复项目与任务 | 返回统一响应，刷新后数据仍在 | 待执行 | 不做压测 |
| 跨账号权限 | 两个合成账号 | B账号读取/修改A的私人资源 | 拒绝访问且不返回私人正文 | 待执行 | 不使用私人真实数据 |
| AI草稿 | 用户明确批准少量模型调用和预算 | preview → 查看草稿 → confirm | 模型输出不直接写业务，确认后才落库 | 待执行 | Stub不能替代真实Provider |
| Knowledge | 默认开启且依赖就绪 | 发起backfill，查看status/events | backlog归零、DEAD=0、1024维alias正确 | 待执行 | 需限定付费调用次数 |
| RAG引用 | 默认开启且backfill就绪 | 提问、读取result、修改/撤权后复查 | 引用可追溯；无权或STALE内容不返回 | 待执行 | 需限定付费调用次数 |
| Agent完整链路 | Agent/Worker/Tool Calling默认开启 | 创建Run、等待完成、确认Draft、读取Report | Run状态完整、场景白名单有效、确认后生成报告 | 待执行 | 无全局Tool注册 |
| 内部端口 | 服务启动 | 云外核查22/80/443；目标机 `ss -lntp`核查 | DB/Backend/Qdrant/Actuator/Grafana无公网监听 | 待执行 | 不粘贴完整敏感输出 |
| 管理端点 | SSH会话/容器网络 | 查liveness、readiness、AI分组和metrics | 管理端点内部可达、外部不可达 | 待执行 | healthy不等于AI功能通过 |
| 普通重启 | 已有合成数据且已备份 | 受控重启应用容器 | MySQL数据、Run/Outbox状态保留 | 待执行 | 不停止生产数据库做故障演练 |
| 备份/恢复 | 首个加密备份和离线age私钥 | 在隔离MySQL容器执行restore drill | checksum、mysqlcheck、关键表计数一致 | 待执行 | 未授权不得覆盖当前库 |

真实Chat、Embedding、Rerank调用必须先确认模型、最大次数和预算。本轮没有发起任何付费调用。

## H. 演示安全与费用控制

### H.1 已实现保护

- 模型API Key只注入Backend，不进入前端Bundle。
- 应用AI限流按 `userId + scene + time bucket`写Redis，prod设置 `fail-open=false`。
- 宿主机Nginx对登录、注册和全部 `/api/ai/`另做IP限流。
- 普通AI并发上限4；Knowledge、Embedding、Rerank和Agent按低资源值限制；Agent全局和单用户并发均为1。
- Agent使用持久Run、租约/heartbeat、Tool白名单、Tool次数和超时；Tool Calling默认开启，但不注册全局默认Tool。
- Worker/RAG/Agent/Cleanup都有独立紧急关闭开关。

### H.2 仍存在的边界

- 当前 `/api/user/register`是登录拦截白名单，宿主机Nginx也只是5次/分钟限流，并未关闭注册。
- 应用限流按用户维度，多账号可以绕过单用户额度；宿主机IP限流也不能覆盖分布式来源。
- `AI_DAILY_COST_*`是观测/告警阈值，不会在达到hard值时自动阻止模型调用。
- 初始索引回填会批量调用Embedding；RAG可能继续调用Embedding和Rerank；Agent一次Run可能包含多轮模型调用。
- AI调用记录、失败信息、业务数据库和Trace都可能包含或关联业务内容；不能使用私人真实数据做公开演示，也不能上传未脱敏日志。
- 单机没有高可用，公网演示不应宣传SLA、P95或零停机。

最小建议是：受邀账号、合成数据、默认禁止公众注册、按阶段开启付费能力、Provider侧设置预算/额度提醒，必要时直接将 `AI_CHAT_ENABLED=false`并关闭RAG/Agent/Worker。若选择受邀模式，需经批准后把宿主机Nginx的注册location改为明确拒绝，并验证前端注册入口处理；本轮不修改。

## I. 更新、回滚与恢复

### I.1 更新前

1. 记录正在运行的Agent Run、Knowledge backlog/DEAD、Cleanup Run和Outbox状态。
2. 先关闭Tool Calling、Agent Worker、RAG和Knowledge Worker，等待正在执行项结束或租约状态可解释。
3. 只使用成功Gate的不可变前后端SHA和Bundle。
4. 在隔离库验证migration；单机更新允许短暂不可用，不承诺零停机。
5. 确认回退候选包含三个关键安全/一致性修复，不能只看旧Tag名称。

当前脚本没有在更新或回退前自动等待活动Agent Run、PROCESSING Outbox或Worker积压归零，所以这一步是人工只读门禁，不能省略。

### I.2 四种恢复动作

| 动作 | 适用对象 | 当前实现 | 不能混淆的边界 |
|---|---|---|---|
| 应用/前端回退 | JAR、静态资源、配置绑定 | `rollback.sh <BACKEND_SHA>-<FRONTEND_SHA>` | 只重建Backend/Frontend，不自动回退数据库 |
| 功能降级 | Worker、RAG、Agent、Tool、Cleanup | 修改受保护env后只重建Backend | 不恢复已删除数据 |
| 数据库恢复 | MySQL业务事实 | 从验证过的age加密备份在维护窗口恢复 | 只有确认数据破坏才考虑；不能常规使用 |
| Qdrant重建 | 派生向量索引 | 关闭RAG/Worker，基于MySQL REBUILD并切alias | 不等于MySQL恢复；重建会产生Embedding费用 |

`rollback.sh`会先以当前镜像关闭高风险AI，再要求目标release存在、两边migration集合相同、外部镜像清单相同，最后切换`current`并smoke；不满足条件时拒绝自动回退。最新 `stage7-v1.0.0`指向的提交早于三项关键修复，不能作为默认回退目标。

备份脚本设计为MySQL一致性逻辑dump → zstd → age公钥加密 → 私有OSS，并保存checksum；这仍需在目标环境真实执行和隔离恢复后，才能写“恢复成功”。

## J. 最小运行维护

| 关注点 | 最小检查 | 证据来源 | 当前证据状态 |
|---|---|---|---|
| 应用错误/重启 | `docker compose ps`、Backend有限时间日志、restart count | Docker/宿主机 | 目标机待执行 |
| 内存/Swap | `docker stats --no-stream`、`free -h` | 宿主机 | 目标机待执行 |
| 磁盘 | `df -h`、Docker磁盘用量、备份目录和卷增长 | 宿主机 | 目标机待执行 |
| 数据库 | readiness、连接池错误、备份结果 | Actuator/MySQL | 目标机待执行 |
| Worker积压 | pending/retry/dead数量和oldest age | 管理API/指标 | 历史本地有指标；目标机待执行 |
| AI失败/耗时/费用 | 调用状态、failure type、duration、token和estimated cost | 应用管理API/指标 | 仅在配置单价时可估算；不是Provider账单 |
| RAG索引 | backfill、event/document状态、alias/维度 | Knowledge admin/Qdrant | 目标机待执行 |
| Agent | queue depth、oldest pending、Run状态、lease | 管理API/指标 | 目标机待执行 |
| 日志/Trace内容 | 抽样检查脱敏、截断和保留 | 日志/MySQL/Tempo | 目标机待执行 |

仓库有Prometheus规则、六个Grafana dashboard和Tempo配置；历史隔离环境曾验证采集。但生产Compose没有Alertmanager/通知通道，不能称为“告警已触达”。不启动完整观测时，只能依赖容器状态、内部health、有限日志和管理查询，缺少连续时序与Trace关联。

内存持续超过85%或Swap持续增长时，先运行 `observability-off.sh`关闭Prometheus、Tempo、Grafana并用基础env重建Backend关闭tracing。

## K. 命令与变更纪律

下列命令仅为后续获批操作手册。本轮全部“未执行”。执行目录均为上传并验证后的 `/opt/learning-manage/releases/<BACKEND_SHA>-<FRONTEND_SHA>`，除非另有说明。

| 类型 | 命令 | 位置/前置条件 | 风险 | 本轮状态 |
|---|---|---|---|---|
| 只读 | `git rev-parse HEAD`、`git status --short --branch` | 可信构建机仓库 | 无外部副作用 | 已用于基线核查 |
| 只读 | `scripts/prod/verify-release-bundle.sh` | 目标机release目录；Bundle完整 | 读取文件和镜像元数据 | 未执行 |
| 只读 | `docker compose --project-name learning-manage --env-file /etc/learning-manage/learning.env --file deploy/docker-compose.prod.yml ps` | 目标机；不得复制完整敏感config输出 | 查询容器状态 | 未执行 |
| 只读 | `ss -lntp` | 目标机 | 输出需脱敏，只记录监听地址/端口 | 未执行 |
| 副作用 | `scripts/prod/deploy.sh --initialize-database` | 首次空库、凭据和Bundle已审批 | 创建账户、迁移、启动容器、切换current | 未执行 |
| 副作用 | `scripts/prod/migrate.sh` | 迁移窗口、临时migration.env | DDL/DML前向变更 | 未执行 |
| 副作用 | `scripts/prod/observability-on.sh` | 资源余量足够 | 重建Backend并启动观测 | 未执行 |
| 副作用 | `scripts/prod/observability-off.sh` | 观测结束/资源压力 | 停止观测并重建Backend | 未执行 |
| 副作用 | `scripts/prod/backup-mysql.sh` | backup.env、age公钥、私有OSS | 读取全库并上传加密对象 | 未执行 |
| 副作用 | `scripts/prod/restore-drill.sh <加密备份> <离线identity>` | 隔离环境、明确批准 | 创建并最终移除精确命名临时容器/卷 | 未执行 |
| 副作用 | `scripts/prod/rollback.sh <旧后端SHA>-<旧前端SHA>` | 已验证且兼容的保留release | 短暂不可用、重建应用容器 | 未执行 |

常规排障禁止使用 `docker compose down -v`、`docker system prune --volumes`、Flyway `clean/repair`、全局卷删除或直接修改Flyway history。

### K.1 上线前阻塞项

| 阻塞项 | 最小处理 | 验证 | 恢复/退路 |
|---|---|---|---|
| 当前后端HEAD已进入 `develop`，但无本次成功Gate证据 | 冻结 `13e916f...`与最终前端SHA并运行跨仓Gate | 后端/前端Gate、生产manifest和artifact checksum均PASS | 不上传/不切current |
| 最新Tag早于三个关键修复 | 新候选必须以包含 `3c06eeb`、`5d51eb3`、`72f2aab`的SHA构建 | ancestry检查和manifest SHA | 不以旧Tag作为默认回退 |
| 前端本地dist和后端`deploy/dist`均非权威产物 | 只使用同一次Gate生成的dist和`dist.sha256` | manifest、文件清单、镜像revision一致 | 拒绝Bundle |
| 开放范围/公众注册未确认 | 明确“本人/受邀/公开注册”；建议受邀并阻断register | 公网register返回预期拒绝；既有账号登录正常 | 恢复原Nginx配置需重新审批 |
| 真实域名、备案/DNS/证书当前状态未确认 | 核对后再开放80/443 | DNS、HTTPS、renew dry-run和外部端口检查 | 继续只开放22和SSH隧道 |
| 模型、次数、预算和Provider授权未确认 | 批准模型及最大调用次数 | Provider账单/调用ID脱敏摘要 | 按故障顺序关闭AI开关，保留基础业务 |
| 目标主机现状未在本轮核实 | 脱敏核对OS/架构/资源/Docker/同机服务/重要数据 | `verify-host.sh`及人工复核 | 不执行初始化/部署 |
| 备份目标和离线age恢复介质未验证 | 配置私有Bucket最小权限并先做隔离恢复 | checksum、对象存在、mysqlcheck和计数 | 不宣称可恢复，不开放真实数据 |

### K.2 非阻塞建议

- 前端 `.env.example`仍写Grafana回环3000，而生产隧道口径是13000；当前Release Gate不注入该变量，页面会隐藏链接，不影响核心业务。若要展示链接，后续以构建期变量单独修正并验证。
- 前端根 `index.html`引用 `/favicon.ico`，当前public/dist只有PNG，可能产生一次低风险404。
- 根 [`README.md`](../../README.md)仍偏向遗留 `deploy/docker-compose.yml`和旧端口；生产操作应以 `deploy/README.prod.md`及本文为准，后续可单独校正文档，但不阻塞Bundle。

## L. 仍需一次性确认的环境信息

1. 最终开放范围：仅本人、受邀账号，还是允许公众注册？
2. 京东云主机是否仍为 Ubuntu 24.04.2 x86_64、2核/4GB/2GB Swap/60GB，且是否有其他同机服务或必须保护的数据？
3. 域名、ICP备案、A记录和HTTPS当前分别处于什么状态？
4. 首次只开启普通AI/任务拆解是否确认？允许的模型、最大真实调用次数和预算是多少？
5. Prometheus/Tempo/Grafana是否仍按需启动而非长期运行？
6. 私有OSS Bucket、最小权限子账号、age离线私钥介质是否已经准备？

这些信息未确认不影响继续评审仓库和组装文档，但会阻止公网开放、真实付费调用和生产数据操作。
