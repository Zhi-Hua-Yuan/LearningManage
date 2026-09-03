# Stage 1 Release Gate Runbook

## 触发前检查

```text
工作区 clean
后端 origin/develop 已包含 WP7-F 合并提交 b363fc29…
前端 origin/develop SHA 已重新读取
两仓库保护性 CI 全绿
GitHub workflow ref = develop
```

候选输入使用两个仓库的完整 40 位 SHA，示例：

```text
candidate_id: stage1-final-YYYYMMDD-001
reason: Stage 1 final candidate for PR8 acceptance and release seal
```

## 门禁顺序

```text
Freeze exact SHAs
→ repository/static guards
→ backend verification
→ frontend verification/build
→ Flyway empty database
→ Flyway V1→V2 upgrade
→ Docker runtime
→ legacy 37/current 44 API comparison
→ personal business regression
→ Stage 1 multi-user regression
→ AI breakdown regression
→ evidence index
→ candidate manifest
→ recheck develop SHAs
```

## 产物

候选至少上传：

```text
stage1-release-candidate-manifest.json(.sha256)
stage1-source-evidence-index.json(.sha256)
stage1-api-compatibility-report.json(.sha256)
stage1-full-stack-evidence.json(.sha256)
frontend-api-contract.json(.sha256)
runtime-openapi.json(.sha256)
```

Manifest 不保存密码、JWT、API Key、数据库转储或真实业务正文。

## Tag/Release

Tag 必须是 annotated tag，并指向 Manifest 中的 `backend.sha`。Release 附件的 SHA 必须与候选 Manifest 和各自 sidecar 校验一致。Tag 和 Release 失败时，保留候选为未发布状态并使用新的 candidateId 重跑，不移动已创建 Tag。
