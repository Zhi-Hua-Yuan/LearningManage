# AI 调用管线设计说明（阶段一）

- 状态：实施中（公共调用管线和今日任务推荐迁移已完成）
- 日期：2026-08-12
- 适用范围：LearningManage 后端 AI 调用公共管线第一阶段
- 首个迁移场景：今日任务推荐顺序

## 1. 问题定义

### 1.1 用户和问题

- 目标用户：使用 LearningManage AI 辅助能力的登录用户，以及维护 AI 功能的后端开发者。
- 当前问题：`AiServiceImpl` 同时承担多个 AI 业务场景、提示词解析、模型调用、调用日志、模型结果解析、降级和草稿生命周期等职责。
- 当前实现不足：不同 AI 场景重复实现“创建调用日志、调用模型、更新执行元数据、区分调用失败和解析失败、更新日志终态”等技术流程。继续在同一个实现类中增加功能会扩大重复代码，并提高日志状态不一致和异常处理分叉的风险。
- 现有证据：`AiServiceImpl` 当前约 2382 行；今日排序、日报改名、清单重排等场景已经出现相似的模型调用和日志生命周期代码。

### 1.2 输入、输出与副作用

- 管线输入：当前用户 ID、首选模型名称、提示词编码、场景构造的用户提示词、解析失败时可安全记录的错误说明、场景响应处理回调。
- 管线输出：经过场景解析和业务校验后的类型化结果，以及调用日志 ID、实际模型、重试次数和总耗时等执行元数据。
- 副作用：创建和更新 AI 调用日志；调用外部大模型服务。
- 不产生的副作用：不修改任务、项目、周总结或草稿等正式业务数据。

### 1.3 验收标准

1. `/ai/today-order/recommend` 的 API 契约不变。
2. 今日无任务时不进入 AI 调用管线。
3. 模型调用成功且响应校验成功时返回原有 AI 排序结果。
4. 模型调用失败或今日排序响应处理失败时保持原有规则降级行为。
5. 调用日志能够区分成功、调用失败、超时和响应处理失败。
6. 调用日志自身写入失败时不阻断可正常完成的 AI 主流程。
7. Redis 限流、数据库结构和其他 AI 场景行为不变。
8. 聚焦测试和完整 Maven 测试通过。

### 1.4 范围

本阶段包含：

- 抽取回调式最小 AI 调用管线。
- 只迁移今日任务推荐顺序。
- 为管线和今日任务推荐补充行为测试。

本阶段明确不包含：

- 不修改 Controller、对外 API 或响应结构。
- 不修改数据库表和 SQL 初始化脚本。
- 不调整 `AiRateLimitInterceptor` 或限流 Key。
- 不抽取统一草稿服务，不合并草稿存储。
- 不迁移日报改名、清单重排、任务拆解和周总结润色。
- 不引入 Handler 注册表、Handler 工厂或抽象模板父类。
- 不开发 RAG。

## 2. 已确认的现有调用路径

今日任务推荐当前路径为：

```text
AiController
→ LoginInterceptor
→ AiRateLimitInterceptor
→ AiServiceImpl.recommendTodayOrder
→ 标准化请求参数并查询当前用户的今日任务
→ PromptTemplateResolver.resolve
→ AiCallLogService.createRunningLog
→ AiModelClient.invoke
→ 解析并校验今日排序结果
→ 更新调用日志
→ 返回 AI 排序或规则降级结果
```

已确认的边界：

- 身份认证由登录拦截器和 `UserHolder` 负责。
- HTTP 入口的场景限流由 `AiRateLimitInterceptor` 负责。
- `PromptTemplateResolver` 负责数据库提示词和内置提示词之间的解析规则。
- `AiModelClient` 负责模型供应商调用、超时分类、兜底模型和执行元数据。
- `AiCallLogService` 负责调用日志持久化。
- `AiServiceImpl` 当前负责业务查询、Prompt 上下文、响应校验和规则降级。

## 3. 设计决策

采用“回调式最小调用管线”。公共管线固定技术执行骨架，场景通过响应处理回调提供结果解析和业务校验。

```text
业务场景准备数据和 userPrompt
→ AiInvocationPipeline 解析 systemPrompt
→ 创建 RUNNING 调用日志
→ AiModelClient.invoke
→ 更新实际模型和重试次数
→ AiResponseProcessor 解析并校验结果
→ 更新调用日志终态
→ 返回类型化结果
```

### 3.1 管线职责

- 根据提示词编码调用 `PromptTemplateResolver`。
- 尽力创建 `RUNNING` 调用日志。
- 调用 `AiModelClient`。
- 尽力更新实际模型与重试次数。
- 调用场景提供的响应处理器。
- 将日志标记为成功、失败、超时或解析失败。
- 返回类型化结果和执行元数据。
- 将未分类的模型调用异常包装为统一的 `AiInvocationException`。

