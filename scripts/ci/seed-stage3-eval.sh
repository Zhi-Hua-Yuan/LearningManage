#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/ci-common.sh
source "${script_dir}/lib/ci-common.sh"

ci_assert_ci_target
ci_assert_ci_app_identity
ci_require_command curl
ci_require_command node
ci_require_env STAGE3_API_BASE_URL
ci_require_env STAGE3_EVAL_PASSWORD
[[ "${DB_NAME}" == *_eval ]] || ci_fail "stage3_database_suffix_invalid"

base_url="${STAGE3_API_BASE_URL%/}"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

for actor in owner member outsider; do
    account="stage3${actor}"
    body="$(STAGE3_SEED_ACCOUNT="$account" STAGE3_SEED_USERNAME="Stage3 ${actor}" node -e '
      const password = process.env.STAGE3_EVAL_PASSWORD;
      process.stdout.write(JSON.stringify({account:process.env.STAGE3_SEED_ACCOUNT,username:process.env.STAGE3_SEED_USERNAME,password,confirmPassword:password}));
    ')"
    status="$(curl --silent --show-error --max-time 30 --output "$work_dir/${actor}.json" --write-out '%{http_code}' \
        --request POST --header 'Content-Type: application/json' --data "$body" "$base_url/user/register")"
    [[ "$status" =~ ^2[0-9][0-9]$ ]] || ci_fail "stage3_actor_registration_http_failure"
    node -e "const fs=require('fs');const body=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));if(body.code!==0)process.exit(1)" \
        "$work_dir/${actor}.json" || ci_fail "stage3_actor_registration_failure"
done

owner_id="$(ci_mysql_app --database="$DB_NAME" --execute="SELECT id FROM user WHERE account='stage3owner' AND is_delete=0;")"
member_id="$(ci_mysql_app --database="$DB_NAME" --execute="SELECT id FROM user WHERE account='stage3member' AND is_delete=0;")"
outsider_id="$(ci_mysql_app --database="$DB_NAME" --execute="SELECT id FROM user WHERE account='stage3outsider' AND is_delete=0;")"
for id in "$owner_id" "$member_id" "$outsider_id"; do
    [[ "$id" =~ ^[1-9][0-9]*$ ]] || ci_fail "stage3_actor_id_invalid"
done

ci_mysql_app --default-character-set=utf8mb4 --database="$DB_NAME" <<SQL
INSERT INTO team (id, name, description, owner_id, invite_code)
VALUES (910001, 'Stage3 合成团队', '仅用于隔离评测', ${owner_id}, 'STAGE3-EVAL-ONLY');
INSERT INTO team_member (id, team_id, user_id, role) VALUES
  (910011, 910001, ${owner_id}, 'OWNER'),
  (910012, 910001, ${member_id}, 'MEMBER');

INSERT INTO project (id, user_id, team_id, name, goal, status, order_no, progress, start_date, end_date) VALUES
  (920001, ${owner_id}, NULL, 'Stage3 个人清单', '验证个人场景', 0, 0, 40.00, '2026-09-01', '2026-12-31'),
  (920002, ${owner_id}, 910001, 'Stage3 团队清单', '验证团队场景', 0, 1, 35.00, '2026-09-01', '2026-12-31'),
  (920003, ${outsider_id}, NULL, 'Stage3 隔离清单', '验证越权隔离', 0, 0, 0.00, '2026-09-01', '2026-12-31'),
  (920004, ${owner_id}, NULL, 'Stage3 排序故障夹具', '隔离排序故障任务', 0, 2, 0.00, '2026-09-01', '2026-12-31'),
  (920005, ${owner_id}, NULL, 'Stage3 改名故障夹具', '隔离改名故障任务', 0, 3, 0.00, '2026-09-01', '2026-12-31');

INSERT INTO project (id, user_id, team_id, name, goal, status, order_no, progress, start_date, end_date)
SELECT 921000 + sequence.n, ${owner_id}, NULL,
       CONCAT('[[STAGE3_STUB:', sequence.fault, ']] 故障清单'), '仅用于故障注入', 0, 10 + sequence.n, 0.00,
       '2026-09-01', '2026-12-31'
FROM (
  SELECT 0 n, 'invalid-json' fault UNION ALL SELECT 1, 'empty' UNION ALL
  SELECT 2, 'missing-choices' UNION ALL SELECT 3, 'http-500' UNION ALL
  SELECT 4, 'timeout' UNION ALL SELECT 5, 'completed-id' UNION ALL
  SELECT 6, 'invalid-date' UNION ALL SELECT 7, 'missing-usage'
) sequence;

