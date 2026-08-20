# PR6-A 后端 CI 迁移门禁基础执行记录

执行日期：2026-08-20（Asia/Shanghai）
执行状态：进行中，A1 完成

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
branch=<A1执行时填写>
starting_commit=<A1执行时填写>
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

状态：未开始

### A3 V1 不可变检查

状态：未开始

### A4 后端 CI 脚本

状态：未开始

### A5 隔离 MySQL 验证

状态：未开始

### A6 收尾

状态：未开始

## 5. A1 验证结果

- 文档骨架：待执行后填写
- 相对链接：待执行后填写
- `git diff --check`：待执行后填写
- Maven 测试：未执行；A1 仅新增文档，不修改代码、配置、SQL 或运行行为

## 6. 数据库影响

A1 未连接、读取或修改任何数据库。

## 7. 安全检查

A1 未新增密码、Token、API Key、有效数据库连接串或其他敏感信息。

## 8. 下一步

进入 A2：从 PR5 结构备份生成并审查脱敏存量库 fixture。
