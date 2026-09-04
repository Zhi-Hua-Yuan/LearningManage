# WP3 Release 记录

状态：`PASS`

## 发布绑定

| 项目 | 值 |
|---|---|
| 工作包 | WP3 AI 调用管线 2.0 |
| 合并 PR | [#110](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/110) |
| 合并提交 | `e76e2b5d77b09a9dfb2dad0f2c4b46404fcbeaf3` |
| 合并后 CI | [run 33867556286](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33867556286) |
| Tag | `stage2-wp3-v1.0.0` |
| Release | [stage2-wp3-v1.0.0](https://github.com/Zhi-Hua-Yuan/LearningManage/releases/tag/stage2-wp3-v1.0.0) |

## 门禁结果

- Backend CI 5/5 Job 通过。
- Maven 629/629 通过。
- WP3 MySQL 集成测试 4/4 通过。
- Flyway 空库、存量库和 Docker 运行与迁移门禁通过。
- 业务代码直接调用 `AiModelClient`：0；直接依赖 `AiHttpTransport`：0。
- V1/V2/V3 迁移保持不可变；RAG/Agent 未实现。

## 范围说明

本 Release 仅封存阶段 2 的 WP3，不代表阶段 2 整体完成，也不创建 `stage2-v1.0.0`。WP4～WP8 仍按阶段合同继续实施。
