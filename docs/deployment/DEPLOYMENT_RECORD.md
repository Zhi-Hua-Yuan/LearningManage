# LearningManage 实际上线记录

> 当前结论：**部署尚未执行**。本文件已建立可追溯模板，并只填写仓库事实、用户提供但未复核的环境计划，以及已有历史隔离验证。任何目标服务器结果均为“待执行”或“未验证”。

## 1. 状态定义

| 状态 | 含义 |
|---|---|
| 待执行 | 已计划但尚未在对应环境运行 |
| 已执行通过 | 在标明的证据环境实际执行且满足预期 |
| 已执行失败 | 在标明的证据环境执行但未满足预期 |
| 未验证 | 有配置、陈述或间接信息，但没有足够证据确认结果 |
| 不适用 | 经确认不属于本次部署范围 |

证据环境固定使用：`本地隔离环境`、`目标服务器`、`历史发布记录`。三者不能互相替代。

## 2. 本轮分析基线

### 2.1 最终快照

| 项目 | 结果 | 状态 | 证据环境 |
|---|---|---|---|
| 最终复核时间 | 2026-09-10 21:00:32 +08:00 | 已执行通过 | 本地隔离环境 |
| 后端仓库 | `D:/ajavacode/LearningManage` | 已执行通过 | 本地隔离环境 |
| 后端分支 | `develop` | 已执行通过 | 本地隔离环境 |
| 后端完整HEAD | `13e916f0363e88d21b5053e7555f36ccb605d0ca` | 已执行通过 | 本地隔离环境 |
| 后端远端跟踪 | `origin/develop`指向相同SHA，ahead/behind为0/0 | 已执行通过 | 本地Git引用；未重新fetch |
| PR #157 | 已将生产部署分支合并到 `develop` | 已执行通过 | 本地Git历史/远端跟踪引用 |
| tracked/staged修改 | 无 | 已执行通过 | 本地隔离环境 |
| untracked | 18个：`docs/interview/` 9个、`docs/stage8/` 9个 | 已执行通过 | 本地隔离环境 |
| 前端仓库 | `D:/ajavacode/learning-manage-frontend` | 已执行通过 | 本地隔离环境 |
| 前端分支/HEAD | `develop` / `37fbb504e111b72d88d589367f86fd0b9648e812` | 已执行通过 | 本地隔离环境 |
| 前端工作区 | tracked/untracked均无输出，未显示ahead/behind | 已执行通过 | 本地隔离环境 |

本轮第一次读取时，后端HEAD是 `0083abdfdfe4c131be7b90df29c565f307c521c1`且有8个已暂存的CI/生产门禁文件；共享工作区随后由外部流程推进到 `99ef925...`，又通过PR #157合并为 `develop`上的 `13e916f...`。本轮没有创建、提交、推送或合并这些变更，也没有覆盖它们。文档采用21:00:32再次读取的最终快照。

### 2.2 基线时未跟踪文件

```text
docs/interview/CORE_FLOWS.md
docs/interview/CRITICAL_FINDINGS_AUDIT.md
docs/interview/CRITICAL_REPRO_EVIDENCE.md
docs/interview/INTERVIEW_ANSWER_KEY.md
docs/interview/INTERVIEW_CHEATSHEET.md
docs/interview/INTERVIEW_QUESTION_BANK.md
docs/interview/MINIMAL_FIX_PLAN.md
docs/interview/PROJECT_MAP.md
docs/interview/PROJECT_PITCHES.md
docs/stage8/README.md
docs/stage8/architecture/agent-state-and-tool-calling.md
docs/stage8/architecture/ai-invocation-flow.md
docs/stage8/architecture/outbox-index-consistency.md
docs/stage8/architecture/rag-retrieval-and-citation.md
docs/stage8/architecture/system-architecture.md
docs/stage8/authorization/permission-matrix.md
docs/stage8/presentation/project-introductions.md
docs/stage8/presentation/project-summary.md
```

