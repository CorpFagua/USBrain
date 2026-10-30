#!/usr/bin/env python3
"""Prueba end-to-end de roles dinámicos, permisos individuales, administración y olvido de contraseña.

Uso:  python3 backend/tests/admin_e2e.py

SOLO PARA LA BASE DE DESARROLLO. Usa usuarios e2e.*@usbrain.test y el rol de prueba
SUPERVISOR_E2E; los crea y los borra. No toca los usuarios ni roles de demostración.
"""
import datetime
import time

from support import (E2E_ADMIN, E2E_PATIENT, E2E_SUPERADMIN, E2E_THERAPIST, PASSWORD, call, check,
                     delete_role, delete_user, enrolled_session, ensure_test_users, login, passed, sql, totp,
                     wait_next_step)

SUPERVISOR_EMAIL = "e2e.supervisor@usbrain.test"
CREATED_THERAPIST = "e2e.nuevo.terapeuta@usbrain.test"
CREATED_PATIENT = "e2e.nuevo.paciente@usbrain.test"
ROLE = "SUPERVISOR_E2E"


def find(items, **match):
    return next((i for i in items or [] if all(i.get(k) == v for k, v in match.items())), None)


def overview(token):
    status, body = call("GET", "/admin/overview", token=token)
    assert status == 200, body
    return body


print("Preparando…")
for email in (SUPERVISOR_EMAIL, CREATED_THERAPIST, CREATED_PATIENT):
    delete_user(email)
delete_role(ROLE)
ensure_test_users()
sql("delete from usb_user_permissions where user_id in "
    "(select user_id from usb_users where email like 'e2e.%@usbrain.test');")
superadmin, _ = enrolled_session(E2E_SUPERADMIN, "e2e-adm-superadmin-01")
admin, _ = enrolled_session(E2E_ADMIN, "e2e-adm-admin-000001")
therapist, _ = enrolled_session(E2E_THERAPIST, "e2e-adm-therapist-01")
S, A, T = superadmin["access_token"], admin["access_token"], therapist["access_token"]

print("\n1. Quién ve la administración")
check(superadmin["user"]["role"] == "SUPERADMIN" and "roles.manage" in superadmin["user"]["permissions"],
      "el login del super admin trae su rol y sus permisos")
check(admin["user"]["profile"] == "ADMIN" and "roles.manage" not in admin["user"]["permissions"],
      "el admin tiene interfaz de administración pero no gestiona roles")
data = overview(S)
check(len(data["permissions"]) >= 14 and find(data["roles"], id="SUPERADMIN")["is_locked"],
      "overview: catálogo de permisos y rol SUPERADMIN bloqueado")
therapists = [t["id"] for t in data["therapists"]]
check(find(data["users"], email=E2E_THERAPIST)["id"] in therapists
      and find(data["users"], email=E2E_SUPERADMIN)["id"] not in therapists,
      "los candidatos a terapeuta son roles de terapeuta (no el super admin)")
check(data["events"] is not None and len(data["events"]) > 0, "el super admin ve la bitácora")
check(overview(A)["users"] is not None, "el admin ve los usuarios")
status, analytics = call("GET", "/admin/analytics", token=S)
check(status == 200 and analytics["status"] == "CONNECTED" and analytics["privacy"]["pseudonymized"],
      "el super admin recibe la exportación analítica seudonimizada")
serialized = str([analytics["series"], analytics["studies"]]).lower()
check(not any(field in serialized for field in ("@usb", "@demo", "full_name", "diagnos", "note", "email", "patient")),
      "la exportación no contiene nombres, correos, diagnósticos, notas ni ids de paciente")
check(all(len(x["baseline"]) >= 3 and len(x["intervention"]) >= 3 for x in analytics["series"]),
      "cada caso individual tiene al menos 3 días en A1 y 3 en B1")
study = find(analytics["studies"], code="ANSIEDAD_COHORTE_2026")
check(study is not None and len(study["participants"]) == 200
      and all(set(p) == {"baseline_mean", "intervention_mean"} for p in study["participants"]),
      "estudio de grupo: 200 participantes, una fila por persona sin identificador")
check(all(point.get("date") is None for point in study["baseline"] + study["intervention"]),
      "el estudio no trae fechas: solo el día relativo")
check(all(g["participants"] >= 3 for g in study["program_groups"] + study["age_groups"]),
      "los grupos por programa y edad solo aparecen con n ≥ 3")
