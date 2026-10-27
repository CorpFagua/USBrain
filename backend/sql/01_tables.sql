-- =============================================================================
-- USBrain · 01_tables.sql
-- Tablas de identidad, 2FA y sesiones. Se ejecuta como USBRAIN.
-- Ningún token ni código de recuperación se guarda en claro: solo su hash.
-- El secreto TOTP se guarda cifrado (AES-256) con la llave de usb_app_keys.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

-- Parámetros de la política de autenticación (editables desde APEX o SQL).
create table if not exists usb_auth_config (
  config_key   varchar2(64)  primary key,
  config_value varchar2(400) not null,
  description  varchar2(400)
);

merge into usb_auth_config c
using (
  select 'ACCESS_TTL_SECONDS' k, '600' v, 'Vida del access token (segundos). Corto, como en la banca.' d from dual union all
  select 'REFRESH_IDLE_DAYS', '30', 'Días sin abrir la app antes de exigir login + 2FA de nuevo.' from dual union all
  select 'SESSION_ABSOLUTE_DAYS', '90', 'Vida máxima de una sesión aunque se use a diario.' from dual union all
  select 'CHALLENGE_TTL_SECONDS', '300', 'Tiempo para ingresar el código 2FA tras la contraseña.' from dual union all
  select 'CHALLENGE_MAX_ATTEMPTS', '5', 'Intentos de código 2FA por desafío.' from dual union all
  select 'MAX_FAILED_LOGINS', '5', 'Fallos consecutivos (contraseña o 2FA) antes de bloquear.' from dual union all
  select 'LOCKOUT_MINUTES', '15', 'Duración del bloqueo temporal de la cuenta.' from dual union all
  select 'PBKDF2_ITERATIONS', '310000', 'Iteraciones PBKDF2-HMAC-SHA256 para contraseñas nuevas.' from dual union all
  select 'TOTP_WINDOW_STEPS', '1', 'Pasos de 30 s de tolerancia al desfase de reloj.' from dual union all
  select 'MAX_SESSIONS_PER_USER', '1', 'Sesiones activas por usuario. Con 1, iniciar sesión en otro dispositivo o pestaña cierra la anterior.' from dual union all
  select 'TOTP_ISSUER', 'USBrain', 'Nombre que muestra la app autenticadora.' from dual
) s on (c.config_key = s.k)
when not matched then insert (config_key, config_value, description) values (s.k, s.v, s.d);

-- Llaves de la aplicación. En producción: mover a Oracle Wallet / TDE.
create table if not exists usb_app_keys (
  key_name   varchar2(64) primary key,
  key_value  raw(32)      not null,
  created_at timestamp with time zone default systimestamp not null
);

insert into usb_app_keys (key_name, key_value)
select 'TOTP_ENC', sys.dbms_crypto.randombytes(32) from dual
 where not exists (select 1 from usb_app_keys where key_name = 'TOTP_ENC');
insert into usb_app_keys (key_name, key_value)
select 'PEPPER', sys.dbms_crypto.randombytes(32) from dual
 where not exists (select 1 from usb_app_keys where key_name = 'PEPPER');

-- --------------------------------------------------------------------------
-- Roles y permisos dinámicos (RBAC). El código revisa PERMISOS, nunca nombres de
-- rol: un rol es solo un conjunto de permisos que el super administrador arma.
--   app_profile: qué interfaz ve el rol en la app (paciente, terapeuta o administración).
--   is_system:   rol semilla, no se puede borrar.
--   is_locked:   sus permisos no se pueden cambiar (SUPERADMIN).
-- --------------------------------------------------------------------------
-- sensitive: da poder de administración o acceso a TODOS los casos. Solo quien ya lo
-- tiene puede asignar un rol que lo incluya (evita escalar privilegios).
create table if not exists usb_permissions (
  permission_id varchar2(60)  primary key,
  name          varchar2(200) not null,
  category      varchar2(60)  not null,
  description   varchar2(400),
  sensitive     boolean default false not null
);

declare
  l_count number;
begin
  select count(*) into l_count from user_tab_columns
   where table_name = 'USB_PERMISSIONS' and column_name = 'SENSITIVE';
  if l_count = 0 then
    execute immediate 'alter table usb_permissions add (sensitive boolean default false not null)';
  end if;
end;
/

