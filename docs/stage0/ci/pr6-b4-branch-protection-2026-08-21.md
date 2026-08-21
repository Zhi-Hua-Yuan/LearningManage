# 阶段 0 / PR6-B4：单人仓库分支保护执行记录

日期：2026-08-21
状态：执行中

## 1. 目标与边界

本步骤为默认分支 `develop` 建立仓库级 Ruleset，把 PR6-B3 已远程验收的后端 CI 接入合并规则。

执行边界：

- 仓库由单一所有者维护，不配置必需人工审批；
- 所有 `develop` 变更必须通过 Pull Request；
- 五项 Backend CI 必须全部成功；
- PR 必须基于最新 `develop`，未解决对话必须清零；
- 禁止删除和强制推送 `develop`；
- 日常 bypass list 为空；
- 不连接数据库，不修改生产凭据、部署环境、V1 或业务代码。

## 2. 执行前快照

```text
repository=Zhi-Hua-Yuan/LearningManage
visibility=public
default_branch=develop
administrator=Zhi-Hua-Yuan
starting_commit=4c5455ecc114c8cde6b569dd53d2196c69bfa70f
origin_develop=4c5455ecc114c8cde6b569dd53d2196c69bfa70f
worktree=clean
allow_update_branch=true
active_repository_rulesets=0
active_rules_for_develop=0
```

GitHub 经典分支保护端点需要认证；公开 Rules API 的快照显示执行前不存在仓库 Ruleset，也不存在命中 `develop` 的 Active Rule。

## 3. 已验证的必需状态检查

B3 成功运行：

```text
run_id=32453122804
run_url=https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32453122804
validated_commit=21dfdcd
expected_source=github-actions
integration_id=15368
```

Required Check contexts：

```text
Guard and migration immutability
Maven verification and tested artifact
Flyway empty database gate
Flyway existing database gate
Docker runtime and migration gate
```

五项 Check Run 在上述运行中均为 `completed/success`。

## 4. Ruleset 期望配置

```text
name=protect-develop-v1
target=branch
include=refs/heads/develop
exclude=<none>
enforcement=active
bypass_actors=[]
required_approving_review_count=0
dismiss_stale_reviews_on_push=false
require_last_push_approval=false
require_code_owner_review=false
require_extra_approval_for_unattributed_changes=false
required_review_thread_resolution=true
strict_required_status_checks_policy=true
restrict_deletions=true
block_force_pushes=true
```

允许的合并方式保持仓库现状：`merge`、`squash`、`rebase`。

## 5. 单人仓库自审

PR 作者不能批准自己的 PR，因此本仓库以结构化自审替代不可实现的独立审批。PR 模板要求最后一次提交后复核 `Files changed`、确认五项 Backend CI，并声明数据库迁移兼容性和回滚方式。

## 6. 管理员 Break-glass

日常不保留管理员绕过。若 GitHub Actions 或工作流自身故障导致修复 PR 无法通过，必须先建立 `BREAK-GLASS` Issue，记录失败检查、目标 PR、风险、操作人和时间；随后仅临时增加 `Repository administrator / Pull requests only` 绕过，仍通过 PR 合并最小修复，并在完成后立即移除绕过、复核 Ruleset History。禁止使用 `Always` 或 `Exempt`。

## 7. 执行与验证结果

Ruleset 已按“Disabled 创建、核验、Active 激活”的顺序完成。GitHub Effective Rules API 已确认四类规则均命中 `develop`，且当前用户不能日常绕过（`current_user_can_bypass=never`）。

验证 PR 首轮运行已确认五项 Backend CI 均为 `completed/success`。首轮通过后将 PR 从 Draft 转为 Ready，并通过本次记录更新验证新提交会使旧检查失效、重新触发完整门禁。

```text
ruleset_id=21133622
ruleset_url=https://github.com/Zhi-Hua-Yuan/LearningManage/rules/21133622
ruleset_json_sha256=FB1892DF0C7927ECA7A83704ECED68D34115C07BCC1CA92938E001C270A4F92F
validation_pr=https://github.com/Zhi-Hua-Yuan/LearningManage/pull/21
validation_run=https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/32461067038
merge_commit=<pending>
post_merge_run=<pending>
```

## 8. 回滚原则

若 Ruleset context、目标分支或合并行为异常，先将 Ruleset 切换为 `disabled` 或从 Ruleset History 恢复上一版本，不通过真实提交测试直接推送。修正后重新执行验证 PR。
