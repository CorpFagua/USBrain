-- =============================================================================
-- USBrain · 07_usb_domain.sql
-- Lógica clínica: lectura del estado completo de la app (bootstrap) y todas las
-- acciones de las pantallas, con control de acceso por rol:
--   · PATIENT   ve su propio caso y es el único que registra sus actividades.
--   · THERAPIST ve y edita solo los pacientes que tiene asignados.
--   · ADMIN     ve todos los casos (solo lectura).
-- Requiere usb_auth.authenticate previo (lo hace usb_app_api).
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

create or replace package usb_domain authid definer as

  -- Zona horaria de la sede (Bogotá) para decidir qué es "hoy"; va como literal en el SQL.

  function to_id(p_value varchar2) return number;
  function to_iso_date(p_value varchar2) return date;

  -- Todo lo que la app necesita para el usuario actual, en un solo JSON.
  function bootstrap return clob;

  function create_behavior(p_patient_id number, p_name varchar2, p_definition varchar2,
                           p_type varchar2, p_function_id varchar2,
                           p_replacement_id number) return number;
  procedure configure_baseline(p_behavior_id number, p_dimensions json_array_t);
  procedure close_baseline(p_behavior_id number);
  function create_task(p_request json_object_t) return number;
  function record_entry(p_task_id number, p_date date, p_value number, p_note varchar2,
                        p_observation_minutes number) return number;
  function record_widget_entry(p_task_id number, p_request json_object_t) return number;
  function create_widget(p_request json_object_t) return varchar2;
  function create_function(p_label varchar2) return varchar2;
  procedure assign_diagnosis(p_patient_id number, p_code varchar2, p_label varchar2);
  procedure remove_diagnosis(p_patient_id number, p_code varchar2);

  -- Administración (SQL / APEX): da de alta un consultante con su caso.
  function create_patient(p_email varchar2, p_full_name varchar2, p_password varchar2,
                          p_therapist_email varchar2, p_birth_date date, p_program varchar2,
                          p_condition varchar2, p_color_argb number default null) return number;
  procedure assign_therapist(p_patient_id number, p_therapist_email varchar2);

end usb_domain;
/