create table if not exists usb_roles (
  role_id     varchar2(40)  primary key,
  name        varchar2(100) not null,
  description varchar2(400),
  app_profile varchar2(20)  not null,
  is_system   boolean default false not null,
  is_locked   boolean default false not null,
  created_at  timestamp with time zone default systimestamp not null,
  updated_at  timestamp with time zone default systimestamp not null,
  constraint usb_roles_id_ck check (regexp_like(role_id, '^[A-Z][A-Z0-9_]{1,39}$')),
  constraint usb_roles_profile_ck check (app_profile in ('PATIENT', 'THERAPIST', 'ADMIN'))
);

create table if not exists usb_role_permissions (
  role_id       varchar2(40) not null references usb_roles on delete cascade,
  permission_id varchar2(60) not null references usb_permissions,
  constraint usb_role_permissions_pk primary key (role_id, permission_id)
);

merge into usb_permissions p
using (
  select 'case.view.own' id, 'Ver su propio caso' name, 'Casos' cat,
         'El consultante ve sus conductas, actividades y gráficas.' d from dual union all
  select 'case.view.assigned', 'Ver pacientes asignados', 'Casos',
         'Ve los casos de los pacientes que tiene asignados. Lo hace elegible como terapeuta.' from dual union all
  select 'case.view.all', 'Ver todos los casos', 'Casos',
         'Ve los casos de todos los pacientes del sistema.' from dual union all
  select 'case.edit.assigned', 'Editar casos asignados', 'Casos',
         'Crea conductas, línea base, actividades y diagnósticos de sus pacientes asignados.' from dual union all
  select 'case.edit.all', 'Editar cualquier caso', 'Casos',
         'Crea conductas, línea base, actividades y diagnósticos de cualquier paciente.' from dual union all
  select 'entry.record', 'Registrar actividades propias', 'Casos',
         'El consultante registra las actividades de su propio caso.' from dual union all
  select 'catalog.manage', 'Gestionar catálogos', 'Catálogos',
         'Crea mediciones personalizadas y funciones conductuales.' from dual union all
  select 'users.view', 'Ver usuarios', 'Administración',
         'Consulta la lista de usuarios, sus roles y su estado.' from dual union all
  select 'users.manage', 'Gestionar usuarios', 'Administración',
         'Crea usuarios, cambia su rol, los deshabilita, desbloquea y restablece su acceso.' from dual union all
  select 'roles.manage', 'Gestionar roles y permisos', 'Administración',
         'Crea, edita y borra roles y decide qué permisos tiene cada uno.' from dual union all
  select 'users.permissions', 'Otorgar permisos individuales', 'Administración',
         'Da o quita un permiso a un usuario concreto sin cambiar su rol (con motivo y vencimiento opcional).' from dual union all
  select 'audit.view', 'Ver bitácora de seguridad', 'Administración',
         'Consulta los inicios de sesión, fallos, bloqueos y cambios de administración.' from dual
) s on (p.permission_id = s.id)
when matched then update set p.name = s.name, p.category = s.cat, p.description = s.d,
  p.sensitive = (s.cat = 'Administración' or s.id in ('case.view.all', 'case.edit.all'))
when not matched then insert (permission_id, name, category, description, sensitive)
values (s.id, s.name, s.cat, s.d, s.cat = 'Administración' or s.id in ('case.view.all', 'case.edit.all'));

merge into usb_roles r
using (
  select 'SUPERADMIN' id, 'Super administrador' name, 'ADMIN' prof, 1 locked,
         'Control total: gestiona roles, permisos y usuarios. Su rol no se puede modificar.' d from dual union all
  select 'ADMIN', 'Administrador', 'ADMIN', 0,
         'Gestiona usuarios y consulta todos los casos y la bitácora.' from dual union all
  select 'THERAPIST', 'Terapeuta', 'THERAPIST', 0,
         'Define conductas y asigna actividades a sus pacientes.' from dual union all
  select 'PATIENT', 'Paciente', 'PATIENT', 0,
         'Consulta su caso y registra las actividades que le asigna su terapeuta.' from dual
) s on (r.role_id = s.id)
when not matched then insert (role_id, name, description, app_profile, is_system, is_locked)
values (s.id, s.name, s.d, s.prof, true, s.locked = 1);

-- El super administrador siempre tiene TODOS los permisos (también los que se agreguen después).
insert into usb_role_permissions (role_id, permission_id)
select 'SUPERADMIN', permission_id from usb_permissions p
 where not exists (select 1 from usb_role_permissions rp
                    where rp.role_id = 'SUPERADMIN' and rp.permission_id = p.permission_id);

