#!/usr/bin/env python3
"""Prueba end-to-end de roles dinámicos, administración y olvido de contraseña.

Uso:  python3 backend/tests/admin_e2e.py

SOLO PARA LA BASE DE DESARROLLO. Usa usuarios e2e.*@usbrain.test y el rol de prueba
SUPERVISOR_E2E; los crea y los borra. No toca los usuarios ni roles de demostración.
"""
import datetime
import time

from support import (E2E_ADMIN, E2E_PATIENT, E2E_SUPERADMIN, E2E_THERAPIST, PASSWORD, call, check,
                     delete_role, delete_user, enrolled_session, ensure_test_users, login, passed, totp,
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
check(len(data["permissions"]) >= 11 and find(data["roles"], id="SUPERADMIN")["is_locked"],
      "overview: catálogo de permisos y rol SUPERADMIN bloqueado")
therapists = [t["id"] for t in data["therapists"]]
check(find(data["users"], email=E2E_THERAPIST)["id"] in therapists
      and find(data["users"], email=E2E_SUPERADMIN)["id"] not in therapists,
      "los candidatos a terapeuta son roles de terapeuta (no el super admin)")
check(data["events"] is not None and len(data["events"]) > 0, "el super admin ve la bitácora")
check(overview(A)["users"] is not None, "el admin ve los usuarios")
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
status, body = call("POST", f"/admin/users/{created_patient_id}", {"role": "THERAPIST"}, A)
check(status == 409 and body["error"] == "profile_mismatch", "un paciente no pasa a un rol de terapeuta")
status, body = call("POST", f"/admin/users/{created_therapist_id}/actions", {"action": "unlock"}, A)
check(status == 200, "el admin desbloquea un usuario")
status, body = call("POST", f"/admin/users/{created_therapist_id}/actions",
                    {"action": "set_password", "password": PASSWORD + "-2"}, A)
check(status == 200, "el admin restablece una contraseña")

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

print("\n5. Olvido de contraseña (con la app autenticadora)")
patient_tokens, secret = enrolled_session(E2E_PATIENT, "e2e-adm-patient-0001")
status, unknown = call("POST", "/auth/password/forgot", {"email": "nadie@usbrain.test", "device_id": "e2e-forgot-00001"})
status2, known = call("POST", "/auth/password/forgot", {"email": E2E_PATIENT, "device_id": "e2e-forgot-00001"})
check(status == status2 == 200 and unknown.keys() == known.keys() and unknown["message"] == known["message"],
      "correo existente o no: la respuesta es idéntica (no revela usuarios)")
status, body = call("POST", "/auth/password/reset", {"reset_id": unknown["reset_id"], "code": "123456",
                                                     "new_password": "OtraClave-Segura-1"})
check(status == 401 and body["error"] == "invalid_code", "un reset de correo inexistente falla como código malo")
status, body = call("POST", "/auth/mfa/verify", {"challenge_id": known["reset_id"], "code": totp(secret)})
check(status == 401, "el reset_id NO sirve para iniciar sesión (sin contraseña)")
status, body = call("POST", "/auth/password/reset", {"reset_id": known["reset_id"], "code": "000000",
                                                     "new_password": "OtraClave-Segura-1"})
check(status == 401 and body["error"] == "invalid_code", "código incorrecto → 401")
status, body = call("POST", "/auth/password/reset", {"reset_id": known["reset_id"], "code": totp(secret),
                                                     "new_password": "corta"})
check(status == 400 and body["error"] == "weak_password", "contraseña débil → 400 sin gastar el código")
wait_next_step()
new_password = "OtraClave-Segura-1"
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
