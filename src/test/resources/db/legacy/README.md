# 存量数据库测试 Fixture

本目录用于保存经过脱敏和规范化的 Flyway 接管前结构 fixture。

## 计划文件

```text
pre_flyway_v1_schema.sql
```

## 用途

该 fixture 表示主库接入 Flyway 之前的 20 张 V1 业务表结构，用于隔离 CI 数据库中的显式 baseline 和后续升级测试。

## 权威来源

来源为 PR5-A 的 MySQL 8.0.41 结构-only 备份。

源文件 SHA-256：

```text
7BC761F10CC60973BCB8A41C93C70E5DE7074293F79CD951540748C5B980EB58
```

源备份位于 Git 忽略目录，不提交到仓库。规范化后的 fixture 将在 A2 生成并重新计算 SHA-256。

## 禁止内容

- 业务数据；
- `INSERT` 语句；
- 密码、Token 和密钥；
- `CREATE DATABASE` 和 `USE`；
- `DROP TABLE`；
- `CREATE USER` 和 `GRANT`；
- `DEFINER`；
- `LOCK TABLES`；
- `flyway_schema_history`；
- 生产 `AUTO_INCREMENT` 计数。

## 使用边界

- 只能导入空的 `learning_manage_ci_*` 临时数据库。
- 不是 Flyway 迁移文件。
- 不放入 `db/migration` 目录。
- 不用于新环境安装。
- 不连接或修改主库。
- fixture 将在 A2 生成，当前目录只包含说明文件。
