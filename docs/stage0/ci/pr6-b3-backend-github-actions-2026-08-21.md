# 阶段 0 / PR6-B3：后端 GitHub Actions 与 Docker 门禁执行记录

日期：2026-08-21
状态：已通过 GitHub Linux Runner 远程验收

## 本次变更

- 新增 `.github/workflows/backend-ci.yml`。
- 工作流按 `guard -> backend-test -> flyway-empty -> flyway-existing -> docker-gate` 串联。
- `guard` 执行 Gitleaks、Bash 语法检查、CI 静态保护、V1 不可变检查和 `git diff --check`。
- `backend-test` 在隔离 MySQL `127.0.0.1:13306` 上执行存量库/空库准备、Maven `verify`、固定测试数量检查，并上传带 SHA-256 校验的生产 JAR。
- `flyway-empty` 与 `flyway-existing` 分别验证空库首次迁移和存量库基线场景。
- `docker-gate` 只使用上一个 job 产出的已测试 JAR，构建非 root 后端镜像，使用 CI Compose 启动隔离 MySQL，验证健康检查、Flyway 历史行数和业务账号 DDL 拒绝。
- Docker 失败时上传脱敏范围内的 Compose 状态与日志，并始终执行带卷清理的 `docker compose down`。
- 所有第三方 Actions 使用完整 commit SHA 固定；MySQL 与 Temurin 运行时镜像使用 digest 固定。

## 凭据与边界

- 未使用仓库生产凭据、正式数据库、3306 主端口、生产数据卷或前端服务。
- CI 密码仅由 GitHub 运行编号和尝试次数生成，限定在本次 Runner 生命周期内。
- Flyway 账号固定为 `learning_manage_ci_migrator`，应用账号固定为 `learning_manage_ci_app`。
- `FLYWAY_BASELINE_ON_MIGRATE=false`，只有存量库脚本在局部命令作用域内显式授权 baseline。

## 本地验证与限制

- 已完成工作流文件和脚本的静态检查准备，并更新 `FlywayCiScriptStaticTest` 覆盖 Docker 运行时脚本。
- 本机 Docker Desktop Linux daemon 与 WSL 仍不可用，因此未在本地执行真实 Docker build、Compose 启动或 Bash 门禁脚本。
- 首次 GitHub Linux Runner 执行是本 PR6-B3 的最终验收点；若失败，优先查看 `docker-diagnostics-*`、Surefire 报告和 job 日志。

## 远程验收结果

- 工作流运行：[#2](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32453122804)
- 验收提交：`21dfdcd fix(ci): 修复Docker运行时脚本Bash语法`
- 结果：成功
- `Guard and migration immutability`：success
- `Maven verification and tested artifact`：success
- `Flyway empty database gate`：success
- `Flyway existing database gate`：success
- `Docker runtime and migration gate`：success
- 首次运行因 Docker 运行时脚本 Bash 语法错误失败，已在 `21dfdcd` 修复后重跑通过。

## 验收标准

1. `guard` 成功解析比较基线，且 V1 已发布迁移不可被修改。
2. Maven 测试数量保持 81 项，测试产物 SHA-256 校验通过。
3. 空库迁移执行 1 次、二次迁移幂等；存量库只建立 baseline，不重复执行 V1 SQL。
4. Docker 后端健康检查返回业务成功码，Flyway 历史表保持 1 行，应用账号 DDL 探针被拒绝。
5. 任一失败路径均完成 Compose 卷和容器清理，不触碰 3306 主库。
