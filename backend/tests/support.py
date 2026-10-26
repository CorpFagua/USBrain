"""Utilidades compartidas por las pruebas end-to-end (solo base de desarrollo)."""
import base64
import hashlib
import hmac
import json
import os
import pathlib
import shlex
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request

BACKEND = pathlib.Path(__file__).resolve().parent.parent
# Mismo archivo que lee install.sh (bash): KEY=valor, con comillas opcionales.
ENV = {
    key: value.strip().strip('"').strip("'")
    for key, value in (
        line.split("=", 1)
        for line in pathlib.Path(os.environ.get("USBRAIN_ENV", BACKEND / ".env")).read_text().splitlines()
        if line and not line.startswith("#") and "=" in line
    )
}
API = ENV["API_BASE"]
PASSWORD = ENV["DEV_SEED_PASSWORD"]

_passed = 0


def check(condition, label):
    global _passed
    if not condition:
        print(f"  FALLA  {label}")
        sys.exit(1)
    _passed += 1
    print(f"  ok     {label}")


def passed():
    return _passed


def call(method, path, body=None, token=None):
    request = urllib.request.Request(API + path, method=method)
    # ORDS rechaza (400) un POST con Content-Type JSON y cuerpo vacío.
    if body is not None:
        request.add_header("Content-Type", "application/json")
    request.add_header("User-Agent", "usbrain-e2e")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(request, data) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b"{}")


def sql(statement):
    """Ejecuta PL/SQL como USBRAIN dentro del contenedor (credenciales por stdin)."""
    # Mismo modo de conexión que install.sh: dentro del contenedor o con SQLcl/sqlplus local.
    service = ENV.get("ORACLE_SERVICE") or "FREEPDB1"
    if ENV.get("ORACLE_MODE", "docker") == "local":
        target = f'//{ENV.get("DB_HOST") or "localhost"}:{ENV.get("DB_PORT") or "1521"}/{service}'
        command = shlex.split(ENV.get("SQL_CLIENT") or "sql") + ["-S", "/nolog"]
    else:
        target = f"//localhost:1521/{service}"
        command = ["docker", "exec", "-i", ENV.get("ORACLE_CONTAINER") or "oracle-db", "sqlplus", "-s", "-l", "/nolog"]
    script = (
        f'connect usbrain/"{ENV["USBRAIN_DB_PASSWORD"]}"@{target}\n'
        "whenever sqlerror exit failure\n"
        f"begin {statement} commit; end;\n/\nexit\n"
    )
    result = subprocess.run(command, input=script.encode(), capture_output=True)
    if result.returncode != 0:
        raise RuntimeError("Falló el SQL de preparación:\n" + result.stdout.decode(errors="replace"))


# Usuarios exclusivos de las pruebas: así nunca se toca el 2FA de los usuarios de demo.
E2E_THERAPIST = "e2e.therapist@usbrain.test"
E2E_PATIENT = "e2e.patient@usbrain.test"
E2E_ADMIN = "e2e.admin@usbrain.test"
E2E_SUPERADMIN = "e2e.superadmin@usbrain.test"


def ensure_test_users():
    """Crea los usuarios de prueba si faltan y vacía el caso del paciente de prueba."""
    sql(f"""
      declare
        l_id number;
        procedure ensure(p_email varchar2, p_name varchar2, p_role varchar2) is
          l_count number;
        begin
          select count(*) into l_count from usb_users where email = p_email;
          if l_count = 0 then
            l_id := usb_auth.create_user(p_email, p_name, p_role, '{PASSWORD}');
          end if;
        end;
      begin
        ensure('{E2E_THERAPIST}', 'Terapeuta E2E', 'THERAPIST');
        ensure('{E2E_ADMIN}', 'Admin E2E', 'ADMIN');
        ensure('{E2E_SUPERADMIN}', 'Super admin E2E', 'SUPERADMIN');
        select count(*) into l_id from usb_users where email = '{E2E_PATIENT}';
        if l_id = 0 then
          l_id := usb_domain.create_patient('{E2E_PATIENT}', 'Paciente E2E', '{PASSWORD}',
                    '{E2E_THERAPIST}', date '2000-01-01', 'Pruebas', 'Caso de prueba');
        end if;
        for c in (select c.case_id, c.patient_id from usb_cases c join usb_users u on u.user_id = c.patient_id
                   where u.email = '{E2E_PATIENT}') loop
          delete from usb_task_entries where task_id in
            (select t.task_id from usb_tasks t join usb_behaviors b on b.behavior_id = t.behavior_id where b.case_id = c.case_id);
          delete from usb_widget_entries where task_id in
            (select t.task_id from usb_tasks t join usb_behaviors b on b.behavior_id = t.behavior_id where b.case_id = c.case_id);
          delete from usb_tasks where behavior_id in (select behavior_id from usb_behaviors where case_id = c.case_id);
          update usb_behaviors set replacement_behavior_id = null where case_id = c.case_id;
          delete from usb_behaviors where case_id = c.case_id;
          delete from usb_patient_diagnoses where patient_id = c.patient_id;
        end loop;
      end;""")