ana_series = [x for x in analytics["series"] if x["behavior"] == "Episodios de ansiedad en clase"]
check(len(ana_series) == 1 and len(ana_series[0]["baseline"]) == 30,
      "el caso individual de Ana sigue aparte aunque su conducta se llame igual que la del estudio")
check(not any(u["email"].endswith("@demo.usbrain.local") for u in overview(S)["users"]),
      "los participantes sintéticos no aparecen en la lista de usuarios")
status, body = call("GET", "/admin/analytics", token=A)
check(status == 403, "el administrador normal no accede al análisis seudonimizado")

print("\n1b. Parámetros clínicos (settings.clinical)")
status, body = call("POST", "/admin/settings", {"min_phase_observations": 2}, A)
check(status == 400 and body["error"] == "invalid_setting", "mínimo de observaciones por debajo de 3 → 400")
status, body = call("POST", "/admin/settings", {"min_phase_observations": 5}, A)
check(status == 200, "el admin cambia el mínimo de observaciones por fase")
status, data = call("GET", "/app/bootstrap", token=T)
check(data["settings"]["min_phase_observations"] == 5, "el terapeuta recibe el valor nuevo")
status, body = call("POST", "/admin/settings", {"min_phase_observations": 3}, S)
check(status == 200, "el super admin lo devuelve a 3")
status, body = call("POST", "/admin/settings", {"min_phase_observations": 4}, T)
check(status == 403, "el terapeuta no cambia parámetros clínicos")
status, body = call("GET", "/admin/overview", token=T)
check(status == 403, "el terapeuta no entra a la administración")

print("\n2. Roles dinámicos (solo el super admin)")
new_role = {"id": ROLE, "name": "Supervisor E2E", "profile": "THERAPIST",
            "description": "Ve y edita todos los casos", "permissions": ["case.view.all", "case.edit.all", "catalog.manage"]}
status, body = call("POST", "/admin/roles", new_role, A)
check(status == 403, "el admin NO puede crear roles")
status, body = call("POST", "/admin/roles", {**new_role, "id": "mal id"}, S)
check(status == 400 and body["error"] == "invalid_role_id", "código de rol inválido → 400")
status, body = call("POST", "/admin/roles", {**new_role, "permissions": ["permiso.inventado"]}, S)
check(status == 400 and body["error"] == "invalid_permission", "permiso inexistente → 400")
status, body = call("POST", "/admin/roles", new_role, S)
check(status == 200, "el super admin crea el rol SUPERVISOR_E2E")
status, body = call("POST", "/admin/roles", new_role, S)
check(status == 409, "código de rol repetido → 409")
status, body = call("POST", "/admin/roles/SUPERADMIN", {"permissions": []}, S)
check(status == 403 and body["error"] == "role_locked", "el rol SUPERADMIN no se puede modificar")
status, body = call("DELETE", "/admin/roles/THERAPIST", token=S)
check(status == 403 and body["error"] == "role_system", "los roles base no se pueden borrar")

print("\n3. Usuarios y anti-escalada de privilegios")
users = overview(S)["users"]
superadmin_id = find(users, email=E2E_SUPERADMIN)["id"]
therapist_id = find(users, email=E2E_THERAPIST)["id"]
status, body = call("POST", "/admin/users", {"email": "e2e.escalada@usbrain.test", "full_name": "Escalada",
                                              "role": "SUPERADMIN", "password": PASSWORD}, A)
check(status == 403, "el admin NO puede crear un super admin")
status, body = call("POST", f"/admin/users/{superadmin_id}", {"status": "DISABLED"}, A)
check(status == 403, "el admin NO puede modificar al super admin")
status, body = call("POST", f"/admin/users/{superadmin_id}", {"status": "DISABLED"}, S)
check(status == 403, "nadie puede deshabilitarse a sí mismo")
status, body = call("POST", "/admin/users", {"email": CREATED_THERAPIST, "full_name": "Terapeuta Nuevo",
                                              "role": "THERAPIST", "password": PASSWORD}, A)
check(status == 200, "el admin crea un terapeuta")
created_therapist_id = body["id"]
status, body = call("POST", "/admin/users", {"email": CREATED_PATIENT, "full_name": "Paciente Nuevo",
                                              "role": "PATIENT", "password": PASSWORD}, A)
check(status == 400 and body["error"] == "invalid_therapist", "un paciente sin terapeuta → 400")
status, body = call("POST", "/admin/users", {"email": CREATED_PATIENT, "full_name": "Paciente Nuevo",
                                              "role": "PATIENT", "password": PASSWORD,
                                              "therapist_id": created_therapist_id, "birth_date": "2004-03-15",
                                              "program": "Psicología", "condition": "Ansiedad"}, A)
