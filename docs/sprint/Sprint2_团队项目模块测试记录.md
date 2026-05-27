# Sprint 2 团队项目模块测试记录

> 说明：项目实际路由为  
> 个人创建：`POST /project/add`  
> 个人详情：`GET /project/get/{id}`  
> （与清单中的 `/project/create`、`/project/{id}` 语义一致）

## 一、测试准备

- OWNER：`S2_OWNER`（id=`2059542029876191233`）
- ADMIN：`S2_ADMIN`（id=`2059542032044646401`）
- MEMBER：`S2_MEMBER`（id=`2059542033349074945`）
- OUTSIDER：`S2_OUTSIDER`（id=`2059542034674475009`）

- 团队A：`teamId=2059542115523878913`
- 团队B：`teamId=2059542568953307138`（用于跨团队隔离测试）

团队A成员关系：
- OWNER = OWNER
- ADMIN = ADMIN
- MEMBER = MEMBER
- OUTSIDER 不在团队中

---

## 二、数据库结构检查

### 测试场景 1.1：project 表存在 team_id 字段
- 请求参数：`DESC project;`
- 预期结果：存在 `team_id BIGINT`，允许 `NULL`
- 实际结果：存在 `team_id bigint YES`
- SQL 回查：通过
- 是否通过：通过

### 测试场景 1.2：历史个人项目 team_id 为 NULL
- 请求参数：
```sql
select id,user_id,team_id,name from project where team_id is null limit 5;
```
- 预期结果：个人项目 `team_id = null`
- 实际结果：符合预期
- SQL 回查：通过
- 是否通过：通过

---

## 三、旧个人项目接口回归测试

### 测试场景 2.1：创建个人项目
- 请求参数：`POST /project/add`（OWNER token）
```json
{
  "name": "个人项目-Sprint2回归",
  "goal": "测试个人项目不受团队项目影响",
  "icon": "📌",
  "color": "#409EFF",
  "startDate": "2026-05-27",
  "endDate": "2026-06-10"
}
```
- 预期结果：`code=0`，返回 projectId，且 `team_id=null`
- 实际结果：`code=0`，`projectId=2059542419669639170`
- SQL 回查：`user_id=OWNER`，`team_id=null`
- 是否通过：通过

### 测试场景 2.2：个人项目列表不包含团队项目
- 请求参数：`GET /project/list?pageNum=1&pageSize=100`
- 预期结果：不包含 `team_id!=null` 项目
- 实际结果：不包含（containsTeamProject=False）
- SQL 回查：通过
- 是否通过：通过

### 测试场景 2.3：个人详情不能查询团队项目
- 请求参数：`GET /project/get/{团队项目ID}`
- 预期结果：失败（项目不存在/无权限）
- 实际结果：`code=10003`
- SQL 回查：团队项目未受影响
- 是否通过：通过

### 测试场景 2.4：个人更新不能修改团队项目
- 请求参数：`POST /project/update`（id=团队项目ID）
- 预期结果：失败
- 实际结果：`code=10003`
- SQL 回查：团队项目内容未变化
- 是否通过：通过

### 测试场景 2.5：个人删除不能删除团队项目
- 请求参数：`POST /project/delete/{团队项目ID}`
- 预期结果：失败
- 实际结果：`code=10003`
- SQL 回查：`deleted_at` 仍为 `null`
- 是否通过：通过

### 测试场景 2.6：个人归档不能归档团队项目
- 请求参数：`POST /project/archive`，body=`[团队项目ID]`
- 预期结果：失败
- 实际结果：`code=10003`
- SQL 回查：状态未变化
- 是否通过：通过

### 测试场景 2.7：个人恢复不能恢复团队项目
- 请求参数：`POST /project/recover/{团队项目ID}`
- 预期结果：失败
- 实际结果：`code=10003`
- SQL 回查：团队项目未变化
- 是否通过：通过

### 测试场景 2.8：个人排序不能影响团队项目
- 请求参数：`POST /project/reorder`
```json
[
  { "id": 2059542420302979073, "orderNo": 999 }
]
```
- 预期结果：失败
- 实际结果：`code=10003`
- SQL 回查：团队项目 `order_no` 未变化
- 是否通过：通过

---

## 四、创建团队项目测试

### 测试场景 3.1：未登录不能创建团队项目
- 请求参数：`POST /project/team/create`（无 token）
- 预期结果：未登录错误
- 实际结果：`code=40100`
- 是否通过：通过

### 测试场景 3.2：teamId 为空失败
- 请求参数：
```json
{ "name": "团队项目-teamId为空", "goal": "x" }
```
- 预期结果：参数错误
- 实际结果：`code=40000`
- 是否通过：通过

### 测试场景 3.3：teamId 不存在失败
- 请求参数：`teamId=999999999`
- 预期结果：团队不存在
- 实际结果：`code=40400`
- 是否通过：通过