create or replace package body usb_domain as

  -- ---------------------------------------------------------------------------
  -- Utilidades
  -- ---------------------------------------------------------------------------

  procedure fail(p_code pls_integer, p_slug varchar2, p_message varchar2) is
  begin
    raise_application_error(p_code, p_slug || '|' || p_message);
  end fail;

  function today return date is
  begin
    return trunc(cast(systimestamp at time zone 'America/Bogota' as date));
  end today;

  function format_date(p_date date) return varchar2 is
  begin
    return to_char(p_date, 'FMDD') || '/' || to_char(p_date, 'FMMM') || '/' || to_char(p_date, 'YYYY');
  end format_date;

  -- Los ids llegan como texto en el JSON; uno mal formado equivale a inexistente.
  function to_id(p_value varchar2) return number is
  begin
    return to_number(p_value);
  exception
    when value_error or invalid_number then
      return null;
  end to_id;

  function to_iso_date(p_value varchar2) return date is
  begin
    if p_value is null or not regexp_like(p_value, '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') then
      return null;
    end if;
    return to_date(p_value, 'YYYY-MM-DD');
  exception
    when others then
      return null;
  end to_iso_date;

  function require_text(p_value varchar2, p_message varchar2) return varchar2 is
  begin
    if trim(p_value) is null then
      fail(usb_auth.e_bad_request, 'missing_fields', p_message);
    end if;
    return trim(p_value);
  end require_text;

  -- Permisos: el código revisa PERMISOS (usb_role_permissions), nunca nombres de rol,
  -- así un rol nuevo creado por el super administrador funciona sin tocar código.
  function flag(p_permission varchar2) return number is
  begin
    return case when usb_auth.has_permission(p_permission) then 1 else 0 end;
  end flag;

  procedure require_catalog_manager is
  begin
    usb_auth.require_permission('catalog.manage');
  end require_catalog_manager;

  -- Editar un caso: con case.edit.all cualquiera; con case.edit.assigned solo los asignados.
  -- Un paciente ajeno responde igual que uno inexistente para no revelar qué pacientes existen.
  procedure require_case_editor(p_patient_id number) is
    l_count number;
  begin
    if usb_auth.has_permission('case.edit.all') then
      select count(*) into l_count from usb_patient_profiles where user_id = p_patient_id;
    elsif usb_auth.has_permission('case.edit.assigned') then
      select count(*) into l_count from usb_patient_profiles
       where user_id = p_patient_id and therapist_id = usb_auth.current_user_id;
    else
      fail(usb_auth.e_forbidden, 'forbidden', 'No tienes permiso para editar casos.');
    end if;
    if l_count = 0 then
      fail(usb_auth.e_not_found, 'not_found', 'El paciente no existe o no está asignado a ti.');
    end if;
  end require_case_editor;

  function patient_of_behavior(p_behavior_id number) return number is
    l_patient_id number;
  begin
    select c.patient_id into l_patient_id
      from usb_behaviors b join usb_cases c on c.case_id = b.case_id
     where b.behavior_id = p_behavior_id;
    return l_patient_id;
  exception
    when no_data_found then
      fail(usb_auth.e_not_found, 'not_found', 'La conducta no existe.');
  end patient_of_behavior;

  function load_task(p_task_id number) return usb_tasks%rowtype is
    l_task usb_tasks%rowtype;
  begin
    select * into l_task from usb_tasks where task_id = p_task_id;
    return l_task;
  exception
    when no_data_found then
      fail(usb_auth.e_not_found, 'not_found', 'La actividad no existe.');
  end load_task;

  -- Solo el consultante dueño del caso registra sus propias actividades.
  procedure require_patient_owner(p_task usb_tasks%rowtype) is
  begin
    if not usb_auth.has_permission('entry.record') then
      fail(usb_auth.e_forbidden, 'forbidden',
           'Solo el consultante puede registrar sus actividades. La vista previa es de solo lectura.');
    end if;
    if patient_of_behavior(p_task.behavior_id) != usb_auth.current_user_id then
      fail(usb_auth.e_not_found, 'not_found', 'La actividad no existe.');
    end if;
  end require_patient_owner;

  procedure validate_entry_date(p_date date) is
  begin
    if p_date is null then
      fail(usb_auth.e_bad_request, 'missing_fields', 'Falta la fecha del registro.');
    elsif p_date > today then
      fail(usb_auth.e_bad_request, 'invalid_date', 'No puedes registrar una fecha futura.');
    end if;
  end validate_entry_date;

  -- ---------------------------------------------------------------------------
  -- Lectura
  -- ---------------------------------------------------------------------------

  function bootstrap return clob is
    l_user_id number := usb_auth.current_user_id;
    -- Qué pacientes ve, según sus permisos (1 = sí).
    l_own      number := flag('case.view.own');
    l_assigned number := flag('case.view.assigned');
    l_all      number := flag('case.view.all');
    l_today   date := today;
    l_result  clob;
  begin
    select json_serialize(
      json_object(
        'today' value to_char(l_today, 'YYYY-MM-DD'),
        'user' value json_object(
          'id' value to_char(u.user_id), 'email' value u.email,
          'full_name' value u.full_name, 'role' value u.role,
          'role_name' value r.name, 'profile' value r.app_profile,
          'permissions' value (select json_arrayagg(rp.permission_id order by rp.permission_id returning json)
                                 from usb_role_permissions rp where rp.role_id = u.role)),
        'patients' value (
          select json_arrayagg(
            json_object(
              'id' value to_char(p.user_id),
              'name' value pu.full_name,
              'email' value pu.email,
              'age' value nvl(trunc(months_between(l_today, p.birth_date) / 12), 0),
              'program' value nvl(p.program, ''),
              'color' value p.color_argb,
              'condition' value nvl(p.condition, ''),
              'therapist_name' value (select t.full_name from usb_users t where t.user_id = p.therapist_id),
              'diagnoses' value (
                select json_arrayagg(json_object('code' value d.code, 'label' value d.label)
                                     order by d.assigned_at returning json)
                  from usb_patient_diagnoses d where d.patient_id = p.user_id),
              'case' value (
                select json_object(
                  'id' value to_char(c.case_id),
                  'title' value c.title,
                  'behaviors' value (
                    select json_arrayagg(
                      json_object(
                        'id' value to_char(b.behavior_id),
                        'case_id' value to_char(b.case_id),
                        'name' value b.name,
                        'operational_definition' value b.operational_definition,
                        'type' value b.behavior_type,
                        'baseline_open' value b.baseline_open,
                        'baseline_closed_on' value
                          to_char(b.baseline_closed_at at time zone 'America/Bogota', 'FMDD/FMMM/YYYY'),
                        'function_id' value b.function_id,
                        'replacement_behavior_id' value to_char(b.replacement_behavior_id),
                        'baseline_dimensions' value
                          json('["' || replace(b.baseline_dimensions, ',', '","') || '"]'),
                        'baseline_entries' value (
                          select json_arrayagg(
                            json_object(
                              'id' value 'r' || e.entry_id,
                              'task_id' value to_char(e.task_id),
                              'date' value to_char(e.entry_date, 'YYYY-MM-DD'),
                              'value' value e.value,
                              'note' value nvl(e.note, ''),
                              'dimension' value bt.dimension,
                              'observation_minutes' value e.observation_minutes)
                            order by e.entry_date returning json)
                            from usb_task_entries e join usb_tasks bt on bt.task_id = e.task_id
                           where bt.behavior_id = b.behavior_id and e.is_baseline),
                        'tasks' value (
                          select json_arrayagg(
                            json_object(
                              'id' value to_char(t.task_id),
                              'behavior_id' value to_char(t.behavior_id),
                              'title' value t.title,
                              'instructions' value nvl(t.instructions, ''),
                              'task_type' value t.task_type,
                              'dimension' value t.dimension,
                              'metric_unit' value t.metric_unit,
                              'widget_id' value t.widget_id,
                              'therapeutic_goal' value t.therapeutic_goal,
                              'assigned_date' value t.assigned_label,
                              'due_date' value t.due_label,
                              'is_recurring' value t.is_recurring,
                              'frequency' value t.frequency,
                              'reminder_active' value t.reminder_active,
                              'phase' value t.phase,
                              -- Recurrente: pendiente hasta que se registre hoy. Única: pendiente hasta el primer registro.
                              'status' value case when exists (
                                  select 1 from usb_task_entries x
                                   where x.task_id = t.task_id
                                     and (not t.is_recurring or x.entry_date = l_today)
                                  union all
                                  select 1 from usb_widget_entries x
                                   where x.task_id = t.task_id
                                     and (not t.is_recurring or x.entry_date = l_today))
                                then 'COMPLETADA' else 'PENDIENTE' end,
                              'entries' value (
                                select json_arrayagg(
                                  json_object(
                                    'date' value to_char(e.entry_date, 'YYYY-MM-DD'),
                                    'value' value e.value,
                                    'note' value nvl(e.note, ''),
                                    'phase' value e.phase,
                                    'observation_minutes' value e.observation_minutes)
                                  order by e.entry_date returning json)
                                  from usb_task_entries e where e.task_id = t.task_id),
                              'widget_entries' value (
                                select json_arrayagg(
                                  json_object(
                                    'id' value 'we' || w.entry_id,
                                    'widget_id' value w.widget_id,
                                    'date' value to_char(w.entry_date, 'YYYY-MM-DD'),
                                    'time' value w.entry_time,
                                    'numeric_value' value w.numeric_value,
                                    'observation_minutes' value w.observation_minutes,
                                    'bool_value' value w.bool_value,
                                    'text_value' value w.text_value,
                                    'selected_options' value w.selected_options,
                                    'note' value w.note)
                                  order by w.entry_date returning json)
                                  from usb_widget_entries w where w.task_id = t.task_id)
                              returning json)
                            order by t.created_at, t.task_id returning json)
                            from usb_tasks t where t.behavior_id = b.behavior_id)
                        returning json)
                      order by b.created_at, b.behavior_id returning json)
                      from usb_behaviors b where b.case_id = c.case_id)
                  returning json)
                  from usb_cases c where c.patient_id = p.user_id)
              returning json)
            order by pu.full_name returning json)
            from usb_patient_profiles p
            join usb_users pu on pu.user_id = p.user_id
           where pu.status = 'ACTIVE'
             and (   (l_own = 1 and p.user_id = l_user_id)
                  or (l_assigned = 1 and p.therapist_id = l_user_id)
                  or l_all = 1)),
        'catalogs' value json_object(
          'widgets' value (
            select json_arrayagg(
              json_object(
                'id' value w.widget_id, 'name' value w.name, 'value_kind' value w.value_kind,
                'unit' value w.unit, 'min_value' value w.min_value, 'max_value' value w.max_value,
                'options' value w.options, 'description' value w.description,
                'is_preset' value w.is_preset)
              order by case when w.is_preset then 0 else 1 end, w.created_at, w.widget_id returning json)
              from usb_widgets w),
          'functions' value (
            select json_arrayagg(json_object('id' value f.function_id, 'label' value f.label)
                                 order by case when f.is_seed then 0 else 1 end, f.created_at, f.function_id
                                 returning json)
              from usb_behavioral_functions f),
          'cie10' value (
            select json_arrayagg(json_object('code' value c.code, 'label' value c.label)
                                 order by c.code returning json)
              from usb_cie10 c)
          returning json)
        returning json)
      returning clob)
      into l_result
      from usb_users u
      join usb_roles r on r.role_id = u.role
     where u.user_id = l_user_id;
    return l_result;
  end bootstrap;

  -- ---------------------------------------------------------------------------
  -- Conductas y línea base
  -- ---------------------------------------------------------------------------

  function create_behavior(p_patient_id number, p_name varchar2, p_definition varchar2,
                           p_type varchar2, p_function_id varchar2,
                           p_replacement_id number) return number is
    l_name        varchar2(300);
    l_definition  varchar2(2000);
    l_case_id     number;
    l_function_id varchar2(40) := 'fn-no-aplica';
    l_replacement number;
    l_count       number;
    l_behavior_id number;
  begin
    require_case_editor(p_patient_id);
    l_name := require_text(p_name, 'Completa el nombre y la definición operacional de la conducta.');
    l_definition := require_text(p_definition, 'Completa el nombre y la definición operacional de la conducta.');
    if p_type not in ('ADAPTATIVA', 'DESADAPTATIVA') or p_type is null then
      fail(usb_auth.e_bad_request, 'invalid_type', 'Indica si la conducta es adaptativa o desadaptativa.');
    end if;

    begin
      select case_id into l_case_id from usb_cases where patient_id = p_patient_id;
    exception
      when no_data_found then
        select 'Caso de ' || nvl(lower(condition), full_name) into l_name
          from usb_patient_profiles join usb_users using (user_id)
         where user_id = p_patient_id;
        insert into usb_cases (patient_id, title) values (p_patient_id, l_name)
        returning case_id into l_case_id;
        l_name := trim(p_name);
    end;

    -- Función conductual y conducta de reemplazo solo aplican a conductas desadaptativas.
    if p_type = 'DESADAPTATIVA' then
      if p_function_id is not null then
        select count(*) into l_count from usb_behavioral_functions where function_id = p_function_id;
        if l_count = 0 then
          fail(usb_auth.e_bad_request, 'invalid_function', 'La función conductual no existe.');
        end if;
        l_function_id := p_function_id;
      end if;
      if p_replacement_id is not null then
        select count(*) into l_count from usb_behaviors
         where behavior_id = p_replacement_id and case_id = l_case_id and behavior_type = 'ADAPTATIVA';
        if l_count = 0 then
          fail(usb_auth.e_bad_request, 'invalid_replacement',
               'La conducta de reemplazo debe ser una conducta adaptativa del mismo caso.');
        end if;
        l_replacement := p_replacement_id;
      end if;
    end if;

    insert into usb_behaviors (case_id, name, operational_definition, behavior_type, function_id,
                               replacement_behavior_id, created_by)
    values (l_case_id, l_name, l_definition, p_type, l_function_id, l_replacement, usb_auth.current_user_id)
    returning behavior_id into l_behavior_id;
    return l_behavior_id;
  end create_behavior;

  procedure configure_baseline(p_behavior_id number, p_dimensions json_array_t) is
    l_dimensions varchar2(100);
    l_dimension  varchar2(20);
  begin
    require_case_editor(patient_of_behavior(p_behavior_id));
    if p_dimensions is not null then
      for i in 0 .. p_dimensions.get_size - 1 loop
        l_dimension := p_dimensions.get_string(i);
        if l_dimension not in ('FRECUENCIA', 'DURACION', 'INTENSIDAD') or l_dimension is null then
          fail(usb_auth.e_bad_request, 'invalid_dimension', 'Dimensión de medición no válida.');
        end if;
        if instr(',' || l_dimensions || ',', ',' || l_dimension || ',') = 0 then
          l_dimensions := l_dimensions || case when l_dimensions is not null then ',' end || l_dimension;
        end if;
      end loop;
    end if;
    l_dimensions := nvl(l_dimensions, 'FRECUENCIA');

    update usb_behaviors set baseline_dimensions = l_dimensions where behavior_id = p_behavior_id;

    -- Una actividad diaria de registro autónomo por cada dimensión de la línea base.
    for d in (select regexp_substr(l_dimensions, '[^,]+', 1, level) dimension
                from dual connect by level <= regexp_count(l_dimensions, ',') + 1) loop
      insert into usb_tasks (behavior_id, title, instructions, task_type, dimension, is_recurring,
                             frequency, phase, created_by)
      values (p_behavior_id,
              case d.dimension
                when 'FRECUENCIA' then 'Registrar frecuencia diaria'
                when 'DURACION'   then 'Registrar duración diaria'
                else                   'Registrar intensidad diaria' end,
              case d.dimension
                when 'FRECUENCIA' then 'Cuenta cuántas veces ocurrió la conducta en el día.'
                when 'DURACION'   then 'Anota cuántos minutos duró la conducta en el día.'
                else                   'Valora la intensidad de la conducta en una escala del 0 al 10.' end,
              'REGISTRO_AUTONOMO', d.dimension, true, 'Diaria', 'LINEA_BASE', usb_auth.current_user_id);
    end loop;
  end configure_baseline;

  procedure close_baseline(p_behavior_id number) is
  begin
    require_case_editor(patient_of_behavior(p_behavior_id));
    update usb_behaviors
       set baseline_open = false, baseline_closed_at = systimestamp
     where behavior_id = p_behavior_id and baseline_open;
  end close_baseline;

  -- ---------------------------------------------------------------------------
  -- Actividades
  -- ---------------------------------------------------------------------------

  function create_task(p_request json_object_t) return number is
    l_behavior_id number := to_id(p_request.get_string('behavior_id'));
    l_title       varchar2(300);
    l_widget_id   varchar2(40) := p_request.get_string('widget_id');
    l_task_type   varchar2(30) := nvl(p_request.get_string('task_type'), 'REGISTRO_AUTONOMO');
    l_dimension   varchar2(20) := nvl(p_request.get_string('dimension'), 'FRECUENCIA');
    l_metric      varchar2(30) := p_request.get_string('metric_unit');
    l_phase       varchar2(20) := p_request.get_string('phase');
    l_recurring   boolean := nvl(p_request.get_boolean('is_recurring'), false);
    l_reminder    boolean := nvl(p_request.get_boolean('reminder_active'), false);
    l_instruct    varchar2(2000) := trim(p_request.get_string('instructions'));
    l_goal        varchar2(1000) := trim(p_request.get_string('therapeutic_goal'));
    l_assigned    varchar2(60) := nvl(trim(p_request.get_string('assigned_date')), 'Hoy');
    l_due         varchar2(60) := trim(p_request.get_string('due_date'));
    l_frequency   varchar2(60) := p_request.get_string('frequency');
    l_count       number;
    l_task_id     number;
  begin
    if l_behavior_id is null then
      fail(usb_auth.e_bad_request, 'missing_fields', 'Falta la conducta de la actividad.');
    end if;
    require_case_editor(patient_of_behavior(l_behavior_id));
    l_title := require_text(p_request.get_string('title'), 'Completa el nombre de la actividad.');
    if l_phase not in ('LINEA_BASE', 'INTERVENCION') or l_phase is null then
      fail(usb_auth.e_bad_request, 'invalid_phase', 'Fase no válida.');
    end if;

    if l_widget_id is not null then
      select count(*) into l_count from usb_widgets where widget_id = l_widget_id;
      if l_count = 0 then
        fail(usb_auth.e_bad_request, 'invalid_widget', 'Selecciona un método de medición para la actividad.');
      end if;
      l_task_type := 'MEDICION_CONFIGURABLE';
      l_metric := null;
    elsif l_task_type = 'MEDICION_CONFIGURABLE' then
      fail(usb_auth.e_bad_request, 'invalid_widget', 'Selecciona un método de medición para la actividad.');
    elsif l_task_type = 'REGISTRO_AUTONOMO' then
      l_metric := null;
    end if;

    insert into usb_tasks (behavior_id, title, instructions, task_type, dimension, metric_unit, widget_id,
                           therapeutic_goal, assigned_label, due_label, is_recurring, frequency,
                           reminder_active, phase, created_by)
    values (l_behavior_id, l_title, l_instruct, l_task_type, l_dimension, l_metric, l_widget_id, l_goal,
            l_assigned, l_due, l_recurring, case when l_recurring then l_frequency end,
            l_reminder, l_phase, usb_auth.current_user_id)
    returning task_id into l_task_id;

    -- La fase de la conducta avanza sola: una actividad de intervención cierra la línea base.
    if l_phase = 'INTERVENCION' then
      close_baseline(l_behavior_id);
    end if;
    return l_task_id;
  end create_task;

  -- ---------------------------------------------------------------------------
  -- Registros del consultante
  -- ---------------------------------------------------------------------------

  function record_entry(p_task_id number, p_date date, p_value number, p_note varchar2,
                        p_observation_minutes number) return number is
    l_task          usb_tasks%rowtype := load_task(p_task_id);
    l_baseline_open boolean;
    l_count         number;
    l_entry_id      number;
  begin
    require_patient_owner(l_task);
    if l_task.widget_id is not null then
      fail(usb_auth.e_bad_request, 'invalid_task', 'Esta actividad se registra con su medición configurable.');
    end if;
    validate_entry_date(p_date);
    if p_value is null or p_value < 0 then
      fail(usb_auth.e_bad_request, 'invalid_value', 'Ingresa un valor válido.');
    elsif l_task.task_type = 'REGISTRO_AUTONOMO' and l_task.dimension = 'INTENSIDAD' and p_value > 10 then
      fail(usb_auth.e_bad_request, 'invalid_value', 'La intensidad va de 0 a 10.');
    end if;

    -- Igual que en la app: una sola observación por día, fase y dimensión de la conducta.
    if l_task.task_type = 'REGISTRO_AUTONOMO' then
      select count(*) into l_count
        from usb_task_entries e join usb_tasks t on t.task_id = e.task_id
       where t.behavior_id = l_task.behavior_id and t.task_type = 'REGISTRO_AUTONOMO'
         and t.widget_id is null and t.dimension = l_task.dimension
         and e.phase = l_task.phase and e.entry_date = p_date;
      if l_count > 0 then
        fail(usb_auth.e_conflict, 'duplicate_entry',
             'Ya existe un registro de esta tarea para el ' || format_date(p_date) || '.');
      end if;
    end if;

    select baseline_open into l_baseline_open from usb_behaviors where behavior_id = l_task.behavior_id;

    insert into usb_task_entries (task_id, entry_date, value, note, phase, observation_minutes,
                                  is_baseline, recorded_by)
    values (p_task_id, p_date, p_value, trim(p_note), l_task.phase,
            case when l_task.task_type = 'REGISTRO_AUTONOMO' and l_task.dimension = 'FRECUENCIA'
                      and p_observation_minutes > 0 then p_observation_minutes end,
            l_task.task_type = 'REGISTRO_AUTONOMO' and l_task.phase = 'LINEA_BASE' and l_baseline_open,
            usb_auth.current_user_id)
    returning entry_id into l_entry_id;
    return l_entry_id;
  exception
    when dup_val_on_index then
      fail(usb_auth.e_conflict, 'duplicate_entry',
           'Ya existe un registro de esta tarea para el ' || format_date(p_date) || '.');
  end record_entry;

  function record_widget_entry(p_task_id number, p_request json_object_t) return number is
    l_task     usb_tasks%rowtype := load_task(p_task_id);
    l_widget   usb_widgets%rowtype;
    l_date     date;
    l_time     varchar2(5) := nvl(p_request.get_string('time'), '09:00');
    l_value    json_object_t := p_request.get_object('value');
    l_number   number;
    l_bool     boolean;
    l_text     varchar2(2000);
    l_options  json_array_t;
    l_allowed  json_array_t;
    l_found    boolean;
    l_minutes  number;
    l_json     clob;
    l_note     varchar2(2000) := trim(p_request.get_string('note'));
    l_entry_id number;
  begin
    require_patient_owner(l_task);
    if l_task.widget_id is null then
      fail(usb_auth.e_bad_request, 'invalid_task', 'Esta actividad no usa una medición configurable.');
    end if;
    select * into l_widget from usb_widgets where widget_id = l_task.widget_id;

    l_date := to_iso_date(p_request.get_string('date'));
    validate_entry_date(l_date);
    if l_value is null then
      fail(usb_auth.e_bad_request, 'missing_fields', 'Falta el valor del registro.');
    end if;

    case l_widget.value_kind
      when 'NUMERIC' then
        l_number := l_value.get_number('value');
        if l_number is null then
          fail(usb_auth.e_bad_request, 'invalid_value', 'Ingresa un valor numérico.');
        end if;
        if l_number < l_widget.min_value or l_number > l_widget.max_value then
          fail(usb_auth.e_bad_request, 'invalid_value',
               'El valor debe estar entre ' || nvl(to_char(l_widget.min_value), 'el mínimo')
               || ' y ' || nvl(to_char(l_widget.max_value), 'el máximo') || '.');
        end if;
      when 'YES_NO' then
        l_bool := l_value.get_boolean('value');
        if l_bool is null then
          fail(usb_auth.e_bad_request, 'invalid_value', 'Indica sí o no.');
        end if;
      when 'TEXT' then
        l_text := trim(l_value.get_string('value'));
      when 'MULTI_SELECT' then
        l_options := l_value.get_array('values');
        if l_options is null or l_options.get_size = 0 then
          fail(usb_auth.e_bad_request, 'invalid_value', 'Selecciona al menos una opción antes de guardar.');
        end if;
        l_allowed := json_array_t(json_serialize(l_widget.options));
        for i in 0 .. l_options.get_size - 1 loop
          l_found := false;
          for j in 0 .. l_allowed.get_size - 1 loop
            if l_allowed.get_string(j) = l_options.get_string(i) then
              l_found := true;
            end if;
          end loop;
          if not l_found then
            fail(usb_auth.e_bad_request, 'invalid_value', 'Opción no válida: ' || l_options.get_string(i));
          end if;
        end loop;
    end case;

    l_minutes := l_value.get_number('observation_minutes');
    if l_options is not null then
      l_json := l_options.to_clob;
    end if;
    insert into usb_widget_entries (task_id, widget_id, entry_date, entry_time, numeric_value,
                                    observation_minutes, bool_value, text_value, selected_options,
                                    note, recorded_by)
    values (p_task_id, l_widget.widget_id, l_date, l_time, l_number,
            case when l_minutes > 0 then l_minutes end,
            l_bool, l_text, case when l_json is not null then json(l_json) end,
            l_note, usb_auth.current_user_id)
    returning entry_id into l_entry_id;
    return l_entry_id;
  exception
    when dup_val_on_index then
      fail(usb_auth.e_conflict, 'duplicate_entry',
           'Ya existe un registro de esta actividad para el ' || format_date(l_date) || '.');
    when others then
      if sqlcode = -2290 then  -- check constraint: hora inválida
        fail(usb_auth.e_bad_request, 'invalid_value', 'La hora del registro no es válida.');
      end if;
      raise;
  end record_widget_entry;

  -- ---------------------------------------------------------------------------
  -- Catálogos que amplía el terapeuta
  -- ---------------------------------------------------------------------------

  function create_widget(p_request json_object_t) return varchar2 is
    l_name    varchar2(200);
    l_kind    varchar2(20) := p_request.get_string('value_kind');
    l_min     number := p_request.get_number('min_value');
    l_max     number := p_request.get_number('max_value');
    l_options json_array_t := p_request.get_array('options');
    l_unit    varchar2(40) := trim(p_request.get_string('unit'));
    l_descr   varchar2(1000) := trim(p_request.get_string('description'));
    l_json    clob;
    l_id      varchar2(40);
  begin
    require_catalog_manager;
    l_name := require_text(p_request.get_string('name'), 'Completa el nombre de la variable a medir.');
    if l_kind not in ('NUMERIC', 'YES_NO', 'TEXT', 'MULTI_SELECT') or l_kind is null then
      fail(usb_auth.e_bad_request, 'invalid_kind', 'Tipo de respuesta no válido.');
    end if;
    if l_kind = 'MULTI_SELECT' and (l_options is null or l_options.get_size = 0) then
      fail(usb_auth.e_bad_request, 'missing_fields', 'Agrega al menos una opción, separadas por coma.');
    end if;
    if l_min > l_max then
      fail(usb_auth.e_bad_request, 'invalid_range', 'El valor mínimo no puede ser mayor que el máximo.');
    end if;

    if l_kind = 'MULTI_SELECT' then
      l_json := l_options.to_clob;
    end if;
    l_id := 'cw' || usb_catalog_seq.nextval;
    insert into usb_widgets (widget_id, name, value_kind, unit, min_value, max_value, options,
                             description, is_preset, created_by)
    values (l_id, l_name, l_kind,
            case when l_kind = 'NUMERIC' then l_unit end,
            case when l_kind = 'NUMERIC' then l_min end,
            case when l_kind = 'NUMERIC' then l_max end,
            case when l_json is not null then json(l_json) end,
            l_descr, false, usb_auth.current_user_id);
    return l_id;
  end create_widget;

  function create_function(p_label varchar2) return varchar2 is
    l_label varchar2(200);
    l_id    varchar2(40);
  begin
    require_catalog_manager;
    l_label := require_text(p_label, 'Escribe el nombre de la función conductual.');
    -- Si ya existe con el mismo nombre, se reutiliza en vez de duplicarla.
    select max(function_id) into l_id from usb_behavioral_functions where lower(label) = lower(l_label);
    if l_id is null then
      l_id := 'fn' || usb_catalog_seq.nextval;
      insert into usb_behavioral_functions (function_id, label, created_by)
      values (l_id, l_label, usb_auth.current_user_id);
    end if;
    return l_id;
  end create_function;

  procedure assign_diagnosis(p_patient_id number, p_code varchar2, p_label varchar2) is
    l_code varchar2(10);
  begin
    require_case_editor(p_patient_id);
    l_code := upper(require_text(p_code, 'Escribe el código CIE-10.'));
    if not regexp_like(l_code, '^[A-Z][0-9]{2}(\.[0-9A-Z]{1,4})?$') then
      fail(usb_auth.e_bad_request, 'invalid_code', 'El código CIE-10 no es válido (ej.: F41.1).');
    end if;
    merge into usb_patient_diagnoses d
    using (select p_patient_id patient_id, l_code code from dual) s
       on (d.patient_id = s.patient_id and d.code = s.code)
     when not matched then
       insert (patient_id, code, label, assigned_by)
       values (s.patient_id, s.code, nvl(trim(p_label), l_code), usb_auth.current_user_id);
  exception
    when value_error then
      fail(usb_auth.e_bad_request, 'invalid_code', 'El código CIE-10 no es válido (ej.: F41.1).');
  end assign_diagnosis;

  procedure remove_diagnosis(p_patient_id number, p_code varchar2) is
  begin
    require_case_editor(p_patient_id);
    delete from usb_patient_diagnoses where patient_id = p_patient_id and code = upper(p_code);
  end remove_diagnosis;

  -- ---------------------------------------------------------------------------
  -- Administración
  -- ---------------------------------------------------------------------------

  function therapist_id(p_email varchar2) return number is
    l_id number;
  begin
    -- Terapeuta = cualquier usuario cuyo rol puede ver pacientes asignados.
    select u.user_id into l_id from usb_users u join usb_roles r on r.role_id = u.role
     where u.email = lower(trim(p_email)) and r.app_profile = 'THERAPIST'
       and exists (select 1 from usb_role_permissions rp
                    where rp.role_id = u.role and rp.permission_id = 'case.view.assigned');
    return l_id;
  exception
    when no_data_found then
      fail(usb_auth.e_bad_request, 'invalid_therapist', 'No existe un terapeuta con el correo ' || p_email || '.');
  end therapist_id;

  function create_patient(p_email varchar2, p_full_name varchar2, p_password varchar2,
                          p_therapist_email varchar2, p_birth_date date, p_program varchar2,
                          p_condition varchar2, p_color_argb number default null) return number is
    l_user_id      number;
    l_therapist_id number := therapist_id(p_therapist_email);
  begin
    l_user_id := usb_auth.create_user(p_email, p_full_name, 'PATIENT', p_password);
    insert into usb_patient_profiles (user_id, therapist_id, birth_date, program, condition, color_argb)
    values (l_user_id, l_therapist_id, p_birth_date, p_program, p_condition,
            nvl(p_color_argb, 4286277314));
    insert into usb_cases (patient_id, title)
    values (l_user_id, 'Caso de ' || nvl(lower(p_condition), p_full_name));
    return l_user_id;
  end create_patient;

  procedure assign_therapist(p_patient_id number, p_therapist_email varchar2) is
    l_therapist_id number := therapist_id(p_therapist_email);
  begin
    update usb_patient_profiles set therapist_id = l_therapist_id
     where user_id = p_patient_id;
  end assign_therapist;

end usb_domain;
/

show errors package body usb_domain
begin
  for e in (select 1 from user_errors where name = 'USB_DOMAIN' and rownum = 1) loop
    raise_application_error(-20000, 'USB_DOMAIN no compiló; revisa los errores de arriba.');
  end loop;
end;
/

prompt Paquete USB_DOMAIN listo.
exit
