#!/usr/bin/env bash
# Instala (o actualiza) el backend de USBrain en el Oracle que corre en Docker.
# Uso: backend/scripts/install.sh [--seed]
#   --seed  crea/actualiza los usuarios de prueba y el workspace APEX (solo desarrollo).
# Es idempotente: se puede correr cada vez que cambie un .sql.
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$BACKEND_DIR/.env"
SQL_DIR="$BACKEND_DIR/sql"

random_password() { echo "U$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 23)"; }

if [[ ! -f "$ENV_FILE" ]]; then
  sed -e "s/^USBRAIN_DB_PASSWORD=.*/USBRAIN_DB_PASSWORD=$(random_password)/" \
      -e "s/^DEV_SEED_PASSWORD=.*/DEV_SEED_PASSWORD=Dev-$(random_password | head -c 12)!/" \
      "$BACKEND_DIR/.env.example" > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  echo "Creado $ENV_FILE con contraseñas aleatorias."
fi
# shellcheck disable=SC1090
source "$ENV_FILE"

SYS_PWD="$(docker exec "$ORACLE_CONTAINER" printenv ORACLE_PWD)"
CONNECT_STRING="//localhost:1521/$ORACLE_SERVICE"

# Las credenciales viajan por stdin, no por la línea de comandos.
run_sql() {
  local file="$1" connect="$2" defines="${3:-}"
  echo "→ $(basename "$file")"
  { echo "$connect"; echo "$defines"; cat "$file"; } \
    | docker exec -i -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 "$ORACLE_CONTAINER" sqlplus -s -l /nolog
}

run_sql "$SQL_DIR/00_schema.sql" "connect sys/\"$SYS_PWD\"@$CONNECT_STRING as sysdba" \
  "define app_pwd = $USBRAIN_DB_PASSWORD"

APP_CONNECT="connect usbrain/\"$USBRAIN_DB_PASSWORD\"@$CONNECT_STRING"
# 01–79: esquema, paquetes y API (00 va como SYS; 80+ son solo de desarrollo, con --seed).
for file in "$SQL_DIR"/[0-7][0-9]_*.sql; do
  [[ "$(basename "$file")" == 00_* ]] && continue
  run_sql "$file" "$APP_CONNECT"
done

if [[ "${1:-}" == "--seed" ]]; then
  run_sql "$SQL_DIR/90_seed_dev.sql" "$APP_CONNECT" "define dev_pwd = $DEV_SEED_PASSWORD"

  if [[ -z "${APEX_DEV_PASSWORD:-}" ]]; then
    APEX_DEV_PASSWORD="Apex-$(random_password | head -c 14)!"
    printf '\n# Acceso a APEX (http://localhost:8080/ords/apex) · workspace USBRAIN\nAPEX_DEV_USER=dev\nAPEX_DEV_PASSWORD=%s\n' \
      "$APEX_DEV_PASSWORD" >> "$ENV_FILE"
    APEX_DEV_USER=dev
  fi
  run_sql "$SQL_DIR/80_apex_workspace.sql" "connect sys/\"$SYS_PWD\"@$CONNECT_STRING as sysdba" \
    "define apex_user = ${APEX_DEV_USER:-dev}
define apex_pwd = $APEX_DEV_PASSWORD
define apex_email = $(git config user.email || echo dev@localhost)"
fi

echo "Backend instalado. API: $API_BASE"
