# PR6-D2-B2：后端运行时 OpenAPI 与前端接口存在性门禁

执行日期：2026-08-22（Asia/Shanghai）
状态：已实现、受保护合并，并完成跨仓候选验收

## 1. 目标与边界

在 PR6-D2-B1 固定的前端接口契约基础上，启动冻结后端 JAR 的隔离 Docker 运行时，下载运行时 OpenAPI 文档，并对前端契约中的每个接口执行存在性比对。任一前端接口未出现在运行时 OpenAPI 中，候选门禁必须失败。

本阶段只覆盖接口“存在性”与证据摘要，不覆盖参数/响应字段兼容性、Nginx 路由、前端运行时访问、AI 外部调用或生产环境。远程工作流使用隔离 MySQL 与临时 Docker 资源，不连接 3306 主库。

## 2. 固定输入与输出

- 前端固定提交：`cdff8f777843ab18f0c01c08d5f2ac7a82ec23e9`
- 前端契约：`schemaVersion=1`、`basePath=/api`
- 运行时 OpenAPI 地址：`http://127.0.0.1:18123/api/v3/api-docs`
- 前端契约、运行时文档、比较报告均生成 SHA-256 摘要；比较报告和运行时文档上传为候选 Artifact。

## 3. 实现内容

| 内容 | 文件 |
|---|---|
| 下载并校验运行时 OpenAPI、比对接口集合 | `scripts/ci/verify-runtime-api-contract.sh` |
| 在 Docker runtime gate 中接入比对并上传证据 | `.github/workflows/release-gate.yml` |
| 候选 Manifest 增加 `interfaceContract` 证据块并升级 schemaVersion=2 | `scripts/ci/create-release-manifest.sh`、`docs/stage0/ci/release-candidate-manifest.schema.json` |
| 更新测试数量断言（新增 1 个静态测试后为 83） | `.github/workflows/backend-ci.yml`、`.github/workflows/release-gate.yml` |
| 静态防回退与文档 | `scripts/ci/tests/static-guards-test.sh`、`src/test/java/com/spt/learningmanage/flyway/FlywayCiScriptStaticTest.java`、`scripts/ci/README.md` |

比对规则：方法统一为大写；路径参数统一为 `{}`；前端契约不得重复；报告中 `missingOperationCount` 必须为 0；Manifest 中 `matchedOperationCount` 必须等于前端操作数量。

## 4. 本地验收

- `mvnw.cmd test`：83 项通过，0 失败、0 错误、0 跳过；
- Git Bash `bash -n`：新增及修改脚本通过；
- `scripts/ci/tests/static-guards-test.sh`：31 项通过；
- `git diff --check`：通过；
- 本地未启动 Docker，未连接数据库，未访问 3306 主库。

## 5. 远程验收

实现 PR [#29](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/29) 已按 Ruleset 使用 Squash 方式受保护合并。

- 合并提交：`7e4670e9e43260e5f1ab4f2013fa887eb4927cab`；
- PR CI：[Run 32555308799](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32555308799)，五项 Job 全部 PASS；
- 合并后 `develop` CI：[Run 32555569651](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32555569651)，五项 Job 全部 PASS，Maven 测试数量为 83；
- 前端固定提交：`cdff8f777843ab18f0c01c08d5f2ac7a82ec23e9`，冻结期间前后 SHA 一致。

跨仓候选 [Run 32555816201](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32555816201) 使用候选 ID `stage0-pr6-d2-b2-20260822-001`，10 个 Job 全部 PASS。运行时接口门禁证据如下：

| 指标 | 结果 |
|---|---:|
| OpenAPI 版本 | `3.0.1` |
| 前端契约操作数 | `37` |
| 运行时 OpenAPI 操作数 | `60` |
| 匹配操作数 | `37` |
| 缺失操作数 | `0` |
| 前端契约 SHA-256 | `39CA49E63C1D1F3C6F7D232180F57B20A668B14573AC6C2792C65C4A53F69035` |
| 运行时文档 SHA-256 | `F2F2FA3DC3D4E70A99E6A356FB59E5636F160DE339DD1CEB4D250EEE433DA2D7` |
| 比较报告 SHA-256 | `AB096EE147D560367EF9F9916E7D674B35DEEF4547996F579838F29530F4A9F5` |

候选 Manifest 为 schemaVersion 2，`interfaceContract.existenceGate=PASS`，并记录以上接口数量与摘要。候选 Manifest SHA-256 为：

```text
813394F44AE06AEA63A4DBA319AECA77C75964913098193D1E9BBD67DBF7A0BB  release-candidate-manifest.json
```

下载候选 API contract 与 Manifest Artifact 后，`sha256sum --check` 对运行时文档、比较报告和 Manifest 均通过。

## 6. 回滚

若运行时文档地址、路径归一化或证据 schema 出现问题，通过新的受保护 PR 回滚本阶段工作流、脚本和 Manifest schema 改动。不得关闭接口存在性门禁、跳过 Manifest 校验或扩大数据库权限。
