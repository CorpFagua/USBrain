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
--   6. Permisos individuales (usb_user_permissions): solo con users.permissions, nunca
--      sobre uno mismo ni sobre un SUPERADMIN, solo con permisos que el actor tiene, y
--      cada excepción lleva motivo (y, si es temporal, fecha de vencimiento).
--   Las reglas 2 y 4 miran los permisos EFECTIVOS (rol + excepciones), no solo el rol.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

create or replace package usb_admin authid definer as

  -- Usuarios, roles, permisos, terapeutas y (con audit.view) la bitácora reciente.
  function overview return clob;
  -- Exportación seudonimizada para análisis (casos individuales A1 vs B1 y estudios de grupo):
  -- sin nombres, correos, documentos, diagnósticos ni notas. APEX y ORDS leen el mismo esquema.
  function analytics_dataset return clob;
  -- {"min_phase_observations": 3..30}. Requiere settings.clinical.
  procedure update_settings(p_request json_object_t);

  function create_user(p_request json_object_t) return number;
  procedure update_user(p_user_id number, p_request json_object_t);
  -- p_action: 'unlock' | 'reset_mfa' | 'set_password'
  procedure user_action(p_user_id number, p_action varchar2, p_password varchar2);
  -- Reemplaza las excepciones del usuario: {"overrides": [{"permission", "effect": GRANT|DENY,
  -- "reason", "expires_at": "AAAA-MM-DD" (opcional, válido hasta ese día inclusive)}]}.
  procedure set_user_permissions(p_user_id number, p_request json_object_t);

  procedure create_role(p_request json_object_t);
  procedure update_role(p_role_id varchar2, p_request json_object_t);
  procedure delete_role(p_role_id varchar2);

end usb_admin;
/

