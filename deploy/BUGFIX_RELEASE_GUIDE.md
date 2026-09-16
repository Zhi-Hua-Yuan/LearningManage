# LearningManage 生产 Bug 修复发布手册

本文用于指导 LearningManage 已完成首次生产部署后，如何把本地 Bug 修复安全地发布到生产服务器。

本文只适用于后续版本发布，不用于新服务器初始化。生产发布的基本原则是：

> 本地修改必须经过提交、Release Gate、不可变 Release Bundle 和生产 smoke，禁止直接修改服务器中的代码、JAR、前端文件或容器。

Phase 0 冻结后的默认运行矩阵为：Knowledge Worker、RAG、Agent、Agent Worker、
受控 Tool Calling 开启，Cleanup 与 Cleanup Schedule 关闭。发布前后都必须记录这七个开关的
实际值；发生故障时允许按 Tool Calling → Agent Worker → Agent → RAG → Knowledge Worker
顺序显式关闭。业务回归根据代码影响范围选择，无法判断影响范围时扩大验收。

## 1. 先判断是否需要发布新版本

| 修改类型 | 是否需要新 Release | 处理方式 |
| --- | --- | --- |
| Backend Java 代码 | 是 | 提交 Backend，运行跨仓库 Release Gate，发布新 Backend 镜像 |
| Frontend 代码 | 是 | 提交 Frontend，运行跨仓库 Release Gate，发布新 Frontend 镜像 |
| Backend 与 Frontend 都修改 | 是 | 两个仓库都提交，由同一次 Gate 冻结两个 SHA |
| Dockerfile、Compose、Nginx 或生产脚本 | 是 | 作为生产资产提交并经过完整 Gate |
| 仅调整已有生产环境变量 | 通常否 | 走受控配置变更，只重建受影响服务，不打印环境文件 |
| 修改数据库结构或历史数据 | 不能按普通 Bug 发布处理 | 先扩展并验证迁移工具链，再使用新增的 forward-only Migration |

如果不能明确判断修改类型，按需要新 Release 处理。

## 2. 发布前的固定规则

- 只部署来自同一次成功 Cross-repository Release Gate 的产物。
- Backend 和 Frontend 工作区必须干净，所有生产修改必须提交。
- 每个 Release 使用准确的 Backend SHA 和 Frontend SHA，不使用 `latest` 等浮动标签。
- Release 目录必须命名为 `<BACKEND_SHA>-<FRONTEND_SHA>`。
- 不覆盖或修改现有 Release 目录。
- 不在生产服务器运行 Maven、npm 完整构建或拉取源码。
- 不把 `/etc/learning-manage` 下的任何真实凭据放入 Release Bundle。
- 不输出或粘贴 `learning.env`、`migration.env`、`backup.env`、`s3cfg`、完整 Docker inspect、密码或 API Key。
- 不再次执行首次数据库初始化。
- 发布前记录当前 Release，确保它仍可作为回滚目标。

生产服务器禁止执行：

```text
docker compose down -v
docker system prune --volumes
flyway clean
flyway repair
全局删除 Docker volume
```

## 3. 本地修复与提交

### 3.1 修复 Bug

在对应仓库完成修改，并根据影响范围完成测试和回归。至少确认：

- Bug 的复现步骤在修改前能够稳定复现；
- 修改后原问题消失；
- 相关正常路径没有被破坏；
- 前后端接口、DTO 和错误码仍兼容；
- 没有把真实凭据、测试账号或本地配置提交进 Git。

### 3.2 提交代码

Backend 或 Frontend 有修改时，分别提交对应仓库。记录最终提交 SHA：

```bash
git rev-parse HEAD
```

不要从含有未提交生产文件的工作区组装 Release。Bundle 组装器会拒绝 `deploy`、`scripts/prod` 和数据库迁移目录中的未提交文件。

## 4. 运行跨仓库 Release Gate

从 GitHub Actions 运行当前项目规定的跨仓库 `release-gate.yml`，让同一次 Gate 冻结：

- Backend SHA；
- Frontend SHA；
- Candidate ID；
- Run ID 和 Attempt；
- Backend 已测试 JAR；
- Frontend 已测试 `dist`；
- Release Candidate Manifest；
- Production Release Manifest；
- API 契约及完整运行时证据。

只有全部必需 Gate 任务成功后才能继续。不要混用不同 Run、不同 Attempt 或不同 Candidate 的产物。

