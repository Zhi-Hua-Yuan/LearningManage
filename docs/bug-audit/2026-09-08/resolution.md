# 修复结果

修复日期：2026-09-08

结论：`CONDITIONAL_GO`。原审计确认的 7 个缺陷均已完成实现和本地复验；未发现新的
P0/P1。尚未执行发布操作，Firefox smoke 因本机浏览器运行时无法启动而保持
`BLOCKED`，需由兼容主机或远程 CI 补跑后再作最终发布批准。

修复以审计基线 commit 为起点。实现提交如下；本文件所在的独立文档提交不计入实现 SHA：

| 项目 | 分支 | 基线 Commit | 修复 Commit |
|---|---|---|---|
| 后端 | `develop` | `fd493cf77090579604a568f2f0ef8bed076d2a6e` | `0ec567c` |
| 前端 | `codex/team-management-20260907` | `3455f1349a152e93cb3cd988f5e32e1dd962dbcc` | `f5c7a58` |

## 缺陷闭环

| 编号 | 修复摘要 | 回归证据 | 状态 |
|---|---|---|---|
| `BUG-AUDIT-001` | 团队写路径统一按 `team → project → member/task` 取锁；任务创建、指派、状态、批量重命名、AI 重排等写路径在锁后重新鉴权 | 两个成员终止并发类分别 17/17、14/14；关键竞态各重复 10 次 | RESOLVED |
| `BUG-AUDIT-002` | 为每个 `ErrorCode` 建立 HTTP 状态映射，异常处理器返回 `ResponseEntity`，并补充 OpenAPI 响应声明 | 运行时 400/401/403/404/409/429/503 可见；Playwright 401/400/403 断言通过 | RESOLVED |
| `BUG-AUDIT-003` | 捕获注册唯一键竞态并统一映射为账号已存在冲突 | Chromium 桌面/移动端各 8 路并发：仅 1 个成功，无 500 | RESOLVED |
| `BUG-AUDIT-004` | 个人项目锁用户、团队项目锁团队；排序读取改为 `FOR UPDATE` 当前读，并为列表增加稳定 ID 次序 | Chromium 桌面/移动端各并发创建 12 个项目，排序号全部唯一 | RESOLVED |
| `BUG-AUDIT-005` | 里程碑排序号按包含逻辑删除历史的最大值单调递增，项目内写入串行化，重复键转为业务冲突 | 删除末尾里程碑后创建替代项，桌面/移动端均通过 | RESOLVED |
| `BUG-AUDIT-006` | 后端 CI 和发布门禁固定计数同步更新为 896，静态契约同步更新 | 817 个非 MySQL + 79 个 MySQL 测试；静态契约 11/11 | RESOLVED |
| `BUG-AUDIT-007` | 升级 Axios、ECharts、PostCSS 及传递依赖，锁文件重新生成 | 官方 registry `npm audit --omit=dev`：0 vulnerabilities | RESOLVED |

项目排序首次修复使用父行锁加普通快照读。真实 MySQL E2E 证明，在默认
`REPEATABLE READ` 下，请求会在等待父行锁前由鉴权查询建立旧快照，后续普通查询仍可能
读到旧最大值。最终实现将最大排序号查询改成锁定当前读，重新运行后稳定通过。

## 最终验证

### 后端

| 门禁 | 结果 |
|---|---|
| 非 MySQL 测试 | PASS，817 tests，0 failures，0 errors |
| MySQL 集成/并发测试 | PASS，79 tests，0 failures，0 errors |
| V8 知识索引、RAG、Agent、清理 E2E | PASS，17 tests，0 failures，0 errors |
| Flyway 空库 V1→V8 | PASS，本轮在全新数据库执行 8 个迁移 |
| Flyway 存量 V7→V8 | PASS，原审计已验证；本轮未修改迁移 SQL |
| HTTP/OpenAPI | PASS，95 个后端操作；错误状态声明已进入运行时文档 |
| 后端打包 | PASS，生成 Spring Boot JAR |

MySQL 主套件此前有一个类级固定夹具在重复运行或中断后可能遗留数据。本轮为
`TaskAssignmentConcurrencyMySqlTest` 增加前后清理脚本，使该并发测试可重复执行。

### 前端与接口

| 门禁 | 结果 |
|---|---|
| `npm ci` | PASS；存在既有 Node 22.13.1 `EBADENGINE` 警告，不阻断安装 |
| Vitest | PASS，71 files / 515 tests |
| 覆盖率 | PASS，statements 84.36%、branches 74.91%、functions 84.89%、lines 87.94% |
| 类型、只读 lint、存储/缓存/AI 渲染守卫 | PASS |
| 生产构建 | PASS，Vite 7.3.2 / 799 modules |
| 前端 API 契约 | PASS，71/71 均匹配 95 个运行时后端操作 |
| Chromium desktop + mobile | PASS，26/26 |
| WebKit smoke | PASS，2/2 |
| Firefox smoke | BLOCKED，2 项均在页面加载前因 `browserType.launch: spawn UNKNOWN` 失败 |

Firefox 结果是当前 Windows 主机的浏览器启动环境阻塞，没有形成产品缺陷结论。失败目录
保留 Playwright trace 与上下文；在兼容主机复验前，当前结论保持 `CONDITIONAL_GO`。

## 约束与残余风险

- 全程使用一次性 MySQL、Redis、Qdrant 和确定性 AI Stub；没有访问生产数据或真实 Qwen。
- 没有执行部署、发布、推送或提交。
- 100,000 个 1024 维 Qdrant 点、真实供应商验证及 Firefox 页面行为仍属于原报告列明的
  未覆盖/环境受限范围。
- MySQL 对 `BINARY expr` 的弃用警告、周复盘分页 count 优化警告和 Node 引擎警告仍为
  非阻断观察项。
