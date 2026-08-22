# PR6-D2-C：全栈运行与确定性 AI 流程门禁

状态：本地实现与静态/单元验证完成；远程候选运行、受保护合并和 Artifact 下载验收待执行。

## 目标

把 PR6-D2-B2 的运行时 OpenAPI 契约门禁扩展为真实的边缘入口全栈门禁：前端制品只通过 Nginx 访问，Nginx 反向代理到后端，后端只连接隔离 CI MySQL，并通过确定性 AI Stub 验证任务拆解的预览、取消、确认、落库和幂等重放。

## 实现范围

- `deploy/docker-compose.release-gate.yml`
  - MySQL 使用非 3306 的隔离端口 `13306`；
  - Nginx 只发布 `127.0.0.1:18080`，后端仅 `expose: 8123`；
  - 后端 `FLYWAY_ENABLED=false`，使用独立迁移占位变量；
  - AI Base URL 固定指向同一内部网络中的 CI Stub；
  - 应用服务位于 `release-internal` 内部网络；MySQL 另接仅供 Runner 使用的 `ci-host-access` 回环桥接，以便既保留应用隔离又让既有数据库门禁脚本访问 `127.0.0.1:13306`；前端边缘代理另接独立的 `ci-edge-access` 回环桥接，以便 Runner 访问 `127.0.0.1:18080`，且不把前端与 MySQL 放到同一主机访问网络。
- `scripts/ci/stubs/ai-chat-completions-stub.py`
  - 仅实现健康检查和 OpenAI 兼容 Chat Completions；
  - 固定返回 2 个里程碑、4 个任务；
  - 不记录请求头、Token、Prompt 或业务数据。
- `scripts/ci/verify-ai-breakdown-flow.sh`
  - 注册/登录/鉴权；
  - 预览 → 详情 → 取消 → 取消后确认拒绝；
  - 预览 → 确认 → 详情 → 相同 `operationId` 幂等重放；
  - 核验项目、里程碑、任务和 AI 调用日志；
  - 生成带 SHA-256 的无敏感信息证据文件。
- `scripts/ci/create-release-manifest.sh` 与 `release-candidate-manifest.schema.json`
  - Manifest 升级到 schemaVersion 3；
  - 新增 `fullStackRuntime` 门禁、证据摘要和业务计数。
- `.github/workflows/release-gate.yml`
  - 下载并校验前端 `dist.sha256`；
  - 启动 Nginx、后端、AI Stub 全栈；
  - 通过 Nginx 执行 OpenAPI 与 AI 流程门禁；
  - 将全栈证据纳入候选 Manifest。

## 本地验证

| 项目 | 结果 |
|---|---|
| `docker compose ... deploy/docker-compose.release-gate.yml config --quiet` | PASS |
| Manifest Schema JSON 解析 | PASS |
| `git diff --check` | PASS |
| Maven 测试 | PASS，84 项，0 失败 |
| 3306 主库 | 未连接、未修改 |
| 真实 AI 凭据/生产环境 | 未使用 |
| 本地 Docker 全栈启动 | 未宣称通过；本机镜像层下载在构建阶段中断，未启动容器 |

## 远程验收门槛

远程执行 `release-gate.yml` 后，只有在以下条件全部满足时才可收口 D2-C：

1. 全栈 Job、API Contract、Flyway 空库/存量库和静态保护全部成功；
2. 前端制品清单下载后 `sha256sum --check` 成功；
3. AI Stub 流程证据显示取消、确认、幂等重放均 PASS，确认后项目/里程碑/任务计数为 `1`、`>=1`、`>=1`；
4. 候选 Manifest schemaVersion 为 `3`，`fullStackRuntime.gate=PASS` 且 Manifest 自身 SHA-256 校验成功；
5. 下载候选 Manifest 和全栈证据后再次校验摘要；
6. 受保护 PR 合并后，两个仓库 `develop` 与候选 SHA 一致，工作区 clean。

本记录不包含远程候选 ID、运行号或 PASS Manifest；这些字段必须由实际远程运行产生，不能预填。
