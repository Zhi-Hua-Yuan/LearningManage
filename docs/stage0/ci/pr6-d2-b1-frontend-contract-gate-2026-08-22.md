# PR6-D2-B1：前端接口契约接入跨仓候选门禁

执行日期：2026-08-22（Asia/Shanghai）
状态：D2-B1 实现已合并；跨仓候选验证待 D2-B2

## 1. 目标

将前端 PR6-D2-A 生成的接口契约接入后端跨仓候选工作流。候选工作流必须从冻结的前端提交重新验证并导出契约，校验摘要和 Schema 后再打包前端产物，为后续 PR6-D2-B 的后端运行时 OpenAPI 比对提供不可变输入。

本地实施阶段未启动后端 Docker、MySQL、Redis、Qdrant 或部署环境；远程 CI 仅使用 Runner 隔离资源。

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

## 5. 远程实现验收结果

实现 PR [#27](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/27) 已按 Ruleset 使用 Squash 方式受保护合并。

修订后的实现提交为 `8e62f07`，同步更新了后端 CI 和跨仓候选工作流的期望测试数量 `82`。

最终 PR CI：[Run 32553165338](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32553165338)

| Job | 结论 |
|---|---|
| Guard and migration immutability | PASS |
| Maven verification and tested artifact | PASS |
| Flyway empty database gate | PASS |
| Flyway existing database gate | PASS |
| Docker runtime and migration gate | PASS |

合并提交：`541b8bf286f62d81cd3183aeceaf641c2d5414f3`

合并后的 `develop` CI：[Run 32553494594](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32553494594)

- 五项 Job 全部 PASS；
- Maven 测试数量断言为 82 并通过；
- Docker runtime、Flyway 和清理步骤全部通过；
- 远程 `develop` 当前 SHA 为 `541b8bf286f62d81cd3183aeceaf641c2d5414f3`；
- Review Thread 已解决，未使用管理员绕过、强制推送或直接写入 `develop`。

本阶段实现已确认：

- 工作流包含前端契约验证、导出、Schema 和 SHA-256 校验逻辑；
- 前端构建 Artifact 定义包含 dist、契约、Schema 和各自摘要；
- 新增的静态测试和测试数量同步已通过后端 CI；
- 实现 PR 和合并后的后端 `develop` CI 均通过五项门禁。

本记录尚未声称跨仓候选验证完成：截至本次收口，尚未执行 `release-gate.yml`，因此尚无 D2-B 候选 ID、跨仓 Manifest、前端冻结 SHA 的候选期末复核或候选 Artifact 下载证据。上述验证将在 D2-B2 的候选运行中完成。

跨仓候选中实际运行时 OpenAPI 导出和前后端接口存在性比对属于下一步 D2-B2，不在本记录的验收范围内。

## 6. 回滚

若后续发现工作流契约接入问题，通过新的受保护 PR 回滚本次工作流和静态测试改动。不得禁用 Ruleset、跳过契约验证、增加生产凭据或扩大数据库权限。