本轮新建的三份指定文档会使后续 `git status`与上述基线不同；这属于本任务授权范围，不反写成原始基线。

## 3. 候选版本与产物

| 对象 | 计划版本/来源 | 当前状态 | 备注 |
|---|---|---|---|
| Backend源码候选 | `develop` HEAD `13e916f...` | 未验证 | 生产资产已由PR #157合并；仍需与最终前端SHA运行同一次Release Gate |
| Frontend源码候选 | `37fbb504...` | 未验证 | 工作区干净；仍需在同一次Release Gate冻结 |
| Backend JAR | Gate产出的 `LearningManage-0.0.1-SNAPSHOT.jar` | 待执行 | 没有本次candidate artifact和SHA-256 |
| Frontend dist | Gate对精确前端SHA执行 `npm run build`后的artifact | 待执行 | 本地dist和后端遗留`deploy/dist`均不是权威候选 |
| Backend镜像 | `learningmanage-backend:<完整BACKEND_SHA>` | 待执行 | 镜像尚未在目标机组装/核验revision label |
| Frontend镜像 | `learningmanage-frontend:<完整FRONTEND_SHA>` | 待执行 | 镜像尚未在目标机组装/核验revision label |
| 第三方镜像 | production manifest中的完整digest | 未验证 | 模板有清单；需以本次成功Gate和目标机RepoDigests为准 |
| Release Bundle | `<BACKEND_SHA>-<FRONTEND_SHA>` | 待执行 | 尚未组装和上传 |
| 当前Git描述 | `stage7-v1.0.0-17-g13e916f` | 已执行通过 | 描述提交距离，不是新release tag |

最新已有标签 `stage7-v1.0.0`指向 `4f0f4d8...`，早于三项关键修复和生产部署提交。它不能仅凭“Release”名称成为本次部署或回滚目标。当前HEAD尚未被任何Tag包含。

## 4. 三项关键修复核对

三项提交均位于当前HEAD祖先链，并通过 `bfc8760d...`（PR #156合并提交）进入 `develop`；本轮只做Git与源码核对，没有重新运行测试。

| 风险 | 修复提交 | 当前HEAD包含 | 实现/已有测试证据 | 本次目标验证 |
|---|---|---|---|---|
| 私人复盘可能进入团队共享报告 | `3c06eeb479f197014f4679c916261bcfdb091172` | 是 | `RetrieveProjectHistoryAgentTool`先按报告受众过滤；单测及`AgentPrivateEvidenceIsolationMySqlTest`覆盖prompt/draft/report | 待执行 |
| 成员终止遗漏相关项目版本递增 | `5d51eb31ea282d5632cd8ac7a62475b37387d264` | 是 | 清理锁定行携带projectId并逐项目increment；单测和MySQL对账存在 | 待执行 |
| RAG陈旧候选纠偏事件缺少事务 | `72f2aaba009e31b3c01ccebe4110b59a45822b1e` | 是 | `KnowledgeRecoveryEventService`以`REQUIRES_NEW`包装MANDATORY publisher；无外层事务MySQL测试存在 | 待执行 |

## 5. 已有部署能力（代码存在）

| 能力 | 当前实现状态 | 实际运行状态 |
|---|---|---|
| 低资源生产Compose、三网络、资源限制、命名卷 | 已实现于 `deploy/docker-compose.prod.yml` | 目标服务器待执行 |
| JAR/dist不可变镜像组装 | 已实现两个生产Dockerfile和构建脚本 | 本次镜像待执行 |
| 前后端SHA、镜像digest、V1～V8 checksum绑定 | 已实现生产manifest schema和Release Gate job | 当前SHA的Gate未验证 |
| 主机初始化、SSH加固、Nginx/Certbot安装流程 | 已实现脚本/模板 | 目标服务器未执行或未复核 |
| 空库账户provision及独立Flyway migrator | 已实现脚本和admin profile | 目标数据库待执行 |
| Phase A smoke与原子切换current | 已实现 | 目标服务器待执行 |
| 按需Prometheus/Tempo/Grafana | 已实现profile和on/off脚本 | 目标服务器待执行 |
| MySQL加密备份、OSS上传、systemd timer | 已实现 | 目标服务器待执行 |
| 隔离恢复演练 | 已实现脚本 | 本次备份待执行 |
| 应用回退与兼容性拒绝条件 | 已实现脚本 | 目标服务器待执行 |