### 3.2 管线非职责

- 不读取当前登录用户。
- 不执行 Redis 限流。
- 不查询任务、项目或周总结等业务数据。
- 不构造具体业务场景的用户 Prompt。
- 不解析具体场景的 JSON 字段。
- 不决定失败后报错还是降级。
- 不创建、确认、取消或过期 AI 草稿。
- 不修改正式业务数据。
- 不开启覆盖外部模型调用的数据库事务。

## 4. 内部契约

以下为设计契约，具体包名和命名可在实现时做不改变职责的微调。

### 4.1 执行命令

```java
public record AiExecutionCommand(
        Long userId,
        String modelName,
        AiPromptCodeEnum promptCode,
        String userPrompt,
        String parseFailureMessage
) {
}
```

约束：

- `userId`、`modelName`、`promptCode` 和 `userPrompt` 必须非空。
- `parseFailureMessage` 必须是允许写入日志的安全信息，不能携带密钥或未经脱敏的敏感内容。
- 命令不允许依赖 `Task`、`Project` 等业务实体。

### 4.2 响应处理器

```java
@FunctionalInterface
public interface AiResponseProcessor<T> {
    T process(String rawContent);
}
```

响应处理器负责：

- 将模型原始文本转换为场景结果。
- 校验结构、字段范围、ID 归属、重复和遗漏等场景规则。
- 校验失败时抛出异常，不能返回一个伪造的成功结果。

响应处理器不负责更新调用日志，也不决定是否降级。

### 4.3 执行结果

```java
public record AiExecutionResult<T>(
        T data,
        Long callLogId,
        String actualModel,
        Integer retryCount,
        long costTimeMs
) {
}
```

该结果仅供后端内部使用，不进入现有 API 响应结构。

### 4.4 异常契约

- `AiInvocationException`：模型配置、超时、上游拒绝、供应商响应结构异常和内部调用错误。
- `AiResponseProcessingException`：模型调用已经返回内容，但场景解析或业务校验失败。
- 提示词解析发生的既有业务异常保持原语义，不由管线错误转换成“模型调用失败”。
- 日志持久化异常只记录警告，不覆盖模型调用或业务解析的真实结果。

## 5. 今日任务推荐迁移边界

迁移后 `AiServiceImpl.recommendTodayOrder` 继续负责：

- 获取和校验当前用户。
- 标准化策略、时区、当前时间和数量限制。
- 只查询当前用户的今日未完成任务。
- 无任务时直接返回空列表。
- 构造今日任务推荐用户 Prompt。
- 提供今日排序解析和校验回调。
- 在调用或响应处理失败时执行现有规则降级。
- 设置 `generatedAt` 和 `fallbackUsed`。

以下今日排序方法本阶段不抽入公共管线：

- `loadTodayTodoTasks`
- `buildTodayOrderUserPrompt`
- `parseAndValidateTodayOrderResult`
- `fallbackByRule`
- 今日排序参数标准化和分数计算方法

## 6. API 与数据契约

### 6.1 API

- 路径：`POST /ai/today-order/recommend`
- 请求 DTO：保持 `AiTodayOrderRequest` 不变。
- 响应 VO：保持 `AiTodayOrderVO` 不变。
- Controller：本阶段不修改。

### 6.2 数据模型

- 不新增或修改数据库表。
- 不修改 `ai_call_log` 字段和状态定义。
- 不修改 `ai_draft`、`ai_replan_operation` 或相关表。
- 管线只复用现有 `AiCallLogService` 写入调用日志。

## 7. 权限、数据隔离与事务

### 7.1 权限和数据隔离

- 继续由现有登录链路确定当前用户。
- 今日任务查询在当前兼容口径下必须保留 `Task.createdByUserId = currentUserId` 条件；受理人维度查询由 PR4 后续任务列表工作包统一处理。
- 模型返回的所有 `taskId` 必须来自本次传给模型的任务集合。
- 公共管线不得自行接受或查询其他用户的业务数据。

### 7.2 事务边界

- `AiInvocationPipeline.execute` 不使用 `@Transactional`。
- 外部模型调用不能处于长数据库事务中。
- 调用日志采用独立的现有服务操作和尽力而为语义。
- 今日推荐只读业务数据，不产生需要幂等控制的正式业务写入。

### 7.3 幂等性

- 今日推荐是建议型、无正式业务数据写入的接口，本阶段不增加幂等键。
- 相同请求可能产生不同模型建议，这是当前产品行为。
- 调用日志每次请求独立记录。

## 8. 失败矩阵

