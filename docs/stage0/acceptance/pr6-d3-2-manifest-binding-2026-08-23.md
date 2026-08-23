# PR6-D3-2 验收合同与候选 Manifest 绑定记录

## 目标

将阶段 0 的机器可读验收合同、验收矩阵、风险登记表和合同 Schema 固定绑定到跨仓最终候选 Manifest，确保候选运行使用的证据来自同一个后端提交快照。

## 实施内容

- 候选 Manifest 升级为 Schema v4。
- 新增 `stage0Acceptance` 字段，记录四份 Stage 0 证据的 SHA-256、合同状态、绑定状态、必需失败数和待关闭门数。
- 冻结候选时从精确后端 SHA 重新计算四份证据哈希，并校验合同仍为 `PROVISIONAL`、必需失败数为 `0`、待关闭门数为 `2`。
- 生成 Manifest 后重新计算文件哈希，与 Manifest 字段逐项比对。
- 静态门禁要求工作流、生成器和 Schema 同时包含 v4 与 Stage 0 绑定契约。

## D3-2 预期绑定值

| 字段 | 预期 |
|---|---|
| `stage0Acceptance.bindingStatus` | `BOUND` |
| `stage0Acceptance.contractStatus` | `PROVISIONAL` |
| `stage0Acceptance.requiredFailureCount` | `0` |
| `stage0Acceptance.pendingClosingGateCount` | `2` |

上述值表示“验收合同已绑定到候选”，不表示阶段 0 已最终封存。D4 完成最终候选 10/10 Job、Manifest SHA、Tag 和 Release 后，才关闭 E0-22/E0-23。

## 最终候选运行输入

受保护合并 D3-2 后重新读取两个远端 `develop` 的完整 SHA，使用唯一候选 ID（例如 `stage0-final-20260823-001`）触发 `Cross-repository release gate`。D3-2 本身不创建 Tag、GitHub Release，不连接 3306 主库，也不执行生产部署。

## 验收状态

- [ ] D3-2 分支 CI 全部通过
- [ ] 受保护 PR 合并并完成合并后 `develop` CI
- [ ] 记录最终候选运行 URL、10/10 Job 结果和 Manifest SHA-256
- [ ] 进入 D4 最终封存
