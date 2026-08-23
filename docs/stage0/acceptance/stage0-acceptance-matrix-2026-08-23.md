# 阶段 0 总验收矩阵（D3-0 初稿）

状态：D3-1 已完成；本文件是阶段 0 总验收的证据矩阵和机器合同输入，不代表 D3 总验收或 D4 最终封存已经完成。

建立日期：2026-08-23
后端仓库：`Zhi-Hua-Yuan/LearningManage`
前端仓库：`Zhi-Hua-Yuan/learning-manage-frontend`

## 1. D3-0 同步基线

| 项目 | 结果 | 证据/说明 |
|---|---|---|
| 后端当前分支 | PASS | `develop` |
| 后端当前 SHA | PASS | `5a8a2a42208b33655b34b5f4e95909ec5a062b92` |
| 后端远端同步 | PASS | 已从 `origin/develop` 快进同步；对应 PR #35 合并提交 |
| 前端当前分支 | PASS | `develop` |
| 前端当前 SHA | PASS | `cdff8f777843ab18f0c01c08d5f2ac7a82ec23e9` |
| 前端远端同步 | PASS | 使用临时 SSH 地址完成 fetch；未改变 `origin` 配置 |
| 后端工作区 | PASS | D3-0 检查时 clean |
| 前端工作区 | PASS | D3-0 检查时 clean |
| 3306 主库 | NOT_APPLICABLE | D3-0 未连接、未执行查询或写入 |

## 2. 阶段 0 验收矩阵

状态值约定：`PASS` 为已满足；`ACCEPTED_RISK` 为用户已接受的残余风险；`DEFERRED` 为明确延期；`NOT_APPLICABLE` 为当前范围不适用；`PENDING` 仅允许出现在 D3/D4 尚未执行的项目中。

| 编号 | 验收域 | 验收标准 | 当前状态 | 主要证据 | D3/D4 后续动作 |
|---|---|---|---|---|---|
| E0-01 | 实施前基线 | 仓库、运行环境、数据库和凭据边界已记录 | PASS | `docs/stage0/baseline/` | 在总验收报告中引用 |
| E0-02 | 数据质量 | 主库审计、修复演练和修复结果有可追溯记录 | PASS | `docs/stage0/database-audit/` | 复核证据日期和摘要 |
| E0-03 | 凭据清理 | 配置不再默认使用 Root，敏感配置通过环境变量注入 | PASS | `docs/stage0/security/README.md` | 执行最终敏感信息扫描 |
| E0-04 | 账号隔离 | 应用、测试和 Flyway 迁移账号独立，业务账号无 DDL | PASS | B2 记录、PR6 CI 门禁 | 在最终 Run 中再次确认 |
| E0-05 | JWT/API Key | JWT 已轮换；阿里云 Key 已按 B2 记录处理 | PASS | `docs/stage0/security/b2-3-*`、`b2-4-*` | 仅确认没有敏感值进入证据 |
| E0-06 | MySQL Root | 本机 Root 密码未轮换，风险已接受且限定在本机 | ACCEPTED_RISK | `b2-5-mysql-root-rotation-waiver-2026-08-19.md` | 保留触发条件，不写成 PASS |
| E0-07 | Redis | Redis 5 认证/ACL 方案未在本阶段实施 | DEFERRED | `docs/stage0/security/README.md` | 阶段 1 或基础设施变更重新评审 |
| E0-08 | Qdrant | 当前无代码调用方，未接入 | NOT_APPLICABLE | 凭据清单及安全 README | 进入 RAG 前重新评审 |
| E0-09 | Flyway V1 | V1 可从空库安装，已发布迁移不可修改 | PASS | `docs/stage0/flyway/`、已发布迁移清单 | 最终 Manifest 固定 V1 SHA |
| E0-10 | 存量库升级 | 存量数据库 baseline/升级门禁成功 | PASS | PR3、PR5、PR6-B/D 记录 | 最终 Run 保留远程证据 |
| E0-11 | 主库接管 | 主库只完成授权的 V1 baseline，未重复迁移 | PASS | `pr5-b-main-baseline-execution-2026-08-20.md` | D3 默认不重新写主库 |
| E0-12 | 后端 CI | 5 项后端必需检查全部通过 | PASS | develop CI 32564757176 | D4 针对最终 SHA 再确认 |
| E0-13 | 前端 CI | 3 项前端必需检查全部通过 | PASS | PR6-C3 记录及前端 develop CI | D4 针对最终 SHA 再确认 |
| E0-14 | 分支保护 | 后端/前端 develop 必须 PR 合并，禁止删除和强推 | PASS | Ruleset 快照及 PR6-C3 记录 | D4 保存 Ruleset 摘要 |
| E0-15 | 跨仓候选 | 精确 SHA 冻结、候选期间分支不变、Manifest 校验通过 | PASS | D1 Run 32490153711 | D4 重新冻结最终候选 |
| E0-16 | 运行时 API 契约 | 前端 37、运行时 60、匹配 37、缺失 0 | PASS | D2-C Run 32563964654 | D4 重新执行并写入最终 Manifest |
| E0-17 | AI 确定性闭环 | 预览、取消、确认、幂等重放全部通过 | PASS | D2-C 全栈证据 | D4 重新执行并校验摘要 |
| E0-18 | 业务落库计数 | 项目/里程碑/任务为 `1/2/4` | PASS | D2-C 全栈证据 | D4 重新执行并校验摘要 |
| E0-19 | 真实 AI 范围 | CI 使用离线确定性 Stub，不代表真实模型质量验收 | ACCEPTED_RISK | D2-C 执行记录 | 阶段 2/评测阶段单独验收 |
| E0-20 | 本地 Docker | 本地镜像下载受限，未宣称本地全栈通过 | ACCEPTED_RISK | D2-C 执行记录 | 以 GitHub Linux Runner 作为门禁证据 |
| E0-21 | 系统角色 | `user.system_role` 属于 V2 迁移和阶段 1 权限范围 | DEFERRED | `docs/stage0/flyway/v1-design.md` | D3 需在范围决策中正式确认 |
| E0-22 | 阶段 0 总验收 | 所有必需项 PASS，延期/风险均有明确记录 | PENDING | 本矩阵 | D3 建立机器可验证验收合同 |
| E0-23 | 阶段 0 证据封存 | 最终候选、Manifest、摘要、Tag 和 Release 完整绑定 | PENDING | 待建立 | D4 完成最终 Run 后封存 |

