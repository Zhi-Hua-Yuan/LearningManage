# 阶段 0 验收合同

本目录保存阶段 0 总验收的机器可读合同、验收矩阵和残余风险登记表。

| 文件 | 用途 |
|---|---|
| [stage0-acceptance-matrix-2026-08-23.md](stage0-acceptance-matrix-2026-08-23.md) | 面向审阅者的验收矩阵 |
| [stage0-acceptance.json](stage0-acceptance.json) | 机器可读的 D3-1 验收合同 |
| [stage0-acceptance.schema.json](stage0-acceptance.schema.json) | 验收合同 JSON Schema |
| [stage0-risk-register-2026-08-23.md](stage0-risk-register-2026-08-23.md) | 残余风险、延期和不适用项 |

当前合同状态为 `PROVISIONAL`。E0-22 总验收和 E0-23 证据封存在 D4 最终候选通过前保持 `PENDING`。

验证入口：

```text
bash scripts/ci/verify-stage0-acceptance.sh
bash scripts/ci/tests/stage0-acceptance-test.sh
```

D3-1 不连接 3306，不执行 Flyway，不修改已发布 V1，不写入受保护 `develop`。
