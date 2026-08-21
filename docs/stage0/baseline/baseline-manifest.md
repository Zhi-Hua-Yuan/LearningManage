# Baseline Manifest

```text
baseline_id=stage0-baseline-20260817-001
executed_at=2026-08-17 Asia/Shanghai
backend_branch=develop
backend_commit=7a0661886f28f4b51319fee3fcb50972cd393af7
frontend_branch=develop
frontend_commit=b06a115cfaa8413ff2f466dfe021cd4f493aaced
```

## Backend

```text
java=17.0.12
maven=3.9.12
spring_boot=3.3.6
mysql_server=8.0.41
```

```text
test_command=.\mvnw.cmd test
test_result=PASS
test_count=56
test_failures=0
test_errors=0
test_duration_seconds=34.117

package_command=.\mvnw.cmd package -DskipTests
package_result=PASS
jar=target/LearningManage-0.0.1-SNAPSHOT.jar
jar_size_bytes=51933926
jar_sha256=328b94928cbe75e377e3aa1001a03fa9a9d5dfb5713fc6c34651a736cb936328
```

## Frontend

```text
node=22.13.1
npm=10.9.2
npm_ci=PASS
npm_ci_packages_added=318
type_check=PASS
vite_build=PASS
vite_version_after_clean_install=7.3.2
vue_tsc_version_after_clean_install=3.2.6
vite_modules_transformed=714
vite_build_duration_seconds=4.49
frontend_test_script=ABSENT
```

## API

```text
health_url=http://127.0.0.1:8123/api/health
health_status=200
health_body_sha256=2be1ab8fc1d7e23f0d2f7a94343f5f97667935c345fd6169427cb5ee61605434
openapi_status=200
openapi_length_bytes=63663
2ad2526a2f2c16aa2d3da716b326576960739a0f323a78675b2837c51a31b31f  openapi-document.json
knife4j_status=200
```

## Database backup

```text
database=learning_manage
backup_dir=D:\ajavacode\LearningManage\.codex-tmp\stage0-baseline-20260817
full_dump_size_bytes=133090
full_dump_sha256=7e8e109405960ac1cea76e1465041ee8fd68f50d40e2cc916ea752285e8d616c
schema_dump_size_bytes=27971
schema_dump_sha256=805d0ffd9caad67417a1ece1859c33fbea7f6203c3cfcaee2aa57fab456b66f9
restore_database=learning_manage_baseline_restore_20260817
restore_table_count=20
```

## Repository findings

```text
backend_github_workflows=ABSENT
frontend_github_workflows=ABSENT
gitleaks=NOT_INSTALLED
docker_daemon=UNAVAILABLE_AT_BASELINE_TIME
```
