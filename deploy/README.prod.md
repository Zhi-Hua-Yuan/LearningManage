# LearningManage production deployment assets

These files implement the low-resource, single-node production topology. They
do not change the public API, DTOs, or the V1-V8 database schema.

## Immutable inputs

Only artifacts downloaded from one successful cross-repository Release Gate
may be deployed. The trusted workstation must have:

- the tested backend JAR directory, including `backend.jar.sha256`;
- the tested frontend artifact directory, including `dist/` and `dist.sha256`;
- the schema-v4 passing `release-candidate-manifest.json`;
- the schema-v1 passing `production-release-manifest.json` from the same Gate;
- every external image in that manifest verified against the current successful
  Stage 7 Gate.

Assemble the bundle on the trusted workstation:

```bash
scripts/prod/assemble-release-bundle.sh \
  /path/to/backend-artifact \
  /path/to/frontend-artifact \
  /path/to/release-candidate-manifest.json \
  /path/to/release-evidence \
  /path/to/production-release-manifest.json \
  /path/to/output
```

The output directory is named `<BACKEND_SHA>-<FRONTEND_SHA>`. Upload it without
changing any file to `/opt/learning-manage/releases/`.
The assembler exports only an explicit production allowlist from the immutable
backend Git SHA; it refuses uncommitted files under deployment, production
script, or migration paths and never copies the legacy Compose or stale dist.

## Protected server configuration

Create these files outside every release directory:

```text
/etc/learning-manage/learning.env
/etc/learning-manage/observability.env
/etc/learning-manage/backup.env
/etc/learning-manage/s3cfg
/etc/learning-manage/secrets/mysql-root-password
/etc/learning-manage/secrets/grafana-admin-password
```

Use `root:lmdeploy` ownership and mode `0640` for environment/configuration
files. Secret value files should use `lmdeploy:lmdeploy` and mode `0600` so the
Compose client can read and mount them without running the deployment as root. Generate independent random
values for the database accounts, Redis, JWT, RAG HMAC, Qdrant, and Grafana.
Database passwords are restricted to 32-128 URL-safe characters so the guarded
account-provisioning script can quote them safely.
`verify-production-secrets.sh` runs before any first-deployment database
operation. It rejects template values, short or whitespace-containing secrets,
and reuse among the root, application, migrator, Redis, JWT, RAG HMAC, Qdrant,
and Grafana credentials.

On a new host, run `initialize-host.sh` as root with a file containing exactly
one deployment public key. The script installs the required packages, creates
the fixed directories, and enables unattended security upgrades without an
automatic reboot. Open and verify a separate `lmdeploy` SSH session, then run
`harden-ssh.sh --lmdeploy-login-verified` as root to disable root and password
SSH login. Do not close the original session until the second login works.
The trusted deployment account is explicitly passwordless-sudo enabled because
Docker-group membership is already root-equivalent; this keeps later Certbot,
UFW, Nginx, and systemd administration direct and auditable.

`migration.env` is temporary. Create it immediately before the migration
window from `migration.env.example`; the production migration script removes
the fixed production path on exit.

## First deployment before filing completion

Keep both cloud firewall and UFW restricted to SSH. From the uploaded release:

```bash
scripts/prod/verify-release-bundle.sh
scripts/prod/deploy.sh --initialize-database
scripts/prod/smoke-test.sh
scripts/prod/verify-host.sh
```

Use an SSH tunnel for acceptance:

```text
local 18080 -> server 127.0.0.1:18080
```

The first deployment performs only Phase A. Ordinary AI and task breakdown
remain available; Knowledge Worker, RAG, Agent, Tool Calling, Cleanup, and
Cleanup scheduling remain disabled. `deploy.sh` refuses to proceed unless all
seven higher-risk feature flags are explicitly false, including on later
application releases.
Before `current` changes, the deployment script runs the complete Phase A smoke
suite. Release Gate also runs an isolated full production Compose stack using
the production MySQL settings, Redis ACL, Qdrant API key, Backend, Frontend, and
deterministic AI stub.

## Controlled feature phases

Change only the listed feature flags, atomically replace `learning.env`, and
recreate only Backend after each approved phase:

1. Phase B: set `AI_KNOWLEDGE_WORKER_ENABLED=true`. Require V6 REBUILD success,
   backlog 0, DEAD 0, 1024-dimensional vectors, and the expected alias.