## 6. 目标环境与部署范围

以下来自用户此前提供的部署方案，不是本轮远程核验结果：

| 项目 | 用户提供的计划/陈述 | 状态 | 证据环境 |
|---|---|---|---|
| 云主机 | 京东云轻量云主机，北京地域 | 未验证 | 用户提供方案 |
| OS/架构 | Ubuntu 24.04.2 LTS x86_64 | 未验证 | 用户提供方案 |
| 资源 | 2核CPU、4GB内存、2GB Swap、60GB SSD、5Mbps | 未验证 | 用户提供方案 |
| 已有软件 | Docker CE 29.8.0、Compose 5.5.1、Buildx 0.37.0 | 未验证 | 用户提供方案 |
| 主机初始安全 | UFW仅22；系统更新、Swap和Docker日志轮转已做 | 未验证 | 用户提供方案 |
| 数据库 | 全新MySQL空库，V1～V8 | 待确认 | 用户提供方案 |
| 公网入口 | 域名尚未备案；备案前仅SSH隧道，之后宿主机Nginx+Certbot | 待确认 | 用户提供方案 |
| 观测 | Prometheus/Tempo/Grafana按需启动 | 待确认 | 用户提供方案 |
| 备份 | 私有OSS、zstd+age、日7/周4、月度隔离恢复 | 待确认 | 用户提供方案 |
| 旧腾讯云 | 仅作为2026-11-10前临时加密副本，不做在线依赖 | 未验证 | 用户提供方案 |

本次推荐最小部署范围仍为Phase A：MySQL、Redis、Qdrant、Backend、Frontend常驻；普通AI/任务拆解在预算获批后验证；Knowledge Worker、RAG、Agent、Tool Calling、Cleanup全部关闭；观测按需。是否公众注册尚未确认。

## 7. 批准的配置差异（不含真实值）

| 配置差异 | 计划值 | 状态 |
|---|---|---|
| Backend JVM | `-Xms128m -Xmx512m -XX:MaxMetaspaceSize=192m` | 代码已配置；目标未验证 |
| 核心容器内存 | Backend896MiB、MySQL640MiB、Qdrant512MiB、Redis128MiB、Frontend64MiB | 代码已配置；目标未验证 |
| AI/Worker并发 | 普通AI 4；Knowledge 1；Embedding 1；Vector 2；Agent 1；Rerank 1 | 代码已配置；目标未验证 |
| tracing | 默认false；按需true、sampling 0.02 | 代码已配置；目标未验证 |
| Cleanup | 默认功能和schedule均false | 代码已配置；目标未验证 |
| Qdrant | 同机internal HTTP + API Key，主机不发布端口 | 已批准方案中的单机例外；目标未验证 |
| MySQL | buffer pool 256MiB、max connections 50 | 代码已配置；目标未验证 |
| 观测保留 | Prometheus 3天/512MB、Tempo24小时 | 代码已配置；目标未验证 |

注意：费用soft/hard limit当前只形成配置、指标和Prometheus规则，不会自动阻断模型调用。

## 8. 数据库与功能状态

### 8.1 迁移

| 项目 | 迁移前 | 迁移后 | 状态 | 证据环境 |
|---|---|---|---|---|
| 目标数据库存在性/是否为空 | 待确认 | 不适用 | 待执行 | 目标服务器 |
| Flyway history | 待确认 | 期望V1～V8恰好8条SQL success | 待执行 | 目标服务器 |
| 应用账号权限 | 待确认 | 期望仅DML、DDL失败 | 待执行 | 目标服务器 |
| migration checksum | 待确认 | 期望与production manifest一致 | 待执行 | 目标服务器 |

