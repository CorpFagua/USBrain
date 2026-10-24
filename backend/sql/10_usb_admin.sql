-- =============================================================================
-- USBrain · 10_usb_admin.sql
-- Administración de usuarios, roles y permisos (roles dinámicos).
--
-- Reglas de seguridad (todas se validan ANTES de escribir, porque usb_api confirma
-- la transacción también cuando responde un error esperado):
--   1. Nadie otorga permisos que no tiene: un rol que se asigna o se arma debe ser
--      un subconjunto de los permisos de quien lo hace (evita escalar privilegios).
--   2. Nadie modifica a un usuario con más permisos que él (un admin no toca al super admin).
--   3. Nadie cambia su propio rol ni se deshabilita a sí mismo.
--   4. Siempre queda al menos un usuario activo con roles.manage.
--   5. El rol SUPERADMIN (is_locked) no se edita ni se borra; los roles semilla no se borran.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

create or replace package usb_admin authid definer as

  -- Usuarios, roles, permisos, terapeutas y (con audit.view) la bitácora reciente.
  function overview return clob;

  function create_user(p_request json_object_t) return number;
  procedure update_user(p_user_id number, p_request json_object_t);
  -- p_action: 'unlock' | 'reset_mfa' | 'set_password'
  procedure user_action(p_user_id number, p_action varchar2, p_password varchar2);

  procedure create_role(p_request json_object_t);
  procedure update_role(p_role_id varchar2, p_request json_object_t);
  procedure delete_role(p_role_id varchar2);

end usb_admin;
/

