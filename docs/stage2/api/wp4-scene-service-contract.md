# WP4 AI 场景服务内部合同

状态：`PASS / CANDIDATE CI PASS`
日期：2026-09-04

## 公共兼容合同

WP4 没有新增、删除或重命名 HTTP 接口。`AiController`、`AiService`、请求 DTO、响应 VO、`BaseResponse`、JWT、权限结果和业务错误码保持原样；前端仍使用冻结的 44 个 operation。

## 内部服务合同

| 服务 | 职责 | 可依赖能力 |
|---|---|---|
| `AiChatCompatibilityService` | 固定 legacy-chat 内部兼容调用 | Pipeline |
| `TaskBreakdownAiService` | 拆解生成、预览、草稿和确认事务 | Pipeline、权限、Mapper、草稿支持 |
| `WeeklyReviewAiService` | 周复盘润色、预览和确认事务 | Pipeline、权限、Mapper、草稿支持 |
| `TodayOrderAiService` | 今日排序、ID 校验和规则降级 | Pipeline、权限、Mapper |
| `DailyRenameAiService` | 日报改名、校验、降级和改名日志 | Pipeline、权限、Mapper |
| `ListReplanAiService` | 重排预览、确认、取消、快照和项目日期同步 | Pipeline、权限、Mapper |
| `AiDraftLifecycleService` | 通用草稿创建、查询、取消、过期、状态与确认记录 | 草稿 Mapper |
| `AiModelSelector` | 场景模型选择及既有回退规则 | `AiProperties` |
| `AiJsonResponseSanitizer` | Markdown 包裹移除和 JSON 对象/数组提取 | 无业务依赖 |

场景实现不得互相调用；业务代码不得绕过 Pipeline 调用 `AiModelClient` 或 Transport。事务只能放在真正执行写操作的公开场景方法上。

## 行为冻结

- 今日排序、日报改名和清单重排继续使用 WP3 已定义的规则降级及失败分类。
- 非法、重复、不存在或越权业务 ID 继续被拒绝或进入原有降级路径。
- 任务拆解确认继续在同一事务内创建项目、里程碑和任务。
- 周复盘确认继续重新校验当前权限，预览后失权不得写入。
- 清单重排继续校验任务快照、操作状态和项目管理权限，并维持原幂等语义。
- WP4 不改变草稿 Schema、状态值、确认记录唯一约束或锁策略。

候选发布门禁已通过工作流 [33875362611](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33875362611) 固定到合并提交 `d3a9c673e2e79709f2cd140be9c24cad874957bd`；Docker Stub 全链路通过，运行时 API 与前端使用接口 44/44 匹配，缺失 0。详细证据见 `docs/stage2/evidence/wp4/candidate-release-gate-2026-09-04.json`。

## 版本边界

已发布 Flyway 头保持 V3，V1/V2/V3 SHA-256 均未变化；WP4 不产生 V4。`S2-A-008` 已在候选 CI、Docker Stub 和运行时 OpenAPI 比对完成后封存为 `PASS`；`S2-R-008` 继续保持 `OPEN`，直到 WP8 最终跨仓兼容验收。
