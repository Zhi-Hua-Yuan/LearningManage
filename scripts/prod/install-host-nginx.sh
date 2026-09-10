#!/usr/bin/env bash

set -Eeuo pipefail

script_dir="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/prod/common.sh
source "${script_dir}/common.sh"

[[ $# -eq 2 ]] || lm_die "usage: install-host-nginx.sh DOMAIN http|https"
domain="$1"
mode="$2"
[[ "$domain" =~ ^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?[.])+[A-Za-z]{2,63}$ ]] \
    || lm_die "invalid domain name"
[[ "$mode" == http || "$mode" == https ]] || lm_die "mode must be http or https"
[[ "$(id -u)" == 0 ]] || lm_die "host Nginx installation must run as root"
lm_acquire_operation_lock

source_file="${LM_RELEASE_DIR}/deploy/nginx.host.conf"
[[ "$mode" == http ]] && source_file="${LM_RELEASE_DIR}/deploy/nginx.host.http.conf"
target=/etc/nginx/sites-available/learning-manage.conf
temporary="${target}.tmp"
backup="${target}.backup.$$"
had_target=false
had_default=false
default_target=
installed=false

rollback_config() {
    [[ "$installed" == false ]] || return 0
    if [[ "$had_target" == true && -f "$backup" ]]; then
        mv -f "$backup" "$target"
    else
        rm -f -- "$target"
        if [[ -L /etc/nginx/sites-enabled/learning-manage.conf ]]; then
            rm -f -- /etc/nginx/sites-enabled/learning-manage.conf
        fi
    fi
    if [[ "$had_default" == true && -n "$default_target" ]]; then
        ln -sfn "$default_target" /etc/nginx/sites-enabled/default
    fi
}
trap rollback_config EXIT

sed "s/__DOMAIN__/${domain}/g" "$source_file" > "$temporary"
chown root:root "$temporary"
chmod 0644 "$temporary"
if [[ -f "$target" ]]; then
    cp -a "$target" "$backup"
    had_target=true
fi
mv -f "$temporary" "$target"
ln -sfn "$target" /etc/nginx/sites-enabled/learning-manage.conf
nginx -t
if [[ -L /etc/nginx/sites-enabled/default ]]; then
    had_default=true
    default_target="$(readlink /etc/nginx/sites-enabled/default)"
    unlink /etc/nginx/sites-enabled/default
elif [[ -e /etc/nginx/sites-enabled/default ]]; then
    lm_die "refusing to replace a non-symlink default Nginx site"
fi
nginx -t
systemctl reload nginx
installed=true
rm -f -- "$backup"
trap - EXIT

lm_log "host Nginx $mode configuration installed for $domain"