2. Phase C: set `AI_RAG_ENABLED=true`, retaining
   `AI_RAG_REQUIRE_BACKFILL=true`. Verify citation, authorization, STALE,
   rerank degradation, and fail-closed behavior.
3. Phase D1: set `AI_AGENT_ENABLED=true` and
   `AI_AGENT_WORKER_ENABLED=true`; keep Tool Calling false. Verify the fixed
   Draft -> Confirm -> Report workflow.
4. Phase D2: set `AI_AGENT_TOOL_CALLING_ENABLED=true`. Verify the tool
   allowlist, authorization, timeout, and concurrency limit of one.
5. Phase E1: set `AI_CLEANUP_ENABLED=true`; keep scheduling false. Complete an
   approved dry run and the first manual execution.
6. Phase E2: set `AI_CLEANUP_SCHEDULE_ENABLED=true` only after Phase E1 passes.

On failure, disable Tool Calling, Agent, RAG, then Worker, recreating only
Backend after each change.

Qdrant uses an API key over the same-host `data-internal` bridge and publishes
no host port. This is an explicit single-node residual-risk exception to the
application's normal HTTPS transport guard. Do not extend that bridge to
another host; a multi-host deployment must enable Qdrant TLS and restore
`QDRANT_REQUIRE_SECURE_TRANSPORT=true`.

## Optional observability

Start tracing and the three observation services:

```bash
scripts/prod/observability-on.sh
```

Access Grafana only through an SSH tunnel to server `127.0.0.1:13000`. Stop
observability immediately after acceptance or when memory remains above 85% or
swap grows continuously:

```bash
scripts/prod/observability-off.sh
```

## Public HTTPS after filing completion

After the domain A record points to the server and ports 80/443 are allowed,
install the HTTP bootstrap configuration, obtain the certificate, then install
the final configuration:

```bash
sudo scripts/prod/install-host-nginx.sh example.com http
sudo scripts/prod/enable-public-web.sh --filing-complete
sudo certbot certonly --nginx -d example.com
sudo scripts/prod/install-host-nginx.sh example.com https
sudo certbot renew --dry-run
```

## Backup and recovery drill

Install the checked-in systemd timer with
`sudo scripts/prod/install-backup-timer.sh --enable`. It runs
`backup-mysql.sh` daily at 01:30 Asia/Shanghai. The script streams a consistent
dump through zstd and age without writing plaintext, uploads both the encrypted
object and checksum, retains seven daily and four weekly copies, and verifies
object existence. It records user/project/task row counts only when those
counts remain stable across the dump, and the restore drill requires exact
matches against the checksum-protected metadata.

Run a monthly restore with an offline age identity temporarily attached:

```bash
scripts/prod/restore-drill.sh \
  /var/backups/learning-manage/learning-manage-YYYYMMDDTHHMMSSZ.sql.zst.age \
  /media/offline/age-identity.txt
```

The drill creates an isolated, unpublished MySQL container and exact temporary
volume, runs `mysqlcheck`, validates the table set and V1-V8 history, then
removes only those temporary resources.

## Rollback

Rollback accepts only a retained directory named with two 40-character SHAs:

```bash
scripts/prod/rollback.sh <PREVIOUS_BACKEND_SHA>-<PREVIOUS_FRONTEND_SHA>
```

It refuses automatic rollback when migration checksums differ, disables the
high-risk AI features including Cleanup, recreates only Backend and Frontend,
checks health, synchronizes the protected release identity/image inventory,
and then atomically changes `current`. It also refuses automatic rollback when
external image inventories differ. MySQL, Redis, Qdrant, and named volumes are
not restarted or deleted.

Deployment, migration, rollback, observability changes, backups, restore
drills, image builds, and image pruning share one five-minute-timeout `flock`
under `/opt/learning-manage`; overlapping operations fail safely.

After a successful deployment and rollback check, remove older application
image tags with
`prune-old-application-images.sh --previous-release <BACKEND_SHA>-<FRONTEND_SHA>`.
The script only targets SHA-tagged `learningmanage-backend` and
`learningmanage-frontend` images and never prunes volumes or third-party images.

Never run `docker compose down -v`, `docker system prune --volumes`, Flyway
`clean`, or a global Docker volume deletion on this host.