def delete_user(email):
    """Borra un usuario de prueba y todo lo que cuelga de él (solo usuarios e2e)."""
    assert email.endswith("@usbrain.test")
    sql(f"""
      for u in (select user_id from usb_users where email = '{email}') loop
        -- Lo que creó en casos ajenos se conserva, sin autor.
        update usb_behaviors set created_by = null where created_by = u.user_id;
        update usb_tasks set created_by = null where created_by = u.user_id;
        update usb_widgets set created_by = null where created_by = u.user_id;
        update usb_behavioral_functions set created_by = null where created_by = u.user_id;
        update usb_patient_diagnoses set assigned_by = null where assigned_by = u.user_id;
        update usb_task_entries set recorded_by = null where recorded_by = u.user_id;
        update usb_widget_entries set recorded_by = null where recorded_by = u.user_id;
        update usb_patient_profiles set therapist_id = null where therapist_id = u.user_id;
        delete from usb_sessions where user_id = u.user_id;
        delete from usb_login_challenges where user_id = u.user_id;
        delete from usb_recovery_codes where user_id = u.user_id;
        delete from usb_auth_events where user_id = u.user_id;
        for c in (select case_id from usb_cases where patient_id = u.user_id) loop
          delete from usb_task_entries where task_id in (select t.task_id from usb_tasks t
            join usb_behaviors b on b.behavior_id = t.behavior_id where b.case_id = c.case_id);
          delete from usb_widget_entries where task_id in (select t.task_id from usb_tasks t
            join usb_behaviors b on b.behavior_id = t.behavior_id where b.case_id = c.case_id);
          delete from usb_tasks where behavior_id in (select behavior_id from usb_behaviors where case_id = c.case_id);
          delete from usb_behaviors where case_id = c.case_id;
          delete from usb_cases where case_id = c.case_id;
        end loop;
        delete from usb_patient_diagnoses where patient_id = u.user_id;
        delete from usb_patient_profiles where user_id = u.user_id;
        delete from usb_users where user_id = u.user_id;
      end loop;""")


def delete_role(role_id):
    sql(f"delete from usb_roles where role_id = '{role_id}' and not is_system;")


def reset_user(email):
    sql(
        "for u in (select user_id from usb_users where email = '" + email + "') loop "
        "usb_auth.reset_mfa(u.user_id); usb_auth.set_password(u.user_id, '" + PASSWORD + "'); "
        "update usb_users set must_change_password = false where user_id = u.user_id; "
        "usb_auth.unlock_user(u.user_id); end loop;"
    )


def totp(secret_b32, at=None):
    key = base64.b32decode(secret_b32 + "=" * (-len(secret_b32) % 8))
    counter = int((at or time.time()) // 30)
    digest = hmac.new(key, struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    return f"{(struct.unpack('>I', digest[offset:offset + 4])[0] & 0x7FFFFFFF) % 1_000_000:06d}"


def wait_next_step():
    time.sleep(30 - time.time() % 30 + 1)


def login(email, device, password=PASSWORD):
    return call("POST", "/auth/login", {
        "email": email, "password": password, "device_id": device, "device_name": f"E2E {device}",
    })


def enrolled_session(email, device):
    """Reinicia el usuario, enrola su TOTP y devuelve (tokens, secreto)."""
    reset_user(email)
    status, body = login(email, device)
    assert status == 200, body
    secret = body["enrollment"]["secret"]
    status, tokens = call("POST", "/auth/mfa/verify", {"challenge_id": body["challenge_id"], "code": totp(secret)})
    assert status == 200, tokens
    return tokens, secret
