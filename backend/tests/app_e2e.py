#!/usr/bin/env python3
"""Prueba end-to-end de la API clínica (/v1/app) de USBrain contra ORDS.

Uso:  python3 backend/tests/app_e2e.py

SOLO PARA LA BASE DE DESARROLLO. Usa usuarios exclusivos (e2e.*@usbrain.test):
vacía el caso del paciente de prueba al iniciar y recorre todas las acciones de
las pantallas con los tres roles. No modifica los casos de demo (Ana, Daniel).
"""
import datetime

from support import (E2E_ADMIN, E2E_PATIENT, E2E_THERAPIST, call, check, enrolled_session,
                     ensure_test_users, passed)

print("Preparando usuarios de prueba…")
ensure_test_users()
therapist, _ = enrolled_session(E2E_THERAPIST, "e2e-app-therapist-01")
patient, _ = enrolled_session(E2E_PATIENT, "e2e-app-patient-0001")
admin, _ = enrolled_session(E2E_ADMIN, "e2e-app-admin-000001")
T, P, A = therapist["access_token"], patient["access_token"], admin["access_token"]
today = datetime.date.today().isoformat()


def bootstrap(token):
    status, body = call("GET", "/app/bootstrap", token=token)
    assert status == 200, body
    return body


def find(items, **match):
    return next(i for i in items or [] if all(i.get(k) == v for k, v in match.items()))


print("\n1. Bootstrap según el rol")
status, body = call("GET", "/app/bootstrap")
check(status == 401, "sin token → 401")
data = bootstrap(T)
check(data["user"]["role"] == "THERAPIST", "el terapeuta recibe su usuario")
check([p["email"] for p in data["patients"]] == [E2E_PATIENT], "el terapeuta solo ve a sus pacientes asignados")
check(len(data["catalogs"]["functions"]) >= 6 and len(data["catalogs"]["cie10"]) >= 8, "catálogos: funciones y CIE-10")
check(find(data["catalogs"]["widgets"], id="w-seleccion")["options"][0] == "Ansiedad", "widgets con sus opciones")
patient_id = data["patients"][0]["id"]
admin_data = bootstrap(A)
ana = find(admin_data["patients"], email="ana.torres@usbbog.edu.co")
check(len(admin_data["patients"]) >= 3, "el admin ve todos los casos")
anxiety = find(ana["case"]["behaviors"], name="Episodios de ansiedad en clase")
check(len(anxiety["baseline_entries"]) == 30 and anxiety["baseline_open"] is False and anxiety["phase_seq"] == 2,
      "caso demo de Ana: 30 días de línea base (A1) y ahora en intervención (B1)")
check([ph["seq"] for ph in anxiety["phases"]] == [1, 2] and anxiety["phases"][0]["ended_on"] == "2026-10-01",
      "Ana: A1 del 1/9 al 1/10, B1 desde el 1/10 (coherente con sus registros)")
ana_task = anxiety["tasks"][0]["id"]

print("\n2. El terapeuta construye el caso")
status, body = call("POST", "/app/behavioral-functions", {"label": "Autorregulación E2E"}, T)
check(status == 200 and body["id"].startswith("fn"), "crear función conductual")
function_id = body["id"]
status, body = call("POST", "/app/behavioral-functions", {"label": "autorregulación e2e"}, T)
check(body["id"] == function_id, "una función con el mismo nombre no se duplica")
status, body = call("POST", "/app/behaviors", {
    "patient_id": patient_id, "name": "Participar en clase E2E", "operational_definition": "Levanta la mano.",
    "type": "ADAPTATIVA"}, T)
check(status == 200, "crear conducta adaptativa")
adaptive_id = body["id"]
status, body = call("POST", "/app/behaviors", {
    "patient_id": patient_id, "name": "Evitar exposiciones E2E", "operational_definition": "Sale del salón.",
    "type": "DESADAPTATIVA", "function_id": function_id, "replacement_behavior_id": adaptive_id}, T)