### 8.2 功能开关

| 功能 | Phase A计划 | 目标实际值 | 验证状态 |
|---|---|---|---|
| 基础业务 | 开启 | 待执行 | 待执行 |
| 普通Chat/任务拆解 | 计划开启，需先批准模型/次数/预算 | 待确认 | 未验证 |
| Knowledge Worker | 关闭 | 待执行 | 未验证 |
| RAG | 关闭 | 待执行 | 未验证 |
| Agent API/Worker | 关闭 | 待执行 | 未验证 |
| Tool Calling | 关闭 | 待执行 | 未验证 |
| Cleanup/Schedule | 关闭 | 待执行 | 未验证 |
| Prometheus/Tempo/Grafana | 按需 | 待确认 | 未验证 |

## 9. 操作时间线

| 时间 | 操作 | 结果 | 状态 | 证据环境 |
|---|---|---|---|---|
| 2026-09-10 20:13 +08:00 | 首次读取后端基线 | HEAD `0083abd...`，8个部署/CI文件已暂存 | 已执行通过 | 本地隔离环境 |
| 2026-09-10 20:17 +08:00 | 发现共享工作区变化后重新冻结只读基线 | HEAD更新为`99ef925...`，tracked/staged干净，远端同名分支一致 | 已执行通过 | 本地隔离环境 |
| 2026-09-10 20:38 +08:00 | 外部流程合并PR #157后第三次读取基线 | `develop`与`origin/develop`均为`13e916f...`；生产资产已合并 | 已执行通过 | 本地Git历史/远端跟踪引用 |
| 2026-09-10 | 核对生产Compose、构建、Nginx、迁移、备份、回滚脚本 | 能力存在；未运行 | 已执行通过 | 本地隔离环境 |
| 2026-09-10 | 核对三项关键修复 ancestry和源码证据 | 三项均进入当前HEAD | 已执行通过 | 本地隔离环境 |
| 2026-09-10 | 创建三份部署准备文档 | 不含部署配置修改 | 已执行通过 | 本地隔离环境 |
| 待定 | Release Gate、Bundle组装 | PR已由外部流程合并；其余尚未开始 | 待执行 | 历史发布记录/可信构建机 |
| 待定 | 主机初始化、迁移、Phase A、HTTPS、备份恢复 | 尚未开始 | 待执行 | 目标服务器 |

## 10. 验证与证据台账

| 验证项 | 结果 | 状态 | 证据环境 | 脱敏证据位置 |
|---|---|---|---|---|
| 2026-09-07 Stage 7空库V1→V8、V7→V8 | 记录为通过 | 已执行通过 | 本地隔离环境 | `docs/stage7/evidence/local-verification-2026-09-07.md` |
| 2026-09-07九容器/观测/Stub AI链路 | 记录为通过 | 已执行通过 | 本地隔离环境 | 同上；不能外推真实Provider或目标机 |
| 2026-09-07真实Qwen和受保护Gate | 记录中明确pending | 未验证 | 历史发布记录 | 同上 |
| 当前后端13e916f Release Gate | 无成功产物记录 | 未验证 | 历史发布记录 | 待补 |
| 当前前后端绑定manifest | 未生成 | 待执行 | 历史发布记录 | 待补 |
| 目标机端口/防火墙 | 未连接 | 待执行 | 目标服务器 | 待补脱敏摘要 |
| 目标机Phase A smoke | 未部署 | 待执行 | 目标服务器 | 待补 |
| 真实模型调用 | 未获本轮授权 | 待执行 | 目标服务器 | 待补调用次数/模型/结果摘要 |
| 公网HTTPS/续期dry-run | 未执行 | 待执行 | 目标服务器 | 待补 |
| 加密备份上传 | 未执行 | 待执行 | 目标服务器 | 待补对象key后缀、size、checksum |
| 隔离恢复 | 未执行 | 待执行 | 目标服务器 | 待补mysqlcheck和计数摘要 |
| 应用回滚 | 未执行 | 待执行 | 目标服务器 | 待补兼容性和smoke摘要 |

