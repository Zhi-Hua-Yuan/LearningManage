# PR6-A 后端 CI 迁移门禁基础执行记录

执行日期：2026-08-20（Asia/Shanghai）
执行状态：进行中，A4 完成

## 1. 目标

建立后端 CI 可复用的迁移验证脚本、脱敏存量库 fixture 和已发布迁移不可变检查，为 PR6-B GitHub Actions 接入提供稳定输入。

## 2. 执行边界

- 不创建 GitHub Actions 工作流。
- 不连接或修改 3306 `learning_manage` 主库。
- 不执行 Flyway `clean` 或 `repair`。
- 不提交业务数据、数据库备份或凭据。
- 不修改已发布的 V1 迁移。
- 不实现 V2。

## 3. 冻结输入

```text
branch=develop
starting_commit=a99266030bb9963176aeea7bfbdfc7aabe278e07
flyway_version=10.10.0
mysql_target_version=8.0.41
v1_sha256=E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9
pr5_schema_source_sha256=7BC761F10CC60973BCB8A41C93C70E5DE7074293F79CD951540748C5B980EB58
```

## 4. 实施进度

### A1 目录和文档骨架

状态：完成

已建立 CI 文档、脚本目录说明和存量库 fixture 目录说明。未创建脚本、fixture、测试或工作流。

### A2 存量库 fixture

状态：完成

生成文件：

```text
src/test/resources/db/legacy/pre_flyway_v1_schema.sql
```

来源文件：

```text
.codex-tmp/pr5a-main-20260820/learning_manage-schema.sql
```

来源文件 SHA-256：

```text
7BC761F10CC60973BCB8A41C93C70E5DE7074293F79CD951540748C5B980EB58
```

fixture SHA-256：

```text
1ECF286291C3276585DA18722348BC4D70FAC8B751C0563568CC4B58B417FF96
```

审查结果：

| 检查项 | 结果 |
|---|---:|
| fixture 表数量 | 20 |
| 来源 `CREATE TABLE` 数量 | 20 |
| 来源 `DROP TABLE` 数量 | 20，未进入 fixture |
| fixture 业务数据 | 0 |
| fixture Flyway 历史表 | 0 |
| 来源逐表定义差异 | 0 |
| V1 逐表定义差异 | 0 |
| 禁止语句/授权/凭据扫描 | 0 |
| 表顺序 | 与 V1 冻结顺序一致 |

本步骤只进行了文件读取、提取和静态审查，没有导入数据库。

### A3 V1 不可变检查

状态：完成

已建立：

- .gitattributes 对迁移 SQL、fixture SQL 和哈希清单统一使用 LF；
- src/test/resources/flyway/published-migrations.sha256 已登记 V1；
- FlywayTestSupport 统一提供原始字节读取、SHA-256、表块和清单解析；
- FlywayPublishedMigrationImmutabilityTest 检查 V1 哈希、清单覆盖、版本唯一性和非法清单；
- FlywayLegacyFixtureStaticTest 检查 fixture 哈希、20 张表、逐表结构和禁止内容；
- 负向测试验证修改 V1 字节后不会匹配冻结哈希。

当前冻结哈希：

v1_sha256=E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9
legacy_fixture_sha256=1ECF286291C3276585DA18722348BC4D70FAC8B751C0563568CC4B58B417FF96

A3 未连接数据库，也未创建 GitHub Actions。基于目标分支的迁移文件增删改策略已在 A4 脚本中实现，PR6-B 只负责传入准确的目标提交。

本地验证结果：

| 检查项 | 结果 |
|---|---:|
| Maven 全量测试 | 73 项通过，0 失败，0 错误 |
| V1 固定 SHA-256 | 通过 |
| fixture 固定 SHA-256 | 通过 |
| 发布清单覆盖与版本唯一性 | 通过 |
| 非法发布清单负向测试 | 通过 |
| 修改 V1 字节负向测试 | 通过 |
| fixture 逐表结构和禁止内容 | 通过 |
| 跳过测试打包 | 成功 |
| 生产 JAR 包含 V1 | 是 |
| 生产 JAR 包含 fixture | 否 |
| 生产 JAR 包含测试哈希清单 | 否 |
| 数据库连接或修改 | 未执行 |