check(status == 200, "crear conducta desadaptativa con función y conducta de reemplazo")
maladaptive_id = body["id"]
status, body = call("POST", "/app/behaviors", {"patient_id": patient_id, "name": "", "type": "ADAPTATIVA"}, T)
check(status == 400 and body["error"] == "missing_fields", "conducta sin nombre → 400")
status, body = call("POST", "/app/behaviors", {
    "patient_id": ana["id"], "name": "x", "operational_definition": "x", "type": "ADAPTATIVA"}, T)
check(status == 404, "no puede crear conductas en pacientes de otro terapeuta")
status, body = call("POST", f"/app/behaviors/{maladaptive_id}/baseline", {"dimensions": ["FRECUENCIA", "INTENSIDAD"]}, T)
check(status == 200, "configurar línea base con dos dimensiones")
behavior = find(find(bootstrap(T)["patients"], id=patient_id)["case"]["behaviors"], id=maladaptive_id)
check([t["dimension"] for t in behavior["tasks"]] == ["FRECUENCIA", "INTENSIDAD"]
      and behavior["baseline_dimensions"] == ["FRECUENCIA", "INTENSIDAD"],
      "se crean las actividades diarias de línea base")
check(behavior["function_id"] == function_id and behavior["replacement_behavior_id"] == adaptive_id,
      "la conducta guarda su función y su reemplazo")
frequency_task = behavior["tasks"][0]["id"]

status, body = call("POST", "/app/widgets", {
    "name": "Emociones E2E", "value_kind": "MULTI_SELECT", "options": ["Calma", "Miedo"], "description": "Prueba"}, T)
check(status == 200 and body["id"].startswith("cw"), "crear medición personalizada de selección múltiple")
widget_id = body["id"]
status, body = call("POST", "/app/widgets", {"name": "Sin opciones", "value_kind": "MULTI_SELECT"}, T)
check(status == 400, "selección múltiple sin opciones → 400")

print("\n3. Diagnósticos CIE-10")
status, _ = call("POST", f"/app/patients/{patient_id}/diagnoses", {"code": "f41.1", "label": "Ansiedad generalizada"}, T)
check(status == 200, "asignar diagnóstico")
call("POST", f"/app/patients/{patient_id}/diagnoses", {"code": "F41.1", "label": "Duplicado"}, T)
status, body = call("POST", f"/app/patients/{patient_id}/diagnoses", {"code": "XYZ"}, T)
check(status == 400 and body["error"] == "invalid_code", "código CIE-10 inválido → 400")
diagnoses = find(bootstrap(T)["patients"], id=patient_id)["diagnoses"]
check(diagnoses == [{"code": "F41.1", "label": "Ansiedad generalizada"}], "el código se normaliza y no se duplica")
status, _ = call("DELETE", f"/app/patients/{patient_id}/diagnoses/F41.1", token=T)
check(status == 200 and find(bootstrap(T)["patients"], id=patient_id)["diagnoses"] is None, "quitar diagnóstico")

print("\n4. El paciente registra")
data = bootstrap(P)
check(data["user"]["role"] == "PATIENT" and [p["id"] for p in data["patients"]] == [patient_id],
      "el paciente solo ve su propio caso")
check(find(find(data["patients"][0]["case"]["behaviors"], id=maladaptive_id)["tasks"], id=frequency_task)["status"]
      == "PENDIENTE", "la actividad diaria aparece pendiente")
status, body = call("POST", f"/app/tasks/{frequency_task}/entries",
                    {"date": today, "value": 4, "note": "Cuatro veces", "observation_minutes": 45}, P)
check(status == 200, "registrar observación de línea base")
status, body = call("POST", f"/app/tasks/{frequency_task}/entries", {"date": today, "value": 2}, P)
check(status == 409 and body["error"] == "duplicate_entry", "segundo registro del mismo día → 409")
tomorrow = (datetime.date.today() + datetime.timedelta(days=2)).isoformat()
status, body = call("POST", f"/app/tasks/{frequency_task}/entries", {"date": tomorrow, "value": 1}, P)
check(status == 400 and body["error"] == "invalid_date", "fecha futura → 400")
status, body = call("POST", f"/app/tasks/{frequency_task}/entries", {"date": today, "value": 1}, T)
check(status == 403, "el terapeuta no puede registrar por el paciente")
status, body = call("POST", f"/app/tasks/{ana_task}/entries", {"date": today, "value": 1}, P)
check(status == 404, "no puede registrar en actividades de otro paciente")
behavior = find(bootstrap(P)["patients"][0]["case"]["behaviors"], id=maladaptive_id)
check(len(behavior["baseline_entries"]) == 1 and behavior["baseline_entries"][0]["observation_minutes"] == 45,
      "el registro cuenta como observación de línea base")
