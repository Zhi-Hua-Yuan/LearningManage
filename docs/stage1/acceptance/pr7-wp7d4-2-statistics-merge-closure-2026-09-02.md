# PR7 / WP7-D4-2 统计事实保护合并收口记录

状态：`COMPLETED / MERGED / CI_PASS`

日期：2026-09-02

## 合并链路

1. D4-2 分支 `codex/wp7-d4-2-statistics-review-fixes` 基于前端 D4-1 合并后的 `develop` 基线实施。
2. 前端 [PR #32](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/pull/32) 受保护合并。
3. 前端 PR Head SHA 为 `dce64caffe37722feca653a64d119ed8525de39c`。
4. 前端 Merge SHA 为 `c6bb5cbdbd820e5227279e893b9a8a3447191180`。
5. 前端 PR CI [33587108792](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33587108792) 三项门禁全部成功。
6. Merge SHA 对应的 develop post-merge CI [33587275741](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33587275741) 三项门禁全部成功。
7. 后端证据 [PR #78](https://github.com/Zhi-Hua-Yuan/LearningManage/pull/78) 受保护合并，Head SHA 为 `e2b89d59e6e6c1b3d9e64a7fafebbff02b40e9d4`，Merge SHA 为 `effd9c3bab11042f9cc7b433535f87f614efe019`。
8. 后端 PR CI [33587737550](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33587737550) 与 develop post-merge CI [33588088956](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33588088956) 全部成功。
9. 本次后端变更仅包含阶段证据、总览、风险登记和机器合同状态，不包含业务代码、数据库迁移或 API 合同变更。

## 关闭判定

- 服务端 `completedTaskCount`、`focusProjectName` 未被前端辅助统计覆盖；
- 辅助完成率仅使用当前用户负责人任务；
- 不完整任务快照失败关闭，服务端作者事实保持可用；
- 历史周服务端完成数差值支持跨年日期；
- 统计迟到响应、身份变化和团队快照变化均受保护；
- 聚焦测试 15 个、全量测试 334 个，全部通过；
- 覆盖率、Type-check、Lint、Build、合同测试和合同校验全部通过；
- API 合同保持 44 operations 和 SHA-256 `4f8cb8d3b92252e4375b49dd102e7cde75f819827713060d6e521bed19f0b2f6`；
- Frontend post-merge CI [33587275741](https://github.com/Zhi-Hua-Yuan/learning-manage-frontend/actions/runs/33587275741) 成功。

因此 WP7-D4-2 可标记为 `PASS / COMPLETED / MERGED / CI_PASS`。

## 阶段边界

WP7-D 整体继续执行，下一主目标切换为 `WP7-D4-3`。D4-3 负责 AI 任务上下文治理；D5 负责团队共享白名单界面；WP7-E 负责全局缓存、401、登出和多账号隔离。`S1-R-013` 保持 `OPEN`。