create or replace package body usb_admin as

  c_manager_permission constant varchar2(30) := 'roles.manage';

  procedure fail(p_code pls_integer, p_slug varchar2, p_message varchar2) is
  begin
    raise_application_error(p_code, p_slug || '|' || p_message);
  end fail;

  function actor return number is
  begin
    return usb_auth.current_user_id;
  end actor;

  function load_role(p_role_id varchar2) return usb_roles%rowtype is
    l_role usb_roles%rowtype;
  begin
    select * into l_role from usb_roles where role_id = p_role_id;
    return l_role;
  exception
    when no_data_found then
      fail(usb_auth.e_bad_request, 'invalid_role', 'El rol no existe.');
  end load_role;

  function load_user(p_user_id number) return usb_users%rowtype is
    l_user usb_users%rowtype;
  begin
    select * into l_user from usb_users where user_id = p_user_id for update;
    return l_user;
  exception
    when no_data_found then
      fail(usb_auth.e_not_found, 'not_found', 'El usuario no existe.');
  end load_user;

  -- Regla 1 y 2: los permisos SENSIBLES del rol (administración, acceso a todos los
  -- casos) deben estar entre los del actor. Los operativos (registrar, ver asignados…)
  -- los puede asignar cualquiera con users.manage: así un admin crea terapeutas y pacientes.
  procedure require_can_grant_role(p_role_id varchar2, p_message varchar2) is
  begin
    for rp in (select rp.permission_id from usb_role_permissions rp
                 join usb_permissions p on p.permission_id = rp.permission_id
                where rp.role_id = p_role_id and p.sensitive) loop
      if not usb_auth.has_permission(rp.permission_id) then
        fail(usb_auth.e_forbidden, 'forbidden', p_message);
      end if;
    end loop;
  end require_can_grant_role;

  function role_has(p_role_id varchar2, p_permission varchar2) return boolean is
    l_count number;
  begin
    select count(*) into l_count from usb_role_permissions
     where role_id = p_role_id and permission_id = p_permission;
    return l_count > 0;
  end role_has;

  -- Regla 4: ¿cuántos usuarios activos con roles.manage quedan sin contar a este?
  function other_managers(p_user_id number) return number is
    l_count number;
  begin
    select count(*) into l_count
      from usb_users u join usb_role_permissions rp on rp.role_id = u.role
     where rp.permission_id = c_manager_permission and u.status = 'ACTIVE' and u.user_id != p_user_id;
    return l_count;
  end other_managers;

  function is_patient(p_user_id number) return boolean is
    l_count number;
  begin
    select count(*) into l_count from usb_patient_profiles where user_id = p_user_id;
    return l_count > 0;
  end is_patient;

  -- Terapeuta = rol con interfaz de terapeuta que puede ver pacientes asignados.
  -- (El super administrador tiene todos los permisos, pero no atiende pacientes.)
  function is_therapist_candidate(p_user_id number) return boolean is
    l_count number;
  begin
    select count(*) into l_count
      from usb_users u join usb_roles r on r.role_id = u.role
     where u.user_id = p_user_id and u.status = 'ACTIVE' and r.app_profile = 'THERAPIST';
    return l_count > 0 and usb_auth.user_has_permission(p_user_id, 'case.view.assigned');
  end is_therapist_candidate;

  procedure require_therapist_candidate(p_therapist_id number) is
  begin
    if p_therapist_id is null or not is_therapist_candidate(p_therapist_id) then
      fail(usb_auth.e_bad_request, 'invalid_therapist', 'Elige un terapeuta válido para el paciente.');
    end if;
  end require_therapist_candidate;

  function to_id(p_value varchar2) return number is
  begin
    return to_number(p_value);
  exception
    when value_error or invalid_number then
      return null;
  end to_id;

  -- ---------------------------------------------------------------------------
  -- Lectura
  -- ---------------------------------------------------------------------------

  function overview return clob is
    l_users  number := case when usb_auth.has_permission('users.view') then 1 else 0 end;
    l_audit  number := case when usb_auth.has_permission('audit.view') then 1 else 0 end;
    l_roles  number := case when usb_auth.has_permission(c_manager_permission) then 1 else 0 end;
    l_result clob;
  begin
    if l_users = 0 and l_roles = 0 then
      fail(usb_auth.e_forbidden, 'forbidden', 'No tienes permiso para ver la administración.');
    end if;

    select json_serialize(json_object(
      'users' value case when l_users = 1 then (
        select json_arrayagg(json_object(
                 'id' value to_char(u.user_id),
                 'email' value u.email,
                 'full_name' value u.full_name,
                 'role' value u.role,
                 'role_name' value r.name,
                 'profile' value r.app_profile,
                 'status' value u.status,
                 'mfa_enabled' value u.mfa_enabled,
                 'locked' value case when u.locked_until > systimestamp then 'true' else 'false' end format json,
                 'last_login_at' value to_char(sys_extract_utc(u.last_login_at), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                 'is_patient' value case when p.user_id is not null then 'true' else 'false' end format json,
                 'therapist_id' value to_char(p.therapist_id),
                 'therapist_name' value t.full_name)
               order by u.full_name returning json)
          from usb_users u
          join usb_roles r on r.role_id = u.role
          left join usb_patient_profiles p on p.user_id = u.user_id
          left join usb_users t on t.user_id = p.therapist_id) end,
      'roles' value (
        select json_arrayagg(json_object(
                 'id' value r.role_id,
                 'name' value r.name,
                 'description' value r.description,
                 'profile' value r.app_profile,
                 'is_system' value r.is_system,
                 'is_locked' value r.is_locked,
                 'user_count' value (select count(*) from usb_users u where u.role = r.role_id),
                 'permissions' value (select json_arrayagg(rp.permission_id order by rp.permission_id returning json)
                                        from usb_role_permissions rp where rp.role_id = r.role_id))
               order by case when r.is_system then 0 else 1 end, r.name returning json)
          from usb_roles r),
      'permissions' value (
        select json_arrayagg(json_object('id' value p.permission_id, 'name' value p.name,
                                         'category' value p.category, 'description' value p.description)
                             order by p.category, p.permission_id returning json)
          from usb_permissions p),
      'therapists' value (
        select json_arrayagg(json_object('id' value to_char(u.user_id), 'full_name' value u.full_name)
                             order by u.full_name returning json)
          from usb_users u
          join usb_roles r on r.role_id = u.role
         where u.status = 'ACTIVE' and r.app_profile = 'THERAPIST'
           and exists (select 1 from usb_role_permissions rp
                        where rp.role_id = u.role and rp.permission_id = 'case.view.assigned')),
      'events' value case when l_audit = 1 then (
        select json_arrayagg(json_object(
                 'at' value to_char(sys_extract_utc(e.created_at), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                 'type' value e.event_type,
                 'email' value u.email,
                 'detail' value e.detail,
                 'ip' value e.ip_address)
               order by e.event_id desc returning json)
          from (select * from usb_auth_events order by event_id desc fetch first 60 rows only) e
          left join usb_users u on u.user_id = e.user_id) end
      returning json) returning clob)
      into l_result
      from dual;
    return l_result;
  end overview;

  -- ---------------------------------------------------------------------------
  -- Usuarios
  -- ---------------------------------------------------------------------------

  function create_user(p_request json_object_t) return number is
    l_role         usb_roles%rowtype;
    l_therapist_id number := to_id(p_request.get_string('therapist_id'));
    l_full_name    varchar2(200) := trim(p_request.get_string('full_name'));
    l_program      varchar2(200) := trim(p_request.get_string('program'));
    l_condition    varchar2(400) := trim(p_request.get_string('condition'));
    l_birth        date;
    l_user_id      number;
  begin
    usb_auth.require_permission('users.manage');
    l_role := load_role(p_request.get_string('role'));
    require_can_grant_role(l_role.role_id, 'No puedes crear usuarios con un rol que tiene permisos de administración que tú no tienes.');

    if l_role.app_profile = 'PATIENT' then
      require_therapist_candidate(l_therapist_id);
      begin
        l_birth := to_date(p_request.get_string('birth_date'), 'YYYY-MM-DD');
      exception
        when others then
          fail(usb_auth.e_bad_request, 'invalid_date', 'La fecha de nacimiento no es válida (AAAA-MM-DD).');
      end;
    end if;

    -- create_user valida correo y contraseña antes de insertar.
    l_user_id := usb_auth.create_user(p_request.get_string('email'), l_full_name,
                                      l_role.role_id, p_request.get_string('password'));
    if l_role.app_profile = 'PATIENT' then
      insert into usb_patient_profiles (user_id, therapist_id, birth_date, program, condition)
      values (l_user_id, l_therapist_id, l_birth, l_program, l_condition);
      insert into usb_cases (patient_id, title)
      values (l_user_id, 'Caso de ' || nvl(lower(l_condition), l_full_name));
    end if;
    usb_auth.log_event('ADMIN_USER_CREATED', l_user_id, 'rol=' || l_role.role_id || ' por=' || actor);
    return l_user_id;
  end create_user;

  procedure update_user(p_user_id number, p_request json_object_t) is
    l_user         usb_users%rowtype := load_user(p_user_id);
    l_new_role     varchar2(40) := nvl(p_request.get_string('role'), l_user.role);
    l_new_status   varchar2(20) := nvl(p_request.get_string('status'), l_user.status);
    l_name         varchar2(200) := nvl(trim(p_request.get_string('full_name')), l_user.full_name);
    l_therapist_id number := to_id(p_request.get_string('therapist_id'));
    l_role         usb_roles%rowtype;
  begin
    usb_auth.require_permission('users.manage');
    require_can_grant_role(l_user.role, 'No puedes modificar a un usuario con más permisos de administración que tú.');
    l_role := load_role(l_new_role);
    if l_new_status not in ('ACTIVE', 'DISABLED') then
      fail(usb_auth.e_bad_request, 'invalid_status', 'Estado no válido.');
    end if;
    if p_user_id = actor and (l_new_role != l_user.role or l_new_status != l_user.status) then
      fail(usb_auth.e_forbidden, 'forbidden', 'No puedes cambiar tu propio rol ni deshabilitarte.');
    end if;
    if l_new_role != l_user.role then
      require_can_grant_role(l_new_role, 'No puedes asignar un rol con permisos de administración que tú no tienes.');
      -- Un paciente necesita su ficha y su caso: no se mezcla con roles de otra interfaz.
      if is_patient(p_user_id) != (l_role.app_profile = 'PATIENT') then
        fail(usb_auth.e_conflict, 'profile_mismatch',
             'Un paciente solo puede pasar a otro rol de paciente, y viceversa.');
      end if;
    end if;
    if role_has(l_user.role, c_manager_permission) and l_user.status = 'ACTIVE'
       and (not role_has(l_new_role, c_manager_permission) or l_new_status != 'ACTIVE')
       and other_managers(p_user_id) = 0 then
      fail(usb_auth.e_conflict, 'last_manager', 'Debe quedar al menos un super administrador activo.');
    end if;
    if l_therapist_id is not null then
      if not is_patient(p_user_id) then
        fail(usb_auth.e_bad_request, 'invalid_therapist', 'Solo los pacientes tienen terapeuta asignado.');
      end if;
      require_therapist_candidate(l_therapist_id);
    end if;

    -- Validado todo: se escribe.
    update usb_users set full_name = l_name, role = l_new_role, updated_at = systimestamp
     where user_id = p_user_id;
    if l_therapist_id is not null then
      update usb_patient_profiles set therapist_id = l_therapist_id where user_id = p_user_id;
    end if;
    if l_new_status != l_user.status then
      usb_auth.set_status(p_user_id, l_new_status);
    end if;
    if l_new_role != l_user.role then
      -- Sus permisos cambian: que vuelva a entrar y la app cargue la interfaz nueva.
      usb_auth.revoke_all_sessions(p_user_id, 'ROLE_CHANGED');
    end if;
    usb_auth.log_event('ADMIN_USER_UPDATED', p_user_id,
                       'rol=' || l_new_role || ' estado=' || l_new_status || ' por=' || actor);
  end update_user;

  procedure user_action(p_user_id number, p_action varchar2, p_password varchar2) is
    l_user usb_users%rowtype := load_user(p_user_id);
  begin
    usb_auth.require_permission('users.manage');
    require_can_grant_role(l_user.role, 'No puedes modificar a un usuario con más permisos de administración que tú.');
    if p_user_id = actor then
      fail(usb_auth.e_forbidden, 'forbidden', 'Para tu propia cuenta usa las opciones de tu perfil.');
    end if;
    case p_action
      when 'unlock' then usb_auth.unlock_user(p_user_id);
      when 'reset_mfa' then usb_auth.reset_mfa(p_user_id);
      when 'set_password' then usb_auth.set_password(p_user_id, p_password);
      else fail(usb_auth.e_bad_request, 'invalid_action', 'Acción no válida.');
    end case;
    usb_auth.log_event('ADMIN_USER_' || upper(p_action), p_user_id, 'por=' || actor);
  end user_action;

  -- ---------------------------------------------------------------------------
  -- Roles
  -- ---------------------------------------------------------------------------

  -- Valida la lista de permisos pedida y devuelve si incluye roles.manage.
  procedure validate_permissions(p_permissions json_array_t) is
    l_permission varchar2(60);
    l_count      number;
  begin
    if p_permissions is null then
      return;
    end if;
    for i in 0 .. p_permissions.get_size - 1 loop
      l_permission := p_permissions.get_string(i);
      select count(*) into l_count from usb_permissions where permission_id = l_permission;
      if l_count = 0 then
        fail(usb_auth.e_bad_request, 'invalid_permission', 'El permiso ' || l_permission || ' no existe.');
      end if;
      if not usb_auth.has_permission(l_permission) then
        fail(usb_auth.e_forbidden, 'forbidden', 'No puedes otorgar un permiso que tú no tienes: ' || l_permission || '.');
      end if;
    end loop;
  end validate_permissions;

  function includes(p_permissions json_array_t, p_permission varchar2) return boolean is
  begin
    if p_permissions is not null then
      for i in 0 .. p_permissions.get_size - 1 loop
        if p_permissions.get_string(i) = p_permission then
          return true;
        end if;
      end loop;
    end if;
    return false;
  end includes;

  procedure write_permissions(p_role_id varchar2, p_permissions json_array_t) is
    l_permission varchar2(60);
  begin
    delete from usb_role_permissions where role_id = p_role_id;
    if p_permissions is not null then
      for i in 0 .. p_permissions.get_size - 1 loop
        l_permission := p_permissions.get_string(i);
        merge into usb_role_permissions rp
        using (select p_role_id role_id, l_permission permission_id from dual) x
           on (rp.role_id = x.role_id and rp.permission_id = x.permission_id)
         when not matched then insert (role_id, permission_id) values (x.role_id, x.permission_id);
      end loop;
    end if;
  end write_permissions;

  procedure validate_role_fields(p_name varchar2, p_profile varchar2) is
  begin
    if trim(p_name) is null then
      fail(usb_auth.e_bad_request, 'missing_fields', 'Escribe el nombre del rol.');
    end if;
    if p_profile is null or p_profile not in ('PATIENT', 'THERAPIST', 'ADMIN') then
      fail(usb_auth.e_bad_request, 'invalid_profile', 'Elige qué interfaz ve el rol: paciente, terapeuta o administración.');
    end if;
  end validate_role_fields;

  procedure create_role(p_request json_object_t) is
    l_id          varchar2(100) := upper(trim(p_request.get_string('id')));
    l_name        varchar2(100) := trim(p_request.get_string('name'));
    l_profile     varchar2(20) := p_request.get_string('profile');
    l_permissions json_array_t := p_request.get_array('permissions');
    l_descr       varchar2(400) := trim(p_request.get_string('description'));
    l_count       number;
  begin
    usb_auth.require_permission(c_manager_permission);
    if l_id is null or not regexp_like(l_id, '^[A-Z][A-Z0-9_]{1,39}$') then
      fail(usb_auth.e_bad_request, 'invalid_role_id',
           'El código del rol va en mayúsculas, sin espacios (ej.: SUPERVISOR).');
    end if;
    select count(*) into l_count from usb_roles where role_id = l_id;
    if l_count > 0 then
      fail(usb_auth.e_conflict, 'role_exists', 'Ya existe un rol con ese código.');
    end if;
    validate_role_fields(l_name, l_profile);
    validate_permissions(l_permissions);

    insert into usb_roles (role_id, name, description, app_profile)
    values (l_id, l_name, l_descr, l_profile);
    write_permissions(l_id, l_permissions);
    usb_auth.log_event('ROLE_CREATED', actor, l_id);
  end create_role;

  procedure update_role(p_role_id varchar2, p_request json_object_t) is
    l_role        usb_roles%rowtype := load_role(p_role_id);
    l_name        varchar2(100) := nvl(trim(p_request.get_string('name')), l_role.name);
    l_profile     varchar2(20) := nvl(p_request.get_string('profile'), l_role.app_profile);
    l_permissions json_array_t := p_request.get_array('permissions');
    l_descr       varchar2(400) := trim(p_request.get_string('description'));
    l_users       number;
    l_managers    number;
  begin
    usb_auth.require_permission(c_manager_permission);
    if l_role.is_locked then
      fail(usb_auth.e_forbidden, 'role_locked', 'El rol de super administrador no se puede modificar.');
    end if;
    validate_role_fields(l_name, l_profile);
    select count(*) into l_users from usb_users where role = p_role_id;
    if l_profile != l_role.app_profile and l_users > 0 then
      fail(usb_auth.e_conflict, 'role_in_use',
           'No se puede cambiar la interfaz de un rol que ya tiene usuarios.');
    end if;
    -- Quitar permisos que el actor no tiene también sería tocar algo "por encima" de él.
    require_can_grant_role(p_role_id, 'No puedes modificar un rol con permisos que tú no tienes.');
    validate_permissions(l_permissions);
    if l_permissions is not null and role_has(p_role_id, c_manager_permission)
       and not includes(l_permissions, c_manager_permission) then
      select count(*) into l_managers
        from usb_users u join usb_role_permissions rp on rp.role_id = u.role
       where rp.permission_id = c_manager_permission and u.status = 'ACTIVE' and u.role != p_role_id;
      if l_managers = 0 then
        fail(usb_auth.e_conflict, 'last_manager', 'Debe quedar al menos un super administrador activo.');
      end if;
    end if;

    update usb_roles
       set name = l_name, app_profile = l_profile, updated_at = systimestamp,
           description = nvl(l_descr, description)
     where role_id = p_role_id;
    if l_permissions is not null then
      write_permissions(p_role_id, l_permissions);
    end if;
    usb_auth.log_event('ROLE_UPDATED', actor, p_role_id);
  end update_role;

  procedure delete_role(p_role_id varchar2) is
    l_role  usb_roles%rowtype := load_role(p_role_id);
    l_users number;
  begin
    usb_auth.require_permission(c_manager_permission);
    if l_role.is_system then
      fail(usb_auth.e_forbidden, 'role_system', 'Los roles base del sistema no se pueden borrar.');
    end if;
    select count(*) into l_users from usb_users where role = p_role_id;
    if l_users > 0 then
      fail(usb_auth.e_conflict, 'role_in_use',
           'El rol tiene ' || l_users || ' usuario(s). Cámbialos de rol antes de borrarlo.');
    end if;
    delete from usb_roles where role_id = p_role_id;
    usb_auth.log_event('ROLE_DELETED', actor, p_role_id);
  end delete_role;

end usb_admin;
/

show errors package body usb_admin
begin
  for e in (select 1 from user_errors where name = 'USB_ADMIN' and rownum = 1) loop
    raise_application_error(-20000, 'USB_ADMIN no compiló; revisa los errores de arriba.');
  end loop;
end;
/

prompt Paquete USB_ADMIN listo.
exit