check(status == 200, "el admin crea un paciente con su terapeuta, ficha y caso")
created_patient_id = body["id"]
status, first_login = login(CREATED_THERAPIST, "e2e-first-login-000001", PASSWORD)
check(status == 200 and first_login["status"] == "password_change_required",
      "usuario nuevo debe cambiar la contraseña temporal antes de 2FA")
status, body = call("POST", "/auth/mfa/verify", {
    "challenge_id": first_login["challenge_id"],
    "code": "123456",
})
check(status == 401 and body["error"] == "challenge_expired",
      "el desafío de cambio obligatorio no permite saltarse directamente a 2FA")
status, body = call("POST", "/auth/password/first-login", {
    "challenge_id": first_login["challenge_id"],
    "current_password": "clave-temporal-incorrecta",
    "new_password": "Nueva-Clave-Segura-123",
})
check(status == 401 and body["error"] == "invalid_credentials",
      "el cambio exige volver a validar la contraseña temporal")
status, body = call("POST", "/auth/password/first-login", {
    "challenge_id": first_login["challenge_id"],
    "current_password": PASSWORD,
    "new_password": "aaaaaaaaaa",
})
check(status == 400 and body["error"] == "weak_password",
      "contraseña sin mezcla de caracteres rechazada antes de 2FA")
for invalid_length_password in ("Abcdef1!X", "Abcdef1!Xyz"):
    status, body = call("POST", "/auth/password/first-login", {
        "challenge_id": first_login["challenge_id"],
        "current_password": PASSWORD,
        "new_password": invalid_length_password,
    })
    check(status == 400 and body["error"] == "weak_password",
          "contraseña inicial con longitud distinta de 10 rechazada")
new_password = "Abcdef1!X😀"
status, changed = call("POST", "/auth/password/first-login", {
    "challenge_id": first_login["challenge_id"],
    "current_password": PASSWORD,
    "new_password": new_password,
})
check(status == 200 and changed["status"] == "mfa_enrollment_required",
      "contraseña temporal actualizada en la base y luego solicita enrolar 2FA")
secret = changed["enrollment"]["secret"]
status, enrolled = call("POST", "/auth/mfa/verify", {
    "challenge_id": changed["challenge_id"],
    "code": totp(secret),
})
check(status == 200 and enrolled["user"]["mfa_enabled"],
      "el 2FA completa el acceso solo después de cambiar la contraseña")
status, body = login(CREATED_THERAPIST, "e2e-first-login-000002", PASSWORD)
check(status == 401 and body["error"] == "invalid_credentials",
      "la contraseña temporal deja de funcionar tras el cambio")
status, body = login(CREATED_THERAPIST, "e2e-first-login-000003", new_password)
check(status == 200 and body["status"] == "mfa_required",
      "la contraseña nueva permite avanzar al 2FA habitual")
status, body = call("POST", f"/admin/users/{created_patient_id}", {"role": "THERAPIST"}, A)
check(status == 409 and body["error"] == "profile_mismatch", "un paciente no pasa a un rol de terapeuta")
status, body = call("POST", f"/admin/users/{created_therapist_id}/actions", {"action": "unlock"}, A)
check(status == 200, "el admin desbloquea un usuario")
status, body = call("POST", f"/admin/users/{created_therapist_id}/actions",
                    {"action": "set_password", "password": PASSWORD + "-2"}, A)
check(status == 200, "el admin restablece una contraseña")
status, body = login(CREATED_THERAPIST, "e2e-reset-password-00001", PASSWORD + "-2")
check(status == 200 and body["status"] == "password_change_required",
      "la contraseña restablecida por administración también es temporal")

print("\n4. Un rol nuevo funciona sin tocar código")
status, body = call("POST", "/admin/users", {"email": SUPERVISOR_EMAIL, "full_name": "Supervisor E2E",
                                              "role": ROLE, "password": PASSWORD}, S)
check(status == 200, "el super admin crea un usuario con el rol nuevo")
supervisor, _ = enrolled_session(SUPERVISOR_EMAIL, "e2e-adm-supervisor-1")
V = supervisor["access_token"]
check(supervisor["user"]["profile"] == "THERAPIST", "el supervisor entra con interfaz de terapeuta")
status, data = call("GET", "/app/bootstrap", token=V)
patient = find(data["patients"], email=E2E_PATIENT)
check(status == 200 and patient is not None and find(data["patients"], email=CREATED_PATIENT) is not None,
      "case.view.all: ve pacientes que no tiene asignados")
