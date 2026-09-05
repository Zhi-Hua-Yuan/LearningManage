# WP8 最终验收与发布执行计划

状态：`WP8-A IMPLEMENTING / STAGE FROZEN`

## 候选边界

- 后端和前端候选只能使用各自受保护 `develop` 的完整 40 位 SHA。
- WP8 不新增公共 API、业务功能、数据库迁移或 RAG/Agent 能力。
- V1/V2/V3 必须与已发布校验和一致，禁止出现 V4。
- WP7 真实供应商证据仅在其验证 SHA 是候选祖先且之后没有 AI 运行时代码变化时复用。

## 执行顺序

```text
合并 WP8 发布基础设施
→ 冻结跨仓候选
→ 运行 11 项 Stage 2 Release Gate
→ 下载并校验候选证据
→ 证据封存 PR 将合同更新为 PASS
→ 最终 Seal
→ Annotated Tag stage2-v1.0.0
→ 正式 GitHub Release
```

## 候选门槛

- 后端测试不少于 710，前端测试不少于 484。
- legacy 37、前端 44、运行时不少于 65，前端缺失 0。
- Docker AI Stub 完成预览、取消、确认和幂等重放，正式数据为 1 个项目、2 个里程碑、4 个任务。
- 阶段合同在候选阶段必须为 `FROZEN`，`S2-A-012=PENDING`，`S2-R-008=OPEN`。
- 只有候选全部通过且证据已封存后才允许改为 `PASS` 并关闭 `S2-R-008`。

## 失败和重试

任何门禁失败均保留运行记录并停止发布。修复通过新 PR 合并后使用新的 `candidate_id` 重跑；不得移动或覆盖已公开 Tag。
