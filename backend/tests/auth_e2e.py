#!/usr/bin/env python3
"""Prueba end-to-end de la API de autenticación de USBrain contra ORDS.

Uso:  python3 backend/tests/auth_e2e.py

SOLO PARA LA BASE DE DESARROLLO. Usa usuarios exclusivos (e2e.*@usbrain.test),
a los que reinicia el 2FA y la contraseña vía sqlplus; no toca los usuarios de demo. Tarda ~1 minuto porque
espera a que cambie la ventana de 30 s del TOTP entre logins (un mismo código
no se acepta dos veces).
"""
import sys

from support import (E2E_ADMIN, E2E_PATIENT, PASSWORD, call, check, ensure_test_users, login, passed,
                     reset_user, totp, wait_next_step)

PATIENT = E2E_PATIENT
ADMIN = E2E_ADMIN
PHONE = "e2e-phone-0000000001"
TABLET = "e2e-tablet-000000002"


print("Preparando usuarios de prueba…")
ensure_test_users()
reset_user(PATIENT)
reset_user(ADMIN)

print("\n1. Primer login: enrolamiento del autenticador")
status, body = login(PATIENT, PHONE, "contraseña-equivocada")
check(status == 401 and body["error"] == "invalid_credentials", "contraseña incorrecta → 401 invalid_credentials")
status, body = login("nadie@usb.edu.co", PHONE)
check(status == 401 and body["error"] == "invalid_credentials", "correo inexistente → mismo 401 (no revela usuarios)")
status, body = login(PATIENT, PHONE)
check(status == 200 and body["status"] == "mfa_enrollment_required", "contraseña correcta → pide enrolar 2FA")
check(body["enrollment"]["otpauth_uri"].startswith("otpauth://totp/USBrain:"), "entrega URI otpauth:// para el QR")
secret, challenge = body["enrollment"]["secret"], body["challenge_id"]
status, body = call("POST", "/auth/mfa/verify", {"challenge_id": challenge, "code": "000000"})
check(status == 401 and body["error"] == "invalid_code", "código TOTP incorrecto → 401 invalid_code")
status, body = call("POST", "/auth/mfa/verify", {"challenge_id": challenge, "code": totp(secret)})
check(status == 200 and body["user"]["mfa_enabled"], "código TOTP correcto → sesión iniciada y 2FA activo")
check(len(body["recovery_codes"]) == 10, "entrega 10 códigos de recuperación (una sola vez)")
recovery_codes = body["recovery_codes"]
phone = body
status, body = call("POST", "/auth/mfa/verify", {"challenge_id": challenge, "code": totp(secret)})
check(status == 401 and body["error"] == "challenge_expired", "el desafío no se puede reutilizar")

print("\n2. Acceso con el access token")
status, body = call("GET", "/auth/me", token=phone["access_token"])
check(status == 200 and body["email"] == PATIENT and body["session"]["mfa_method"] == "TOTP", "GET /me con Bearer válido")
status, body = call("GET", "/auth/me", token="usb_at_" + "A" * 43)
check(status == 401 and body["error"] == "token_expired", "token inventado → 401 token_expired")

print("\n3. Segundo dispositivo: el mismo código TOTP no sirve dos veces")
status, body = login(PATIENT, TABLET)
check(status == 200 and body["status"] == "mfa_required", "usuario enrolado → pide código 2FA")
status, body = call("POST", "/auth/mfa/verify", {"challenge_id": body["challenge_id"], "code": totp(secret)})
check(status == 401 and body["error"] == "invalid_code", "repetir el código ya usado → rechazado (anti-replay)")
print("       (esperando la siguiente ventana TOTP…)")
wait_next_step()
status, body = login(PATIENT, TABLET)
status, body = call("POST", "/auth/mfa/verify", {"challenge_id": body["challenge_id"], "code": totp(secret)})
check(status == 200, "código de la nueva ventana → sesión en la tablet")
tablet = body
status, body = call("GET", "/auth/sessions", token=phone["access_token"])
check(status == 200 and len(body["items"]) == 2, "GET /sessions lista los 2 dispositivos")

