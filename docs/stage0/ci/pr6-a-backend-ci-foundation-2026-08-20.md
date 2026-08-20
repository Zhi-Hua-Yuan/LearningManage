# PR6-A 后端 CI 迁移门禁基础执行记录

执行日期：2026-08-20（Asia/Shanghai）
执行状态：进行中，A2 完成

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

状态：未开始

### A4 后端 CI 脚本

状态：未开始

### A5 隔离 MySQL 验证

状态：未开始

### A6 收尾

状态：未开始

## 5. A1/A2 验证结果

- 文档骨架：A1 已完成
- fixture 文件生成：A2 已完成
- 20 张表逐表来源比较：通过
- 20 张表逐表 V1 比较：通过
- 禁止内容扫描：通过
- `git diff --check`：通过
- 数据库导入：未执行，留给 A5
- Maven 全量测试：留给 A3/A6 统一执行

## 6. 数据库影响

A1/A2 未连接、读取或修改任何数据库。

## 7. 安全检查

A1/A2 未新增密码、Token、API Key、有效数据库连接串或其他敏感信息；fixture 不含业务数据、授权语句或 Flyway 历史表。

## 8. 下一步

进入 A3：实现 V1 和已发布迁移不可变检查。
