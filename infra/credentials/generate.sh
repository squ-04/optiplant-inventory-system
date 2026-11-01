#!/bin/sh
# Materialise individual local credentials so `docker compose up` needs no prior configuration.
# Precedence per credential: a value configured in .env, then the stored one, then a new random one.
set -eu
umask 077

directory=/run/optiplant
backend_uid=10001

stored() {
    for file in "$directory/postgres.env" "$directory/backend.env"; do
        [ -f "$file" ] || continue
        value=$(. "$file"; printenv "$1" || true)
        if [ -n "$value" ]; then
            printf '%s' "$value"
            return
        fi
    done
}

resolve() {
    value=$(printenv "CONFIGURED_$1" || true)
    [ -n "$value" ] || value=$(stored "$1")
    [ -n "$value" ] || value=$(head -c 36 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=\n')
    printf '%s' "$value"
}

entry() {
    printf "export %s='%s'\n" "$1" "$(printf '%s' "$2" | sed "s/'/'\\\\''/g")"
}

# Replace a file atomically so an interrupted run never leaves it truncated.
publish() {
    target=$directory/$1
    owner=$2
    mode=$3
    cat > "$target.tmp"
    chown "$owner" "$target.tmp"
    chmod "$mode" "$target.tmp"
    mv "$target.tmp" "$target"
}

postgres_password=$(resolve POSTGRES_PASSWORD)
runtime_password=$(resolve APP_DB_PASSWORD)
migration_password=$(resolve APP_DB_MIGRATION_PASSWORD)
admin_password=$(resolve APP_ADMIN_PASSWORD)

chmod 755 "$directory"
{
    entry POSTGRES_PASSWORD "$postgres_password"
    entry APP_DB_PASSWORD "$runtime_password"
    entry APP_DB_MIGRATION_PASSWORD "$migration_password"
} | publish postgres.env 0:0 400
{
    entry APP_DB_PASSWORD "$runtime_password"
    entry APP_DB_MIGRATION_PASSWORD "$migration_password"
    entry APP_ADMIN_PASSWORD "$admin_password"
} | publish backend.env "$backend_uid:$backend_uid" 400
printf '%s\n' "$admin_password" | publish admin-password "$backend_uid:$backend_uid" 400

echo "Credenciales locales listas. Contraseña inicial del administrador:"
echo "  docker compose exec backend cat /run/optiplant/admin-password"