behavior = {"patient_id": patient["id"], "name": "Conducta del supervisor",
            "operational_definition": "Prueba", "type": "ADAPTATIVA"}
status, body = call("POST", "/app/behaviors", behavior, V)
check(status == 200, "case.edit.all: edita un caso que no tiene asignado")
status, body = call("POST", f"/admin/roles/{ROLE}", {"permissions": ["case.view.all"]}, S)
check(status == 200, "el super admin le quita al rol el permiso de editar")
status, body = call("POST", "/app/behaviors", behavior, V)
check(status == 403, "…y el cambio aplica al instante, sin reiniciar nada")
status, body = call("DELETE", f"/admin/roles/{ROLE}", token=S)
check(status == 409 and body["error"] == "role_in_use", "no se borra un rol con usuarios")
supervisor_id = find(overview(S)["users"], email=SUPERVISOR_EMAIL)["id"]
status, body = call("POST", f"/admin/users/{supervisor_id}", {"role": "THERAPIST"}, S)
check(status == 200, "cambiar de rol a un usuario")
status, body = call("GET", "/app/bootstrap", token=V)
check(status == 401, "…cierra sus sesiones para que entre con la interfaz nueva")
status, body = call("DELETE", f"/admin/roles/{ROLE}", token=S)
check(status == 200, "ahora sí se borra el rol")

print("\n5. Permisos individuales: a UN usuario, sin cambiar su rol")
in_a_month = (datetime.date.today() + datetime.timedelta(days=30)).isoformat()
overrides_url = f"/admin/users/{therapist_id}/permissions"
grant_all = {"permission": "case.view.all", "effect": "GRANT", "reason": "Cubre la agenda de otro terapeuta",
             "expires_at": in_a_month}


def permissions_of(token):
    status, data = call("GET", "/app/bootstrap", token=token)
    assert status == 200, data
    return data["user"]["permissions"], data["patients"]


status, body = call("POST", overrides_url, {"overrides": [grant_all]}, A)
check(status == 403, "el admin (sin users.permissions) NO puede dar permisos individuales")
status, body = call("POST", f"/admin/users/{superadmin_id}/permissions", {"overrides": []}, S)
check(status == 403, "nadie cambia sus propios permisos")
status, body = call("POST", overrides_url, {"overrides": [{**grant_all, "reason": " "}]}, S)
check(status == 400 and body["error"] == "missing_reason", "cada excepción exige un motivo")
status, body = call("POST", overrides_url, {"overrides": [{**grant_all, "permission": "case.view.assigned"}]}, S)
check(status == 400 and body["error"] == "redundant_override", "otorgar algo que el rol ya tiene → 400")
status, body = call("POST", overrides_url, {"overrides": [{**grant_all, "expires_at": "2000-01-01"}]}, S)
check(status == 400 and body["error"] == "invalid_date", "un vencimiento pasado → 400")
status, body = call("POST", overrides_url, {"overrides": [grant_all, grant_all]}, S)
check(status == 400 and body["error"] == "duplicate_override", "un permiso repetido → 400")
_, patients = permissions_of(T)
check(find(patients, email=CREATED_PATIENT) is None, "antes: el terapeuta solo ve a sus pacientes")
status, body = call("POST", overrides_url, {"overrides": [grant_all]}, S)
check(status == 200, "el super admin le otorga case.view.all a ESE terapeuta (temporal)")
permissions, patients = permissions_of(T)
check("case.view.all" in permissions and find(patients, email=CREATED_PATIENT) is not None,
      "…aplica al instante: ahora ve pacientes que no tiene asignados")
users = overview(S)["users"]
override = find(find(users, email=E2E_THERAPIST)["overrides"], permission="case.view.all")
check(override["active"] and override["expires_at"] == in_a_month and override["granted_by"],
      "overview: la excepción muestra vencimiento, vigencia y quién la dio")
check("case.view.all" not in find(users, email=CREATED_THERAPIST)["permissions"],
      "…y no afecta a los demás usuarios del mismo rol")

status, body = call("POST", overrides_url, {"overrides": [
    {"permission": "roles.manage", "effect": "GRANT", "reason": "Prueba de escalada"}]}, S)
check(status == 200, "el super admin le otorga un permiso de administración")
status, body = call("POST", f"/admin/users/{therapist_id}", {"status": "DISABLED"}, A)
check(status == 403, "…y el admin ya no puede tocarlo: las reglas miran los permisos efectivos")

