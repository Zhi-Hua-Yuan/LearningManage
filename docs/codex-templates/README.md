# Codex 开发模板（本项目）

这套模板用于当前项目的模块化开发流程，目标是让 Codex 输出更稳定、可追踪、可复盘。

## 推荐顺序
1. 复制并填写 `01-需求说明模板.md`
2. 基于需求拆分 `02-TODO清单模板.md`
3. 把 `03-Codex执行任务模板.md` 作为 Codex 任务输入
4. 模块完成后，开新对话使用 `04-模块审阅模板.md`
5. 修复后输出 `05-模块交接文档模板.md`
6. 提交 PR 时使用 `.github/PULL_REQUEST_TEMPLATE.md`

## 建议落地路径
- `docs/modules/<模块名>/requirement.md`
- `docs/modules/<模块名>/todo.md`
- `docs/modules/<模块名>/review.md`
- `docs/modules/<模块名>/handover.md`

