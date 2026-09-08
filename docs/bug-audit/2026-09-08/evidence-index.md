# 证据索引

## 可重复命令入口

后端：

```powershell
.\mvnw.cmd -B -ntp "-Dtest=**/*Test,!**/*MySqlTest" test
.\mvnw.cmd -B -ntp "-Dtest=**/*MySqlTest" test
.\mvnw.cmd -B -ntp "-Dtest=DataCleanupLifecycleV8IT" test
.\mvnw.cmd -B -ntp "-Dtest=KnowledgeIndexEndToEndIT" test
.\mvnw.cmd -B -ntp "-Dtest=RagEndToEndIT" test
.\mvnw.cmd -B -ntp "-Dtest=AgentEndToEndIT" test
```

这些数据库测试需要使用仓库 CI 文档定义的一次性 MySQL V7/V8 数据库及对应环境开关。

前端：

```powershell
npm run lint:ci
npm run test:coverage
npm run type-check
npm run contract:test
npm run contract:verify
npm run build
npm run test:e2e
```

## 当前本地输出

| 证据 | 路径 |
|---|---|
| Surefire 报告 | `target/surefire-reports/` |
| 前端覆盖率摘要 | `../learning-manage-frontend/coverage/coverage-summary.json` |
| Playwright Firefox 启动诊断 JUnit | `../learning-manage-frontend/test-results/e2e-results.xml` |
| Playwright Firefox 启动诊断 HTML | `../learning-manage-frontend/playwright-report/index.html` |
| Firefox 错误上下文/trace | `../learning-manage-frontend/test-results/artifacts/` |

`target/`、`coverage/`、`test-results/` 和 `playwright-report/` 是本地生成且被忽略的证据；
最终 30 项全量结果以本目录的测试矩阵和 `audit-manifest.json` 固化；本地 JUnit/HTML
最后一次运行专门保留 Firefox 启动失败详情。本目录中的摘要和复现测试才是可提交的
持久证据。

## 缺陷到证据映射

| 缺陷 | 持久复现/源码证据 |
|---|---|
| BUG-AUDIT-001 | 后端两个 `*MembershipTerminationConcurrencyMySqlTest`；`TaskAssigneePolicyImpl` 与 `TeamMembershipTerminationServiceImpl` 锁序 |
| BUG-AUDIT-002 | 前端 `e2e/api-semantics.spec.ts`；后端 `GlobalExceptionHandler` |
| BUG-AUDIT-003 | 前端 `e2e/known-bugs.spec.ts`；后端 `UserServiceImpl.register` |
| BUG-AUDIT-004 | 前端 `e2e/known-bugs.spec.ts`；后端 `ProjectServiceImpl.getNext*OrderNo` |
| BUG-AUDIT-005 | 前端 `e2e/known-bugs.spec.ts`；`MilestoneServiceImpl.getNextOrderNo` 与 V1 唯一键 |
| BUG-AUDIT-006 | `backend-ci.yml`、`release-gate.yml`、`FlywayCiScriptStaticTest` 与本轮测试汇总 |
| BUG-AUDIT-007 | 官方 registry 的 `npm audit --omit=dev` 输出与 `package-lock.json` |

## 环境/工具阻塞证据

- Firefox 155 可执行文件直接启动也失败：Windows 报“应用程序的并行配置不正确”，
  Playwright 表现为 `browserType.launch: spawn UNKNOWN`。
- Stage 7 原始静态脚本命中 WindowsApps 的 `python3` 别名并被拒绝；使用仓库约束相同的
  Node/PowerShell/捆绑 Python 等价检查后通过。
- Docker Scout 1.19.0 在当前镜像的 `Indexing` 阶段超过两分钟无进展，已中止并归类为
  工具阻塞。
- 本地 npm 镜像不实现 audit API；切换到官方 registry 后审计成功。