### A4 后端 CI 脚本

状态：完成

已建立：

- `scripts/flyway-admin.sh`：与 PowerShell 入口一致，仅允许 `info`、`validate`、`baseline`、`migrate`；
- `scripts/ci/lib/ci-common.sh`：统一环境检查、目标保护、MySQL调用和 `key=value` 输出；
- `assert-ci-database-target.sh`：只允许 `127.0.0.1`、非 3306 端口和 `learning_manage_ci_*` 数据库；
- `wait-for-mysql.sh`：带超时的临时 MySQL 就绪检查；
- `provision-ci-databases.sh`：创建空库、存量库及相互隔离的 CI 迁移/业务账号；
- `verify-empty-database.sh`：实现 V1 空库迁移、校验、二次迁移和关键结构断言；
- `verify-existing-database.sh`：实现 fixture 哈希校验、显式 baseline、校验和 `migrate(0)`；
- `verify-published-migrations.sh`：检查发布清单，并拒绝相对目标提交修改、删除或重命名已发布迁移；
- `tests/static-guards-test.sh`：覆盖 14 项无需数据库的正向/负向保护场景；
- `FlywayCiScriptStaticTest`：锁定 LF、安全前导、账号边界、禁止动作和自检覆盖范围。

目标保护明确拒绝：

```text
DB_NAME=learning_manage
DB_PORT=3306
DB_HOST=localhost
外部或局域网地址
learning_manage_migrator
learning_manage_app
clean / repair
无法解析的 BASE_REF
```

存量库 baseline 授权仅通过单条命令的进程级环境变量传入，不全局导出。空库或存量库非空时脚本直接失败，不包含自动清库、删除历史表或重建逻辑。

A4 验证结果：

| 检查项 | 结果 |
|---|---:|
| WSL Bash `bash -n` | 9 个 Bash 文件全部通过 |
| 静态目标保护自检 | 14 项通过 |
| 合法临时目标 | `127.0.0.1:3311/learning_manage_ci_empty` 通过静态保护 |
| 已发布迁移检查 | 相对 `HEAD` 和 `origin/develop` 均通过；V1 1 条，最大不可变版本 1 |
| Maven 全量测试 | 78 项通过，0 失败，0 错误 |
| 数据库连接或修改 | 未执行 |
| GitHub Actions | 未创建 |

### A5 隔离 MySQL 验证

状态：未开始

### A6 收尾

状态：未开始

## 5. A1-A4 验证结果

- 文档骨架：A1 已完成
- fixture 文件生成：A2 已完成
- 20 张表逐表来源比较：通过
- 20 张表逐表 V1 比较：通过
- 禁止内容扫描：通过
- `git diff --check`：通过
- 数据库导入：未执行，留给 A5
- Maven 全量测试：A3 已执行并通过 73 项，A6 仍需最终复核
- A4 Bash 语法检查：9 个文件通过
- A4 静态目标保护自检：14 项通过
- A4 Maven 全量测试：78 项通过
- 3306、主库名、非本机地址和正式账号负向保护：通过
- 已发布迁移相对 `HEAD` 和 `origin/develop` 检查：通过

## 6. 数据库影响

A1-A4 未连接、读取或修改任何数据库。A4 只完成脚本语法、静态契约和提前拒绝验证；实际临时数据库执行留给 A5。

## 7. 安全检查

- 未新增密码、Token、API Key、有效数据库连接串或其他敏感信息；
- fixture 不含业务数据、授权语句或 Flyway 历史表；
- CI 密码只能通过环境变量传递，脚本禁止 `set -x` 和命令行密码；
- CI 数据库保护要求显式授权、本机 IPv4、非 3306 端口、CI 数据库名和 CI 专用账号同时成立；
- 所有数据库验证脚本在第一个 MySQL 调用前执行目标保护；
- 禁止 Flyway `clean`、`repair` 和自动清理非空数据库。

## 8. 下一步

进入 A5：在 `127.0.0.1:3311` 的隔离 MySQL 8.0.41 实例上执行临时账号创建、空库 V1 迁移和存量库 baseline 两条端到端路径。A5 仍不得连接或修改 3306 主实例。
