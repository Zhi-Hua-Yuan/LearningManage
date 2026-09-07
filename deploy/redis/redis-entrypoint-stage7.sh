#!/bin/sh
set -eu
umask 077
if [ -z "${REDIS_PASSWORD:-}" ]; then
  echo "REDIS_PASSWORD is required" >&2
  exit 1
fi
password_hash="$(printf '%s' "$REDIS_PASSWORD" | sha256sum | awk '{print $1}')"
printf 'user default off\nuser learning_app on #%s ~* +@all\n' "$password_hash" > /tmp/users.acl
exec redis-server --aclfile /tmp/users.acl --appendonly yes --protected-mode yes
