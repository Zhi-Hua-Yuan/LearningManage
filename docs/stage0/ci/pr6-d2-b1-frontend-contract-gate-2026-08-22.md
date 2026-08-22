# PR6-D2-B1：前端接口契约接入跨仓候选门禁

执行日期：2026-08-22（Asia/Shanghai）
状态：本地实现完成，待受保护 PR 与远程候选验收

## 1. 目标

将前端 PR6-D2-A 生成的接口契约接入后端跨仓候选工作流。候选工作流必须从冻结的前端提交重新验证并导出契约，校验摘要和 Schema 后再打包前端产物，为后续 PR6-D2-B 的后端运行时 OpenAPI 比对提供不可变输入。

本次只完成契约接入，不启动后端 Docker、MySQL、Redis、Qdrant 或部署环境。

## 2. 冻结输入

前端固定提交：`cdff8f777843ab18f0c01c08d5f2ac7a82ec23e9`
前端契约版本：`schemaVersion=1`
前端基础路径：`/api`
本地契约操作数量：37

```text
39ca49e63c1d1f3c6f7d232180f57b20a668b14573ac6c2792c65c4a53f69035  frontend-api-contract.json
```

## 3. 实现内容

| 内容 | 文件 |
|---|---|
| 候选前端测试增加契约验证 | `.github/workflows/release-gate.yml` |
| 候选前端构建导出契约 | `.github/workflows/release-gate.yml` |
| 契约 Schema 和 SHA-256 校验 | `.github/workflows/release-gate.yml` |
| 暴露契约摘要、操作数量和版本 | `.github/workflows/release-gate.yml` |
| 工作流静态保护 | `scripts/ci/tests/static-guards-test.sh` |
| Maven 静态契约测试 | `src/test/java/com/spt/learningmanage/flyway/FlywayCiScriptStaticTest.java` |

前端构建 Artifact 现在包含：

```text
dist/
dist.sha256
frontend-api-contract.json
frontend-api-contract.sha256
frontend-api-contract.schema.json
```

## 4. 本地验证

- `git diff --check`：通过；
- `./mvnw test`：82 项通过，0 失败、0 错误、0 跳过；
- 新增的工作流契约静态测试通过；
- 未启动 Docker，未连接数据库，未访问 3306 主库；
- 当前环境执行 `bash scripts/ci/tests/static-guards-test.sh` 时，Bash 进程被 Windows 沙箱以 `E_ACCESSDENIED` 拒绝启动，因此该 shell 自测未记为通过，待 Ubuntu CI Runner 执行。

## 5. 远程验收要求

实现 PR 必须通过后端 Ruleset 的五项必需检查并受保护合并。合并后，使用合并产生的后端新 `develop` SHA 和前端固定 SHA `cdff8f777843ab18f0c01c08d5f2ac7a82ec23e9` 运行跨仓候选工作流。

本阶段预期确认：

- 前端契约验证通过；
- 契约操作数量为 37；
- 契约 SHA-256 下载后校验通过；
- Artifact 同时包含 dist、契约、Schema 和各自摘要；
- 候选期间两个受保护 `develop` 未发生变化。

后端运行时 OpenAPI 导出和前后端存在性比对属于下一步 D2-B2，不在本记录的验收范围内。

## 6. 回滚

若远程验证发现工作流契约接入问题，通过新的受保护 PR 回滚本次工作流和静态测试改动。不得禁用 Ruleset、跳过契约验证、增加生产凭据或扩大数据库权限。
