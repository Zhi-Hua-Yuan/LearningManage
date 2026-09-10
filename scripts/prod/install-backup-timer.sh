#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 1 && "$1" == --enable ]] || lm_die "usage: install-backup-timer.sh --enable"
[[ "$(id -u)" == 0 ]] || lm_die "systemd unit installation must run as root"

install -o root -g root -m 0644 \
    "${LM_RELEASE_DIR}/deploy/systemd/learning-manage-backup.service" \
    /etc/systemd/system/learning-manage-backup.service
install -o root -g root -m 0644 \
    "${LM_RELEASE_DIR}/deploy/systemd/learning-manage-backup.timer" \
    /etc/systemd/system/learning-manage-backup.timer
systemctl daemon-reload
systemctl enable --now learning-manage-backup.timer
systemctl list-timers learning-manage-backup.timer

lm_log "daily encrypted backup timer installed"