print("\n4. Sesión persistente: rotación del refresh token")
status, body = call("POST", "/auth/refresh", {"refresh_token": phone["refresh_token"], "device_id": PHONE})
check(status == 200 and body["refresh_token"] != phone["refresh_token"], "refresh → par de tokens nuevo")
stolen_refresh, phone = phone["refresh_token"], body
status, body = call("GET", "/auth/me", token=phone["access_token"])
check(status == 200, "el access token nuevo funciona")
status, body = call("POST", "/auth/refresh", {"refresh_token": phone["refresh_token"], "device_id": TABLET})
check(status == 401, "refresh desde otro dispositivo → 401")
status, body = call("GET", "/auth/me", token=phone["access_token"])
check(status == 401, "…y la sesión del teléfono queda revocada (token robado)")

print("\n5. Detección de reutilización del refresh token")
status, body = call("POST", "/auth/refresh", {"refresh_token": tablet["refresh_token"], "device_id": TABLET})
check(status == 200, "la tablet renueva su sesión")
old_tablet, tablet = tablet, body
status, body = call("POST", "/auth/refresh", {"refresh_token": old_tablet["refresh_token"], "device_id": TABLET})
check(status == 401 and body["error"] == "session_expired", "reusar un refresh token ya usado → 401")
status, body = call("POST", "/auth/refresh", {"refresh_token": tablet["refresh_token"], "device_id": TABLET})
check(status == 401, "…y se revoca toda la sesión (el token legítimo tampoco sirve)")

print("\n6. Código de recuperación")
status, body = login(PATIENT, PHONE)
status, body = call("POST", "/auth/mfa/recovery", {"challenge_id": body["challenge_id"], "recovery_code": recovery_codes[0].lower()})
check(status == 200 and body["recovery_codes_remaining"] == 9, "código de recuperación válido → sesión (quedan 9)")
phone = body
status, body = login(PATIENT, TABLET)
status, body = call("POST", "/auth/mfa/recovery", {"challenge_id": body["challenge_id"], "recovery_code": recovery_codes[0]})
check(status == 401 and body["error"] == "invalid_code", "el mismo código de recuperación no sirve dos veces")

print("\n7. Cerrar sesión de otro dispositivo y cambio de contraseña")
status, body = call("GET", "/auth/sessions", token=phone["access_token"])
check(status == 200 and [s["current"] for s in body["items"]] == [True], "solo queda la sesión actual")
status, body = call("POST", "/auth/password", {"current_password": PASSWORD, "new_password": "corta", "code": "000000"}, phone["access_token"])
check(status == 403 and body["error"] == "invalid_code", "cambio de contraseña exige código 2FA")
wait_next_step()
new_password = PASSWORD + "-nueva"
status, body = call("POST", "/auth/password", {"current_password": PASSWORD, "new_password": new_password, "code": totp(secret)}, phone["access_token"])
check(status == 200, "cambio de contraseña con contraseña actual + 2FA")
status, body = login(PATIENT, TABLET)
check(status == 401, "la contraseña anterior ya no funciona")
status, body = login(PATIENT, TABLET, new_password)
check(status == 200 and body["status"] == "mfa_required", "la nueva sí")
status, body = call("POST", "/auth/logout", token=phone["access_token"])
check(status == 200, "logout")
status, body = call("GET", "/auth/me", token=phone["access_token"])
check(status == 401, "después del logout el token ya no sirve")

print("\n8. Bloqueo por intentos fallidos")
for _ in range(5):
    status, body = login(ADMIN, PHONE, "intento-equivocado")
check(status == 401, "5 contraseñas incorrectas")
status, body = login(ADMIN, PHONE)
check(status == 423 and body["error"] == "account_locked", "cuenta bloqueada aunque la contraseña sea correcta")
print("       " + body["message"])

print("\nRestaurando usuarios de prueba…")
reset_user(PATIENT)
reset_user(ADMIN)
print(f"\n✔ {passed()} verificaciones pasaron.")
