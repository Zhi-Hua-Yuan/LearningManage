#!/bin/sh
set -eu

umask 077

if [ -z "${REDIS_PASSWORD:-}" ]; then
  printf '%s\n' 'REDIS_PASSWORD is required' >&2
  exit 1
fi

password_hash="$(printf '%s' "$REDIS_PASSWORD" | sha256sum | awk '{print $1}')"
printf 'user default off\nuser learning_app on #%s ~rate_limit:ai:* +@connection +eval +evalsha +incr +expire\n' \
  "$password_hash" > /tmp/users.acl

exec redis-server \
  --aclfile /tmp/users.acl \
  --appendonly yes \
  --appendfsync everysec \
  --maxmemory 96mb \
  --maxmemory-policy noeviction \
  --protected-mode yes
