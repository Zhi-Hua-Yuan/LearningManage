# LearningManage 项目文档

## 📋 项目概述

**LearningManage** 是一个基于 Spring Boot 3.3.6 的学习管理系统后端服务，提供项目管理、任务跟踪、周总结和 AI 辅助功能。系统采用模块化架构，集成了 MyBatis Plus ORM、MySQL 数据库和阿里云大模型 AI 服务。

## 🏗️ 技术架构

### 核心技术栈
- **Java**: JDK 17
- **框架**: Spring Boot 3.3.6
- **ORM**: MyBatis Plus 3.5.7
- **数据库**: MySQL 8.x
- **API 文档**: Knife4j OpenAPI 3
- **构建工具**: Maven 3.9+
- **AI 集成**: 阿里云 Qwen 大模型
- **工具库**: Hutool 5.8.38

### 项目结构
```
src/main/java/com/spt/learningmanage/
├── LearningManageApplication.java      # 应用启动入口
├── common/                             # 通用响应类和工具
├── config/                             # 配置类
├── constant/                           # 常量定义
├── controller/                         # REST 控制器层
├── exception/                          # 异常处理
├── interceptor/                        # Spring 拦截器
├── mapper/                             # MyBatis Plus 映射接口
├── model/                              # DTO 和 VO 对象
├── service/                            # 业务服务层
└── utils/                              # 工具类
```

## 🎯 核心功能模块

### 1. 项目管理模块
**功能**: 项目的增删改查、归档和恢复

**主要接口**:
- `POST /api/project/add` - 创建项目
- `GET /api/project/get/{id}` - 获取项目详情
- `GET /api/project/list` - 分页获取项目列表
- `POST /api/project/update` - 更新项目
- `POST /api/project/archive` - 归档项目
- `POST /api/project/delete/{id}` - 删除项目
- `POST /api/project/recover/{id}` - 恢复项目

**数据结构**:
```sql
CREATE TABLE `project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `name` VARCHAR(100) NOT NULL COMMENT '项目名称',
    `goal` VARCHAR(500) DEFAULT NULL COMMENT '项目目标',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0进行中, 1已归档',
    `start_date` DATE DEFAULT NULL,
    `end_date` DATE DEFAULT NULL,
    `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`)
);
```

### 2. 任务管理模块
**功能**: 任务的增删改查，支持按项目、状态、逾期状态筛选

**主要接口**:
- `POST /api/task/add` - 创建任务
- `GET /api/task/get/{id}` - 获取任务详情
- `GET /api/task/list` - 分页获取任务列表
- `POST /api/task/update` - 更新任务
- `POST /api/task/delete/{id}` - 删除任务

**任务状态**: 0-待办, 1-进行中, 2-已完成

**数据结构**:
```sql
CREATE TABLE `task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL COMMENT '项目ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `title` VARCHAR(100) NOT NULL COMMENT '任务标题',
    `description` VARCHAR(500) DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `priority` TINYINT NOT NULL DEFAULT 0,
    `due_date` DATE DEFAULT NULL COMMENT '截止时间',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `is_delete` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);
```

### 3. 里程碑管理模块
**功能**: 里程碑的增删改查、归档和恢复

**主要实体**: Milestone 包含里程碑基本信息和任务列表

### 4. 周总结模块
**功能**: 周总结的创建、更新、删除和历史查看

**主要接口**:
- `GET /api/review/current` - 获取当前周总结草稿
- `POST /api/review/save` - 保存或更新周总结
- `GET /api/review/{id}` - 获取周总结详情
- `POST /api/review/update` - 更新周总结
- `POST /api/review/delete/{id}` - 删除周总结
- `GET /api/review/history` - 获取历史周总结列表

**数据结构**:
```sql
CREATE TABLE `weekly_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `year` INT NOT NULL,
    `week_no` INT NOT NULL,
    `start_date` DATE NOT NULL,
    `end_date` DATE NOT NULL,
    `completed_task_count` INT DEFAULT 0,
    `focus_project_name` VARCHAR(100),
    `reflection` TEXT,
    `next_plan` TEXT,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);