-- Permisos iniciales de los demás roles semilla: solo si el rol aún no tiene ninguno,
-- para no pisar lo que el super administrador haya cambiado.
insert into usb_role_permissions (role_id, permission_id)
select s.role_id, s.permission_id
  from (select 'ADMIN' role_id, 'case.view.all' permission_id from dual union all
        select 'ADMIN', 'users.view' from dual union all
        select 'ADMIN', 'users.manage' from dual union all
        select 'ADMIN', 'audit.view' from dual union all
        select 'THERAPIST', 'case.view.assigned' from dual union all
        select 'THERAPIST', 'case.edit.assigned' from dual union all
        select 'THERAPIST', 'catalog.manage' from dual union all
        select 'PATIENT', 'case.view.own' from dual union all
        select 'PATIENT', 'entry.record' from dual) s
 where not exists (select 1 from usb_role_permissions rp where rp.role_id = s.role_id);

create table if not exists usb_users (
  user_id             number generated always as identity primary key,
  email               varchar2(320) not null,
  full_name           varchar2(200) not null,
  role                varchar2(40)  not null,
  status              varchar2(20)  default 'ACTIVE' not null,
  password_hash       raw(32)       not null,
  password_salt       raw(16)       not null,
  password_iterations number(10)    not null,
  password_changed_at timestamp with time zone default systimestamp not null,
  must_change_password boolean default false not null,
  failed_logins       number(5)     default 0 not null,
  locked_until        timestamp with time zone,
  mfa_enabled         boolean       default false not null,
  totp_secret_enc     raw(128),
  totp_last_step      number(15),
  last_login_at       timestamp with time zone,
  created_at          timestamp with time zone default systimestamp not null,
  updated_at          timestamp with time zone default systimestamp not null,
  constraint usb_users_email_uk unique (email),
  constraint usb_users_email_ck check (email = lower(email)),
  constraint usb_users_role_fk foreign key (role) references usb_roles,
  constraint usb_users_status_ck check (status in ('ACTIVE', 'DISABLED'))
);

-- Excepciones por usuario sobre su rol (separación de roles): dar o quitar UN permiso
-- a UNA persona sin crear un rol nuevo ni tocar a los demás usuarios del rol.
--   GRANT: lo tiene aunque su rol no lo incluya.   DENY: no lo tiene aunque su rol sí.
--   reason:     justificación obligatoria (queda en la bitácora).
--   expires_at: acceso temporal; vencido deja de aplicar solo, sin ningún proceso.
create table if not exists usb_user_permissions (
  user_id       number       not null references usb_users on delete cascade,
  permission_id varchar2(60) not null references usb_permissions,
  effect        varchar2(5)  not null,
  reason        varchar2(400) not null,
  expires_at    timestamp with time zone,
  granted_by    number references usb_users on delete set null,
  created_at    timestamp with time zone default systimestamp not null,
  constraint usb_user_permissions_pk primary key (user_id, permission_id),
  constraint usb_user_permissions_effect_ck check (effect in ('GRANT', 'DENY'))
);

-- Permisos EFECTIVOS = (los del rol − DENY vigentes) ∪ GRANT vigentes.
-- Es la única fuente de verdad: todo chequeo de permisos lee de aquí (el estado
-- ACTIVE lo filtra quien consulta).
create or replace view usb_effective_permissions as
select u.user_id, rp.permission_id
  from usb_users u
  join usb_role_permissions rp on rp.role_id = u.role
 where not exists (select 1 from usb_user_permissions up
                    where up.user_id = u.user_id and up.permission_id = rp.permission_id
                      and up.effect = 'DENY'
                      and (up.expires_at is null or up.expires_at > systimestamp))
union
select up.user_id, up.permission_id
  from usb_user_permissions up
 where up.effect = 'GRANT' and (up.expires_at is null or up.expires_at > systimestamp);

-- Desafío intermedio entre "contraseña correcta" y "código 2FA correcto".
create table if not exists usb_login_challenges (
  challenge_hash     raw(32)      primary key,
  user_id            number       not null references usb_users,
  purpose            varchar2(10) not null,
  pending_secret_enc raw(128),
  device_id          varchar2(128) not null,
  device_name        varchar2(200),
  ip_address         varchar2(64),
  attempts           number(3)    default 0 not null,
  created_at         timestamp with time zone default systimestamp not null,
  expires_at         timestamp with time zone not null,
  consumed_at        timestamp with time zone,
  constraint usb_challenges_purpose_ck check (purpose in ('MFA', 'ENROLL', 'RESET', 'CHANGE'))
);
create index if not exists usb_challenges_user_ix on usb_login_challenges (user_id);

