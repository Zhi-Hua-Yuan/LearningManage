# 模块需求说明模板

## 1. 模块信息
- 模块名称：Project
- 负责人：宋小通
- 计划分支：`feature/<Project>`
- 预计周期：2 周
- 关联需求/任务链接：无

## 2. 背景与目标
### 2.1 背景
学生可以定义本学期的目标，并且随时可以修改。

### 2.2 目标
- 创建/编辑/归档个人项目
- 定义项目目标、周期、优先级
- 作为里程碑与任务的顶层容器

### 2.3 非目标
- 多人协作（共享项目、多人任务分配、团队看板等）

## 3. 用户场景
- 场景 1：学生登录系统后，创建新项目，设置项目名称、目标、开始和结束日期。
- 场景 2：学生查看项目列表，分页浏览所有项目。
- 场景 3：学生编辑现有项目，修改目标或日期。
- 场景 4：学生归档已完成的项目。
- 场景 5：学生删除不需要的项目。

## 4. 功能需求
1. 创建一个目标
2. 查询一个目标
3. 分页查询多个目标
4. 归档一个/多个目标
5. 删除一个目标
6. 修改一个目标

## 5. 接口与数据
### 5.1 新增/修改接口
- 方法 + 路径：POST /api/project/add
- 入参：ProjectCreateRequest
  - name 项目名称（必填）
  - goal 项目目标（可选）
  - startDate 开始日期（可选）
  - endDate 结束日期（可选）
- 出参：BaseResponse<Long>（返回项目ID）
- 错误码：PROJECT_NAME_EMPTY (项目名称不能为空), PROJECT_ALREADY_EXISTS (项目已存在)

### 5.2 查询接口
- 方法 + 路径：GET /api/project/get/{id}
- 入参：路径参数 id (项目ID)
- 出参：BaseResponse<ProjectVO>
- 错误码：PROJECT_NOT_FOUND (项目不存在)

### 5.3 分页查询接口
- 方法 + 路径：GET /api/project/list
- 入参：查询参数 pageNum, pageSize, keyword (可选搜索关键词)
- 出参：BaseResponse<Page<ProjectVO>>
- 错误码：无

### 5.4 归档接口
- 方法 + 路径：POST /api/project/archive
- 入参：List<Long> projectIds
- 出参：BaseResponse<Boolean>
- 错误码：PROJECT_NOT_FOUND (项目不存在)

### 5.5 删除接口
- 方法 + 路径：POST /api/project/delete/{id}
- 入参：路径参数 id (项目ID)
- 出参：BaseResponse<Boolean>
- 错误码：PROJECT_NOT_FOUND (项目不存在)

### 5.6 修改接口
- 方法 + 路径：POST /api/project/update
- 入参：ProjectUpdateRequest (类似 ProjectCreateRequest，但包含 id)
- 出参：BaseResponse<Boolean>
- 错误码：PROJECT_NOT_FOUND (项目不存在), PROJECT_NAME_EMPTY (项目名称不能为空)

### 5.2 数据库变更
- 表名：project
- 变更类型（新增字段/索引/表）：新增表
- 迁移方案：使用 Flyway 或 Liquibase 执行 SQL 脚本创建表
- 回滚方案：DROP TABLE project

## 6. 约束条件
- 技术约束：使用 Spring Boot 3.5.10, MyBatis Plus, MySQL 8.0
- 性能约束：分页查询响应时间 < 500ms, 支持并发创建项目
- 安全约束：用户只能操作自己的项目，通过用户ID过滤
- 兼容性约束：兼容现有用户认证系统

## 7. 验收标准（必须可测试）
1. Given 用户登录 When 创建项目 with 有效数据 Then 项目创建成功，返回项目ID
2. Given 项目存在 When 查询项目 by ID Then 返回项目详情
3. Given 多个项目存在 When 分页查询 Then 返回分页结果
4. Given 项目存在 When 归档项目 Then 项目状态更新为已归档
5. Given 项目存在 When 删除项目 Then 项目从数据库删除
6. Given 项目存在 When 修改项目 with 有效数据 Then 项目信息更新

## 8. 风险与预案
- 风险 1：数据库迁移失败
- 影响：系统无法启动
- 预案：备份数据库，手动执行回滚脚本

## 9. 发布与回滚
- 发布步骤：
  1. 代码合并到主分支
  2. 执行自动化测试
  3. 部署到测试环境验证
  4. 部署到生产环境
- 监控指标：API 响应时间、错误率、数据库连接数
- 回滚触发条件：生产环境错误率 > 5%
- 回滚步骤：
  1. 停止新版本服务
  2. 回滚代码到上一版本
  3. 重启服务
  4. 验证功能正常