# PR4 合并收口记录

状态：`PASS / COMPLETED`

日期：2026-08-29
仓库：`Zhi-Hua-Yuan/LearningManage`
目标分支：`develop`

## 1. 合并结果

PR #49 已通过受保护流程合并到 `develop`。

PR #49 合并提交：

```text
5d5c4a23dd01b5a272054862f936d32bb9ad5beb
```

该提交作为 PR4 业务实现完成基线。D2-F 收口 PR #50 已随后合并，合并提交为：

```text
f6951cc8d986a2bd30fc352f37e1bec7a773af17
```

该提交是 PR4 收口后的最终 `develop` 基线。不得把 PR #49 的业务实现提交与
PR #50 的文档收口提交混为一谈。

PR #51 已将本记录的后合并证据修订合并到 `develop`，其合并提交为：

```text
eec04c6741e03a4b386ecd9f94f60ac97f0b237f
```

## 2. 合并后验证

| 项目 | 结果 |
|---|---|
| Backend CI | PASS |
| Surefire 总数 | 432 |
| Failures | 0 |
| Errors | 0 |
| 双并发 CAS | PASS |
| 事务回滚 | PASS |
| no-op | PASS |
| 负责人/审计对账 | PASS |
| V1/V2 migration immutability | PASS |

D2-E 验收记录保存了 CI run `33239260276` 和隔离 MySQL 的完整结果。PR #50 合并后
的 Backend CI 为 [run 33240816476](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33240816476)，
5 个必需 Job 全部 `SUCCESS`。未使用本地未配置数据库凭据的结果替代受保护 CI。

PR #51 合并后的 Backend CI 为 [run 33241345632](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33241345632)，
5 个必需 Job 同样全部 `SUCCESS`。

## 3. 测试门槛

两份 workflow 的测试门槛应继续保持真实源码计数：

```text
CI_EXPECTED_TEST_COUNT = 432
```

D2-F 仅做合同和证据收口，不新增业务测试，不降低门槛，也不跳过 MySQL 集成测试。

## 4. 迁移与工作区守卫

- `src/main/resources/db/migration/V1__baseline_schema.sql` 未修改；
- `src/main/resources/db/migration/V2__stage1_business_semantics_and_permissions.sql` 未修改；
- V1/V2 摘要继续与 PR2 发布记录一致；
- `git diff --check` 必须通过；
- 收口 PR 合并后工作区必须干净。

## 5. 合同状态转移

```text
PR4-D2-E       COMPLETED / CI_PASS
PR4            COMPLETED / MERGED / CI_PASS
S1-A-003       PASS
S1-R-014       CLOSED
S1-R-003       OPEN  → PR5
S1-R-008       OPEN  → PR6
S1-R-012       OPEN  → PR6
```

PR4 收口不代表阶段 1 完成。阶段 1 的下一主目标为 PR5；PR6 和 PR8 的 Gate 继续
保持 `PENDING`，不得在本次收口中提前标记完成。

## 6. 收口清单

- [x] PR #49 merge commit 已记录；
- [x] 432 tests、0 failures、0 errors 已记录；
- [x] D2-E 并发、回滚、no-op、审计对账证据已链接；
- [x] `S1-A-003` 为 `PASS`；
- [x] `S1-R-014` 为 `CLOSED`；
- [x] PR5/PR6 风险责任保持开放并明确移交；
- [x] README 已切换到 PR4 completed、PR5 next；
- [x] V1/V2 migration 保持不可变；
- [x] D2-F 收口 PR #50 已合并，最终 `develop` SHA 已补录；
- [x] 收口 PR 合并后的 CI 链接和 5 个 Job 结果已补录。
- [x] PR #51 后合并证据修订已合并，提交 `eec04c6741e03a4b386ecd9f94f60ac97f0b237f`；
- [x] PR #51 合并后 CI run `33241345632` 已通过。
