# LearningManage

LearningManage 是一个学习管理系统后端项目，基于 Spring Boot 3 + MyBatis Plus + MySQL，提供项目管理、任务管理、里程碑管理、周总结、数据看板与 AI 辅助能力。

## 技术栈

- Java 17
- Spring Boot 3.3.6
- MyBatis Plus 3.5.7
- MySQL 8.x
- Knife4j / OpenAPI 3
- Hutool
- Maven Wrapper (`mvnw`)

## 核心功能

- 用户：注册、登录、个人信息、修改密码（JWT 鉴权）
- 项目：新增、查询、更新、排序、归档、软删除、30 天内恢复
- 任务：新增、查询、更新、软删除，支持优先级与状态管理
- 里程碑：新增、列表、更新、删除
- 周总结：当前周草稿、保存、更新、历史列表
- 数据看板：核心指标、项目排名、近 7 日趋势
- AI 能力：
  - 任务拆解草稿（正式流程：`/api/ai/breakdown/preview` → 查询草稿 → 确认或取消；`/api/ai/breakdown` 仅兼容旧版）
  - 今日任务排序推荐（`/api/ai/today-order/recommend`）
  - 周总结润色（`/api/ai/polish`）

## 默认配置

- 服务端口：`8123`
- 上下文路径：`/api`
- 健康检查：`GET /api/health`
- 接口文档：`http://localhost:8123/api/doc.html`

## 目录说明

```text
src/main/java/com/spt/learningmanage
├─ common        # 统一响应结构与工具
├─ config        # Spring 配置（MyBatis、Knife4j、JSON 等）
├─ controller    # REST API
├─ exception     # 错误码与全局异常处理
├─ interceptor   # 登录拦截器
├─ mapper        # MyBatis Plus Mapper
├─ model         # DTO / Entity / VO
├─ service       # 业务逻辑
└─ utils         # JWT、用户上下文等工具
```

## 本地开发启动

### 1. 环境准备

- JDK 17
- MySQL 8.x
- Maven 3.9+（可直接用 `mvnw`，无需本地安装 Maven）

### 2. 初始化数据库

1. 创建数据库：`learning_manage`
2. 按业务依赖顺序执行 `sql/` 目录下脚本。完整顺序、AI 相关表、已有数据库升级注意事项见 [后端运行与 AI 依赖使用说明](docs/backend-usage-guide.md)。

### 3. 配置环境

默认激活配置为 `dev`，对应文件：

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

关键配置项：

- `spring.datasource.*`（数据库连接）
- `ai.api-key`
- `ai.base-url`
- `ai.model` / `ai.breakdown-model` / `ai.polish-model` / `ai.fallback-model`
- `spring.data.redis.*`
- `rate-limit.ai.*`

建议通过环境变量覆盖敏感信息：

- `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`
- `ALIYUN_API_KEY`

### 4. 启动项目

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

Linux / macOS：

```bash
./mvnw spring-boot:run
```

指定环境：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## Docker 部署（含前后端 + MySQL）

仓库内提供了 `deploy/docker-compose.yml`，可一键部署：

```bash
cd deploy
docker compose up -d --build
```

默认端口映射：

- 前端（Nginx）：`8080`
- 后端：`8124 -> 8123`
- MySQL：`3307 -> 3306`

启动前请先在 `deploy/docker-compose.yml` 中替换占位符：

- `MYSQL_ROOT_PASSWORD`
- `SPRING_DATASOURCE_PASSWORD`
- `ALIYUN_API_KEY`

## 鉴权与调用约定

- 白名单接口：`/user/login`、`/user/register`、文档相关接口
- 其他接口默认需要登录
- 登录成功后，前端需在请求头携带：
  - `Authorization: Bearer <token>`（也兼容仅传 token）

## 统一响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

常见错误码：

- `40000`：参数错误
- `40100`：未登录
- `40101`：无权限
- `40400`：资源不存在
- `50000`：系统错误
- `50001`：操作失败
- `42900`：AI 请求过于频繁
- `30001`：AI 服务暂时不可用
- `30002`：AI 服务响应超时
- `30003`：AI 返回结果格式异常
- `30004`：AI 服务配置异常

## 快速验证

```bash
# 健康检查
curl http://localhost:8123/api/health
```

```bash
# 运行测试
./mvnw test
```

---

## 文档索引

- [后端运行与 AI 依赖使用说明](docs/backend-usage-guide.md)
- [AI 接口文档](docs/api/README.md)
- [AI 草稿接口文档](docs/api/ai-draft-api.md)
- [AI 调用记录接口文档](docs/api/ai-call-log-api.md)
- [Redis AI 限流模块文档](docs/redis-rate-limit-module.md)
- [AI 调用记录模块文档](docs/ai-call-log-module.md)
- [Sprint 3 AI 草稿联调验收记录](docs/sprint/Sprint3_AI草稿联调验收记录.md)

如需完整接口定义，优先以 Knife4j 文档页面和上述 API 文档为准：`/api/doc.html`。