check(find(behavior["tasks"], id=frequency_task)["status"] == "COMPLETADA", "la actividad queda completada hoy")

print("\n5. Fases A-B-A-B: una a la vez, decididas por el terapeuta")
yesterday = (datetime.date.today() - datetime.timedelta(days=1)).isoformat()
before = (datetime.date.today() - datetime.timedelta(days=2)).isoformat()
intensity_task = behavior["tasks"][1]["id"]


def current(token=T):
    return find(find(bootstrap(token)["patients"], id=patient_id)["case"]["behaviors"], id=maladaptive_id)


status, body = call("POST", f"/app/behaviors/{maladaptive_id}/next-phase", token=T)
check(status == 409 and body["error"] == "insufficient_observations",
      "terminar A1 con 1 día de registro → 409 (mínimo configurable, 3 por defecto)")
status, body = call("POST", "/app/tasks", {"behavior_id": maladaptive_id, "title": "Respiración guiada",
                                           "task_type": "EJERCICIO_PRACTICA", "metric_unit": "SOLO_COMPLETADO"}, T)
check(status == 409 and body["error"] == "baseline_no_treatment", "en la línea base no se asignan ejercicios")
status, body = call("POST", "/app/tasks", {"behavior_id": maladaptive_id, "title": "Duración",
                                           "task_type": "REGISTRO_AUTONOMO", "dimension": "DURACION"}, T)
check(status == 400 and body["error"] == "dimension_not_measured",
      "un registro autónomo de otra dimensión no tendría línea base → 400")
status, body = call("POST", "/app/tasks", {
    "behavior_id": maladaptive_id, "title": "Emociones del día", "instructions": "Marca lo que sentiste.",
    "widget_id": widget_id, "phase": "INTERVENCION", "is_recurring": True, "frequency": "Diaria",
    "reminder_active": True, "assigned_date": "Hoy", "therapeutic_goal": "Identificar emociones"}, T)
check(status == 200, "una medición configurable sí se puede crear en la línea base")
widget_task = body["id"]
task = find(current()["tasks"], id=widget_task)
check(task["phase"] == "LINEA_BASE" and task["phase_seq"] == 1 and current()["baseline_open"],
      "la fase la pone el servidor (ignora la que manda el cliente) y no cierra la línea base")
status, body = call("POST", "/app/tasks", {"behavior_id": maladaptive_id, "title": ""}, T)
check(status == 400, "actividad sin nombre → 400")
entry = {"date": today, "time": "18:30", "note": "Tarde tranquila"}
status, body = call("POST", f"/app/tasks/{widget_task}/widget-entries", {**entry, "value": {"values": ["Furia"]}}, P)
check(status == 400 and body["error"] == "invalid_value", "opción que no existe en la medición → 400")
status, body = call("POST", f"/app/tasks/{widget_task}/widget-entries", {**entry, "value": {"values": ["Calma"]}}, P)
check(status == 200 and body["id"].startswith("we"), "registrar medición de selección múltiple")
status, body = call("POST", f"/app/tasks/{widget_task}/widget-entries", {**entry, "value": {"values": ["Miedo"]}}, P)
check(status == 409, "segundo registro del mismo día → 409")
task = find(find(bootstrap(P)["patients"][0]["case"]["behaviors"], id=maladaptive_id)["tasks"], id=widget_task)
check(task["widget_entries"][0]["selected_options"] == ["Calma"] and task["widget_entries"][0]["time"] == "18:30",
      "el registro guarda opciones, hora y nota")

for day in (yesterday, before):
    call("POST", f"/app/tasks/{frequency_task}/entries", {"date": day, "value": 3}, P)