-- Migración de bases instaladas antes de los roles dinámicos y del olvido de contraseña.
-- DDL con texto fijo (sin datos del usuario), solo al instalar.
declare
  procedure ddl_if(p_condition_sql varchar2, p_ddl varchar2) is
    l_count number;
  begin
    execute immediate p_condition_sql into l_count;
    if l_count > 0 then
      execute immediate p_ddl;
    end if;
  end ddl_if;
begin
  ddl_if(q'[select count(*) from user_constraints where constraint_name = 'USB_USERS_ROLE_CK']',
         'alter table usb_users drop constraint usb_users_role_ck');
  ddl_if(q'[select count(*) from user_tab_columns where table_name = 'USB_USERS' and column_name = 'ROLE' and data_length < 40]',
         'alter table usb_users modify (role varchar2(40))');
  ddl_if(q'[select 1 - count(*) from user_constraints where constraint_name = 'USB_USERS_ROLE_FK']',
         'alter table usb_users add constraint usb_users_role_fk foreign key (role) references usb_roles');
  ddl_if(q'[select 1 - count(*) from user_tab_columns where table_name = 'USB_USERS' and column_name = 'MUST_CHANGE_PASSWORD']',
         'alter table usb_users add (must_change_password boolean default false not null)');
  ddl_if(q'[select count(*) from user_constraints where constraint_name = 'USB_CHALLENGES_PURPOSE_CK' and search_condition_vc not like '%CHANGE%']',
         'alter table usb_login_challenges drop constraint usb_challenges_purpose_ck');
  ddl_if(q'[select 1 - count(*) from user_constraints where constraint_name = 'USB_CHALLENGES_PURPOSE_CK']',
         q'[alter table usb_login_challenges add constraint usb_challenges_purpose_ck check (purpose in ('MFA', 'ENROLL', 'RESET', 'CHANGE'))]');
end;
/

-- Una sesión = un dispositivo con login + 2FA completado.
create table if not exists usb_sessions (
  session_id          number generated always as identity primary key,
  user_id             number        not null references usb_users,
  device_id           varchar2(128) not null,
  device_name         varchar2(200),
  user_agent          varchar2(400),
  ip_created          varchar2(64),
  ip_last             varchar2(64),
  mfa_method          varchar2(20)  not null,
  created_at          timestamp with time zone default systimestamp not null,
  last_seen_at        timestamp with time zone default systimestamp not null,
  absolute_expires_at timestamp with time zone not null,
  revoked_at          timestamp with time zone,
  revoke_reason       varchar2(40)
);
create index if not exists usb_sessions_user_ix on usb_sessions (user_id, revoked_at);

create table if not exists usb_access_tokens (
  token_hash  raw(32) primary key,
  session_id  number  not null references usb_sessions on delete cascade,
  expires_at  timestamp with time zone not null
);
create index if not exists usb_access_session_ix on usb_access_tokens (session_id);

-- Refresh tokens rotativos: cada uso emite uno nuevo y marca el anterior.
-- Reusar uno ya marcado = posible robo → se revoca la sesión entera.
create table if not exists usb_refresh_tokens (
  token_hash  raw(32) primary key,
  session_id  number  not null references usb_sessions on delete cascade,
  issued_at   timestamp with time zone default systimestamp not null,
  expires_at  timestamp with time zone not null,
  used_at     timestamp with time zone
);
create index if not exists usb_refresh_session_ix on usb_refresh_tokens (session_id);

create table if not exists usb_recovery_codes (
  code_id    number generated always as identity primary key,
  user_id    number  not null references usb_users,
  code_hash  raw(32) not null,
  used_at    timestamp with time zone,
  created_at timestamp with time zone default systimestamp not null
);
create index if not exists usb_recovery_user_ix on usb_recovery_codes (user_id);

-- Bitácora de seguridad (inicio de sesión, fallos, bloqueos, revocaciones…).
create table if not exists usb_auth_events (
  event_id    number generated always as identity primary key,
  user_id     number,
  session_id  number,
  event_type  varchar2(40) not null,
  ip_address  varchar2(64),
  user_agent  varchar2(400),
  device_id   varchar2(128),
  detail      varchar2(1000),
  created_at  timestamp with time zone default systimestamp not null
);
create index if not exists usb_events_user_ix on usb_auth_events (user_id, created_at);

-- Errores inesperados de la API (nunca se devuelven al cliente).
create table if not exists usb_error_log (
  error_id   number generated always as identity primary key,
  endpoint   varchar2(100),
  message    varchar2(4000),
  backtrace  varchar2(4000),
  created_at timestamp with time zone default systimestamp not null
);

commit;
prompt Tablas listas.
exit
