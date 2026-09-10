#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 1 && "$1" == --lmdeploy-login-verified ]] \
    || lm_die "usage: harden-ssh.sh --lmdeploy-login-verified"
[[ "$(id -u)" == 0 ]] || lm_die "SSH hardening must run as root"
[[ -s /home/lmdeploy/.ssh/authorized_keys ]] || lm_die "lmdeploy authorized_keys is missing"

target=/etc/ssh/sshd_config.d/99-learning-manage.conf
temporary="${target}.tmp"
backup="${target}.backup.$$"
had_target=false
if [[ -f "$target" ]]; then
    cp -a "$target" "$backup"
    had_target=true
fi
install -o root -g root -m 0644 \
    "${LM_RELEASE_DIR}/deploy/sshd/99-learning-manage.conf" "$temporary"
mv -f "$temporary" "$target"
if ! sshd -t; then
    if [[ "$had_target" == true ]]; then
        mv -f "$backup" "$target"
    else
        rm -f -- "$target"
    fi
    lm_die "SSH hardening configuration validation failed"
fi
systemctl reload ssh
rm -f -- "$backup"

lm_log "root and password SSH login disabled after operator verification"
