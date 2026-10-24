#!/usr/bin/env bash
# Instala (o actualiza) el backend de USBrain en Oracle.
# Uso: backend/scripts/install.sh [--seed]
#   --seed  crea los usuarios y casos de demostración y el workspace APEX (solo desarrollo).
# Es idempotente: se puede correr cada vez que cambie un .sql.
# Funciona con los contenedores de docker-compose.yml (ORACLE_MODE=docker) o con un
# Oracle/ORDS/APEX que ya tengas instalado (ORACLE_MODE=local). Ver INSTALACION.md.
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${USBRAIN_ENV:-$BACKEND_DIR/.env}"
SQL_DIR="$BACKEND_DIR/sql"

random_password() { echo "U$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 23)"; }

# Escribe (o reemplaza) KEY=valor en el .env.
set_env() {
  local key="$1" value="$2"
  if grep -q "^$key=" "$ENV_FILE"; then
    sed -i.bak "s|^$key=.*|$key=$value|" "$ENV_FILE" && rm -f "$ENV_FILE.bak"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$BACKEND_DIR/.env.example" "$ENV_FILE"
  echo "Creado $ENV_FILE a partir de .env.example."
fi
chmod 600 "$ENV_FILE"
# shellcheck disable=SC1090
source "$ENV_FILE"

# Contraseñas que falten: se generan una sola vez y quedan guardadas en el .env.
if [[ -z "${USBRAIN_DB_PASSWORD:-}" ]]; then
  USBRAIN_DB_PASSWORD="$(random_password)"; set_env USBRAIN_DB_PASSWORD "$USBRAIN_DB_PASSWORD"
fi
if [[ -z "${DEV_SEED_PASSWORD:-}" ]]; then
  DEV_SEED_PASSWORD="Dev-$(random_password | head -c 12)!"; set_env DEV_SEED_PASSWORD "$DEV_SEED_PASSWORD"
fi

ORACLE_MODE="${ORACLE_MODE:-docker}"
ORACLE_SERVICE="${ORACLE_SERVICE:-FREEPDB1}"
ORACLE_CONTAINER="${ORACLE_CONTAINER:-oracle-db}"
SYS_PWD="${ORACLE_PWD:-}"

if [[ "$ORACLE_MODE" == "docker" ]]; then
  if [[ "$(docker inspect -f '{{.State.Running}}' "$ORACLE_CONTAINER" 2>/dev/null)" != "true" ]]; then
    echo "El contenedor '$ORACLE_CONTAINER' no está corriendo. Enciéndelo con:" >&2
    echo "  docker compose up -d      (desde backend/, si usas docker-compose.yml)" >&2
    echo "  docker start $ORACLE_CONTAINER ${ORDS_CONTAINER:-ords}   (si son tus propios contenedores)" >&2
    exit 1
  fi
  # Dentro del contenedor la base está en su propio localhost.
  CONNECT_STRING="//localhost:1521/$ORACLE_SERVICE"
  [[ -z "$SYS_PWD" ]] && SYS_PWD="$(docker exec "$ORACLE_CONTAINER" printenv ORACLE_PWD 2>/dev/null || true)"
  sql_client() { docker exec -i -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 "$ORACLE_CONTAINER" sqlplus -s -l /nolog; }
elif [[ "$ORACLE_MODE" == "local" ]]; then
  CONNECT_STRING="//${DB_HOST:-localhost}:${DB_PORT:-1521}/$ORACLE_SERVICE"
  SQL_CLIENT="${SQL_CLIENT:-sql}"
  if ! command -v "${SQL_CLIENT%% *}" >/dev/null; then
    echo "No encuentro '$SQL_CLIENT'. Instala SQLcl o sqlplus, o cambia SQL_CLIENT en $ENV_FILE." >&2
    exit 1
  fi
  # shellcheck disable=SC2086
  sql_client() { NLS_LANG=AMERICAN_AMERICA.AL32UTF8 $SQL_CLIENT -S /nolog; }
else
  echo "ORACLE_MODE debe ser 'docker' o 'local' (está en '$ORACLE_MODE')." >&2
  exit 1
fi
if [[ -z "$SYS_PWD" ]]; then
  echo "Falta la contraseña de SYS: ponla en ORACLE_PWD dentro de $ENV_FILE." >&2
  exit 1
fi

SYS_CONNECT="connect sys/\"$SYS_PWD\"@$CONNECT_STRING as sysdba"
APP_CONNECT="connect usbrain/\"$USBRAIN_DB_PASSWORD\"@$CONNECT_STRING"

# Las credenciales viajan por stdin, no por la línea de comandos.
run_sql() {
  local file="$1" connect="$2" defines="${3:-}"
  echo "→ $(basename "$file")"
  { echo "$connect"; echo "$defines"; cat "$file"; } | sql_client
}

# Resultado de una consulta de un solo valor, ejecutada como SYS.
sys_value() {
  { echo "$SYS_CONNECT"; echo "set heading off feedback off pagesize 0 verify off"; echo "$1"; echo "exit"; } \
    | sql_client 2>&1 | tr -d '[:space:]'
}

# Requisitos: Oracle 23ai/26ai (columnas BOOLEAN y JSON, IF NOT EXISTS) con ORDS instalado.
DB_VERSION="$(sys_value "select regexp_substr(version_full, '^[0-9]+') from v\$instance;")"
if [[ ! "$DB_VERSION" =~ ^[0-9]+$ ]]; then
  echo "No pude conectarme como SYS a $CONNECT_STRING ($ORACLE_MODE). Respuesta: ${DB_VERSION:0:200}" >&2
  echo "Revisa ORACLE_MODE, ORACLE_PWD y la conexión en $ENV_FILE (ver INSTALACION.md)." >&2
  exit 1
fi
if (( DB_VERSION < 23 )); then
  echo "Se necesita Oracle Database 23ai/26ai o superior; esta base es versión $DB_VERSION." >&2
  echo "Usa la base de backend/docker-compose.yml (ver INSTALACION.md)." >&2
  exit 1
fi
if [[ "$(sys_value "select count(*) from dba_users where username = 'ORDS_METADATA';")" == "0" ]]; then
  echo "ORDS no está instalado en esta base. Instálalo (docker-compose.yml lo hace solo) y vuelve a correr." >&2
  exit 1
fi

run_sql "$SQL_DIR/00_schema.sql" "$SYS_CONNECT" "define app_pwd = $USBRAIN_DB_PASSWORD"

# 01–79: esquema, paquetes y API (00 va como SYS; 80+ son solo de desarrollo, con --seed).
for file in "$SQL_DIR"/[0-7][0-9]_*.sql; do
  [[ "$(basename "$file")" == 00_* ]] && continue
  run_sql "$file" "$APP_CONNECT"
done

if [[ "${1:-}" == "--seed" ]]; then
  run_sql "$SQL_DIR/90_seed_dev.sql" "$APP_CONNECT" "define dev_pwd = $DEV_SEED_PASSWORD"

  if [[ "$(sys_value "select count(*) from dba_registry where comp_id = 'APEX';")" == "0" ]]; then
    echo "→ APEX no está instalado: se omite el workspace (la app funciona sin APEX)."
  else
    if [[ -z "${APEX_DEV_PASSWORD:-}" ]]; then
      APEX_DEV_PASSWORD="Apex-$(random_password | head -c 14)!"; set_env APEX_DEV_PASSWORD "$APEX_DEV_PASSWORD"
    fi
    run_sql "$SQL_DIR/80_apex_workspace.sql" "$SYS_CONNECT" \
      "define apex_user = ${APEX_DEV_USER:-dev}
define apex_pwd = $APEX_DEV_PASSWORD
define apex_email = $(git config user.email 2>/dev/null || echo dev@localhost)"
  fi
fi

echo "Backend instalado. API: ${API_BASE:-http://localhost:8080/ords/usbrain/v1}"
