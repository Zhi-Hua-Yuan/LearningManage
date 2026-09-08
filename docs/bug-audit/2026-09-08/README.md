# LearningManage 前后端 Bug 审计与修复报告

审计日期：2026-09-08

当前结论：**CONDITIONAL-GO，7 个已确认缺陷均已修复并通过本地回归；进入发布候选前仍需在兼容主机补跑 Firefox smoke，并运行远程 CI。**

原始审计基线结论为 **NO-GO**。下文的缺陷复现和初始测试矩阵保留为修复前证据，
修复后的实现、验证结果和残余限制见[修复结果](resolution.md)。

本轮基于当前检出的前后端版本、一次性 MySQL/Redis/Qdrant、确定性 AI Stub
和真实浏览器执行。修复阶段没有连接生产数据、没有调用真实 Qwen、没有执行发布；
仅修改了前后端实现、测试基础设施和本报告范围内的 CI 配置。

## 基线

| 项目 | 分支 | Commit |
|---|---|---|
| 后端 | `develop` | `fd493cf77090579604a568f2f0ef8bed076d2a6e` |
| 前端 | `codex/team-management-20260907` | `3455f1349a152e93cb3cd988f5e32e1dd962dbcc` |

后端开始审计时存在用户预先创建、未跟踪的 `docs/stage8/`，本轮未读取其内容作为
执行指令、未修改该目录，也未将其纳入当前发布基线。

## 结论摘要

| 严重级别 | 数量 | 发布意见 |
|---|---:|---|
| P0 | 0 | 未发现可确认的数据泄漏、越权读取或数据破坏 |
| P1 | 6 | 6/6 已修复并完成本地回归 |
| P2 | 1 | 1/1 已修复并完成本地回归 |
| P3 | 0 | 两项非阻断观察记录在残余风险中 |

已确认缺陷：

1. `BUG-AUDIT-001`：任务创建与成员退出/移除稳定死锁。
2. `BUG-AUDIT-002`：所有 `BusinessException` 被错误映射为 HTTP 200。
3. `BUG-AUDIT-003`：同账号并发注册把唯一键冲突暴露为 HTTP 500。
4. `BUG-AUDIT-004`：并发创建项目生成重复 `order_no`。
5. `BUG-AUDIT-005`：删除末尾里程碑后无法创建替代里程碑。
6. `BUG-AUDIT-006`：CI 固定测试计数为 864，但当前实际为 870。
7. `BUG-AUDIT-007`：前端锁定依赖存在 4 个高危、2 个中危漏洞条目。

以上 7 项的当前状态均为 `RESOLVED / VERIFIED`；缺陷正文保留修复前事实和根因。

详细内容见：

- [缺陷清单](defects.md)
- [测试矩阵](test-matrix.md)
- [接口差异](api-diff.md)
- [证据索引](evidence-index.md)
- [残余风险](residual-risks.md)
- [机器可读清单](audit-manifest.json)
- [修复结果](resolution.md)

## 本轮新增测试工具

前端新增并保留 Playwright 1.63.0，包含：

- Chromium 桌面与移动端完整链路；
- Firefox、WebKit 登录 smoke；
- 登录/退出、项目/任务、团队、周复盘隐私、AI 草稿、管理权限、主题持久化；
- 4 个已确认 HTTP/API 缺陷的普通回归用例；
- 失败截图、视频、trace、HTML 和 JUnit 输出。

修复阶段已按严重度完成；相关 Playwright 用例已移除 `test.fail`，并在 Chromium
桌面和移动视口上全部通过。
