# PR6-B2 Docker运行契约记录

执行日期：2026-08-21（Asia/Shanghai）  
执行状态：静态与配置验证通过；真实Docker build待GitHub Runner执行

## 1. 执行范围

本次完成PR6-B2的Docker运行契约，不创建GitHub Actions工作流，不连接或修改任何数据库。

已完成：

- Dockerfile改为可覆盖的JRE运行时镜像参数；
- 只复制唯一、已测试的生产JAR；
- 容器使用非root用户；
- 默认profile通过环境变量设置；
- 新增隔离的 `deploy/docker-compose.ci.yml`；
- 新增Docker静态契约测试；
- 将Docker关键边界接入CI静态保护；
- 为Dockerfile和CI Compose固定LF行尾。

## 2. Docker边界

CI Compose只允许使用：

```text
MySQL临时服务：127.0.0.1:13306 -> 3306
后端临时服务：127.0.0.1:18123 -> 8123
CI数据库名：learning_manage_ci_*
应用启动：FLYWAY_ENABLED=false
```

Compose不挂载生产数据卷，不包含前端、不包含正式账号，也不包含数据库迁移或清理命令。

## 3. 本地验证

| 检查项 | 结果 |
|---|---|
| Maven全量测试 | 81项通过，0失败，0错误 |
| Maven跳过测试打包 | 成功 |
| Docker静态契约测试 | 2项通过 |
| Flyway CI脚本静态测试 | 6项通过 |
| Compose配置解析 | 通过 |
| `git diff --check` | 通过 |
| 数据库连接或修改 | 未执行 |
| 3306主库 | 未连接 |
| Bash脚本自检 | 当前Windows Bash/WSL被系统拒绝启动 |
| Docker build/启动 | Docker Desktop Linux daemon未启动 |

Windows Bash/WSL拒绝启动和Docker daemon不可用均未通过放宽安全边界或连接其他数据库来绕过；对应真实执行留给PR6-B3的GitHub Linux Runner。

## 4. 下一步

进入PR6-B3：创建后端GitHub Actions，接入Guard、Maven测试、Flyway空库、Flyway存量库和Docker Gate，并使用本记录中的CI Compose契约进行真实容器启动验证。
