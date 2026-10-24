#!/usr/bin/env bash
# Prueba de integración de la app (Kotlin) contra el backend real.
# Deja a los usuarios e2e.* sin 2FA y con su caso vacío, y corre LiveApiIntegrationTest,
# que recorre el mismo PrototypeState de las pantallas: login, enrolamiento, terapeuta,
# paciente y sesión persistente. SOLO base de desarrollo.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

python3 - <<PY
import sys
sys.path.insert(0, "$ROOT/backend/tests")
from support import E2E_PATIENT, E2E_SUPERADMIN, E2E_THERAPIST, delete_role, ensure_test_users, reset_user
ensure_test_users()
delete_role("APP_E2E")
for email in (E2E_THERAPIST, E2E_PATIENT, E2E_SUPERADMIN):
    reset_user(email)
PY

cd "$ROOT"
USBRAIN_LIVE_API=1 ./gradlew :shared:testAndroidHostTest --tests '*LiveApiIntegrationTest*' --rerun --console=plain
