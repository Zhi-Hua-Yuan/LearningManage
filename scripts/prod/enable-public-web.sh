#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 1 && "$1" == --filing-complete ]] \
    || lm_die "usage: enable-public-web.sh --filing-complete"
[[ "$(id -u)" == 0 ]] || lm_die "firewall changes must run as root"

ufw allow 80/tcp
ufw allow 443/tcp
ufw status verbose

lm_log "UFW web ports enabled; verify the Jingdong Cloud firewall matches 22/80/443"
