# PR6-D1 跨仓候选发布运行手册

## 目标

`Cross-repository release gate` 使用后端和前端两个受保护 `develop` 的精确提交，按固定顺序重新执行已有门禁并生成候选清单。工作流只操作 GitHub Runner 上的临时资源，不连接生产环境或 3306 主库。

## 触发前提

- 后端 `develop` 的五项 Backend CI 全部成功；
- 前端 `develop` 的三项 Frontend CI 全部成功；
- 两个 Ruleset 保持 `active` 且没有日常 bypass；
- 两个仓库工作区 clean，并与各自 `origin/develop` 一致；
- 触发者已取得两个远端 `develop` 的完整 40 位提交 SHA。

## 输入

| 输入 | 规则 |
|---|---|
| `backend_sha` | `Zhi-Hua-Yuan/LearningManage` 当前 `develop` 的完整 SHA |
| `frontend_sha` | `Zhi-Hua-Yuan/learning-manage-frontend` 当前 `develop` 的完整 SHA |
| `candidate_id` | `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$` |
| `reason` | 1～200 字符、单行，不得包含制表符 |

移动分支名、短 SHA、Tag、旧的 `develop` 祖先和任意其他仓库均不被接受。

## 固定顺序

```text
Freeze release candidate
  -> Guard backend candidate
  -> Guard both repositories
  -> Backend verification and tested artifact
  -> Frontend tests and static verification
  -> Frontend production build
  -> Flyway empty database gate
  -> Flyway existing database gate
  -> Backend Docker runtime gate
  -> Candidate manifest
```

最后一步会重新读取两个远端 `develop`。任一分支在运行期间移动时，候选会以 `STALE` 语义失败，必须使用新的精确 SHA 重新触发。

密钥扫描针对冻结提交的完整候选快照执行。候选扫描使用深度为1的独立检出，使`workflow_dispatch`不会把已在阶段0基线中审计过的历史提交重新解释为本次候选泄漏；普通PR仍由现有Backend/Frontend CI扫描本次变更范围。

## 产物

- 已测试的后端 JAR及其 SHA-256，保留30天；
- 后端 Surefire 报告，保留30天；
- 前端覆盖率报告，保留30天；
- 已测试的前端 `dist` 及文件哈希清单，保留30天；
- Docker失败诊断，保留30天；
- `release-candidate-manifest.json` 及其 SHA-256，保留90天。

候选清单遵循 [release-candidate-manifest.schema.json](release-candidate-manifest.schema.json)，不保存数据库密码、JWT、模型密钥、GitHub Token、数据库转储或业务数据。

## 失败处理

- 输入或仓库身份失败：不要放宽规则，重新确认远端 SHA；
- 分支移动：以新 SHA 创建新候选，不复用旧候选 ID；
- 密钥扫描失败：先确认并轮换有效凭据，不使用宽泛忽略；
- 后端/前端测试失败：在对应仓库通过受保护 PR 修复；
- Flyway或Docker失败：保留脱敏报告，确认临时容器和卷已经清理；
- 禁止通过增加生产 Secret、开启 `baseline-on-migrate` 或给业务账号追加 DDL 权限修复门禁。

本工作流不是普通 PR Required Check，也不应加入现有 `develop` Ruleset。