| 失败位置 | 管线行为 | 今日推荐用户可见行为 | 日志行为 |
|---|---|---|---|
| 未登录 | 不进入管线 | 返回现有未登录错误 | 不创建 AI 日志 |
| Redis 限流 | 不进入管线 | 返回现有限流错误 | 不创建 AI 日志 |
| 无今日任务 | 不进入管线 | 返回空任务列表 | 不创建 AI 日志 |
| Prompt 解析异常 | 原异常向上抛出 | 保持现有错误语义 | 不创建或不完成日志 |
| 调用日志创建失败 | 继续调用模型 | 不受影响 | 应用日志记录警告 |
| 模型超时 | 抛出 `AiInvocationException` | 使用现有规则降级 | 尽力标记 `TIMEOUT` |
| 模型普通失败 | 抛出 `AiInvocationException` | 使用现有规则降级 | 尽力标记 `FAILED` |
| 供应商响应结构非法 | 抛出 `AiInvocationException` | 使用现有规则降级 | 尽力标记 `PARSE_FAILED` |
| 今日排序 JSON 非法 | 抛出 `AiResponseProcessingException` | 使用现有规则降级 | 尽力标记 `PARSE_FAILED` |
| taskId 非法、重复或遗漏 | 抛出 `AiResponseProcessingException` | 使用现有规则降级 | 尽力标记 `PARSE_FAILED` |
| 日志执行元数据更新失败 | 继续解析结果 | 不受影响 | 应用日志记录警告 |
| 成功日志更新失败 | 返回 AI 排序 | 不受影响 | 应用日志记录警告 |

## 9. 测试矩阵

### 9.1 基线

2026-08-12 在 `develop` 分支执行完整测试：

```powershell
$env:JAVA_HOME='D:\JDK17'
.\mvnw.cmd test
```

结果：24 个测试全部通过，0 failures，0 errors，0 skipped。

环境说明：系统默认 Maven 使用 JDK 8，直接运行 `./mvnw.cmd test` 会在测试启动前因 Java class file 版本不兼容而失败。项目要求 JDK 17，后续测试必须明确使用 JDK 17。

### 9.2 管线单元测试

| 用例 | 断言 |
|---|---|
| 模型成功且响应处理成功 | 返回类型化结果，日志标记成功 |
| 模型超时 | 原样抛出调用异常，日志标记超时，处理器不执行 |
| 模型普通失败 | 抛出调用异常，日志标记失败 |
| 供应商响应结构非法 | 抛出调用异常，日志标记解析失败 |
| 场景响应处理失败 | 抛出响应处理异常，日志标记解析失败 |
| 日志创建失败 | 模型及响应处理仍正常执行 |
| 日志终态更新失败 | 仍返回正确业务结果 |
| 使用兜底模型 | 更新实际模型和重试次数 |

### 9.3 今日推荐行为测试

| 用例 | 断言 |
|---|---|
| 今日无任务 | 返回空列表且不调用管线 |
| 管线成功 | 返回 AI 排序并设置 `fallbackUsed=false` |
| 模型调用失败 | 返回规则排序并设置 `fallbackUsed=true` |
| 响应处理失败 | 返回规则排序并设置 `fallbackUsed=true` |
| 非法、重复或遗漏 taskId | 响应处理失败并触发规则降级 |
| 指定 taskIds | 只允许返回当前用户、本日、未完成且属于输入集合的任务 |

## 10. 预计影响文件

计划新增：

```text
src/main/java/com/spt/learningmanage/ai/pipeline/AiExecutionCommand.java
src/main/java/com/spt/learningmanage/ai/pipeline/AiExecutionResult.java
src/main/java/com/spt/learningmanage/ai/pipeline/AiResponseProcessor.java
src/main/java/com/spt/learningmanage/ai/pipeline/AiInvocationPipeline.java
src/main/java/com/spt/learningmanage/exception/AiResponseProcessingException.java
src/test/java/com/spt/learningmanage/ai/pipeline/AiInvocationPipelineTest.java
src/test/java/com/spt/learningmanage/service/impl/AiServiceImplTodayOrderTest.java
```

计划修改：

```text
src/main/java/com/spt/learningmanage/service/impl/AiServiceImpl.java
```

明确不修改：

```text
AiController.java
AiService.java
AiModelClient.java
AiRateLimitInterceptor.java
所有 SQL 文件
所有草稿相关类
```

## 11. 实施顺序和停止点

1. 确认 JDK 17 下完整测试基线。
2. 记录本设计说明和 ADR。
3. 补充今日任务推荐现状测试。
4. 建立最小管线内部契约。
5. 编写管线单元测试并实现管线。
6. 只迁移今日任务推荐。
7. 运行聚焦测试和完整 Maven 测试。
8. 评审变更范围和管线边界。

每一步都应保持可编译、可测试。第一个场景验收前，不迁移其他 AI 场景；至少完成三个场景迁移并观察到稳定共性前，不升级为 Handler 注册表。
