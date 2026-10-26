#!/usr/bin/env bash
# Abre el túnel de stage: expone el ORDS local (Docker) en el dominio fijo de ngrok para
# que la web publicada en Render lo use como backend. Ver STAGE.md.
#
# Uso:  backend/scripts/stage-tunnel.sh        (Ctrl + C para cerrarlo)
#
# Mientras está abierto, la base de desarrollo es accesible desde internet: ábrelo solo
# cuando lo necesites.
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${USBRAIN_ENV:-$BACKEND_DIR/.env}"
# shellcheck disable=SC1090
[[ -f "$ENV_FILE" ]] && source "$ENV_FILE"

DOMAIN="${NGROK_DOMAIN:-slinging-overjoyed-landlord.ngrok-free.dev}"
PORT="${ORDS_PORT:-8080}"
LOCAL_API="http://localhost:$PORT/ords/usbrain/v1/app/bootstrap"

if ! command -v ngrok >/dev/null; then
  echo "No encuentro ngrok. Instálalo (brew install ngrok) y configura tu cuenta:" >&2
  echo "  ngrok config add-authtoken <tu-token>   (https://dashboard.ngrok.com)" >&2
  exit 1
fi

if pgrep -f "ngrok http" >/dev/null; then
  echo "Ya hay un túnel de ngrok abierto. Ciérralo antes de abrir otro (Ctrl + C en su terminal)." >&2
  exit 1
fi

# 401 = la API responde y pide sesión: el backend está listo.
status="$(curl -s -o /dev/null -m 5 -w '%{http_code}' "$LOCAL_API" || true)"
if [[ "$status" != "401" ]]; then
  echo "La API local no responde en $LOCAL_API (código: ${status:-sin respuesta})." >&2
  echo "Enciende Docker Desktop y los contenedores, espera un minuto y vuelve a intentar:" >&2
  echo "  docker start ${ORACLE_CONTAINER:-oracle-db} ${ORDS_CONTAINER:-ords}" >&2
  exit 1
fi

cat <<INFO
Backend local listo. Abriendo el túnel de stage…

  API pública:        https://$DOMAIN/ords/usbrain/v1
  Peticiones en vivo: http://127.0.0.1:4040
  Cerrar el túnel:    Ctrl + C

  Mientras esté abierto, tu base de desarrollo es accesible desde internet.

INFO

exec ngrok http --url="$DOMAIN" "$PORT"
