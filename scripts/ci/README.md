# 后端 CI 脚本

本目录存放 PR6-A 提供的 Linux CI 脚本，以及 PR6-D2-C 的全栈运行与 AI 流程门禁脚本。GitHub Actions 只负责编排，不重复实现数据库验证逻辑。Windows 下的通用迁移入口为 `../flyway-admin.ps1`，Linux 下为 `../flyway-admin.sh`。

## 脚本

| 脚本 | 用途 |
|---|---|
| `lib/ci-common.sh` | 统一目标保护、环境检查、MySQL调用和机器可读输出 |
| `assert-ci-database-target.sh` | 拒绝主库、3306、非本机地址和正式账号 |
| `wait-for-mysql.sh` | 等待临时 MySQL 可用 |
| `provision-ci-databases.sh` | 创建空库/存量库及隔离的迁移和业务账号 |
| `verify-empty-database.sh` | 验证空库 V1 迁移和二次迁移幂等性 |
| `verify-existing-database.sh` | 验证 fixture 导入、显式 baseline 和 migrate(0) |
| `verify-published-migrations.sh` | 验证哈希清单及相对目标分支的迁移不可变性 |
| `verify-runtime-api-contract.sh` | 导出运行时 OpenAPI 并比较冻结前端契约的接口存在性 |
| `verify-ai-breakdown-flow.sh` | 通过 Nginx 执行确定性 AI 预览、取消、确认、幂等重放和业务落库核验 |
| `stubs/ai-chat-completions-stub.py` | CI 专用、无凭据、固定响应的 OpenAI 兼容 AI Stub |
| `validate-release-candidate.sh` | 验证跨仓候选输入、仓库身份、`develop` 最新性和运行期间分支稳定性 |
| `create-release-manifest.sh` | 生成不含凭据的跨仓候选 Manifest 和 SHA-256 |
| `tests/static-guards-test.sh` | 不连接数据库的静态负向保护自检 |

## 运行前提

- Bash 5+；
- JDK 17 和仓库 Maven Wrapper；
- MySQL 8 兼容的 `mysql` / `mysqladmin` 客户端；
- `git`、`grep`、`awk`、`sha256sum`；
- A5 本地临时 MySQL 使用非 3306 端口；
- PR6-B GitHub Service Container 计划映射到 `127.0.0.1:13306`。
- 后端 Docker 门禁使用 `deploy/docker-compose.ci.yml`；PR6-D2-C 全栈门禁使用 `deploy/docker-compose.release-gate.yml`，仅发布 Nginx 的 `127.0.0.1:18080`，后端不直接发布端口。
- Docker 镜像只接收已经测试过的 `target/LearningManage-0.0.1-SNAPSHOT.jar`。

## 目标保护

数据库脚本只接受：

```text
CI_DB_GATE_AUTHORIZED=true
DB_HOST=127.0.0.1
DB_PORT=<1-65535且不等于3306>
DB_NAME=learning_manage_ci_empty[_suffix]
或 learning_manage_ci_legacy[_suffix]
FLYWAY_EXPECTED_DB_NAME=DB_NAME
FLYWAY_DB_USERNAME=learning_manage_ci_migrator
```

正式主库名、3306端口、`localhost`、外部地址、正式迁移账号和业务账号都会在网络连接前被拒绝。

## 统一约束

- 使用 `set -Eeuo pipefail`。
- 禁止 `set -x`。
- 不接受命令行密码。
- 所有数据库脚本先执行目标保护。
- 只允许 `learning_manage_ci_*` 数据库。
- 禁止连接或修改 `learning_manage` 主库。
- 禁止执行 Flyway `clean` 和 `repair`。
- 失败必须返回非零退出码。
- 输出采用 `key=value` 格式。
- 日志不得包含密码、Token 或完整凭据。
- 全栈 AI 门禁只能使用 CI Stub，禁止回退到 DashScope 或真实模型；流程脚本会对临时密码和 JWT 做 GitHub Actions 脱敏。
- baseline 授权只能作用于存量库脚本中的单次命令。
- 数据库非空时直接失败，不自动清理或重建。
- 跨仓候选只接受两个指定公开仓库的完整 SHA，且必须等于执行时各自 `develop` 最新提交。
- 跨仓候选运行结束前重新检查两个 `develop`；分支移动时拒绝生成 PASS Manifest。

## A4 静态验证

```text
find scripts -name '*.sh' -print0 | xargs -0 -n1 bash -n
bash scripts/ci/tests/static-guards-test.sh
BASE_REF=<目标提交> bash scripts/ci/verify-published-migrations.sh
```

A4 只验证脚本语法和提前拒绝行为；实际数据库路径留给 A5。