### 测试场景 3.4：非团队成员不能创建
- 请求参数：OUTSIDER token
- 预期结果：无权限
- 实际结果：`code=40101`
- 是否通过：通过

### 测试场景 3.5：MEMBER 不能创建
- 请求参数：MEMBER token
- 预期结果：无权限
- 实际结果：`code=40101`
- 是否通过：通过

### 测试场景 3.6：OWNER 可以创建
- 请求参数：OWNER token
- 实际结果：`code=0`，`projectId=2059542420302979073`
- SQL 回查：`user_id=OWNER`，`team_id=teamA`，`deleted_at=null`，`order_no=0`
- 是否通过：通过

### 测试场景 3.7：ADMIN 可以创建
- 请求参数：ADMIN token
- 实际结果：`code=0`，`projectId=2059542420445585409`
- SQL 回查：`user_id=ADMIN`，`team_id=teamA`，`deleted_at=null`，`order_no=1`
- 是否通过：通过

### 测试场景 3.8：团队项目名称为空失败
- 请求参数：`name=""`
- 预期结果：参数错误
- 实际结果：`code=10001`（项目名称不能为空）
- 说明：该错误码来自项目模块原有参数校验逻辑，语义为项目名称不能为空。
- 是否通过：通过

### 测试场景 3.9：团队项目日期范围非法失败
- 请求参数：`startDate > endDate`
- 预期结果：参数错误
- 实际结果：`code=40000`
- 是否通过：通过

---

## 五、查询团队项目列表测试

### 测试场景 4.1：未登录不能查询
- 请求参数：`GET /project/team/list?...`（无 token）
- 预期结果：未登录错误
- 实际结果：`code=40100`
- 是否通过：通过

### 测试场景 4.2：teamId 不存在失败
- 请求参数：`teamId=999999999`
- 预期结果：团队不存在
- 实际结果：`code=40400`
- 是否通过：通过

### 测试场景 4.3：非团队成员不能查询
- 请求参数：OUTSIDER token
- 预期结果：无权限
- 实际结果：`code=40101`
- 是否通过：通过

### 测试场景 4.4：OWNER 可以查询
- 请求参数：OWNER token
- 实际结果：`code=0`
- 是否通过：通过

### 测试场景 4.5：ADMIN 可以查询
- 请求参数：ADMIN token
- 实际结果：`code=0`
- 是否通过：通过

### 测试场景 4.6：MEMBER 可以查询
- 请求参数：MEMBER token
- 实际结果：`code=0`
- 是否通过：通过

### 测试场景 4.7：查询结果只包含当前 teamId 项目
- 步骤：在 teamB 新建团队项目后，查询 teamA 列表
- 预期结果：不包含 teamB 项目
- 实际结果：`containsTeamBProject=false`
- SQL 回查：通过
- 是否通过：通过

### 测试场景 4.8：keyword 只按名称模糊搜索
- 请求参数：`keyword=OWNER`
- 预期结果：命中 name 包含 OWNER 的项目
- 实际结果：`code=0`，命中 1 条
- 是否通过：通过

### 测试场景 4.9：status 条件过滤
- 请求参数1：`status=1`
- 实际结果：`code=0`，当前数据下返回 0 条（符合过滤语义）
- 请求参数2：`status=999`
- 实际结果：`code=40000`
- 是否通过：通过

### 测试场景 4.10：分页参数兜底
- 请求参数：`pageNum=0&pageSize=0`
- 实际结果：`current=1,size=10`
- 请求参数：`pageSize=999`
- 实际结果：`size=100`
- 是否通过：通过

---

## 六、团队项目 orderNo 测试

### 测试场景 5.1：同一团队下 orderNo 递增
- SQL 回查：teamA 下项目 order_no 为 `0,1`
- 是否通过：通过

### 测试场景 5.2：不同团队 orderNo 独立
- SQL 回查：teamA 从 `0` 开始；teamB 也从 `0` 开始
- 是否通过：通过

### 测试场景 5.3：个人项目与团队项目 orderNo 互不影响
- SQL 回查：同一用户下，个人项目 `team_id=null`、团队项目 `team_id!=null`，排序空间独立
- 是否通过：通过

---

## 七、最终结论（Sprint 2 验收）

- 10 条验收标准全部满足，Sprint 2 当前实现通过：
1. OWNER/ADMIN 可创建团队项目：通过
2. MEMBER/OUTSIDER 不能创建：通过
3. OWNER/ADMIN/MEMBER 可查询团队项目：通过
4. OUTSIDER 不能查询：通过
5. `project.team_id` 落库正确：通过
6. `project.user_id` 表示创建人：通过
7. 团队项目不出现在个人项目列表：通过
8. 个人接口不能操作团队项目：通过
9. 团队项目 orderNo 按 teamId 递增：通过
10. 个人与团队 orderNo 互不影响：通过