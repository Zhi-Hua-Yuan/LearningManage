# 阶段 0 / PR6-D1：跨仓候选发布工作流

执行日期：2026-08-21（Asia/Shanghai）
状态：已完成

## 1. 目标

在后端仓库建立手动触发的跨仓候选发布工作流，冻结后端和前端两个受保护 `develop` 的精确提交，重新执行现有安全、测试、构建、Flyway和后端Docker门禁，并输出不含凭据的机器可读候选清单。

## 2. 执行边界

- 不连接或修改3306 `learning_manage` 主库；
- 不使用生产数据库、JWT、模型或GitHub跨仓凭据；
- 不推送正式镜像，不部署环境；
- 不修改两个仓库的现有Ruleset；
- 不把手动跨仓工作流加入普通PR必需检查；
- D1不包含Nginx全栈、跨仓OpenAPI契约或离线AI闭环，这些留给D2。

## 3. 实施内容

| 内容 | 文件 |
|---|---|
| 跨仓候选编排 | `.github/workflows/release-gate.yml` |
| 候选输入、仓库身份和分支最新性校验 | `scripts/ci/validate-release-candidate.sh` |
| 通用候选安全函数 | `scripts/ci/lib/release-candidate-common.sh` |
| 候选Manifest生成 | `scripts/ci/create-release-manifest.sh` |
| Manifest机器契约 | `docs/stage0/ci/release-candidate-manifest.schema.json` |
| 操作和失败处理 | `docs/stage0/ci/release-gate-runbook.md` |
| 静态负向保护 | `scripts/ci/tests/static-guards-test.sh`、`FlywayCiScriptStaticTest` |

## 4. 冻结输入

实施起点：

```text
backend_develop=22e095b832d2e1d9556384819dc1b8cc9dd61f4f
frontend_develop=901a025783fcb7933994d2d32ccce527046ee02a
v1_sha256=E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9
backend_ruleset_id=21133622
frontend_ruleset_id=21145113
```

远程验收必须使用本PR合并后新的后端 `develop` SHA，不能继续使用上述实施起点。

## 5. 本地验收

- `./mvnw test`：81项测试全部通过，0失败、0错误、0跳过；
- `FlywayCiScriptStaticTest`定向执行通过，工作流由SnakeYAML成功解析；
- 所有Bash脚本通过`bash -n`；
- `static-guards-test.sh`：29项正向/负向契约全部通过；
- `verify-published-migrations.sh`：V1发布哈希和不可变检查通过；
- Manifest Schema通过JSON解析，且敏感字段黑名单检查通过；
- 工作流及新增脚本均为LF，三个新脚本Git模式为`100755`；
- `git diff --cached --check`通过；
- 本地验证未连接数据库，未启动Docker，未访问3306主库。

## 6. 远程验收

实现PR [#23](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/23) 已通过五项Backend CI并由Ruleset受保护合并，合并提交为`c506957362697235988e2490dab4100f667e9d83`；合并后的Backend CI Run `32486312678`五项Job全部成功。

首次候选Run `32486780822`成功冻结后端`c506957362697235988e2490dab4100f667e9d83`和前端`901a025783fcb7933994d2d32ccce527046ee02a`，随后在后端Gitleaks步骤失败。原因是`workflow_dispatch`默认执行全历史扫描，并把已记录的`openapi_sha256`历史哈希误判为通用API Key；普通PR扫描此前已成功。

候选Run `32488088269`验证了深度为1的检出仅扫描1个冻结提交，但当前基线文档仍包含`openapi_sha256=<64位哈希>`形式，继续被默认`generic-api-key`规则解释为密钥赋值。该值是已公开的OpenAPI文档摘要，不是凭据。

第二次修复不增加allowlist，而是把证据改为标准`sha256sum`格式`<摘要>  openapi-document.json`，完整保留摘要值并消除错误的密钥赋值语义。修复PR [#25](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/25)通过五项Backend CI后受保护合并，提交为`3e7caeb876e4bb15865d07963fe0f118caf0d15b`。

候选Run `32489396298`的后端完整快照扫描成功，随后前端完整快照将API文档中的高熵伪JWT识别为`generic-api-key`。前端修复PR [#15](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/15)把示例替换为明确的`<issued-jwt>`占位符，不增加allowlist；三项Frontend CI通过后由Ruleset受保护合并，提交为`7ed05ecb78b529a28d5d7602f85f154c2745fd77`。

### 6.1 最终候选

最终候选 [Run 32490153711](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32490153711) 于`2026-08-21T14:04:14Z`至`14:11:25Z`执行，候选ID为`stage0-pr6-d1-20260821-004`。

| Job | 结论 |
|---|---|
| Freeze release candidate | PASS |
| Guard backend candidate | PASS |
| Guard both repositories | PASS |
| Backend verification and tested artifact | PASS |
| Frontend tests and static verification | PASS |
| Frontend production build | PASS |
| Flyway empty database gate | PASS |
| Flyway existing database gate | PASS |
| Backend Docker runtime gate | PASS |
| Candidate manifest | PASS |

### 6.2 Manifest证据

```text
candidate_id=stage0-pr6-d1-20260821-004
backend_sha=3e7caeb876e4bb15865d07963fe0f118caf0d15b
frontend_sha=7ed05ecb78b529a28d5d7602f85f154c2745fd77
backend_test_count=81
backend_jar_sha256=421CF8D89E158FBD440AE0D3243679238B3C6DFD421545DB5B95929EE487E682
frontend_dist_manifest_sha256=B106AA7DC24EB0F209F27713AA5B5BA5828316FE2EA90139F1FE554F130D3A65
v1_sha256=E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9
backend_ruleset_contract_sha256=FB1892DF0C7927ECA7A83704ECED68D34115C07BCC1CA92938E001C270A4F92F
frontend_ruleset_contract_sha256=82491817E5FA8485B84B488BD262758E419FBD0A0DC7720BE18D9B5D9A8294CF
manifest_sha256=466D90C748ABAA6736BEF16785324B21FB72DCE9A4043C3A99CC176E6617F52B
```

Manifest中两个仓库的`developShaAtStart`、候选`sha`和`developShaAtEnd`分别完全一致；`status=PASS`，Flyway空库和存量库均为PASS，Docker运行门禁为PASS，`applicationFlywayEnabled=false`。本地下载后执行`sha256sum --check`通过，并再次复核上述状态契约。

候选Run生成并保留后端JAR、Surefire报告、前端覆盖率、前端`dist`及候选Manifest五类产物。Docker诊断采集因无失败而跳过，产物上传和`Tear down Docker stack`均成功。全程只使用Runner临时MySQL 13306和Docker资源，未连接或修改3306主库，未部署环境，未推送正式镜像。

候选004由其Manifest中的两个精确提交唯一标识；本执行记录的后续文档收尾提交不属于候选内容，不改变已封存候选证据。

## 7. 回滚

若D1工作流本身存在缺陷，通过新的受保护PR修复或删除 `release-gate.yml`。不禁用现有Ruleset，不直接写入 `develop`，不通过增加生产凭据或扩大数据库权限绕过失败。