发布记录中至少保存：

```text
Candidate ID:
Run ID:
Attempt:
Backend SHA:
Frontend SHA:
修复内容:
```

## 5. 在可信工作机组装 Release Bundle

下载同一次 Gate 生成的全部产物，然后在 Backend 仓库的目标提交上执行：

```bash
scripts/prod/assemble-release-bundle.sh \
  /path/to/backend-artifact \
  /path/to/frontend-artifact \
  /path/to/release-candidate-manifest.json \
  /path/to/release-evidence \
  /path/to/production-release-manifest.json \
  /path/to/output
```

输出目录应为：

```text
<BACKEND_SHA>-<FRONTEND_SHA>
```

组装脚本会校验产物 SHA、Manifest 绑定关系、证据文件和生产文件白名单。组装完成后不要修改 Bundle 中的任何文件。

上传前建议为传输归档计算 SHA-256；上传后在服务器再次核对相同 SHA-256。上传目标为：

```text
/opt/learning-manage/incoming/
```

校验传输归档后，将 Bundle 解压为新的独立目录：

```text
/opt/learning-manage/releases/<BACKEND_SHA>-<FRONTEND_SHA>/
```

不要覆盖 `/opt/learning-manage/current` 当前指向的目录。

## 6. 服务器发布前检查

进入新 Release 目录：

```bash
cd /opt/learning-manage/releases/<BACKEND_SHA>-<FRONTEND_SHA>
```

记录当前回滚目标：

```bash
readlink -f /opt/learning-manage/current
```

执行只读状态检查：

```bash
docker compose ls
docker ps -a
docker volume ls --filter label=com.docker.compose.project=learning-manage
./scripts/prod/verify-host.sh
```

确认：

- 当前核心容器没有异常退出；
- MySQL、Redis、Qdrant 数据卷存在且不会被删除；
- UFW 和京东云控制台防火墙符合当前备案状态；
- 磁盘、内存和 Swap 没有异常；
- 上一个 Release 目录仍然完整保留。

然后验证新 Bundle：

```bash
./scripts/prod/verify-release-bundle.sh
```

## 7. 更新受保护的 Release 身份

普通部署要求 `/etc/learning-manage/learning.env` 中的 `BACKEND_SHA` 和 `FRONTEND_SHA` 与新 Production Manifest 完全一致。

使用受控编辑方式只修改这两个值，并保留其他所有配置和凭据：

```bash
sudoedit /etc/learning-manage/learning.env
```

不要把文件内容打印到终端或聊天。修改后执行不泄露值的校验：

```bash
./scripts/prod/verify-production-secrets.sh
docker compose \
  --project-name learning-manage \
  --env-file /etc/learning-manage/learning.env \
  -f deploy/docker-compose.prod.yml \
  config --quiet
```

## 8. 构建并发布应用镜像

先构建新 Release 的应用镜像：

```bash
./scripts/prod/build-release-images.sh
```

成功标志：

```text
[learning-manage] application images built and verified
```

可以使用新 SHA 验证应用镜像 revision label：

```bash
docker image inspect learningmanage-backend:<BACKEND_SHA> \
  --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}'

docker image inspect learningmanage-frontend:<FRONTEND_SHA> \
  --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}'
```

确认无误后执行普通部署：

```bash
./scripts/prod/deploy.sh
```

严禁在后续发布中执行：

```bash
./scripts/prod/deploy.sh --initialize-database
```

普通 `deploy.sh` 会：

- 再次验证生产 secrets 和 Release Bundle；
- 再次确认两个 SHA 与 Manifest 一致；
- 构建和验证应用镜像；
- 保持 MySQL、Redis、Qdrant 及数据卷；
- 启动或更新 Backend、Frontend；
- 检查健康状态；
- 运行默认开启 smoke；
- smoke 成功后原子切换 `/opt/learning-manage/current`。

只有看到以下结果才算发布成功：

```text
[learning-manage] production smoke checks passed
[learning-manage] Release deployed; current now points to ...
```

## 9. 高风险 AI 功能的发布边界与回归范围

当前默认运行矩阵为：

```text
AI_KNOWLEDGE_WORKER_ENABLED=true
AI_RAG_ENABLED=true
AI_AGENT_ENABLED=true
AI_AGENT_WORKER_ENABLED=true
AI_AGENT_TOOL_CALLING_ENABLED=true
AI_CLEANUP_ENABLED=false
AI_CLEANUP_SCHEDULE_ENABLED=false
```