status, body = call("POST", overrides_url, {"overrides": [
    {"permission": "case.edit.assigned", "effect": "DENY", "reason": "Suspensión temporal de edición"}]}, S)
check(status == 200, "el super admin le QUITA case.edit.assigned sin cambiarle el rol")
e2e_patient = find(permissions_of(T)[1], email=E2E_PATIENT)
status, body = call("POST", "/app/behaviors", {"patient_id": e2e_patient["id"], "name": "Bloqueada",
                                                "operational_definition": "Prueba", "type": "ADAPTATIVA"}, T)
check(status == 403, "…ya no puede editar a sus propios pacientes")
check(find(overview(S)["users"], email=E2E_THERAPIST)["role"] == "THERAPIST", "…y sigue con su rol de terapeuta")

sql(f"""update usb_user_permissions set expires_at = systimestamp - interval '1' minute
         where user_id = {therapist_id} and permission_id = 'case.edit.assigned';""")
check("case.edit.assigned" in permissions_of(T)[0], "una excepción vencida deja de aplicar sola")
status, body = call("POST", overrides_url, {"overrides": []}, S)
check(status == 200 and not find(overview(S)["users"], email=E2E_THERAPIST).get("overrides"),
      "quitar todas las excepciones devuelve al usuario a los permisos de su rol")
check("case.view.all" not in permissions_of(T)[0], "…sin case.view.all")

print("\n6. Olvido de contraseña (con la app autenticadora)")
patient_tokens, secret = enrolled_session(E2E_PATIENT, "e2e-adm-patient-0001")
status, unknown = call("POST", "/auth/password/forgot", {"email": "nadie@usbrain.test", "device_id": "e2e-forgot-00001"})
status2, known = call("POST", "/auth/password/forgot", {"email": E2E_PATIENT, "device_id": "e2e-forgot-00001"})
check(status == status2 == 200 and unknown.keys() == known.keys() and unknown["message"] == known["message"],
      "correo existente o no: la respuesta es idéntica (no revela usuarios)")
status, body = call("POST", "/auth/password/reset", {"reset_id": unknown["reset_id"], "code": "123456",
                                                     "new_password": "Nueva1!Abc"})
check(status == 401 and body["error"] == "invalid_code", "un reset de correo inexistente falla como código malo")
status, body = call("POST", "/auth/mfa/verify", {"challenge_id": known["reset_id"], "code": totp(secret)})
check(status == 401, "el reset_id NO sirve para iniciar sesión (sin contraseña)")
status, body = call("POST", "/auth/password/reset", {"reset_id": known["reset_id"], "code": "000000",
                                                     "new_password": "Nueva1!Abc"})
check(status == 401 and body["error"] == "invalid_code", "código incorrecto → 401")
wait_next_step()
status, body = call("POST", "/auth/password/reset", {
    "reset_id": known["reset_id"],
    "code": totp(secret),
    "new_password": "aaaaaaaaaa",
})
check(status == 400 and body["error"] == "weak_password",
      "recuperación rechaza contraseña que no cumple los grupos requeridos")
for invalid_length_password in ("Abcdef1!X", "Abcdef1!Xyz"):
    status, body = call("POST", "/auth/password/reset", {
        "reset_id": known["reset_id"],
        "code": totp(secret),
        "new_password": invalid_length_password,
    })
    check(status == 400 and body["error"] == "weak_password",
          "recuperación rechaza contraseña con longitud distinta de 10")
new_password = "Nueva1!Abc"
status, body = call("POST", "/auth/password/reset", {"reset_id": known["reset_id"], "code": totp(secret),
                                                     "new_password": new_password})
check(status == 200, "código correcto → contraseña cambiada")
status, body = call("GET", "/app/bootstrap", token=patient_tokens["access_token"])
check(status == 401, "las sesiones abiertas con la contraseña anterior quedan cerradas")
status, body = login(E2E_PATIENT, "e2e-adm-patient-0001")
check(status == 401, "la contraseña anterior ya no sirve")
status, body = login(E2E_PATIENT, "e2e-adm-patient-0001", new_password)
check(status == 200 and body["status"] == "mfa_required", "la nueva sí, y sigue pidiendo 2FA")

print("\nLimpiando…")
for email in (SUPERVISOR_EMAIL, CREATED_PATIENT, CREATED_THERAPIST):
    delete_user(email)
print(f"\n✔ {passed()} verificaciones pasaron.")
