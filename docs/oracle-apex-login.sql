-- USBrain: instalador de desarrollo para Oracle APEX + ORDS.
-- Ejecutar una sola vez en el esquema de parsing de un workspace APEX nuevo.
-- El endpoint queda en /ords/usbrain/usbrain/login (alias/esquema/modulo/plantilla).

create table usbrain_users (
    user_id       varchar2(32) default rawtohex(sys_guid()) primary key,
    email         varchar2(320) not null,
    password_salt varchar2(32) not null,
    password_hash varchar2(64) not null,
    role_name     varchar2(20) not null,
    full_name     varchar2(160) not null,
    is_active     char(1) default 'Y' not null,
    created_at    timestamp with time zone default systimestamp not null,
    constraint usbrain_users_email_uq unique (email),
    constraint usbrain_users_email_ck check (email = lower(trim(email))),
    constraint usbrain_users_role_ck check (role_name in ('PATIENT', 'THERAPIST')),
    constraint usbrain_users_active_ck check (is_active in ('Y', 'N'))
);

create table usbrain_sessions (
    session_id   varchar2(32) default rawtohex(sys_guid()) primary key,
    user_id      varchar2(32) not null references usbrain_users(user_id),
    token_hash   varchar2(64) not null,
    created_at   timestamp with time zone default systimestamp not null,
    expires_at   timestamp with time zone not null,
    constraint usbrain_sessions_token_uq unique (token_hash)
);

create index usbrain_sessions_expiry_ix on usbrain_sessions(expires_at);

-- Cuentas exclusivamente para desarrollo local. Cambiar las contrasenas antes
-- de compartir una instancia y no reutilizarlas en produccion.
declare
    l_salt varchar2(32) := rawtohex(sys_guid());
begin
    insert into usbrain_users (email, password_salt, password_hash, role_name, full_name)
    values (
        'ana.torres@usbbog.edu.co',
        l_salt,
        standard_hash(l_salt || 'UsBrain-Demo-2026!', 'SHA256'),
        'PATIENT',
        'Ana Torres'
    );
exception
    when dup_val_on_index then null;
end;
/

declare
    l_salt varchar2(32) := rawtohex(sys_guid());
begin
    insert into usbrain_users (email, password_salt, password_hash, role_name, full_name)
    values (
        'e.patino@usb.edu.co',
        l_salt,
        standard_hash(l_salt || 'UsBrain-Demo-2026!', 'SHA256'),
        'THERAPIST',
        'Jose E. Patino'
    );
exception
    when dup_val_on_index then null;
end;
/

commit;

-- Habilita el alias ORDS 'usbrain' para este esquema y publica POST /login.
begin
    ords.enable_schema(
        p_enabled             => true,
        p_schema              => user,
        p_url_mapping_type    => 'BASE_PATH',
        p_url_mapping_pattern => 'usbrain',
        p_auto_rest_auth      => false
    );

    ords.define_module(
        p_module_name    => 'usbrain',
        p_base_path      => 'usbrain/',
        p_items_per_page => 0,
        p_status         => 'PUBLISHED'
    );

    ords.define_template(
        p_module_name => 'usbrain',
        p_pattern     => 'login'
    );

    ords.define_handler(
        p_module_name => 'usbrain',
        p_pattern     => 'login',
        p_method      => 'POST',
        p_source_type => ords.source_type_plsql,
        p_source      => q'~
begin
    apex_json.parse(:body_text);

    declare
        l_email       varchar2(320) := lower(trim(apex_json.get_varchar2('email')));
        l_password    varchar2(4000) := apex_json.get_varchar2('password');
        l_user_id     usbrain_users.user_id%type;
        l_role        usbrain_users.role_name%type;
        l_token       varchar2(32);
        l_expires_at  timestamp with time zone := systimestamp + interval '1' hour;
    begin
        select user_id, role_name
          into l_user_id, l_role
          from usbrain_users
         where email = l_email
           and password_hash = standard_hash(password_salt || l_password, 'SHA256')
           and is_active = 'Y';

        l_token := lower(rawtohex(sys_guid()));
        insert into usbrain_sessions (user_id, token_hash, expires_at)
        values (l_user_id, standard_hash(l_token, 'SHA256'), l_expires_at);

        owa_util.status_line(200, 'OK');
        owa_util.mime_header('application/json', false);
        owa_util.http_header_close;
        apex_json.open_object;
        apex_json.write('userId', l_user_id);
        apex_json.write('role', case l_role when 'PATIENT' then 'patient' else 'therapist' end);
        apex_json.write('token', l_token);
        apex_json.write('expiresIn', 3600);
        apex_json.close_object;
    exception
        when no_data_found then
            owa_util.status_line(401, 'Unauthorized');
            owa_util.mime_header('application/json', false);
            owa_util.http_header_close;
            apex_json.open_object;
            apex_json.write('message', 'Invalid credentials');
            apex_json.close_object;
    end;
end;
~'
    );
end;
/

commit;