`deploy.sh` 只强制 Cleanup 两项保持 `false`；其他五项允许因故障处置被显式关闭，
但发布记录必须说明实际值与原因。不得为了发布临时绕过权限、Citation、Draft 或 Tool 边界。

### 9.1 开关记录与恢复

1. 发布前记录七个开关的实际值和任何显式关闭原因。
2. 执行普通 `deploy.sh` 并通过默认开启 smoke；Cleanup 两项必须为 `false`。
3. 若某项因事故被关闭，发布不会自动重开；应在依赖和专项门禁通过后用独立配置变更恢复。
4. 恢复时每次只重建 Backend，并完成对应最低准入检查。
5. 任一检查失败立即停止恢复，保留最后一个已验证状态。

### 9.2 每次发布固定验收

每个新 Release 至少执行：

- Release Bundle、Manifest、SHA 和应用镜像 revision label 校验；
- Compose config 和生产 secrets 保护性校验；
- 默认开启 smoke；
- `/api/health`、登录、核心 Project/Milestone/Task 路径；
- 普通 AI 和 Task Breakdown；
- 本次 Bug 原始复现路径及相邻正常路径；
- 浏览器控制台、Backend 日志、内存和 Swap 检查；
- `current` 链接、新旧 Release 和回滚目标检查。

### 9.3 按影响范围追加验收

| 修改范围 | 最低追加验收 |
| --- | --- |
| 纯前端样式、文案或局部交互 | 对应页面、主要浏览器路径和 API 兼容性 |
| 普通 CRUD、Dashboard 或业务规则 | 对应模块、权限和相邻数据路径 |
| 登录、JWT、Team 隔离或公共权限逻辑 | 所有受影响的数据访问、RAG 和 Agent 权限边界 |
| 普通 AI、Prompt、Task Breakdown | 普通 AI；公共 AI 客户端变更时扩大到 Knowledge、RAG 与 Agent |
| Embedding、Knowledge Worker、Qdrant 写入 | Phase B 和 Phase C |
| RAG、Citation、Rerank、向量检索 | Phase B 和 Phase C |
| Agent Fixed Workflow 或 Tool Calling | Phase D1 和 Phase D2 |
| Cleanup、保留周期或删除条件 | Phase E1 Dry Run 和 E2 定时任务恢复检查 |
| Compose、Nginx、网络、Secret 映射或公共配置 | 基础设施检查及所有受影响阶段 |
| 公共数据库表、共享 DTO、AI SDK/Provider 或无法判断影响范围 | 扩大到完整 A～E 关键链路 |

上述“追加验收”决定人工回归深度，不替代 9.1 的开关记录与恢复要求。

## 10. 数据库变更的特殊限制

当前生产工具链被锁定为 Flyway V1～V8：

- Bundle 组装器只导出 V1～V8；
- `deploy.sh` 明确检查成功记录数量为 8；
- 普通 `deploy.sh` 不执行新增 Migration；
- 首次使用的 `migration.env` 已按设计删除。

所以当前版本不能直接发布 V9 或更高版本的数据库变更。如果 Bug 修复需要修改数据库结构，必须先完成独立的迁移工具链升级，包括：

- 扩展 Production Manifest 和 Bundle 组装逻辑；
- 扩展 Gate 中的空库、已有库、checksum 和回滚兼容验证；
- 修改部署脚本对迁移版本的固定检查；
- 设计后续发布使用的临时 migrator 凭据流程；
- 在生产迁移前完成加密备份和隔离恢复演练；
- 只新增 V9、V10 等 forward-only Migration。

禁止修改已经发布的 V1～V8，也禁止绕过工具链在生产数据库手工执行 DDL。

## 11. 发布后验收

部署完成后立即确认：

```bash
readlink -f /opt/learning-manage/current

docker compose \
  --project-name learning-manage \
  --env-file /etc/learning-manage/learning.env \
  -f deploy/docker-compose.prod.yml \
  ps

curl -sS http://127.0.0.1:18080/api/health
docker stats --no-stream
```

通过 SSH 隧道进行浏览器验收：

```powershell
ssh -N `
  -o IdentitiesOnly=yes `
  -o ExitOnForwardFailure=yes `
  -o ServerAliveInterval=30 `
  -o ServerAliveCountMax=3 `
  -i C:\Users\zhiyuan\.ssh\id_ed25519 `
  -L 18080:127.0.0.1:18080 `
  lmdeploy@<SERVER_IP>
