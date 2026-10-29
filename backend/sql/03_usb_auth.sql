-- =============================================================================
-- USBrain · 03_usb_auth.sql
-- Lógica de autenticación: login en dos pasos (contraseña → TOTP), enrolamiento
-- del autenticador, códigos de recuperación, sesiones por dispositivo con
-- refresh tokens rotativos y detección de reutilización, bloqueo por intentos.
--
-- Errores: raise_application_error(código, 'slug|Mensaje para el usuario').
-- usb_api traduce el código a HTTP y el slug a {"error": slug, "message": …}.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

create or replace package usb_auth authid definer as

  e_bad_request  constant pls_integer := -20400;
  e_unauthorized constant pls_integer := -20401;
  e_forbidden    constant pls_integer := -20403;
  e_not_found    constant pls_integer := -20404;
  e_conflict     constant pls_integer := -20409;
  e_locked       constant pls_integer := -20423;

  -- Contexto de la petición HTTP en curso (lo fija usb_api al inicio de cada llamada).
  procedure begin_request(p_ip varchar2, p_user_agent varchar2);
  function current_user_id return number;
  function current_session_id return number;

  -- Permisos efectivos (usb_effective_permissions): los del rol más las excepciones
  -- vigentes de cada usuario (usb_user_permissions). El código revisa permisos,
  -- nunca nombres de rol.
  function user_has_permission(p_user_id number, p_permission varchar2) return boolean;
  function has_permission(p_permission varchar2) return boolean;
  procedure require_permission(p_permission varchar2);
  function permissions_json(p_user_id number) return json_array_t;

  -- Administración (desde APEX, SQL o futuros endpoints de administrador).
  function create_user(p_email varchar2, p_full_name varchar2, p_role varchar2,
                       p_password varchar2) return number;
  procedure set_password(p_user_id number, p_password varchar2);
  procedure set_status(p_user_id number, p_status varchar2);
  procedure unlock_user(p_user_id number);
  procedure reset_mfa(p_user_id number);
  procedure revoke_all_sessions(p_user_id number, p_reason varchar2);
  -- Bitácora de seguridad (usb_auth_events) con la IP y el navegador de la petición actual.
  procedure log_event(p_type varchar2, p_user_id number, p_detail varchar2 default null);

  -- Paso 1: correo + contraseña → desafío 2FA (o enrolamiento si aún no tiene autenticador).
  function login(p_email varchar2, p_password varchar2,
                 p_device_id varchar2, p_device_name varchar2) return json_object_t;
  function change_initial_password(p_challenge varchar2, p_current_password varchar2,
                                   p_new_password varchar2) return json_object_t;
  -- Paso 2: código TOTP → sesión (access + refresh token).
  function verify_mfa(p_challenge varchar2, p_code varchar2) return json_object_t;
  -- Paso 2 alternativo: código de recuperación de un solo uso.
  function verify_recovery_code(p_challenge varchar2, p_recovery_code varchar2) return json_object_t;
  -- Renueva la sesión: consume el refresh token y entrega un par nuevo.
  function refresh(p_refresh_token varchar2, p_device_id varchar2) return json_object_t;

  -- Valida "Authorization: Bearer usb_at_…" y fija el usuario/sesión actuales.
  procedure authenticate(p_authorization varchar2);

  -- Requieren authenticate previo.
  procedure logout;
  procedure logout_all;
  function me return json_object_t;
  function list_sessions return json_array_t;
  procedure revoke_session(p_session_id number);
  procedure change_password(p_current_password varchar2, p_new_password varchar2, p_code varchar2);

  -- Olvido de contraseña (sin sesión). Sin servidor de correo, la identidad se prueba con
  -- el código de la app autenticadora o con un código de recuperación.
  function forgot_password(p_email varchar2, p_device_id varchar2, p_device_name varchar2) return json_object_t;
  procedure reset_password(p_reset_id varchar2, p_code varchar2, p_recovery_code varchar2,
                           p_new_password varchar2);

  -- Limpieza periódica (job USB_AUTH_PURGE).
  procedure purge_expired;

end usb_auth;
/