create or replace package body usb_admin as

  c_manager_permission  constant varchar2(30) := 'roles.manage';
  c_override_permission constant varchar2(30) := 'users.permissions';
  c_timezone            constant varchar2(30) := 'America/Bogota';
  -- Mínimo de días por fase para una serie y de personas por grupo (protege la identidad).
  c_min_points          constant pls_integer := 3;

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

  -- Regla 2 sobre permisos efectivos: un GRANT individual también "sube" al usuario.
  procedure require_can_manage_user(p_user_id number, p_message varchar2) is
  begin
    for ep in (select ep.permission_id from usb_effective_permissions ep
                 join usb_permissions p on p.permission_id = ep.permission_id
                where ep.user_id = p_user_id and p.sensitive) loop
      if not usb_auth.has_permission(ep.permission_id) then
        fail(usb_auth.e_forbidden, 'forbidden', p_message);
      end if;
    end loop;
  end require_can_manage_user;

  function role_has(p_role_id varchar2, p_permission varchar2) return boolean is
    l_count number;
  begin
    select count(*) into l_count from usb_role_permissions
     where role_id = p_role_id and permission_id = p_permission;
    return l_count > 0;
  end role_has;

  -- ¿Tendría el usuario este permiso con este rol, contando sus excepciones vigentes?
  function would_have(p_user_id number, p_role_id varchar2, p_permission varchar2) return boolean is
    l_effect usb_user_permissions.effect%type;
  begin
    select max(effect) into l_effect from usb_user_permissions
     where user_id = p_user_id and permission_id = p_permission
       and (expires_at is null or expires_at > systimestamp);
    return case l_effect when 'GRANT' then true when 'DENY' then false
                         else role_has(p_role_id, p_permission) end;
  end would_have;

  -- Regla 4: ¿cuántos usuarios activos con roles.manage quedan sin contar a este?
  function other_managers(p_user_id number) return number is
    l_count number;
  begin
    select count(*) into l_count
      from usb_users u join usb_effective_permissions ep on ep.user_id = u.user_id
     where ep.permission_id = c_manager_permission and u.status = 'ACTIVE' and u.user_id != p_user_id;
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
                 'therapist_name' value t.full_name,
                 'permissions' value (select json_arrayagg(ep.permission_id order by ep.permission_id returning json)
                                        from usb_effective_permissions ep where ep.user_id = u.user_id),
                 'overrides' value (
                   select json_arrayagg(json_object(
                            'permission' value up.permission_id,
                            'effect' value up.effect,
                            'reason' value up.reason,
                            -- Último día válido (se guarda como el inicio del día siguiente).
                            'expires_at' value to_char((up.expires_at - interval '1' second) at time zone 'America/Bogota', 'YYYY-MM-DD'),
                            'active' value case when up.expires_at is null or up.expires_at > systimestamp
                                                then 'true' else 'false' end format json,
                            'granted_by' value g.full_name,
                            'created_at' value to_char(sys_extract_utc(up.created_at), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'))
                          order by up.permission_id returning json)
                     from usb_user_permissions up
                     left join usb_users g on g.user_id = up.granted_by
                    where up.user_id = u.user_id))
               order by u.full_name returning json)
          from usb_users u
          join usb_roles r on r.role_id = u.role
          left join usb_patient_profiles p on p.user_id = u.user_id
          left join usb_users t on t.user_id = p.therapist_id
         where not u.is_synthetic) end,
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
                                         'category' value p.category, 'description' value p.description,
                                         'sensitive' value p.sensitive)
                             order by p.category, p.permission_id returning json)
          from usb_permissions p),
      'therapists' value (
        select json_arrayagg(json_object('id' value to_char(u.user_id), 'full_name' value u.full_name)
                             order by u.full_name returning json)
          from usb_users u
          join usb_roles r on r.role_id = u.role
         where u.status = 'ACTIVE' and r.app_profile = 'THERAPIST'
           and exists (select 1 from usb_effective_permissions ep
                        where ep.user_id = u.user_id and ep.permission_id = 'case.view.assigned')),
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
  -- Exportación analítica
  --   series:  casos individuales (conductas sin study_code). Compara A1 (línea base) con B1
  --            (intervención) del mismo paciente: un punto por día = total de ese día.
  --   studies: conductas de un mismo estudio (study_code) en varios pacientes. Cada paciente
  --            aporta su media diaria en A1 y en B1 (prueba pareada, n = pacientes); además se
  --            entrega la serie diaria promedio y grupos por programa y edad con n >= 3.
  -- Sin nombres, correos, documentos, diagnósticos ni notas. Las series individuales llevan
  -- la fecha de cada día (hace falta para ordenarlas); los estudios no llevan nada por persona.
  -- ---------------------------------------------------------------------------

  function measure_unit(p_dimension varchar2) return varchar2 is
  begin
    return case p_dimension when 'DURACION' then 'minutos' when 'INTENSIDAD' then 'escala 0–10'
                            else 'episodios por día' end;
  end measure_unit;

  function expected_direction(p_behavior_type varchar2) return varchar2 is
  begin
    return case when p_behavior_type = 'ADAPTATIVA' then 'INCREASE' else 'DECREASE' end;
  end expected_direction;

  -- Totales diarios de una conducta en una fase, numerados desde el primer día observado.
  function case_points(p_behavior_id number, p_dimension varchar2, p_seq number, p_first date) return json_array_t is
    l_points  json_array_t := json_array_t();
    l_point   json_object_t;
    l_session pls_integer := 0;
  begin
    for d in (select trunc(e.entry_date) entry_date, sum(e.value) total
                from usb_task_entries e join usb_tasks t on t.task_id = e.task_id
               where t.behavior_id = p_behavior_id and t.dimension = p_dimension
                 and t.task_type = 'REGISTRO_AUTONOMO' and t.widget_id is null and e.phase_seq = p_seq
               group by trunc(e.entry_date) order by 1) loop
      l_session := l_session + 1;
      l_point := json_object_t();
      l_point.put('session', l_session);
      l_point.put('day', d.entry_date - p_first + 1);
      l_point.put('date', to_char(d.entry_date, 'YYYY-MM-DD'));
      l_point.put('value', d.total);
      l_points.append(l_point);
    end loop;
    return l_points;
  end case_points;

  -- Promedio entre pacientes del total diario, por día relativo al inicio de cada paciente.
  function study_points(p_study varchar2, p_dimension varchar2, p_seq number) return json_array_t is
    l_points  json_array_t := json_array_t();
    l_point   json_object_t;
    l_session pls_integer := 0;
  begin
    for d in (select x.day_no, avg(x.total) mean_total
                from (select y.*, y.d - min(y.d) over (partition by y.behavior_id) + 1 day_no
                        from (select b.behavior_id, e.phase_seq seq, trunc(e.entry_date) d, sum(e.value) total
                                from usb_behaviors b
                                join usb_tasks t on t.behavior_id = b.behavior_id
                                join usb_task_entries e on e.task_id = t.task_id
                               where b.study_code = p_study and t.dimension = p_dimension
                                 and t.task_type = 'REGISTRO_AUTONOMO' and t.widget_id is null
                                 and e.phase_seq in (1, 2)
                               group by b.behavior_id, e.phase_seq, trunc(e.entry_date)) y) x
               where x.seq = p_seq
               group by x.day_no order by x.day_no) loop
      l_session := l_session + 1;
      l_point := json_object_t();
      l_point.put('session', l_session);
      l_point.put('day', d.day_no);
      l_point.put('value', round(d.mean_total, 3));
      l_points.append(l_point);
    end loop;
    return l_points;
  end study_points;

  function analytics_dataset return clob is
    type t_group is record (n pls_integer := 0, sum_a number := 0, sum_b number := 0, sort_key pls_integer);
    type t_groups is table of t_group index by varchar2(100);
    l_root     json_object_t := json_object_t();
    l_privacy  json_object_t := json_object_t();
    l_list     json_array_t := json_array_t();
    l_studies  json_array_t := json_array_t();
    l_item     json_object_t;
    l_pairs    json_array_t;
    l_pair     json_object_t;
    l_groups   json_array_t;
    l_programs t_groups;
    l_ages     t_groups;
    l_key      varchar2(100);
    l_band     varchar2(30);
    l_today    date := trunc(cast(systimestamp at time zone 'America/Bogota' as date));

    procedure add_to(p_groups in out nocopy t_groups, p_key varchar2, p_a number, p_b number, p_sort pls_integer) is
    begin
      if not p_groups.exists(p_key) then
        p_groups(p_key).sort_key := p_sort;
      end if;
      p_groups(p_key).n := p_groups(p_key).n + 1;
      p_groups(p_key).sum_a := p_groups(p_key).sum_a + p_a;
      p_groups(p_key).sum_b := p_groups(p_key).sum_b + p_b;
    end add_to;

    -- Solo grupos con n >= 3: los más pequeños podrían identificar a alguien.
    function groups_json(p_groups t_groups) return json_array_t is
      l_out  json_array_t := json_array_t();
      l_obj  json_object_t;
      l_name varchar2(100) := p_groups.first;
    begin
      while l_name is not null loop
        if p_groups(l_name).n >= c_min_points then
          l_obj := json_object_t();
          l_obj.put('group', l_name);
          l_obj.put('participants', p_groups(l_name).n);
          l_obj.put('baseline_mean', round(p_groups(l_name).sum_a / p_groups(l_name).n, 3));
          l_obj.put('intervention_mean', round(p_groups(l_name).sum_b / p_groups(l_name).n, 3));
          l_obj.put('sort', p_groups(l_name).sort_key);
          l_out.append(l_obj);
        end if;
        l_name := p_groups.next(l_name);
      end loop;
      return l_out;
    end groups_json;
  begin
    if not usb_auth.has_permission('analytics.view') then
      fail(usb_auth.e_forbidden, 'forbidden', 'No tienes permiso para consultar análisis estadísticos.');
    end if;

    l_root.put('status', 'CONNECTED');
    l_root.put('source', 'Oracle/ORDS · esquema USBRAIN (consultable desde APEX)');
    l_root.put('generated_at', to_char(sys_extract_utc(systimestamp), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'));
    l_privacy.put('pseudonymized', true);
    l_privacy.put('fields', json_array_t('["series_id","behavior","dimension","phase","date","daily_total"]'));
    l_privacy.put('excluded', json_array_t('["name","email","document","diagnosis","notes"]'));
    l_root.put('privacy', l_privacy);

    -- Casos individuales con al menos 3 días en A1 y 3 en B1.
    for s in (select x.*, substr(rawtohex(standard_hash(to_char(x.behavior_id) || ':' || x.dimension
                                                        || ':usbrain-analytics-v1', 'SHA256')), 1, 16) series_id
                from (select b.behavior_id, b.name, b.behavior_type, t.dimension, min(trunc(e.entry_date)) first_day
                        from usb_behaviors b
                        join usb_tasks t on t.behavior_id = b.behavior_id
                        join usb_task_entries e on e.task_id = t.task_id
                       where b.study_code is null and t.task_type = 'REGISTRO_AUTONOMO' and t.widget_id is null
                         and e.phase_seq in (1, 2)
                       group by b.behavior_id, b.name, b.behavior_type, t.dimension
                      having count(distinct case when e.phase_seq = 1 then trunc(e.entry_date) end) >= c_min_points
                         and count(distinct case when e.phase_seq = 2 then trunc(e.entry_date) end) >= c_min_points) x
               order by x.name, x.dimension) loop
      l_item := json_object_t();
      l_item.put('id', s.series_id);
      l_item.put('behavior', s.name);
      l_item.put('dimension', s.dimension);
      l_item.put('unit', measure_unit(s.dimension));
      l_item.put('expected_direction', expected_direction(s.behavior_type));
      l_item.put('baseline', case_points(s.behavior_id, s.dimension, 1, s.first_day));
      l_item.put('intervention', case_points(s.behavior_id, s.dimension, 2, s.first_day));
      l_list.append(l_item);
    end loop;
    l_root.put('series', l_list);

    -- Estudios de grupo.
    for st in (select x.*, substr(rawtohex(standard_hash(x.study_code || ':' || x.dimension, 'SHA256')), 1, 16) study_id
                 from (select b.study_code, min(b.name) behavior, min(b.behavior_type) behavior_type, t.dimension
                         from usb_behaviors b join usb_tasks t on t.behavior_id = b.behavior_id
                        where b.study_code is not null and t.task_type = 'REGISTRO_AUTONOMO' and t.widget_id is null
                        group by b.study_code, t.dimension) x
                order by x.study_code, x.dimension) loop
      l_pairs := json_array_t();
      l_programs.delete;
      l_ages.delete;
      for pt in (select avg(case when y.seq = 1 then y.total end) mean_a,
                        avg(case when y.seq = 2 then y.total end) mean_b,
                        min(nvl(pr.program, 'Sin programa')) program,
                        min(trunc(months_between(l_today, pr.birth_date) / 12)) age
                   from (select b.behavior_id, c.patient_id, e.phase_seq seq, trunc(e.entry_date) d, sum(e.value) total
                           from usb_behaviors b
                           join usb_cases c on c.case_id = b.case_id
                           join usb_tasks t on t.behavior_id = b.behavior_id
                           join usb_task_entries e on e.task_id = t.task_id
                          where b.study_code = st.study_code and t.dimension = st.dimension
                            and t.task_type = 'REGISTRO_AUTONOMO' and t.widget_id is null and e.phase_seq in (1, 2)
                          group by b.behavior_id, c.patient_id, e.phase_seq, trunc(e.entry_date)) y
                   join usb_patient_profiles pr on pr.user_id = y.patient_id
                  group by y.behavior_id
                 having count(distinct case when y.seq = 1 then y.d end) >= c_min_points
                    and count(distinct case when y.seq = 2 then y.d end) >= c_min_points) loop
        l_pair := json_object_t();
        l_pair.put('baseline_mean', round(pt.mean_a, 4));
        l_pair.put('intervention_mean', round(pt.mean_b, 4));
        l_pairs.append(l_pair);
        add_to(l_programs, pt.program, pt.mean_a, pt.mean_b, 0);
        l_band := case when pt.age is null then 'Sin dato'
                       when pt.age < 18 then 'Menos de 18 años'
                       when pt.age <= 20 then '18–20 años'
                       when pt.age <= 23 then '21–23 años'
                       when pt.age <= 26 then '24–26 años'
                       else '27 años o más' end;
        add_to(l_ages, l_band, pt.mean_a, pt.mean_b,
               case when pt.age is null then 9 when pt.age < 18 then 0 when pt.age <= 20 then 1
                    when pt.age <= 23 then 2 when pt.age <= 26 then 3 else 4 end);
      end loop;
      if l_pairs.get_size >= c_min_points then
        l_item := json_object_t();
        l_item.put('id', st.study_id);
        l_item.put('code', st.study_code);
        l_item.put('behavior', st.behavior);
        l_item.put('dimension', st.dimension);
        l_item.put('unit', measure_unit(st.dimension));
        l_item.put('expected_direction', expected_direction(st.behavior_type));
        l_item.put('participants', l_pairs);
        l_item.put('baseline', study_points(st.study_code, st.dimension, 1));
        l_item.put('intervention', study_points(st.study_code, st.dimension, 2));
        l_groups := groups_json(l_programs);
        l_item.put('program_groups', l_groups);
        l_item.put('age_groups', groups_json(l_ages));
        l_studies.append(l_item);
      end if;
    end loop;
    l_root.put('studies', l_studies);
    return l_root.to_clob;
  end analytics_dataset;

  -- ---------------------------------------------------------------------------
  -- Parámetros clínicos
  -- ---------------------------------------------------------------------------

  procedure update_settings(p_request json_object_t) is
    l_min      number;
    l_actor_id number := actor;
  begin
    usb_auth.require_permission('settings.clinical');
    begin
      l_min := p_request.get_number('min_phase_observations');
    exception
      when others then l_min := null;
    end;
    if l_min is null or l_min != trunc(l_min) or l_min < 3 or l_min > 30 then
      fail(usb_auth.e_bad_request, 'invalid_setting',
           'El mínimo de observaciones por fase debe ser un número entero entre 3 y 30.');
    end if;
    update usb_app_settings set value = to_char(l_min), updated_by = l_actor_id, updated_at = systimestamp
     where setting_key = 'MIN_PHASE_OBSERVATIONS';
    usb_auth.log_event('SETTINGS_UPDATED', actor, 'MIN_PHASE_OBSERVATIONS=' || l_min);
  end update_settings;

  -- ---------------------------------------------------------------------------
  -- Usuarios
  -- ---------------------------------------------------------------------------

  function create_user(p_request json_object_t) return number is
    l_role         usb_roles%rowtype;
    l_actor_role   usb_users.role%type;
    l_actor_id     number := actor;
    l_therapist_id number := to_id(p_request.get_string('therapist_id'));
    l_full_name    varchar2(200) := trim(p_request.get_string('full_name'));
    l_program      varchar2(200) := trim(p_request.get_string('program'));
    l_condition    varchar2(400) := trim(p_request.get_string('condition'));
    l_birth        date;
    l_user_id      number;
  begin
    usb_auth.require_permission('users.manage');
    l_role := load_role(p_request.get_string('role'));
    if l_role.role_id = 'SUPERADMIN' then
      select role into l_actor_role from usb_users where user_id = l_actor_id;
      if l_actor_role != 'SUPERADMIN' then
        fail(usb_auth.e_forbidden, 'forbidden',
             'Solo un super administrador puede crear otro super administrador.');
      end if;
    end if;
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
    require_can_manage_user(p_user_id, 'No puedes modificar a un usuario con más permisos de administración que tú.');
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
    if usb_auth.user_has_permission(p_user_id, c_manager_permission)
       and (not would_have(p_user_id, l_new_role, c_manager_permission) or l_new_status != 'ACTIVE')
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
    require_can_manage_user(p_user_id, 'No puedes modificar a un usuario con más permisos de administración que tú.');
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
  -- Permisos individuales (excepciones sobre el rol)
  -- ---------------------------------------------------------------------------

  procedure set_user_permissions(p_user_id number, p_request json_object_t) is
    type t_override is record (
      permission_id usb_user_permissions.permission_id%type,
      effect        usb_user_permissions.effect%type,
      reason        usb_user_permissions.reason%type,
      expires_at    usb_user_permissions.expires_at%type);
    type t_overrides is table of t_override index by pls_integer;
    type t_seen is table of boolean index by varchar2(60);
    l_user      usb_users%rowtype := load_user(p_user_id);
    l_role      usb_roles%rowtype := load_role(l_user.role);
    l_list      json_array_t := p_request.get_array('overrides');
    l_item      json_object_t;
    l_expires   varchar2(30);
    l_new       t_overrides;
    l_seen      t_seen;
    l_manager   boolean := role_has(l_user.role, c_manager_permission);
    l_actor_id  number := actor;
    l_count     number;
    l_detail    varchar2(1000);
  begin
    usb_auth.require_permission(c_override_permission);
    if l_list is null then
      fail(usb_auth.e_bad_request, 'missing_fields', 'Envía la lista de permisos individuales (overrides).');
    end if;
    if p_user_id = actor then
      fail(usb_auth.e_forbidden, 'forbidden', 'No puedes cambiar tus propios permisos.');
    end if;
    if l_role.is_locked then
      fail(usb_auth.e_forbidden, 'role_locked', 'El super administrador ya tiene todos los permisos: no admite excepciones.');
    end if;
    require_can_manage_user(p_user_id, 'No puedes modificar a un usuario con más permisos de administración que tú.');
    -- Tampoco se borra una excepción sobre un permiso que el actor no tiene.
    for up in (select permission_id from usb_user_permissions where user_id = p_user_id) loop
      if not usb_auth.has_permission(up.permission_id) then
        fail(usb_auth.e_forbidden, 'forbidden',
             'El usuario tiene una excepción sobre un permiso que tú no tienes: ' || up.permission_id || '.');
      end if;
    end loop;

    for i in 0 .. l_list.get_size - 1 loop
      if not l_list.get(i).is_object then
        fail(usb_auth.e_bad_request, 'invalid_override', 'Cada excepción debe ser un objeto.');
      end if;
      l_item := treat(l_list.get(i) as json_object_t);
      l_new(i).permission_id := substr(l_item.get_string('permission'), 1, 60);
      l_new(i).effect := upper(l_item.get_string('effect'));
      l_new(i).reason := substr(trim(l_item.get_string('reason')), 1, 400);
      l_expires := l_item.get_string('expires_at');

      select count(*) into l_count from usb_permissions where permission_id = l_new(i).permission_id;
      if l_count = 0 then
        fail(usb_auth.e_bad_request, 'invalid_permission', 'El permiso ' || l_new(i).permission_id || ' no existe.');
      end if;
      if l_seen.exists(l_new(i).permission_id) then
        fail(usb_auth.e_bad_request, 'duplicate_override', 'El permiso ' || l_new(i).permission_id || ' está repetido.');
      end if;
      l_seen(l_new(i).permission_id) := true;
      if l_new(i).effect is null or l_new(i).effect not in ('GRANT', 'DENY') then
        fail(usb_auth.e_bad_request, 'invalid_effect', 'Cada excepción es GRANT (otorgar) o DENY (quitar).');
      end if;
      if not usb_auth.has_permission(l_new(i).permission_id) then
        fail(usb_auth.e_forbidden, 'forbidden',
             'No puedes otorgar ni quitar un permiso que tú no tienes: ' || l_new(i).permission_id || '.');
      end if;
      -- Una excepción que no cambia nada solo confunde al auditar.
      if (l_new(i).effect = 'GRANT') = role_has(l_user.role, l_new(i).permission_id) then
        fail(usb_auth.e_bad_request, 'redundant_override',
             case l_new(i).effect when 'GRANT' then 'El rol ya incluye ' else 'El rol no incluye ' end
             || l_new(i).permission_id || '.');
      end if;
      if l_new(i).reason is null then
        fail(usb_auth.e_bad_request, 'missing_reason', 'Escribe el motivo de cada permiso individual.');
      end if;
      if l_expires is not null then
        begin
          -- Vale hasta el final de ese día, hora de Colombia.
          l_new(i).expires_at := from_tz(cast(to_date(l_expires, 'YYYY-MM-DD') + 1 as timestamp), c_timezone);
        exception
          when others then
            fail(usb_auth.e_bad_request, 'invalid_date', 'La fecha de vencimiento no es válida (AAAA-MM-DD).');
        end;
        if l_new(i).expires_at <= systimestamp then
          fail(usb_auth.e_bad_request, 'invalid_date', 'La fecha de vencimiento ya pasó.');
        end if;
      end if;
      if l_new(i).permission_id = c_manager_permission then
        l_manager := l_new(i).effect = 'GRANT';
      end if;
    end loop;

    if usb_auth.user_has_permission(p_user_id, c_manager_permission) and not l_manager
       and other_managers(p_user_id) = 0 then
      fail(usb_auth.e_conflict, 'last_manager', 'Debe quedar al menos un super administrador activo.');
    end if;

    -- Validado todo: se escribe. Los permisos se evalúan en cada petición, así que
    -- aplican de inmediato sin cerrar sus sesiones.
    delete from usb_user_permissions where user_id = p_user_id;
    for i in 0 .. l_list.get_size - 1 loop
      insert into usb_user_permissions (user_id, permission_id, effect, reason, expires_at, granted_by)
      values (p_user_id, l_new(i).permission_id, l_new(i).effect, l_new(i).reason, l_new(i).expires_at, l_actor_id);
      l_detail := substr(l_detail || case l_new(i).effect when 'GRANT' then '+' else '-' end
                         || l_new(i).permission_id
                         || case when l_new(i).expires_at is not null
                                 then '@' || to_char(l_new(i).expires_at - interval '1' second, 'YYYY-MM-DD') end
                         || ' ', 1, 900);
    end loop;
    usb_auth.log_event('ADMIN_USER_PERMISSIONS', p_user_id, nvl(l_detail, 'sin excepciones ') || 'por=' || actor);
  end set_user_permissions;

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
      -- Quedan: los de otros roles con roles.manage efectivo y los de este rol con un GRANT propio.
      select count(*) into l_managers
        from usb_users u join usb_effective_permissions ep on ep.user_id = u.user_id
       where ep.permission_id = c_manager_permission and u.status = 'ACTIVE'
         and (u.role != p_role_id
              or exists (select 1 from usb_user_permissions up
                          where up.user_id = u.user_id and up.permission_id = c_manager_permission
                            and up.effect = 'GRANT'
                            and (up.expires_at is null or up.expires_at > systimestamp)));
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