## 3. 已确认的 D2-C 证据输入

| 项目 | 值 |
|---|---|
| 候选 Run | [32563964654](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32563964654) |
| D2-C 后端候选 SHA | `bcda9d995b34b47c6da808077eb966d7d3d1321c` |
| D2-C 前端候选 SHA | `cdff8f777843ab18f0c01c08d5f2ac7a82ec23e9` |
| D2-C Manifest SHA-256 | `AEC80AE7494E535D98F106D7AE816B81E661D551201A38C04495ABD4D791F7BD` |
| OpenAPI | 前端 37、运行时 60、匹配 37、缺失 0 |
| AI 落库计数 | 项目 1、里程碑 2、任务 4 |
| 合并后后端 CI | [32564757176](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32564757176) |
| 证据记录 | [PR6-D2-C 执行记录](../ci/pr6-d2-c-full-stack-ai-gate-2026-08-22.md) |

注意：D2-C 候选后端 SHA 与当前 D3 起始 SHA 不同。D3 文档合并后必须重新运行跨仓候选工作流，不能直接复用 D2-C Manifest 作为最终封存依据。

## 4. D3-1 执行结果与 D3-2/D4 进入条件

已完成：

1. 建立 [`stage0-acceptance.json`](stage0-acceptance.json) 及其 Schema。
2. 将 E0-21 的 V2 延期边界和六项残余风险登记到机器合同。
3. 增加 `verify-stage0-acceptance.sh` 和验收自检脚本，并接入后端 CI 与跨仓候选 guard。

后续进入条件：

1. D3-2 通过受保护 PR 合并验收合同和风险登记表。
2. 为矩阵、验收合同和风险登记表计算 SHA-256，并纳入最终候选 Manifest。
3. D3 文档合并后，使用合并后的后端 SHA 重新冻结最终候选。
4. 最终候选通过 10/10 Job 后，才能创建 `stage0-complete-*` Tag 和 Release。

## 5. 范围声明

本矩阵不授权以下操作：连接或修改 3306 主库、执行 Flyway 迁移、修改已发布 V1、轮换生产凭据、部署正式环境、推送正式镜像、直接写入受保护 `develop`。