status, body = call("POST", f"/app/behaviors/{maladaptive_id}/next-phase", token=T)
check(status == 409 and "intensidad" in body["message"],
      "frecuencia ya tiene 3 días, pero intensidad no: cada dimensión medida necesita el mínimo")
for day in (today, yesterday, before):
    call("POST", f"/app/tasks/{intensity_task}/entries", {"date": day, "value": 6}, P)
status, body = call("POST", f"/app/behaviors/{maladaptive_id}/next-phase", token=T)
check(status == 200 and body["phase_seq"] == 2, "con el mínimo en ambas dimensiones, el terapeuta inicia B1")
behavior = current()
check(behavior["baseline_open"] is False and [ph["seq"] for ph in behavior["phases"]] == [1, 2]
      and behavior["phases"][0]["ended_on"],
      "la conducta queda en B1 y A1 registra su cierre")
check(all(t["retired"] and t["status"] == "CERRADA" for t in behavior["tasks"] if t["phase_seq"] == 1),
      "las actividades de A1 se cierran y quedan como historial")
status, body = call("POST", f"/app/tasks/{frequency_task}/entries", {"date": today, "value": 1}, P)
check(status == 409 and body["error"] == "phase_closed", "no se registra en una actividad de una fase terminada")
status, body = call("POST", f"/app/tasks/{widget_task}/widget-entries", {**entry, "date": yesterday, "value": {"values": ["Calma"]}}, P)
check(status == 409 and body["error"] == "phase_closed", "…tampoco en una medición configurable de esa fase")
check(len(behavior["baseline_entries"]) == 6, "A1 conserva sus 6 registros (3 días de frecuencia y 3 de intensidad)")

status, body = call("POST", f"/app/behaviors/{maladaptive_id}/baseline", {"dimensions": ["DURACION"]}, T)
check(status == 200, "el terapeuta crea el registro diario de B1 (decide cuándo; no es automático)")
behavior = current()
b1_tasks = [t for t in behavior["tasks"] if t["phase_seq"] == 2 and t["task_type"] == "REGISTRO_AUTONOMO"]
check(sorted(t["dimension"] for t in b1_tasks) == ["FRECUENCIA", "INTENSIDAD"] and all(t["phase"] == "INTERVENCION" for t in b1_tasks),
      "B1 mide las mismas dimensiones que A1 (no se pueden cambiar después del primer registro)")
status, body = call("POST", "/app/tasks", {"behavior_id": maladaptive_id, "title": "Respiración guiada",
                                           "task_type": "EJERCICIO_PRACTICA", "metric_unit": "SOLO_COMPLETADO"}, T)
check(status == 200, "en la intervención sí se asignan ejercicios")
for t in b1_tasks:
    for day in (today, yesterday, before):
        call("POST", f"/app/tasks/{t['id']}/entries", {"date": day, "value": 2}, P)
status, body = call("POST", f"/app/behaviors/{maladaptive_id}/next-phase", token=T)
check(status == 200 and body["phase_seq"] == 3, "retirar la intervención abre una nueva línea base A2 (diseño A-B-A)")
behavior = current()
check(behavior["baseline_open"] and behavior["phase_seq"] == 3 and len(behavior["phases"]) == 3,
      "una sola fase activa: A2")
status, body = call("POST", "/app/tasks", {"behavior_id": maladaptive_id, "title": "Respiración guiada",
                                           "task_type": "EJERCICIO_PRACTICA", "metric_unit": "SOLO_COMPLETADO"}, T)
check(status == 409, "en A2 vuelve a regir: sin tratamiento durante la línea base")

print("\n6. Permisos por rol")
status, body = call("POST", "/app/behaviors", {
    "patient_id": patient_id, "name": "x", "operational_definition": "x", "type": "ADAPTATIVA"}, P)
check(status == 403, "el paciente no puede crear conductas")
status, body = call("POST", "/app/widgets", {"name": "x", "value_kind": "TEXT"}, A)
check(status == 403, "el admin consulta pero no edita casos")
status, body = call("POST", f"/app/behaviors/{maladaptive_id}/close-baseline", token=P)
check(status == 403, "el paciente no puede cerrar la línea base")

print(f"\n✔ {passed()} verificaciones pasaron.")