```

### 5. AI 辅助模块
**功能**: 集成阿里云大模型，提供任务拆解和周总结润色功能

**主要接口**:
- `POST /api/ai/breakdown` - 根据目标、描述和周期生成里程碑与任务草稿
- `POST /api/ai/polish` - 根据任务完成数、核心项目和反思生成润色文本

**AI 配置** (application.yml):
```yaml
ai:
  api-key: ${ALIYUN_API_KEY:please_set_your_api_key_in_env}
  base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
  model: qwen-plus
```

### 6. 数据大屏模块
**功能**: 提供项目、任务等数据的统计概览

**主要接口**:
- `GET /api/stats/overview` - 获取系统数据概览

**返回数据**: 包含核心指标、项目排名、每日趋势等

## 🔧 配置说明

### 数据库配置
SQL 初始化脚本位于 `sql/` 目录:
- `init_user.sql` - 用户表
- `init_project.sql` - 项目表
- `init_milestone.sql` - 里程碑表
- `init_task.sql` - 任务表
- `init_weekly_review.sql` - 周总结表

### 环境配置
- `application.yml` - 主配置文件
- `application-dev.yml` - 开发环境配置
- `application-test.yml` - 测试环境配置
- `application-prod.yml` - 生产环境配置

### MyBatis Plus 配置
```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: isDelete
      logic-delete-value: 1
      logic-not-delete-value: 0
```

## 🚀 运行指南

### 启动应用
```bash
# Linux/Mac
./mvnw spring-boot:run

# Windows
.\mvnw.cmd spring-boot:run
```

### 访问接口文档
- **基础 URL**: `http://localhost:8123/api`
- **Knife4j 文档**: `http://localhost:8123/api/doc.html`
- **健康检查**: `GET http://localhost:8123/api/health`

### 测试
```bash
# 运行测试
./mvnw test

# 使用测试环境配置
./mvnw test -Dspring-boot.run.profiles=test
```

## 📊 API 响应格式

所有 API 响应遵循统一格式:

```json
{
  "code": 0,
  "message": "OK",
  "data": {}
}
```

### 错误码定义
- `0`: 成功
- `PARAMS_ERROR`: 请求参数错误
- `PROJECT_NAME_EMPTY`: 项目名称不能为空
- `PROJECT_ALREADY_EXISTS`: 项目已存在
- `PROJECT_NOT_FOUND`: 项目不存在

## 🔒 安全与权限

### 用户认证
- 使用 JWT (JSON Web Token) 进行用户认证
- `LoginInterceptor` 处理登录验证
- `UserHolder` 持有当前用户信息

### 数据隔离
- 所有数据查询按用户 ID 隔离
- 确保用户只能访问自己的数据

## 📈 开发规范

### 服务层模式
所有服务遵循统一模式:
- `create()`: 创建实体，返回 ID
- `getById()`: 根据 ID 获取实体
- `list()`: 分页列表查询
- `update()`: 更新实体
- `delete()`: 逻辑删除
- `archive()`: 归档实体
- `recover()`: 恢复归档实体

### 数据库规范
- 所有表使用 `is_delete` 进行逻辑删除
- 使用 camelCase 命名规范
- 所有表包含 `create_time` 和 `update_time`

### 代码规范
- 使用 Lombok 简化代码
- 统一异常处理 (`GlobalExceptionHandler`)
- 统一的响应封装 (`ResultUtils`)

## 🤝 贡献指南

### 开发流程
1. 定义模块需求和验收标准
2. 拆解需求为可执行任务
3. 按优先级顺序执行任务
4. 代码和设计审查
5. 完成文档和交接

### PR 提交
使用 `.github/PULL_REQUEST_TEMPLATE.md` 模板，包含:
- 需求文档链接
- TODO 文档链接
- 主要变更
- 验证结果
- 风险和回滚说明

## 📝 完成标准 (DoD)

- [ ] 代码实现完成且可编译
- [ ] 核心路径测试通过
- [ ] 边界情况验证
- [ ] 响应结构和错误码符合规范
- [ ] 需求/TODO/审查/交接文档完成

## 📚 相关资源

- **项目主页**: [LearningManage](https://github.com/spt/learningmanage)
- **API 文档**: Knife4j UI
- **开发规范**: CLAUDE.md
- **问题跟踪**: GitHub Issues

---

*文档最后更新: 2026-04-10*