create or replace package body usb_auth as

  c_recovery_alphabet constant varchar2(32) := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  c_recovery_count    constant pls_integer  := 10;
  c_email_rule        constant varchar2(100) := 'El correo solo puede tener letras, números, @ y punto.';

  g_ip         varchar2(64);
  g_user_agent varchar2(400);
  g_user_id    number;
  g_session_id number;

  -- ---------------------------------------------------------------------------
  -- Utilidades
  -- ---------------------------------------------------------------------------

  procedure fail(p_code pls_integer, p_slug varchar2, p_message varchar2) is
  begin
    raise_application_error(p_code, p_slug || '|' || p_message);
  end fail;

  function cfg(p_key varchar2) return varchar2 is
    l_value usb_auth_config.config_value%type;
  begin
    select config_value into l_value from usb_auth_config where config_key = p_key;
    return l_value;
  end cfg;

  function cfg_num(p_key varchar2) return number is
  begin
    return to_number(cfg(p_key));
  end cfg_num;

  function iso(p_ts timestamp with time zone) return varchar2 is
  begin
    return to_char(sys_extract_utc(p_ts), 'YYYY-MM-DD"T"HH24:MI:SS"Z"');
  end iso;

  procedure audit_event(p_type varchar2, p_user_id number, p_session_id number default null,
                        p_device_id varchar2 default null, p_detail varchar2 default null) is
  begin
    insert into usb_auth_events (user_id, session_id, event_type, ip_address, user_agent, device_id, detail)
    values (p_user_id, p_session_id, p_type, g_ip, g_user_agent, substr(p_device_id, 1, 128),
            substr(p_detail, 1, 1000));
  end audit_event;

  function load_user(p_user_id number) return usb_users%rowtype is
    l_user usb_users%rowtype;
  begin
    select * into l_user from usb_users where user_id = p_user_id for update;
    return l_user;
  exception
    when no_data_found then
      fail(e_not_found, 'user_not_found', 'El usuario no existe.');
  end load_user;

  function user_json(p_user usb_users%rowtype) return json_object_t is
    l_json json_object_t := json_object_t();
  begin
    l_json.put('id', p_user.user_id);
    l_json.put('email', p_user.email);
    l_json.put('full_name', p_user.full_name);
    l_json.put('role', p_user.role);
    for r in (select name, app_profile from usb_roles where role_id = p_user.role) loop
      l_json.put('role_name', r.name);
      l_json.put('profile', r.app_profile);
    end loop;
    l_json.put('permissions', permissions_json(p_user.user_id));
    l_json.put('mfa_enabled', p_user.mfa_enabled);
    return l_json;
  end user_json;

  -- Los correos los crea la administración, así que basta con letras, números, @ y punto.
  -- Todo lo demás (comillas, <>, espacios, %…) se rechaza antes de tocar la base, también
  -- en el login: nada raro llega a las consultas ni a la bitácora.
  function valid_email(p_email varchar2) return boolean is
  begin
    return p_email is not null and lengthb(p_email) <= 254
       and regexp_like(p_email, '^[a-z0-9]+(\.[a-z0-9]+)*@[a-z0-9]+(\.[a-z0-9]+)+$');
  end valid_email;

  procedure validate_device_id(p_device_id varchar2) is
  begin
    if p_device_id is null or not regexp_like(p_device_id, '^[A-Za-z0-9._:-]{8,128}$') then
      fail(e_bad_request, 'invalid_device', 'Falta un identificador de dispositivo válido.');
    end if;
  end validate_device_id;

  procedure validate_password_policy(p_password varchar2, p_email varchar2,
                                    p_exact_length boolean default false) is
    l_local_part varchar2(320) := lower(substr(p_email, 1, instr(p_email, '@') - 1));
    l_character_groups pls_integer := 0;
  begin
    if p_password is null or (p_exact_length and lengthc(p_password) != 10) then
      fail(e_bad_request, 'weak_password', 'La nueva contraseña debe tener exactamente 10 caracteres.');
    elsif not p_exact_length and lengthc(p_password) < 10 then
      fail(e_bad_request, 'weak_password', 'La contraseña debe tener al menos 10 caracteres.');
    elsif not p_exact_length and lengthc(p_password) > 128 then
      fail(e_bad_request, 'weak_password', 'La contraseña no puede superar 128 caracteres.');
    elsif length(l_local_part) >= 4 and instr(lower(p_password), l_local_part) > 0 then
      fail(e_bad_request, 'weak_password', 'La contraseña no puede contener tu usuario de correo.');
    end if;
    if regexp_like(p_password, '[[:lower:]]') then l_character_groups := l_character_groups + 1; end if;
    if regexp_like(p_password, '[[:upper:]]') then l_character_groups := l_character_groups + 1; end if;
    if regexp_like(p_password, '[[:digit:]]') then l_character_groups := l_character_groups + 1; end if;
    if regexp_like(p_password, '[^[:alnum:]]') then l_character_groups := l_character_groups + 1; end if;
    if l_character_groups < 3 then
      fail(e_bad_request, 'weak_password',
           'Combina al menos 3 tipos de caracteres: mayúsculas, minúsculas, números y símbolos.');
    end if;
  end validate_password_policy;

  procedure store_password(p_user_id number, p_password varchar2, p_changed boolean) is
    l_salt       raw(16) := usb_crypto.random_bytes(16);
    l_iterations number  := cfg_num('PBKDF2_ITERATIONS');
  begin
    update usb_users
       set password_hash       = usb_crypto.pbkdf2_sha256(p_password, l_salt, l_iterations),
           password_salt       = l_salt,
           password_iterations = l_iterations,
           password_changed_at = case when p_changed then systimestamp else password_changed_at end,
           updated_at          = systimestamp
     where user_id = p_user_id;
  end store_password;

  procedure ensure_not_locked(p_user usb_users%rowtype) is
  begin
    if p_user.locked_until > systimestamp then
      fail(e_locked, 'account_locked',
           'Cuenta bloqueada temporalmente por intentos fallidos. Intenta de nuevo en '
           || ceil(extract(day from (p_user.locked_until - systimestamp)) * 1440
                   + extract(hour from (p_user.locked_until - systimestamp)) * 60
                   + extract(minute from (p_user.locked_until - systimestamp)) + 1)
           || ' minutos.');
    end if;
  end ensure_not_locked;

  procedure ensure_active(p_user usb_users%rowtype) is
  begin
    if p_user.status != 'ACTIVE' then
      fail(e_forbidden, 'account_disabled', 'Tu cuenta está deshabilitada. Contacta al administrador.');
    end if;
  end ensure_active;

  -- Cada fallo de contraseña o de 2FA suma; al llegar al máximo se bloquea la cuenta.
  -- Solo un 2FA exitoso reinicia el contador, así que tener la contraseña no da
  -- más intentos para adivinar el código.
  procedure register_failure(p_user_id number, p_event varchar2, p_device_id varchar2) is
    l_failed  number;
    l_minutes number := cfg_num('LOCKOUT_MINUTES');
  begin
    update usb_users
       set failed_logins = failed_logins + 1, updated_at = systimestamp
     where user_id = p_user_id
    returning failed_logins into l_failed;
    audit_event(p_event, p_user_id, null, p_device_id);
    if l_failed >= cfg_num('MAX_FAILED_LOGINS') then
      update usb_users
         set locked_until = systimestamp + numtodsinterval(l_minutes, 'MINUTE')
       where user_id = p_user_id;
      audit_event('ACCOUNT_LOCKED', p_user_id, null, p_device_id, 'failed_logins=' || l_failed);
    end if;
  end register_failure;

  procedure revoke_session_internal(p_session_id number, p_reason varchar2) is
    l_user_id number;
  begin
    update usb_sessions
       set revoked_at = systimestamp, revoke_reason = p_reason
     where session_id = p_session_id and revoked_at is null
    returning user_id into l_user_id;
    delete from usb_access_tokens where session_id = p_session_id;
    delete from usb_refresh_tokens where session_id = p_session_id;
    if l_user_id is not null then
      audit_event('SESSION_REVOKED', l_user_id, p_session_id, null, p_reason);
    end if;
  end revoke_session_internal;

  procedure revoke_user_sessions(p_user_id number, p_reason varchar2, p_except number default null) is
  begin
    for s in (select session_id from usb_sessions
               where user_id = p_user_id and revoked_at is null
                 and session_id != nvl(p_except, -1)) loop
      revoke_session_internal(s.session_id, p_reason);
    end loop;
  end revoke_user_sessions;

  -- Devuelve el paso TOTP que coincide (dentro de la ventana) o null.
  -- Un paso igual o anterior al último usado se rechaza: el mismo código no sirve dos veces.
  function match_totp(p_secret raw, p_code varchar2, p_last_step number) return number is
    l_now    number := usb_crypto.current_step;
    l_window number := cfg_num('TOTP_WINDOW_STEPS');
  begin
    if p_code is null or not regexp_like(p_code, '^[0-9]{6}$') then
      return null;
    end if;
    for l_step in l_now - l_window .. l_now + l_window loop
      if l_step > nvl(p_last_step, -1)
         and usb_crypto.equals(utl_raw.cast_to_raw(usb_crypto.totp(p_secret, l_step)),
                               utl_raw.cast_to_raw(p_code)) then
        return l_step;
      end if;
    end loop;
    return null;
  end match_totp;

  function generate_recovery_codes(p_user_id number) return json_array_t is
    l_codes json_array_t := json_array_t();
    l_bytes raw(10);
    l_code  varchar2(11);
  begin
    delete from usb_recovery_codes where user_id = p_user_id;
    for i in 1 .. c_recovery_count loop
      l_bytes := usb_crypto.random_bytes(10);
      l_code  := null;
      -- 256 es múltiplo de 32: mod 32 no introduce sesgo. 10 caracteres = 50 bits.
      for j in 1 .. 10 loop
        l_code := l_code || substr(c_recovery_alphabet,
                                   mod(to_number(rawtohex(utl_raw.substr(l_bytes, j, 1)), 'XX'), 32) + 1, 1);
      end loop;
      insert into usb_recovery_codes (user_id, code_hash)
      values (p_user_id, usb_crypto.peppered_hash(l_code));
      l_codes.append(substr(l_code, 1, 5) || '-' || substr(l_code, 6));
    end loop;
    return l_codes;
  end generate_recovery_codes;

  function load_challenge(p_challenge varchar2) return usb_login_challenges%rowtype is
    l_challenge usb_login_challenges%rowtype;
  begin
    if p_challenge is null then
      fail(e_bad_request, 'missing_fields', 'Falta el identificador del desafío.');
    end if;
    -- Solo desafíos de login: un reset_id de "olvidé mi contraseña" nunca abre una sesión.
    select * into l_challenge
      from usb_login_challenges
     where challenge_hash = usb_crypto.sha256(p_challenge)
       and purpose in ('MFA', 'ENROLL')
       for update;
    if l_challenge.consumed_at is not null
       or l_challenge.expires_at <= systimestamp
       or l_challenge.attempts >= cfg_num('CHALLENGE_MAX_ATTEMPTS') then
      fail(e_unauthorized, 'challenge_expired', 'El tiempo para verificar expiró. Inicia sesión de nuevo.');
    end if;
    return l_challenge;
  exception
    when no_data_found then
      fail(e_unauthorized, 'challenge_expired', 'El tiempo para verificar expiró. Inicia sesión de nuevo.');
  end load_challenge;

  procedure reject_challenge_code(p_challenge usb_login_challenges%rowtype, p_event varchar2) is
  begin
    update usb_login_challenges set attempts = attempts + 1
     where challenge_hash = p_challenge.challenge_hash;
    register_failure(p_challenge.user_id, p_event, p_challenge.device_id);
    fail(e_unauthorized, 'invalid_code', 'Código incorrecto o vencido.');
  end reject_challenge_code;

  function issue_tokens(p_session_id number, p_absolute_expires timestamp with time zone)
    return json_object_t is
    l_access          varchar2(100) := usb_crypto.new_token('usb_at_');
    l_refresh         varchar2(100) := usb_crypto.new_token('usb_rt_');
    l_access_ttl      number        := cfg_num('ACCESS_TTL_SECONDS');
    l_refresh_expires timestamp with time zone :=
      least(systimestamp + numtodsinterval(cfg_num('REFRESH_IDLE_MINUTES'), 'MINUTE'), p_absolute_expires);
    l_json            json_object_t := json_object_t();
  begin
    insert into usb_access_tokens (token_hash, session_id, expires_at)
    values (usb_crypto.sha256(l_access), p_session_id,
            systimestamp + numtodsinterval(l_access_ttl, 'SECOND'));
    insert into usb_refresh_tokens (token_hash, session_id, expires_at)
    values (usb_crypto.sha256(l_refresh), p_session_id, l_refresh_expires);

    l_json.put('access_token', l_access);
    l_json.put('token_type', 'Bearer');
    l_json.put('expires_in', l_access_ttl);
    l_json.put('refresh_token', l_refresh);
    l_json.put('refresh_expires_at', iso(l_refresh_expires));
    l_json.put('session_id', p_session_id);
    l_json.put('session_expires_at', iso(p_absolute_expires));
    return l_json;
  end issue_tokens;

  function open_session(p_user usb_users%rowtype, p_challenge usb_login_challenges%rowtype,
                        p_method varchar2) return json_object_t is
    l_session_id   number;
    l_absolute     timestamp with time zone :=
      systimestamp + numtodsinterval(cfg_num('SESSION_ABSOLUTE_DAYS'), 'DAY');
    l_max_sessions number := cfg_num('MAX_SESSIONS_PER_USER');
    l_result       json_object_t;
  begin
    update usb_login_challenges set consumed_at = systimestamp
     where challenge_hash = p_challenge.challenge_hash;

    -- Una sesión por dispositivo: un login nuevo en el mismo equipo reemplaza la anterior.
    for s in (select session_id from usb_sessions
               where user_id = p_user.user_id and device_id = p_challenge.device_id
                 and revoked_at is null) loop
      revoke_session_internal(s.session_id, 'REPLACED');
    end loop;

    insert into usb_sessions (user_id, device_id, device_name, user_agent, ip_created, ip_last,
                              mfa_method, absolute_expires_at)
    values (p_user.user_id, p_challenge.device_id, p_challenge.device_name, g_user_agent, g_ip, g_ip,
            p_method, l_absolute)
    returning session_id into l_session_id;

    -- Límite de dispositivos simultáneos: se cierran los menos usados.
    for s in (select session_id from usb_sessions
               where user_id = p_user.user_id and revoked_at is null
                 and absolute_expires_at > systimestamp
               order by last_seen_at desc
              offset l_max_sessions rows) loop
      revoke_session_internal(s.session_id, 'SESSION_LIMIT');
    end loop;

    audit_event('LOGIN_SUCCESS', p_user.user_id, l_session_id, p_challenge.device_id, p_method);
    l_result := issue_tokens(l_session_id, l_absolute);
    l_result.put('user', user_json(p_user));
    return l_result;
  end open_session;

  -- ---------------------------------------------------------------------------
  -- Contexto
  -- ---------------------------------------------------------------------------

  procedure begin_request(p_ip varchar2, p_user_agent varchar2) is
  begin
    -- ORDS reutiliza conexiones: nunca heredar el usuario de la petición anterior.
    g_ip         := substr(p_ip, 1, 64);
    g_user_agent := substr(p_user_agent, 1, 400);
    g_user_id    := null;
    g_session_id := null;
  end begin_request;

  function current_user_id return number is
  begin
    return g_user_id;
  end current_user_id;

  function current_session_id return number is
  begin
    return g_session_id;
  end current_session_id;

  procedure require_session is
  begin
    if g_session_id is null then
      fail(e_unauthorized, 'unauthorized', 'Se requiere iniciar sesión.');
    end if;
  end require_session;

  -- ---------------------------------------------------------------------------
  -- Permisos
  -- ---------------------------------------------------------------------------

  function user_has_permission(p_user_id number, p_permission varchar2) return boolean is
    l_count number;
  begin
    select count(*) into l_count
      from usb_users u
      join usb_effective_permissions ep on ep.user_id = u.user_id
     where u.user_id = p_user_id and u.status = 'ACTIVE' and ep.permission_id = p_permission;
    return l_count > 0;
  end user_has_permission;

  function has_permission(p_permission varchar2) return boolean is
  begin
    return g_user_id is not null and user_has_permission(g_user_id, p_permission);
  end has_permission;

  procedure require_permission(p_permission varchar2) is
  begin
    require_session;
    if not has_permission(p_permission) then
      fail(e_forbidden, 'forbidden', 'No tienes permiso para esta acción.');
    end if;
  end require_permission;

  function permissions_json(p_user_id number) return json_array_t is
    l_list json_array_t := json_array_t();
  begin
    for p in (select permission_id from usb_effective_permissions where user_id = p_user_id order by 1) loop
      l_list.append(p.permission_id);
    end loop;
    return l_list;
  end permissions_json;

  -- ---------------------------------------------------------------------------
  -- Administración
  -- ---------------------------------------------------------------------------

  function create_user(p_email varchar2, p_full_name varchar2, p_role varchar2,
                       p_password varchar2) return number is
    l_email   varchar2(32767) := lower(trim(p_email));
    l_user_id number;
  begin
    if not valid_email(l_email) then
      fail(e_bad_request, 'invalid_email', c_email_rule);
    end if;
    if trim(p_full_name) is null then
      fail(e_bad_request, 'missing_fields', 'El nombre es obligatorio.');
    end if;
    select count(*) into l_user_id from usb_roles where role_id = p_role;
    if l_user_id = 0 then
      fail(e_bad_request, 'invalid_role', 'El rol no existe.');
    end if;
    l_user_id := null;
    validate_password_policy(p_password, l_email);

    insert into usb_users (email, full_name, role, password_hash, password_salt, password_iterations,
                           must_change_password)
    values (l_email, trim(p_full_name), p_role, hextoraw('00'), hextoraw('00'), 0, true)
    returning user_id into l_user_id;
    store_password(l_user_id, p_password, true);
    audit_event('USER_CREATED', l_user_id, null, null, p_role);
    return l_user_id;
  exception
    when dup_val_on_index then
      fail(e_conflict, 'email_taken', 'Ya existe un usuario con ese correo.');
  end create_user;

  procedure set_password(p_user_id number, p_password varchar2) is
    l_user usb_users%rowtype := load_user(p_user_id);
  begin
    validate_password_policy(p_password, l_user.email);
    store_password(p_user_id, p_password, true);
    update usb_users set failed_logins = 0, locked_until = null, must_change_password = true
     where user_id = p_user_id;
    revoke_user_sessions(p_user_id, 'PASSWORD_RESET');
    audit_event('PASSWORD_RESET', p_user_id);
  end set_password;

  procedure set_status(p_user_id number, p_status varchar2) is
    l_user usb_users%rowtype := load_user(p_user_id);
  begin
    update usb_users set status = p_status, updated_at = systimestamp where user_id = l_user.user_id;
    if p_status != 'ACTIVE' then
      revoke_user_sessions(p_user_id, 'ACCOUNT_DISABLED');
    end if;
    audit_event('STATUS_CHANGED', p_user_id, null, null, p_status);
  end set_status;

  procedure unlock_user(p_user_id number) is
    l_user usb_users%rowtype := load_user(p_user_id);
  begin
    update usb_users set failed_logins = 0, locked_until = null, updated_at = systimestamp
     where user_id = l_user.user_id;
    audit_event('ACCOUNT_UNLOCKED', p_user_id);
  end unlock_user;

  -- Para cuando el usuario pierde el teléfono y los códigos de recuperación:
  -- en el próximo login deberá enrolar un autenticador nuevo.
  procedure reset_mfa(p_user_id number) is
    l_user usb_users%rowtype := load_user(p_user_id);
  begin
    update usb_users
       set mfa_enabled = false, totp_secret_enc = null, totp_last_step = null, updated_at = systimestamp
     where user_id = l_user.user_id;
    delete from usb_recovery_codes where user_id = p_user_id;
    revoke_user_sessions(p_user_id, 'MFA_RESET');
    audit_event('MFA_RESET', p_user_id);
  end reset_mfa;

  -- ---------------------------------------------------------------------------
  -- Login
  -- ---------------------------------------------------------------------------

  function login(p_email varchar2, p_password varchar2,
                 p_device_id varchar2, p_device_name varchar2) return json_object_t is
    l_email      varchar2(32767) := lower(trim(p_email));
    l_user       usb_users%rowtype;
    l_hash       raw(32);
    l_purpose    varchar2(10);
    l_secret     raw(20);
    l_token      varchar2(100);
    l_ttl        number := cfg_num('CHALLENGE_TTL_SECONDS');
    l_issuer     varchar2(100) := cfg('TOTP_ISSUER');
    l_secret_b32 varchar2(64);
    l_enrollment json_object_t;
    l_result     json_object_t := json_object_t();
  begin
    if l_email is null or p_password is null then
      fail(e_bad_request, 'missing_fields', 'Correo y contraseña son obligatorios.');
    end if;
    validate_device_id(p_device_id);
    if not valid_email(l_email) then
      audit_event('LOGIN_INVALID_EMAIL', null, null, p_device_id, substr(l_email, 1, 100));
      fail(e_bad_request, 'invalid_email', c_email_rule);
    end if;

    begin
      select * into l_user from usb_users where email = l_email for update;
    exception
      when no_data_found then
        -- Mismo costo que con un usuario real: el tiempo de respuesta no revela qué correos existen.
        l_hash := usb_crypto.pbkdf2_sha256(p_password, usb_crypto.random_bytes(16),
                                           cfg_num('PBKDF2_ITERATIONS'));
        audit_event('LOGIN_UNKNOWN_EMAIL', null, null, p_device_id, l_email);
        fail(e_unauthorized, 'invalid_credentials', 'Correo o contraseña incorrectos.');
    end;

    ensure_not_locked(l_user);
    l_hash := usb_crypto.pbkdf2_sha256(p_password, l_user.password_salt, l_user.password_iterations);
    if not usb_crypto.equals(l_hash, l_user.password_hash) then
      register_failure(l_user.user_id, 'LOGIN_BAD_PASSWORD', p_device_id);
      fail(e_unauthorized, 'invalid_credentials', 'Correo o contraseña incorrectos.');
    end if;
    ensure_active(l_user);

    -- Si se subieron las iteraciones en la configuración, se re-hashea al vuelo.
    if l_user.password_iterations < cfg_num('PBKDF2_ITERATIONS') then
      store_password(l_user.user_id, p_password, false);
    end if;

    -- Un desafío pendiente por dispositivo; los viejos o vencidos se descartan.
    delete from usb_login_challenges
     where user_id = l_user.user_id
       and purpose != 'RESET'
       and (device_id = p_device_id or expires_at <= systimestamp or consumed_at is not null);

    l_token := usb_crypto.new_token('usb_ch_');
    if l_user.must_change_password then
      insert into usb_login_challenges (challenge_hash, user_id, purpose, device_id, device_name,
                                        ip_address, expires_at)
      values (usb_crypto.sha256(l_token), l_user.user_id, 'CHANGE', p_device_id,
              substr(p_device_name, 1, 200), g_ip,
              systimestamp + numtodsinterval(l_ttl, 'SECOND'));
      audit_event('PASSWORD_CHANGE_REQUIRED', l_user.user_id, null, p_device_id);
      l_result.put('status', 'password_change_required');
      l_result.put('challenge_id', l_token);
      l_result.put('expires_in', l_ttl);
      return l_result;
    end if;

    l_purpose := case when l_user.mfa_enabled then 'MFA' else 'ENROLL' end;
    if l_purpose = 'ENROLL' then
      l_secret := usb_crypto.random_bytes(20);
    end if;

    insert into usb_login_challenges (challenge_hash, user_id, purpose, pending_secret_enc,
                                      device_id, device_name, ip_address, expires_at)
    values (usb_crypto.sha256(l_token), l_user.user_id, l_purpose,
            case when l_secret is not null then usb_crypto.encrypt(l_secret) end,
            p_device_id, substr(p_device_name, 1, 200), g_ip,
            systimestamp + numtodsinterval(l_ttl, 'SECOND'));
    audit_event('LOGIN_PASSWORD_OK', l_user.user_id, null, p_device_id, l_purpose);

    l_result.put('status', case l_purpose when 'MFA' then 'mfa_required' else 'mfa_enrollment_required' end);
    l_result.put('challenge_id', l_token);
    l_result.put('expires_in', l_ttl);
    if l_purpose = 'ENROLL' then
      l_secret_b32 := usb_crypto.base32_encode(l_secret);
      l_enrollment := json_object_t();
      l_enrollment.put('secret', l_secret_b32);
      l_enrollment.put('issuer', l_issuer);
      l_enrollment.put('account', l_user.email);
      l_enrollment.put('otpauth_uri',
        'otpauth://totp/' || utl_url.escape(l_issuer, true, 'AL32UTF8') || ':'
        || utl_url.escape(l_user.email, true, 'AL32UTF8')
        || '?secret=' || l_secret_b32
        || '&issuer=' || utl_url.escape(l_issuer, true, 'AL32UTF8')
        || '&algorithm=SHA1&digits=6&period=30');
      l_result.put('enrollment', l_enrollment);
    end if;
    return l_result;
  end login;

  function change_initial_password(p_challenge varchar2, p_current_password varchar2,
                                   p_new_password varchar2) return json_object_t is
    l_challenge usb_login_challenges%rowtype;
    l_user      usb_users%rowtype;
    l_hash      raw(32);
    l_purpose   varchar2(10);
    l_secret    raw(20);
    l_result    json_object_t := json_object_t();
    l_ttl       number := cfg_num('CHALLENGE_TTL_SECONDS');
    l_issuer    varchar2(100) := cfg('TOTP_ISSUER');
    l_secret_b32 varchar2(64);
    l_enrollment json_object_t;
  begin
    if p_challenge is null or p_current_password is null or p_new_password is null then
      fail(e_bad_request, 'missing_fields', 'Completa la contraseña temporal y la nueva contraseña.');
    end if;

    begin
      select * into l_challenge
        from usb_login_challenges
       where challenge_hash = usb_crypto.sha256(p_challenge)
         and purpose = 'CHANGE';
    exception
      when no_data_found then
        fail(e_unauthorized, 'challenge_expired', 'El tiempo para cambiar la contraseña expiró. Inicia sesión de nuevo.');
    end;
    select * into l_user from usb_users where user_id = l_challenge.user_id for update;
    begin
      select * into l_challenge
        from usb_login_challenges
       where challenge_hash = usb_crypto.sha256(p_challenge)
         and purpose = 'CHANGE'
       for update;
    exception
      when no_data_found then
        fail(e_unauthorized, 'challenge_expired', 'El tiempo para cambiar la contraseña expiró. Inicia sesión de nuevo.');
    end;
    if l_challenge.consumed_at is not null
       or l_challenge.expires_at <= systimestamp
       or l_challenge.attempts >= cfg_num('CHALLENGE_MAX_ATTEMPTS') then
      fail(e_unauthorized, 'challenge_expired', 'El tiempo para cambiar la contraseña expiró. Inicia sesión de nuevo.');
    end if;

    ensure_not_locked(l_user);
    ensure_active(l_user);
    if not l_user.must_change_password then
      fail(e_unauthorized, 'challenge_expired', 'El tiempo para cambiar la contraseña expiró. Inicia sesión de nuevo.');
    end if;

    l_hash := usb_crypto.pbkdf2_sha256(p_current_password, l_user.password_salt, l_user.password_iterations);
    if not usb_crypto.equals(l_hash, l_user.password_hash) then
      update usb_login_challenges set attempts = attempts + 1
       where challenge_hash = l_challenge.challenge_hash;
      register_failure(l_user.user_id, 'FIRST_PASSWORD_CHANGE_BAD_PASSWORD', l_challenge.device_id);
      fail(e_unauthorized, 'invalid_credentials', 'La contraseña temporal no es correcta.');
    end if;

    validate_password_policy(p_new_password, l_user.email, p_exact_length => true);
    if usb_crypto.equals(
         usb_crypto.pbkdf2_sha256(p_new_password, l_user.password_salt, l_user.password_iterations),
         l_user.password_hash) then
      fail(e_bad_request, 'weak_password', 'La nueva contraseña debe ser distinta de la contraseña temporal.');
    end if;

    store_password(l_user.user_id, p_new_password, true);
    update usb_users set must_change_password = false, failed_logins = 0, locked_until = null
     where user_id = l_user.user_id;

    l_purpose := case when l_user.mfa_enabled then 'MFA' else 'ENROLL' end;
    if l_purpose = 'ENROLL' then
      l_secret := usb_crypto.random_bytes(20);
    end if;
    update usb_login_challenges
       set purpose = l_purpose,
           pending_secret_enc = case when l_secret is not null then usb_crypto.encrypt(l_secret) end,
           attempts = 0,
           expires_at = systimestamp + numtodsinterval(l_ttl, 'SECOND')
     where challenge_hash = l_challenge.challenge_hash;
    audit_event('FIRST_PASSWORD_CHANGED', l_user.user_id, null, l_challenge.device_id);

    l_result.put('status', case l_purpose when 'MFA' then 'mfa_required' else 'mfa_enrollment_required' end);
    l_result.put('challenge_id', p_challenge);
    l_result.put('expires_in', l_ttl);
    if l_purpose = 'ENROLL' then
      l_secret_b32 := usb_crypto.base32_encode(l_secret);
      l_enrollment := json_object_t();
      l_enrollment.put('secret', l_secret_b32);
      l_enrollment.put('issuer', l_issuer);
      l_enrollment.put('account', l_user.email);
      l_enrollment.put('otpauth_uri',
        'otpauth://totp/' || utl_url.escape(l_issuer, true, 'AL32UTF8') || ':'
        || utl_url.escape(l_user.email, true, 'AL32UTF8')
        || '?secret=' || l_secret_b32
        || '&issuer=' || utl_url.escape(l_issuer, true, 'AL32UTF8')
        || '&algorithm=SHA1&digits=6&period=30');
      l_result.put('enrollment', l_enrollment);
    end if;
    return l_result;
  end change_initial_password;

  function verify_mfa(p_challenge varchar2, p_code varchar2) return json_object_t is
    l_challenge usb_login_challenges%rowtype := load_challenge(p_challenge);
    l_user      usb_users%rowtype := load_user(l_challenge.user_id);
    l_enroll    boolean := l_challenge.purpose = 'ENROLL';
    l_secret    raw(20);
    l_step      number;
    l_result    json_object_t;
  begin
    ensure_not_locked(l_user);
    ensure_active(l_user);

    l_secret := usb_crypto.decrypt(case when l_enroll then l_challenge.pending_secret_enc
                                        else l_user.totp_secret_enc end);
    l_step := match_totp(l_secret, regexp_replace(p_code, '[[:space:]]'),
                         case when l_enroll then null else l_user.totp_last_step end);
    if l_step is null then
      reject_challenge_code(l_challenge, 'MFA_BAD_CODE');
    end if;

    update usb_users
       set totp_last_step  = l_step,
           totp_secret_enc = case when l_enroll then l_challenge.pending_secret_enc else totp_secret_enc end,
           mfa_enabled     = true,
           failed_logins   = 0,
           locked_until    = null,
           last_login_at   = systimestamp,
           updated_at      = systimestamp
     where user_id = l_user.user_id;
    l_user.mfa_enabled := true;

    l_result := open_session(l_user, l_challenge, 'TOTP');
    if l_enroll then
      -- Se muestran una sola vez; solo se guarda su hash.
      l_result.put('recovery_codes', generate_recovery_codes(l_user.user_id));
      audit_event('MFA_ENROLLED', l_user.user_id, null, l_challenge.device_id);
    end if;
    return l_result;
  end verify_mfa;

  function verify_recovery_code(p_challenge varchar2, p_recovery_code varchar2) return json_object_t is
    l_challenge usb_login_challenges%rowtype := load_challenge(p_challenge);
    l_user      usb_users%rowtype := load_user(l_challenge.user_id);
    l_code      varchar2(64) := upper(regexp_replace(p_recovery_code, '[^A-Za-z0-9]'));
    l_remaining number;
    l_result    json_object_t;
  begin
    ensure_not_locked(l_user);
    ensure_active(l_user);
    if l_challenge.purpose != 'MFA' then
      fail(e_bad_request, 'mfa_not_enrolled', 'Primero debes configurar tu app autenticadora.');
    end if;

    update usb_recovery_codes
       set used_at = systimestamp
     where user_id = l_user.user_id
       and used_at is null
       and code_hash = usb_crypto.peppered_hash(l_code);
    if sql%rowcount = 0 then
      reject_challenge_code(l_challenge, 'RECOVERY_BAD_CODE');
    end if;

    update usb_users
       set failed_logins = 0, locked_until = null, last_login_at = systimestamp, updated_at = systimestamp
     where user_id = l_user.user_id;

    select count(*) into l_remaining
      from usb_recovery_codes where user_id = l_user.user_id and used_at is null;
    audit_event('RECOVERY_CODE_USED', l_user.user_id, null, l_challenge.device_id,
                'remaining=' || l_remaining);

    l_result := open_session(l_user, l_challenge, 'RECOVERY_CODE');
    l_result.put('recovery_codes_remaining', l_remaining);
    return l_result;
  end verify_recovery_code;

  -- ---------------------------------------------------------------------------
  -- Sesión persistente
  -- ---------------------------------------------------------------------------

  -- Motivo con que se cerró la última sesión de este dispositivo (último día), o null.
  function last_revoke_reason(p_device_id varchar2) return varchar2 is
    l_reason usb_sessions.revoke_reason%type;
  begin
    select max(revoke_reason) keep (dense_rank last order by revoked_at) into l_reason
      from usb_sessions
     where device_id = p_device_id and revoked_at > systimestamp - interval '1' day;
    return l_reason;
  end last_revoke_reason;

  function refresh(p_refresh_token varchar2, p_device_id varchar2) return json_object_t is
    l_reason  varchar2(10);
    l_token   usb_refresh_tokens%rowtype;
    l_session usb_sessions%rowtype;
    l_user    usb_users%rowtype;
    l_result  json_object_t;
  begin
    if p_refresh_token is null then
      fail(e_bad_request, 'missing_fields', 'Falta el refresh token.');
    end if;
    validate_device_id(p_device_id);

    begin
      select * into l_token from usb_refresh_tokens
       where token_hash = usb_crypto.sha256(p_refresh_token) for update;
    exception
      when no_data_found then
        audit_event('REFRESH_INVALID', null, null, p_device_id);
        -- Al revocar una sesión se borran sus tokens; por el dispositivo se sabe por qué se
        -- cerró, para que la app muestre el motivo real (la renovación automática ocurre justo
        -- después del 401 de authenticate y, sin esto, el mensaje se perdería).
        case last_revoke_reason(p_device_id)
          when 'SESSION_LIMIT' then l_reason := 'replaced';
          when 'REPLACED' then l_reason := 'replaced';
          when 'IDLE_TIMEOUT' then l_reason := 'idle';
          else l_reason := null;
        end case;
        if l_reason = 'replaced' then
          fail(e_unauthorized, 'session_replaced',
               'Tu sesión se cerró porque se inició sesión con tu cuenta en otro dispositivo o pestaña. '
               || 'Si no fuiste tú, cambia tu contraseña.');
        elsif l_reason = 'idle' then
          fail(e_unauthorized, 'session_expired', 'Tu sesión expiró por inactividad. Inicia sesión de nuevo.');
        end if;
        fail(e_unauthorized, 'session_expired', 'Tu sesión expiró. Inicia sesión de nuevo.');
    end;
    select * into l_session from usb_sessions where session_id = l_token.session_id for update;

    if l_token.used_at is not null then
      -- Un refresh token solo sirve una vez. Si vuelve a aparecer, alguien tiene una
      -- copia (robo) o el cliente no serializa los refresh: se cierra la sesión.
      audit_event('REFRESH_REUSE_DETECTED', l_session.user_id, l_session.session_id, p_device_id);
      revoke_session_internal(l_session.session_id, 'REFRESH_REUSE');
      fail(e_unauthorized, 'session_expired', 'Tu sesión expiró. Inicia sesión de nuevo.');
    end if;

    if l_session.revoked_at is not null
       or l_session.absolute_expires_at <= systimestamp then
      fail(e_unauthorized, 'session_expired', 'Tu sesión expiró. Inicia sesión de nuevo.');
    end if;

    if l_session.device_id != p_device_id then
      audit_event('REFRESH_DEVICE_MISMATCH', l_session.user_id, l_session.session_id, p_device_id);
      revoke_session_internal(l_session.session_id, 'DEVICE_MISMATCH');
      fail(e_unauthorized, 'session_expired', 'Tu sesión expiró. Inicia sesión de nuevo.');
    end if;

    if l_session.last_seen_at <= systimestamp
       - numtodsinterval(cfg_num('REFRESH_IDLE_MINUTES'), 'MINUTE') then
      revoke_session_internal(l_session.session_id, 'IDLE_TIMEOUT');
      fail(e_unauthorized, 'session_expired', 'Tu sesión expiró por inactividad. Inicia sesión de nuevo.');
    end if;

    if l_token.expires_at <= systimestamp then
      fail(e_unauthorized, 'session_expired', 'Tu sesión expiró. Inicia sesión de nuevo.');
    end if;

    select * into l_user from usb_users where user_id = l_session.user_id;
    if l_user.status != 'ACTIVE' then
      revoke_session_internal(l_session.session_id, 'ACCOUNT_DISABLED');
      ensure_active(l_user);
    end if;

    update usb_refresh_tokens set used_at = systimestamp where token_hash = l_token.token_hash;
    update usb_sessions
       set last_seen_at = systimestamp, ip_last = g_ip, user_agent = nvl(g_user_agent, user_agent)
     where session_id = l_session.session_id;

    l_result := issue_tokens(l_session.session_id, l_session.absolute_expires_at);
    l_result.put('user', user_json(l_user));
    return l_result;
  end refresh;

  procedure authenticate(p_authorization varchar2) is
    l_last_seen timestamp with time zone;
    l_access_expires timestamp with time zone;
  begin
    if p_authorization is null
       or not regexp_like(p_authorization, '^Bearer usb_at_[A-Za-z0-9_-]{43}$') then
      fail(e_unauthorized, 'unauthorized', 'Se requiere iniciar sesión.');
    end if;

    select s.session_id, s.user_id, s.last_seen_at, a.expires_at
      into g_session_id, g_user_id, l_last_seen, l_access_expires
      from usb_access_tokens a
      join usb_sessions s on s.session_id = a.session_id
      join usb_users u on u.user_id = s.user_id
     where a.token_hash = usb_crypto.sha256(substr(p_authorization, 8))
       and s.revoked_at is null
       and s.absolute_expires_at > systimestamp
       and u.status = 'ACTIVE';

     if l_last_seen <= systimestamp
       - numtodsinterval(cfg_num('REFRESH_IDLE_MINUTES'), 'MINUTE') then
      revoke_session_internal(g_session_id, 'IDLE_TIMEOUT');
      g_session_id := null;
      g_user_id    := null;
      fail(e_unauthorized, 'session_expired', 'Tu sesión expiró por inactividad. Inicia sesión de nuevo.');
     end if;

    if l_access_expires <= systimestamp then
      fail(e_unauthorized, 'token_expired', 'Tu acceso expiró. Renueva la sesión.');
    end if;

    -- Evita escribir en cada petición: basta con actualizar una vez por minuto.
    if l_last_seen < systimestamp - interval '1' minute then
      update usb_sessions set last_seen_at = systimestamp, ip_last = g_ip
       where session_id = g_session_id;
    end if;
  exception
    when no_data_found then
      g_session_id := null;
      g_user_id    := null;
      fail(e_unauthorized, 'token_expired', 'Tu acceso expiró. Renueva la sesión.');
  end authenticate;

  procedure logout is
  begin
    require_session;
    revoke_session_internal(g_session_id, 'LOGOUT');
  end logout;

  procedure logout_all is
  begin
    require_session;
    revoke_user_sessions(g_user_id, 'LOGOUT_ALL');
  end logout_all;

  function me return json_object_t is
    l_user    usb_users%rowtype;
    l_session usb_sessions%rowtype;
    l_result  json_object_t;
    l_json    json_object_t := json_object_t();
  begin
    require_session;
    select * into l_user from usb_users where user_id = g_user_id;
    select * into l_session from usb_sessions where session_id = g_session_id;
    l_result := user_json(l_user);
    l_json.put('id', l_session.session_id);
    l_json.put('device_name', l_session.device_name);
    l_json.put('mfa_method', l_session.mfa_method);
    l_json.put('created_at', iso(l_session.created_at));
    l_json.put('expires_at', iso(l_session.absolute_expires_at));
    l_result.put('session', l_json);
    return l_result;
  end me;

  function list_sessions return json_array_t is
    l_list json_array_t := json_array_t();
    l_json json_object_t;
  begin
    require_session;
    for s in (select * from usb_sessions
               where user_id = g_user_id and revoked_at is null
                 and absolute_expires_at > systimestamp
               order by last_seen_at desc) loop
      l_json := json_object_t();
      l_json.put('id', s.session_id);
      l_json.put('device_name', s.device_name);
      l_json.put('ip_last', s.ip_last);
      l_json.put('created_at', iso(s.created_at));
      l_json.put('last_seen_at', iso(s.last_seen_at));
      l_json.put('current', s.session_id = g_session_id);
      l_list.append(l_json);
    end loop;
    return l_list;
  end list_sessions;

  procedure revoke_session(p_session_id number) is
    l_owner number;
  begin
    require_session;
    select user_id into l_owner from usb_sessions
     where session_id = p_session_id and revoked_at is null;
    if l_owner != g_user_id then
      raise no_data_found;
    end if;
    revoke_session_internal(p_session_id, 'REMOTE_LOGOUT');
  exception
    when no_data_found then
      fail(e_not_found, 'session_not_found', 'La sesión no existe o ya fue cerrada.');
  end revoke_session;

  -- Cambio de contraseña con verificación reforzada (contraseña actual + código 2FA),
  -- como hacen los bancos para operaciones sensibles. Cierra los demás dispositivos.
  procedure change_password(p_current_password varchar2, p_new_password varchar2, p_code varchar2) is
    l_user usb_users%rowtype;
    l_step number;
  begin
    require_session;
    l_user := load_user(g_user_id);
    ensure_not_locked(l_user);

    if p_current_password is null or not usb_crypto.equals(
         usb_crypto.pbkdf2_sha256(p_current_password, l_user.password_salt, l_user.password_iterations),
         l_user.password_hash) then
      register_failure(l_user.user_id, 'PASSWORD_CHANGE_BAD_PASSWORD', null);
      fail(e_forbidden, 'invalid_credentials', 'La contraseña actual no es correcta.');
    end if;

    l_step := match_totp(usb_crypto.decrypt(l_user.totp_secret_enc),
                         regexp_replace(p_code, '[[:space:]]'), l_user.totp_last_step);
    if l_step is null then
      register_failure(l_user.user_id, 'PASSWORD_CHANGE_BAD_CODE', null);
      fail(e_forbidden, 'invalid_code', 'Código incorrecto o vencido.');
    end if;

    validate_password_policy(p_new_password, l_user.email, p_exact_length => true);
    if p_new_password = p_current_password then
      fail(e_bad_request, 'weak_password', 'La nueva contraseña debe ser distinta de la actual.');
    end if;

    store_password(l_user.user_id, p_new_password, true);
    update usb_users set totp_last_step = l_step, failed_logins = 0 where user_id = l_user.user_id;
    revoke_user_sessions(l_user.user_id, 'PASSWORD_CHANGED', p_except => g_session_id);
    audit_event('PASSWORD_CHANGED', l_user.user_id, g_session_id);
  end change_password;

  -- ---------------------------------------------------------------------------
  -- Olvido de contraseña
  -- ---------------------------------------------------------------------------

  function forgot_password(p_email varchar2, p_device_id varchar2, p_device_name varchar2) return json_object_t is
    -- Sin límite aquí: un texto enorme se rechaza con valid_email en vez de reventar (500).
    l_email  varchar2(32767) := lower(trim(p_email));
    l_user   usb_users%rowtype;
    l_found  boolean := true;
    l_token  varchar2(100) := usb_crypto.new_token('usb_rs_');
    l_ttl    number := cfg_num('CHALLENGE_TTL_SECONDS');
    l_result json_object_t := json_object_t();
  begin
    if l_email is null then
      fail(e_bad_request, 'missing_fields', 'Escribe tu correo institucional.');
    end if;
    validate_device_id(p_device_id);
    if not valid_email(l_email) then
      fail(e_bad_request, 'invalid_email', c_email_rule);
    end if;
    begin
      select * into l_user from usb_users where email = l_email for update;
    exception
      when no_data_found then l_found := false;
    end;

    -- La respuesta es idéntica exista o no la cuenta: no revela qué correos están registrados.
    -- Para un correo desconocido el reset_id no corresponde a nada y fallará como un código malo.
    if l_found and l_user.status = 'ACTIVE' and l_user.mfa_enabled then
      delete from usb_login_challenges where user_id = l_user.user_id and purpose = 'RESET';
      insert into usb_login_challenges (challenge_hash, user_id, purpose, device_id, device_name,
                                        ip_address, expires_at)
      values (usb_crypto.sha256(l_token), l_user.user_id, 'RESET', p_device_id,
              substr(p_device_name, 1, 200), g_ip, systimestamp + numtodsinterval(l_ttl, 'SECOND'));
      audit_event('PASSWORD_RESET_REQUESTED', l_user.user_id, null, p_device_id);
    else
      audit_event('PASSWORD_RESET_UNKNOWN', case when l_found then l_user.user_id end, null,
                  p_device_id, l_email);
    end if;

    l_result.put('reset_id', l_token);
    l_result.put('expires_in', l_ttl);
    l_result.put('message', 'Si la cuenta existe y tiene la verificación en dos pasos activa, '
                            || 'ingresa el código de tu app autenticadora o un código de recuperación.');
    return l_result;
  end forgot_password;

  procedure reset_password(p_reset_id varchar2, p_code varchar2, p_recovery_code varchar2,
                           p_new_password varchar2) is
    l_challenge usb_login_challenges%rowtype;
    l_user      usb_users%rowtype;
    l_step      number;
    l_recovery  varchar2(64) := upper(regexp_replace(p_recovery_code, '[^A-Za-z0-9]'));
    l_count     number;
  begin
    if p_reset_id is null or (p_code is null and l_recovery is null) or p_new_password is null then
      fail(e_bad_request, 'missing_fields', 'Completa el código y la nueva contraseña.');
    end if;
    -- Reglas de longitud antes de gastar un intento (la regla del correo va después).
    validate_password_policy(p_new_password, null, p_exact_length => true);

    begin
      select * into l_challenge from usb_login_challenges
       where challenge_hash = usb_crypto.sha256(p_reset_id) and purpose = 'RESET'
         for update;
    exception
      when no_data_found then
        fail(e_unauthorized, 'invalid_code', 'Código incorrecto o vencido.');
    end;
    if l_challenge.consumed_at is not null or l_challenge.expires_at <= systimestamp
       or l_challenge.attempts >= cfg_num('CHALLENGE_MAX_ATTEMPTS') then
      fail(e_unauthorized, 'challenge_expired', 'La solicitud venció. Vuelve a empezar.');
    end if;

    l_user := load_user(l_challenge.user_id);
    ensure_not_locked(l_user);
    ensure_active(l_user);

    if p_code is not null then
      l_step := match_totp(usb_crypto.decrypt(l_user.totp_secret_enc),
                           regexp_replace(p_code, '[[:space:]]'), l_user.totp_last_step);
      if l_step is null then
        reject_challenge_code(l_challenge, 'RESET_BAD_CODE');
      end if;
    else
      select count(*) into l_count from usb_recovery_codes
       where user_id = l_user.user_id and used_at is null
         and code_hash = usb_crypto.peppered_hash(l_recovery);
      if l_count = 0 then
        reject_challenge_code(l_challenge, 'RESET_BAD_RECOVERY_CODE');
      end if;
    end if;

    -- Identidad probada: ahora sí la regla completa (no puede contener el usuario del correo).
    validate_password_policy(p_new_password, l_user.email, p_exact_length => true);

    if l_step is null then
      update usb_recovery_codes set used_at = systimestamp
       where user_id = l_user.user_id and used_at is null
         and code_hash = usb_crypto.peppered_hash(l_recovery);
    end if;
    store_password(l_user.user_id, p_new_password, true);
    update usb_users
       set failed_logins = 0, locked_until = null, totp_last_step = nvl(l_step, totp_last_step),
           must_change_password = false
     where user_id = l_user.user_id;
    update usb_login_challenges set consumed_at = systimestamp
     where challenge_hash = l_challenge.challenge_hash;
    -- Quien tuviera una sesión abierta con la contraseña anterior queda fuera.
    revoke_user_sessions(l_user.user_id, 'PASSWORD_RESET');
    audit_event('PASSWORD_RESET_DONE', l_user.user_id, null, l_challenge.device_id,
                case when l_step is null then 'recovery_code' else 'totp' end);
  end reset_password;

  procedure revoke_all_sessions(p_user_id number, p_reason varchar2) is
  begin
    revoke_user_sessions(p_user_id, p_reason);
  end revoke_all_sessions;

  procedure log_event(p_type varchar2, p_user_id number, p_detail varchar2 default null) is
  begin
    audit_event(p_type, p_user_id, g_session_id, null, p_detail);
  end log_event;

  procedure purge_expired is
  begin
    delete from usb_login_challenges where expires_at < systimestamp - interval '1' day;
    delete from usb_access_tokens where expires_at < systimestamp;
    delete from usb_refresh_tokens where expires_at < systimestamp;
    delete from usb_sessions
     where coalesce(revoked_at, absolute_expires_at) < systimestamp - interval '365' day(3);
    commit;
  end purge_expired;

end usb_auth;
/

show errors package body usb_auth
-- Un paquete inválido detiene la instalación.
begin
  for e in (select 1 from user_errors where name = 'USB_AUTH' and rownum = 1) loop
    raise_application_error(-20000, 'USB_AUTH no compiló; revisa los errores de arriba.');
  end loop;
end;
/

-- Limpieza diaria de tokens y desafíos vencidos.
declare
  l_exists number;
begin
  select count(*) into l_exists from user_scheduler_jobs where job_name = 'USB_AUTH_PURGE';
  if l_exists = 0 then
    dbms_scheduler.create_job(
      job_name        => 'USB_AUTH_PURGE',
      job_type        => 'PLSQL_BLOCK',
      job_action      => 'begin usb_auth.purge_expired; end;',
      repeat_interval => 'FREQ=DAILY;BYHOUR=3;BYMINUTE=0',
      enabled         => true,
      comments        => 'Borra tokens, desafíos y sesiones vencidas.');
  end if;
end;
/

prompt Paquete USB_AUTH listo.
exit