`smoke-test.sh`当前只覆盖入口健康、文档封锁、三个管理健康端点和容器状态，不覆盖登录、CRUD、跨账号、真实AI、RAG或Agent；这些必须另做人工/专项验收。

## 11. 问题与处理记录

| 问题 | 排查依据 | 处理 | 结果 |
|---|---|---|---|
| 分析期间Git基线两次变化 | 三次`rev-parse`和status显示`0083abd`→`99ef925`→`13e916f` | 停止沿用旧快照，每次重新读取并记录最终HEAD | 已解决文档口径；没有回滚或覆盖外部变更 |
| 根README与生产路径冲突 | README仍指向旧Compose、`up --build`和旧端口 | 手册指定以`deploy/README.prod.md`和生产Compose为准 | 文档澄清；根README未修改 |
| 前端dist版本不可追溯 | 本地dist被忽略、无SHA；后端deploy/dist来自旧提交 | 本次候选只接受Gate dist及`dist.sha256` | 待Gate验证 |
| “hard limit”易被误说为费用阻断 | 源码仅做validator、gauge和告警规则 | 文档改称观测阈值 | 已澄清；未实现新计费系统 |

本次没有虚构故障经历、在线用户数、QPS、P95、可用性、成本节省、备份恢复成功或稳定运行时长。

## 12. 备份、恢复与回滚

| 项目 | 状态 | 说明 |
|---|---|---|
| 备份脚本存在 | 已执行通过 | 仅代表仓库实现存在 |
| 目标机定时器安装 | 待执行 | 尚未确认systemd状态 |
| 首个age加密备份 | 待执行 | 尚无目标文件/对象证据 |
| OSS对象存在与checksum | 待执行 | 尚未连接Bucket |
| 月度隔离恢复 | 待执行 | 不能写“恢复成功” |
| 应用回滚脚本 | 已执行通过 | 仅只读确认代码存在 |
| 目标机回滚演练 | 待执行 | 旧Tag不作为默认回退候选 |
| Qdrant重建 | 待执行 | 需要Phase B及Embedding预算 |

## 13. 尚未完成与开放结论

### 13.1 尚未完成

1. 冻结当前后端 `develop` SHA与最终前端SHA，运行并通过跨仓Release Gate。
2. 组装、校验、上传Release Bundle。
3. 脱敏核实目标主机现状和同机服务。
4. 确认空库、开放范围、域名/备案、模型预算、观测范围和备份介质。
5. 完成迁移、Phase A验收、HTTPS、备份、隔离恢复和回滚演练。
6. 根据批准逐步决定是否启用Worker、RAG、Agent和Cleanup。

### 13.2 当前适合对谁开放

当前尚未上线，因此不适合对任何外部用户开放。完成Phase A与安全验收后，优先只向本人和受邀面试演示账号开放；允许公众注册必须另行确认费用与滥用风险。

### 13.3 观察时段

目标环境观察尚未开始：开始时间、结束时间、容器重启次数、资源曲线、错误摘要和用户范围均为“待执行”。不得写成“已全天候稳定运行”。

## 14. 待用户一次性确认

1. 最终开放范围：本人、受邀，还是公开注册？
2. 目标机规格和已完成主机配置是否仍与此前方案一致？是否有同机服务或重要数据？
3. 域名、备案、DNS和证书当前状态？
4. Phase A真实模型、允许调用次数、预算和账号授权？
5. 观测组件是否继续按需启动？
6. 私有OSS、最小权限子账号和age离线恢复介质是否已准备？

确认后，每个获批动作应立即追加到第9～12节，保留状态、时间、证据环境和脱敏结果，不另建重复上线记录。
