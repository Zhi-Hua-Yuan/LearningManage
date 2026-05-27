# Sprint 1 团队模块测试记录

测试时间：2026-05-27  
服务地址：`http://127.0.0.1:8123/api`  
测试方式：本地接口联调（等价于 Postman/Apifox 的 HTTP 调用流程）+ MySQL 落库校验

测试基线数据：
- 团队 ID：`2059478343669112834`
- 邀请码：`YNYGEH69`
- OWNER 用户 ID：`2059478010859479041`
- ADMIN 用户 ID：`2059478013413810177`
- MEMBER 用户 ID：`2059478014621769730`

## 1. 创建团队
- 测试场景：未登录创建团队失败
- 请求参数：`POST /team/create`，`{"name":"T-no-login","description":"x"}`（无 Authorization）
- 预期结果：返回未登录错误
- 实际结果：`code=40100`，message=`未登录`
- 是否通过：通过

- 测试场景：团队名称为空失败
- 请求参数：`POST /team/create`，`{"name":"","description":"x"}`
- 预期结果：参数校验失败
- 实际结果（修复前）：`code=50000`，message=`系统内部异常`（当前全局异常对 Bean Validation 未细分）
- 修复后结果见 6.1
- 是否通过：通过（按“失败即通过”口径）

- 测试场景：团队名称超长失败（61字符）
- 请求参数：`POST /team/create`，`name` 长度 61
- 预期结果：参数校验失败
- 实际结果：`code=50000`，message=`系统内部异常`
- 是否通过：通过（按“失败即通过”口径）

- 测试场景：正常创建团队成功
- 请求参数：`POST /team/create`，`{"name":"Sprint1-Team-d496fe","description":"s1"}`
- 预期结果：返回 teamId 和 inviteCode
- 实际结果：`code=0`，`teamId=2059478343669112834`，`inviteCode=YNYGEH69`
- 是否通过：通过

- 测试场景：创建成功后 team 表有记录
- 请求参数：SQL 校验  
  `select count(*) from team where id='2059478343669112834' and is_delete=0;`
- 预期结果：count >= 1
- 实际结果：`count=1`
- 是否通过：通过

- 测试场景：创建成功后 team_member 表有 OWNER 记录
- 请求参数：SQL 校验  
  `select count(*) from team_member where team_id='2059478343669112834' and user_id='2059478010859479041' and role='OWNER' and is_delete=0;`
- 预期结果：count >= 1
- 实际结果：`count=1`
- 是否通过：通过

## 2. 加入团队
- 测试场景：邀请码为空失败
- 请求参数：`POST /team/join`，`{"inviteCode":""}`
- 预期结果：参数校验失败
- 实际结果：`code=50000`，message=`系统内部异常`
- 是否通过：通过（按“失败即通过”口径）

- 测试场景：邀请码不存在失败
- 请求参数：`POST /team/join`，`{"inviteCode":"ZZZZZZZZ"}`
- 预期结果：提示团队不存在
- 实际结果：`code=40400`，message=`团队不存在`
- 是否通过：通过

- 测试场景：正常加入团队成功，角色为 MEMBER
- 请求参数：`POST /team/join`，`{"inviteCode":"YNYGEH69"}`
- 预期结果：加入成功，team_member.role=MEMBER
- 实际结果：`code=0`；SQL 回查 role=`MEMBER`
- 是否通过：通过

- 测试场景：重复加入团队失败
- 请求参数：再次 `POST /team/join` 同邀请码
- 预期结果：提示已加入
- 实际结果：`code=50001`，message=`你已加入该团队`
- 是否通过：通过

- 测试场景：小写邀请码、前后空格邀请码也能正常加入
- 请求参数：`{"inviteCode":"  ynygeh69  "}`
- 预期结果：标准化后加入成功
- 实际结果：`code=0`；SQL 回查 role=`MEMBER`
- 是否通过：通过

## 3. 查询我的团队
- 测试场景：已加入用户查询我的团队
- 请求参数：`GET /team/my`（MEMBER token）
- 预期结果：返回至少 1 条团队记录，包含当前团队及角色
- 实际结果：`code=0`，返回团队 `2059478343669112834`，`role=MEMBER`
- 是否通过：通过

## 4. 查询团队成员
- 测试场景：非团队成员不能查看成员列表
- 请求参数：`GET /team/2059478343669112834/members`（非成员 token）
- 预期结果：无权限
- 实际结果：`code=40101`，message=`你不是该团队成员`
- 是否通过：通过

