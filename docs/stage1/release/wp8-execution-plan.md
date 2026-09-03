# WP8：阶段 1最终验收与发布执行计划

状态：`IN_PROGRESS`

基线：`origin/develop@b363fc29c07d6231eaae883b6a024ff758be731a`

## 目标

WP8只负责阶段级发布门禁，不新增业务功能。最终结果必须由一个不可变候选绑定：

```text
精确后端 SHA + 精确前端 SHA
→ API 兼容子集验证
→ 后端/前端/全栈回归
→ Stage 1 证据索引与哈希
→ Candidate Manifest
→ 注解 Tag
→ GitHub Release
```

## 工作包

| 工作包 | 内容 | 退出条件 |
|---|---|---|
| WP8-A | 发布脚本、Stage 1 schema、37 operation 基线、证据索引 | PR 受保护合并，develop post-merge CI PASS |
| WP8-B | 精确双仓 SHA 的最终候选和回归 | S1-A-009、S1-A-010、S1-A-011 PASS |
| WP8-C | Tag、Release 和附件校验 | S1-A-012 PASS，S1-R-010 CLOSED |
| WP8-D | 发布后文档收口 | 合同 status=PASS，阶段状态 COMPLETE |

## API 兼容门禁

阶段 0 的 37 个 operation 保存在 `stage0-frontend-operation-baseline.json`，最终候选同时验证：

```text
旧 37 ⊆ 当前前端 44
当前前端 44 ⊆ 运行时 OpenAPI
```

通过条件：

```text
legacyMissingFromCurrentCount = 0
legacyMissingFromRuntimeCount = 0
currentMissingFromRuntimeCount = 0
```

## 候选防漂移

候选开始和结束时重新读取两个受保护 `develop`。任一分支移动，候选标记为 `STALE`，不得创建 Tag 或复用 candidateId。

## 发布后收口

候选产物和 Tag 指向实际测试过的提交。Release 后再通过一个纯文档受保护 PR，将 S1-A-009～012 改为 `PASS`、关闭 S1-R-010，并记录 `releaseTag`、`releaseUrl`、`manifestSha256` 和 `closureCommitSha`。
