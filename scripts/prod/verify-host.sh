#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

for command in docker free sleep ss ufw; do
    lm_require_command "$command"
done
lm_require_private_file "$LM_ENV_FILE"

lm_export_application_images
lm_compose config --quiet
lm_compose ps

public_listeners="$(ss -lntH | awk '$4 ~ /^(0[.]0[.]0[.]0|\[::\]|\*):/ { print $4 }')"
if grep -Eq ':(3306|6379|6333|8123|9123|13000)$' <<<"$public_listeners"; then
    printf '%s\n' "$public_listeners" >&2
    lm_die "a private service is listening on a public address"
fi
if awk -F: '{print $NF}' <<<"$public_listeners" | grep -Ev '^(22|80|443)?$' | grep -q .; then
    printf '%s\n' "$public_listeners" >&2
    lm_die "unexpected public TCP listener detected"
fi

memory_used_percent="$(free -m | awk '/^Mem:/ { printf "%d", ($3 * 100) / $2 }')"
observability_running=false
for service in prometheus tempo grafana; do
    if [[ -n "$(docker ps --quiet \
        --filter "label=com.docker.compose.project=$LM_PROJECT_NAME" \
        --filter "label=com.docker.compose.service=$service")" ]]; then
        observability_running=true
        break
    fi
done
memory_limit=80
[[ "$observability_running" == true ]] && memory_limit=90
((memory_used_percent < memory_limit)) \
    || lm_die "host memory usage must remain below ${memory_limit}%"

swap_first="$(free -m | awk '/^Swap:/ { print $3 }')"
sleep 5
swap_second="$(free -m | awk '/^Swap:/ { print $3 }')"
sleep 5
swap_third="$(free -m | awk '/^Swap:/ { print $3 }')"
if ((swap_first < swap_second && swap_second < swap_third)); then
    lm_die "swap usage increased across all three samples"
fi
lm_log "host memory used=${memory_used_percent}% swap samples=${swap_first}/${swap_second}/${swap_third}MiB"

ufw_command=(ufw)
if [[ "$(id -u)" != 0 ]]; then
    lm_require_command sudo
    sudo -n true >/dev/null 2>&1 || lm_die "passwordless sudo is required to verify UFW"
    ufw_command=(sudo -n ufw)
fi
ufw_status="$("${ufw_command[@]}" status verbose)"
grep -Fq 'Status: active' <<<"$ufw_status" || lm_die "UFW is not active"
unexpected_ufw_targets="$(awk '
    {
        for (field = 1; field <= NF; field++) {
            if ($field == "ALLOW") print $1
        }
    }
' <<<"$ufw_status" | grep -Ev '^(OpenSSH|22/tcp|80/tcp|443/tcp)$' || true)"
[[ -z "$unexpected_ufw_targets" ]] \
    || lm_die "unexpected UFW allow target: $unexpected_ufw_targets"
printf '%s\n' "$ufw_status"

lm_log "host listener and Compose exposure checks passed"