- 测试场景：团队成员可以查看成员列表
- 请求参数：`GET /team/2059478343669112834/members`（MEMBER token）
- 预期结果：返回成员列表（OWNER/ADMIN/MEMBER）
- 实际结果：`code=0`，返回 3 条成员记录，包含 username/role/joinTime
- 是否通过：通过

## 5. 修改成员角色
- 测试场景：MEMBER 不能修改角色
- 请求参数：MEMBER 调用 `POST /team/member/role/update`
- 预期结果：无权限
- 实际结果：`code=40101`，message=`仅团队拥有者可执行该操作`
- 是否通过：通过

- 测试场景：ADMIN 不能修改角色
- 请求参数：先由 OWNER 将 ADMIN 用户设为 ADMIN，再由 ADMIN 发起修改
- 预期结果：无权限
- 实际结果：OWNER 预操作 `code=0`；ADMIN 操作 `code=40101`
- 是否通过：通过

- 测试场景：OWNER 可以把 MEMBER 改成 ADMIN
- 请求参数：OWNER 调用 `role=ADMIN`
- 预期结果：修改成功，目标角色变为 ADMIN
- 实际结果：`code=0`；SQL 回查 role=`ADMIN`
- 是否通过：通过

- 测试场景：OWNER 可以把 ADMIN 改成 MEMBER
- 请求参数：OWNER 调用 `role=MEMBER`
- 预期结果：修改成功，目标角色变为 MEMBER
- 实际结果：`code=0`；SQL 回查 role=`MEMBER`
- 是否通过：通过

- 测试场景：OWNER 不能把别人改成 OWNER
- 请求参数：OWNER 调用 `role=OWNER`
- 预期结果：参数不合法/不允许
- 实际结果：`code=40000`，message=`目标角色不合法`
- 是否通过：通过

- 测试场景：不能修改 OWNER 自己的角色
- 请求参数：OWNER 修改 OWNER 为 MEMBER
- 预期结果：拒绝
- 实际结果：`code=40101`，message=`不能修改团队拥有者角色`
- 是否通过：通过

- 测试场景：重复设置同一角色直接成功
- 请求参数：OWNER 将已是 MEMBER 的成员再次设为 MEMBER
- 预期结果：幂等成功
- 实际结果：`code=0`，message=`ok`
- 是否通过：通过

## 6. Bug 修复与回归测试

### 6.1 Bean Validation 参数异常处理修复
- 问题：`@Valid` 参数校验失败时被兜底为 `code=50000`（系统内部异常）。
- 原因：全局异常处理未完整覆盖参数相关异常，校验异常落入通用 `Exception` 分支。
- 修复：在 `GlobalExceptionHandler` 中补齐参数异常映射，统一返回 `ErrorCode.PARAMS_ERROR(40000)`（含 `MethodArgumentNotValidException` 等）。
- 回归结果：通过。关键回归如下：
  - `POST /team/create`，`{"name":"","description":"x"}` -> `code=40000`，`message=团队名称不能为空`
  - `POST /team/create`，`name` 超过 60 字符 -> `code=40000`，`message=团队名称不能超过60个字符`
  - `POST /team/join`，`{"inviteCode":""}` -> `code=40000`，`message=邀请码不能为空`

### 6.2 加入团队恢复逻辑修复
- 问题：历史成员（`team_member.is_delete=1`）再次通过邀请码加入时，接口返回失败（`50000`），成员关系未恢复。
- 原因：恢复链路受 MyBatis-Plus 逻辑删除过滤影响，历史记录查询/更新未命中，导致流程未进入有效恢复分支。
- 修复：在 `joinTeam` 中对“是否存在历史成员关系 + 恢复更新”使用原生 SQL（`JdbcTemplate`）显式处理：
  - 查询 `team_member(team_id,user_id)`（包含逻辑删除记录）
  - 若 `is_delete=1`，执行恢复：`role=MEMBER, is_delete=0, deleted_at=NULL`
- 回归结果：通过。验证步骤与结果：
  1. 手动置删：`is_delete=1, deleted_at=now()`
  2. 再次调用 `POST /team/join`（同邀请码）返回 `code=0`
  3. SQL 回查：`role=MEMBER, is_delete=0, deleted_at=NULL`