INSERT INTO task (id, project_id, user_id, title, description, status, priority, due_date, completed_at,
                  assignee_user_id, assigned_by_user_id, assigned_at) VALUES
  (930001, 920001, ${owner_id}, '实现鉴权接口', '完成权限验证', 0, 3, '2026-09-05', NULL, ${owner_id}, ${owner_id}, NOW()),
  (930002, 920001, ${owner_id}, '补充自动化测试', '覆盖异常分支', 0, 2, '2026-09-05', NULL, ${owner_id}, ${owner_id}, NOW()),
  (930003, 920001, ${owner_id}, '整理接口文档', '更新示例', 0, 1, '2026-09-05', NULL, ${owner_id}, ${owner_id}, NOW()),
  (930004, 920001, ${owner_id}, '准备演示数据', '匿名化数据', 0, 2, '2026-09-05', NULL, ${owner_id}, ${owner_id}, NOW()),
  (930005, 920001, ${owner_id}, '完成需求分析', '已完成事实', 2, 2, '2026-09-04', '2026-09-04 18:00:00', ${owner_id}, ${owner_id}, NOW()),
  (930006, 920001, ${owner_id}, '完成数据库设计', '已完成事实', 2, 3, '2026-09-05', '2026-09-05 17:00:00', ${owner_id}, ${owner_id}, NOW()),
  (930101, 920002, ${owner_id}, '团队接口联调', '团队任务', 0, 3, '2026-09-05', NULL, ${owner_id}, ${owner_id}, NOW()),
  (930102, 920002, ${owner_id}, '团队测试验收', '团队任务', 0, 2, '2026-09-05', NULL, ${owner_id}, ${owner_id}, NOW()),
  (930103, 920002, ${owner_id}, '团队风险复盘', '团队任务', 0, 1, '2026-09-05', NULL, ${owner_id}, ${owner_id}, NOW()),
  (930104, 920002, ${owner_id}, '团队发布准备', '团队任务', 0, 2, '2026-09-05', NULL, ${owner_id}, ${owner_id}, NOW()),
  (930105, 920002, ${owner_id}, '团队需求确认', '已完成团队任务', 2, 2, '2026-09-04', '2026-09-04 18:00:00', ${owner_id}, ${owner_id}, NOW()),
  (930201, 920003, ${outsider_id}, '不可访问任务', '权限负例', 0, 3, '2026-09-05', NULL, ${outsider_id}, ${outsider_id}, NOW());

INSERT INTO task (id, project_id, user_id, title, description, status, priority, due_date,
                  assignee_user_id, assigned_by_user_id, assigned_at)
SELECT 931000 + sequence.n, 920004, ${owner_id}, CONCAT('[[STAGE3_STUB:', sequence.fault, ']] 排序任务'),
       '排序故障注入', 0, 2, '2026-09-05', ${owner_id}, ${owner_id}, NOW()
FROM (
  SELECT 0 n, 'invalid-json' fault UNION ALL SELECT 1, 'empty' UNION ALL
  SELECT 2, 'missing-choices' UNION ALL SELECT 3, 'http-500' UNION ALL
  SELECT 4, 'timeout' UNION ALL SELECT 5, 'unknown-id' UNION ALL
  SELECT 6, 'duplicate-id' UNION ALL SELECT 7, 'missing-usage'
) sequence;

INSERT INTO task (id, project_id, user_id, title, description, status, priority, due_date,
                  assignee_user_id, assigned_by_user_id, assigned_at)
SELECT 932000 + sequence.n, 920005, ${owner_id}, CONCAT('[[STAGE3_STUB:', sequence.fault, ']] 改名任务'),
       '改名故障注入', 0, 2, '2026-09-05', ${owner_id}, ${owner_id}, NOW()
FROM (
  SELECT 0 n, 'invalid-json' fault UNION ALL SELECT 1, 'empty' UNION ALL
  SELECT 2, 'missing-choices' UNION ALL SELECT 3, 'http-429' UNION ALL
  SELECT 4, 'timeout' UNION ALL SELECT 5, 'unauthorized-id' UNION ALL
  SELECT 6, 'overlong-title' UNION ALL SELECT 7, 'missing-usage'
) sequence;

INSERT INTO task (id, project_id, user_id, title, description, status, priority, due_date,
                  assignee_user_id, assigned_by_user_id, assigned_at)
SELECT 933000 + sequence.n, 921000 + sequence.n, ${owner_id}, CONCAT('[[STAGE3_STUB:', sequence.fault, ']] 重排任务'),
       '重排故障注入', 0, 2, '2026-10-01', ${owner_id}, ${owner_id}, NOW()
FROM (
  SELECT 0 n, 'invalid-json' fault UNION ALL SELECT 1, 'empty' UNION ALL
  SELECT 2, 'missing-choices' UNION ALL SELECT 3, 'http-500' UNION ALL
  SELECT 4, 'timeout' UNION ALL SELECT 5, 'completed-id' UNION ALL
  SELECT 6, 'invalid-date' UNION ALL SELECT 7, 'missing-usage'
) sequence;
SQL

fixture_count="$(ci_mysql_app --database="$DB_NAME" --execute='SELECT COUNT(*) FROM task WHERE id BETWEEN 930001 AND 933007;')"
ci_assert_equals "36" "$fixture_count" "stage3_fixture_task_count_invalid"
ci_emit "stage3.seed.status" "PASS"
ci_emit "stage3.seed.tasks" "$fixture_count"
