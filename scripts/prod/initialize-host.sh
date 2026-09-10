#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 1 ]] || lm_die "usage: initialize-host.sh LMDEPLOY_PUBLIC_KEY_FILE"
[[ "$(id -u)" == 0 ]] || lm_die "host initialization must run as root"
public_key_file="$(realpath -e "$1")"
lm_require_file "$public_key_file"
lm_require_command docker
docker compose version >/dev/null

# Validate the fixed low-resource production host before changing it.
# shellcheck disable=SC1091
source /etc/os-release
[[ "${ID:-}" == ubuntu && "${VERSION_ID:-}" == 24.04 ]] \
    || lm_die "Ubuntu 24.04 is required"
[[ "$(uname -m)" == x86_64 ]] || lm_die "x86_64 is required"
[[ "$(nproc)" -ge 2 ]] || lm_die "at least two CPU cores are required"
memory_kib="$(awk '/^MemTotal:/ { print $2 }' /proc/meminfo)"
swap_kib="$(awk '/^SwapTotal:/ { print $2 }' /proc/meminfo)"
[[ "$memory_kib" -ge 3500000 ]] || lm_die "at least 4GB-class memory is required"
[[ "$swap_kib" -ge 1900000 ]] || lm_die "at least 2GB swap must be configured first"
[[ "$(sysctl -n vm.swappiness)" == 10 ]] || lm_die "vm.swappiness must equal 10"
disk_kib="$(df -Pk / | awk 'NR == 2 { print $2 }')"
[[ "$disk_kib" -ge 50000000 ]] || lm_die "at least 50GB root filesystem capacity is required"

key_count="$(grep -Ec '^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521))[[:space:]]+[A-Za-z0-9+/=]+' "$public_key_file")"
[[ "$key_count" == 1 && "$(grep -Ec '[^[:space:]]' "$public_key_file")" == 1 ]] \
    || lm_die "public key file must contain exactly one supported SSH public key"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install --yes \
    nginx certbot python3-certbot-nginx age zstd s3cmd jq curl openssl ufw sudo \
    unattended-upgrades ca-certificates

getent group docker >/dev/null || lm_die "Docker group is missing; install Docker CE first"
if ! id lmdeploy >/dev/null 2>&1; then
    useradd --create-home --shell /bin/bash lmdeploy
fi
usermod --append --groups docker lmdeploy

install -d -o lmdeploy -g lmdeploy -m 0700 /home/lmdeploy/.ssh
install -o lmdeploy -g lmdeploy -m 0600 "$public_key_file" /home/lmdeploy/.ssh/authorized_keys
install -o root -g root -m 0644 \
    "${LM_RELEASE_DIR}/deploy/sshd/10-learning-manage-bootstrap.conf" \
    /etc/ssh/sshd_config.d/10-learning-manage-bootstrap.conf
if ! sshd -t; then
    rm -f -- /etc/ssh/sshd_config.d/10-learning-manage-bootstrap.conf
    lm_die "SSH bootstrap configuration validation failed"
fi
systemctl reload ssh
passwd --delete lmdeploy >/dev/null

install -d -o root -g lmdeploy -m 2775 /opt/learning-manage
install -d -o root -g lmdeploy -m 2775 /opt/learning-manage/releases
install -d -o root -g lmdeploy -m 0770 /etc/learning-manage
install -d -o root -g lmdeploy -m 0750 /etc/learning-manage/secrets
install -d -o lmdeploy -g lmdeploy -m 0700 /var/backups/learning-manage
install -d -o root -g root -m 0755 /var/www/html

install -o root -g root -m 0644 \
    "${LM_RELEASE_DIR}/deploy/apt/20auto-upgrades" \
    /etc/apt/apt.conf.d/20auto-upgrades
install -o root -g root -m 0644 \
    "${LM_RELEASE_DIR}/deploy/apt/52unattended-upgrades-local" \
    /etc/apt/apt.conf.d/52unattended-upgrades-local
sudoers_candidate=/etc/sudoers.d/.90-learning-manage.candidate
install -o root -g root -m 0440 \
    "${LM_RELEASE_DIR}/deploy/sudoers/90-learning-manage" "$sudoers_candidate"
visudo -cf "$sudoers_candidate"
mv -f "$sudoers_candidate" /etc/sudoers.d/90-learning-manage
systemctl enable --now nginx unattended-upgrades

ufw allow OpenSSH
ufw --force enable

docker version
docker compose version
lm_log "host initialized; verify a separate lmdeploy SSH session, then run harden-ssh.sh"
