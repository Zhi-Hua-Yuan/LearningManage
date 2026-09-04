# WP2 模型协议与供应商兼容层验收

日期：2026-09-04
结论：`PASS`
阶段门禁：`S2-A-006`

## 能力验收

| 项目 | 结果 | 证据 |
|---|---|---|
| 四类消息和多轮工具结果回传 | PASS | `AiChatCommandValidatorTest`、`AiChatRequestMapperTest` |
| tools/tool_choice 请求映射 | PASS | Jackson 结构断言，强制函数、AUTO、NONE 均覆盖 |
| content、Tool Calls、finish reason 解析 | PASS | 单个/多个 Tool Calls、空文本工具响应、字段不一致负例 |
| Usage 和供应商请求 ID | PASS | 完整/部分/缺失 Usage，body/header 优先级覆盖 |
| 非法响应失败分类 | PASS | 非 JSON、空 choices、非法 arguments、多模态 content、负 Token 覆盖 |
| 主模型与 fallback 元数据 | PASS | 429、5xx、timeout、双失败 suppressed exception 覆盖 |
| 旧 `invoke(...)` 兼容 | PASS | 旧文本结果结构不变，Tool-only 响应安全失败 |
| 离线供应商 Stub | PASS | 16 个固定文本、工具、多轮、非法 Tool index、Usage、异常和 HTTP 故障场景 |
| 敏感信息边界 | PASS | 敏感 Header 丢弃；Stub 不记录 Header、Prompt、响应；异常不包含上游正文 |
| 数据库迁移不可变 | PASS | V1/V2/V3 相对 `origin/develop` 无差异，SHA-256 已记录 |
| 公共接口兼容 | PASS | Controller 零改动；运行时 OpenAPI 继续由既有门禁验证 |

## 测试结果

```text
WP2 协议与客户端测试：37/37 PASS（其中新增 31，包含真实 HTTP 两轮 Tool Call 往返）
本机非 MySQL 回归：554/554 PASS
离线 Stub：16/16 PASS
CI 新测试总数：607
```

本机直接执行全量测试时，既有 `*MySqlTest` 因未注入 CI 专用 `TEST_DB_*` 凭据而无法建立连接；失败发生在测试前置数据库连接阶段，与 WP2 代码无关。PR #108 的 Run #254 与合并后 `develop` 的 Run #255 均已通过完整 607 测试、Flyway 和 Docker 门禁，合并提交为 `7cc9bb574d0da5f56b4a77fa14ff89317601ba8b`，因此 `S2-A-006` 关闭为 `PASS`。

## 受保护 CI 证据

- PR #108 / Run #254：[Backend CI](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33856492022)
- 合并后 Run #255：[Backend CI](https://github.com/Zhi-Hua-Yuan/LearningManage/actions/runs/33857172196)
- 合并提交：`7cc9bb574d0da5f56b4a77fa14ff89317601ba8b`

## 不可变边界

```text
V1 E9438D40535CDC814CF83C22A1616958E770D6719A0FD7C9922FFB33F99D97D9
V2 B40BD46F7CB303F8ED5B79AC86F78AE9078E78F8F3C26C91AAFA89F758683FE1
V3 A626B41B40EFB8EDC2D72F57454A738B6196A11DEA9C6E14070F07E6CFAC4177
```

WP2 未新增数据库迁移、公开 Controller、业务工具执行、RAG 或 Agent 能力。

## 剩余风险

`S2-R-003` 保持 `OPEN`：离线 OpenAI-compatible 协议差异已经得到适配器和负向测试缓解，但真实 Qwen 模型对 Tool Calls、Usage 和请求 ID 的受控验证仍由 WP7 完成。