```

浏览器访问：

```text
http://127.0.0.1:18080/
```

至少验收：

- 登录和退出；
- 本次 Bug 的原始复现路径；
- 与修改相关的正常路径；
- Project、Milestone、Task 等核心操作；
- 普通 AI 和 Task Breakdown；
- 浏览器控制台和 Backend 日志没有新增异常；
- 内存低于既定阈值且 Swap 不持续增长。

如果发布前有功能因故障被显式关闭，以上检查不会自动恢复该功能；必须按照第 9 节完成独立恢复并记录结果。

SSH 隧道断开会导致浏览器出现 `ERR_CONNECTION_REFUSED`，这不等同于生产容器故障。

## 12. 发布失败时回滚

如果 `deploy.sh` 在切换 `current` 前失败，先保留完整输出并确认当前链接是否仍指向旧版本，不要删除容器或数据卷。

需要回滚时，在任一完整 Release 目录中执行：

```bash
./scripts/prod/rollback.sh <PREVIOUS_BACKEND_SHA>-<PREVIOUS_FRONTEND_SHA>
```

回滚脚本只接受保留的双 SHA Release，并且会：

- 验证目标 Bundle；
- 比较数据库 Migration 集合；
- 比较外部镜像清单；
- 关闭高风险 AI 功能；
- 只重建 Backend 和 Frontend；
- 执行健康检查和 smoke；
- 成功后原子切换 `current`；
- 保留 MySQL、Redis、Qdrant 和全部数据卷。

如果 Migration 或外部镜像清单不同，自动回滚会被拒绝。此时停止操作，先制定专项恢复方案，不要强行切换链接或手工改数据库。

## 13. 发布完成后的保留与清理

确认新版本稳定后：

- 保留当前 Release；
- 至少保留紧邻的上一 Release；
- 保留当前和上一版本的应用镜像；
- 保存 Candidate、Run、SHA、发布时间和验收结果；
- 不使用通用 Docker prune 清理生产主机。

需要清理更旧的应用镜像时，只使用项目提供的受限脚本：

```bash
./scripts/prod/prune-old-application-images.sh \
  --previous-release <PREVIOUS_BACKEND_SHA>-<PREVIOUS_FRONTEND_SHA>
```

该脚本只处理 SHA 标签的 LearningManage 应用镜像，不会清理数据卷或第三方镜像。

## 14. 每次发布记录模板

```markdown
# LearningManage 生产发布记录

- 发布时间：
- 操作人：
- Bug/需求：
- Candidate ID：
- Run ID：
- Attempt：
- Backend SHA：
- Frontend SHA：
- 上一 Release：
- 新 Release：
- 是否包含数据库变更：否
- 发布前七项开关实际值：
- 本次专项回归范围：
- Bundle 校验：通过 / 失败
- 应用镜像校验：通过 / 失败
- 默认开启 smoke：通过 / 失败
- 显式关闭项恢复与验收：不适用 / 通过 / 失败
- 浏览器业务验收：通过 / 失败
- 当前链接：
- 资源状态：
- 是否回滚：否 / 是
- 备注：
```

## 15. 最短检查清单

```text
[ ] Bug 已修复并回归
[ ] Backend/Frontend 修改已提交
[ ] 工作区干净
[ ] 同一次 Release Gate 全绿
[ ] 同一次 Gate 的全部产物已下载
[ ] 新 Bundle 已组装并校验
[ ] 上传归档 SHA-256 一致
[ ] 新 Release 使用双 SHA 独立目录
[ ] 已记录当前 Release 作为回滚目标
[ ] 已记录发布前七项功能开关状态和显式关闭原因
[ ] learning.env 仅同步新 Backend/Frontend SHA
[ ] 生产 secrets 校验通过
[ ] Compose config 校验通过
[ ] 应用镜像及 revision label 校验通过
[ ] 执行 deploy.sh，未使用 --initialize-database
[ ] 默认开启 smoke 通过
[ ] current 指向新 Release
[ ] SSH 隧道业务验收通过
[ ] 已按改动影响范围完成专项回归
[ ] 显式关闭项如需恢复，已通过独立配置变更和专项验收
[ ] 当前和上一 Release 均已保留
[ ] 发布记录已归档
```
