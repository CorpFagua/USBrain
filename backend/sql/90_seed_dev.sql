-- =============================================================================
-- USBrain · 90_seed_dev.sql  (SOLO DESARROLLO)
-- Crea o actualiza los usuarios de prueba del prototipo con la contraseña
-- DEV_SEED_PASSWORD de backend/.env. Ninguno tiene autenticador todavía:
-- el primer login pedirá enrolar la app (escanear el QR).
-- =============================================================================
whenever sqlerror exit failure rollback
set define on verify off feedback off

declare
  procedure upsert(p_email varchar2, p_full_name varchar2, p_role varchar2) is
    l_user_id number;
  begin
    -- La semilla siempre restaura las credenciales de demostración para que el
    -- entorno sea reproducible. También reinicia el 2FA para el primer ingreso.
    select user_id into l_user_id from usb_users where email = p_email;
    usb_auth.set_password(l_user_id, '&dev_pwd');
    usb_auth.reset_mfa(l_user_id);
  exception
    when no_data_found then
      l_user_id := usb_auth.create_user(p_email, p_full_name, p_role, '&dev_pwd');
  end upsert;
begin
  upsert('e.patino@usb.edu.co', 'José E. Patiño', 'THERAPIST');
  upsert('ana.torres@usbbog.edu.co', 'Ana Torres', 'PATIENT');
  upsert('daniel.ruiz@usbbog.edu.co', 'Daniel Ruiz', 'PATIENT');
  upsert('admin@usb.edu.co', 'Administrador USBrain', 'ADMIN');
  upsert('superadmin@usb.edu.co', 'Super Administrador USBrain', 'SUPERADMIN');
  commit;
end;
/

-- -----------------------------------------------------------------------------
-- Casos de demostración (los mismos del prototipo). Solo se cargan si el caso
-- del paciente todavía no tiene conductas: no pisa lo que se haya registrado.
-- -----------------------------------------------------------------------------
declare
  l_therapist number;
  l_ana       number;
  l_daniel    number;
  l_case      number;
  l_count     number;
  l_b         number;
  l_t         number;
  l_t_base    number;
  l_t_treatment number;

  function user_id(p_email varchar2) return number is
    l_id number;
  begin
    select user_id into l_id from usb_users where email = p_email;
    return l_id;
  end user_id;

  function ensure_patient(p_user_id number, p_birth date, p_program varchar2,
                          p_condition varchar2, p_color number, p_case_title varchar2) return number is
    l_case_id number;
  begin
    merge into usb_patient_profiles p
    using (select p_user_id user_id from dual) s on (p.user_id = s.user_id)
     when not matched then
       insert (user_id, therapist_id, birth_date, program, condition, color_argb)
       values (p_user_id, l_therapist, p_birth, p_program, p_condition, p_color);
    begin
      select case_id into l_case_id from usb_cases where patient_id = p_user_id;
    exception
      when no_data_found then
        insert into usb_cases (patient_id, title) values (p_user_id, p_case_title)
        returning case_id into l_case_id;
    end;
    return l_case_id;
  end ensure_patient;

  function behavior(p_case number, p_name varchar2, p_definition varchar2, p_type varchar2,
                    p_open boolean, p_closed date default null) return number is
    l_id number;
  begin
    insert into usb_behaviors (case_id, name, operational_definition, behavior_type, baseline_open,
                               baseline_closed_at, created_by)
    values (p_case, p_name, p_definition, p_type, p_open,
            case when p_closed is not null then from_tz(cast(p_closed as timestamp), 'America/Bogota') end,
            l_therapist)
    returning behavior_id into l_id;
    return l_id;
  end behavior;

  function task(p_behavior number, p_title varchar2, p_instructions varchar2, p_type varchar2,
                p_dimension varchar2, p_phase varchar2, p_recurring boolean, p_frequency varchar2,
                p_reminder boolean, p_widget varchar2 default null, p_goal varchar2 default null,
                p_assigned varchar2 default 'Hoy', p_due varchar2 default null) return number is
    l_id number;
  begin
    insert into usb_tasks (behavior_id, title, instructions, task_type, dimension, widget_id,
                           therapeutic_goal, assigned_label, due_label, is_recurring, frequency,
                           reminder_active, phase, created_by)
    values (p_behavior, p_title, p_instructions, p_type, p_dimension, p_widget, p_goal, p_assigned,
            p_due, p_recurring, p_frequency, p_reminder, p_phase, l_therapist)
    returning task_id into l_id;
    return l_id;
  end task;

  procedure entry(p_task number, p_date date, p_value number, p_note varchar2, p_phase varchar2,
                  p_minutes number default null) is
  begin
    insert into usb_task_entries (task_id, entry_date, value, note, phase, observation_minutes,
                                  is_baseline, recorded_by)
    values (p_task, p_date, p_value, p_note, p_phase, p_minutes, p_phase = 'LINEA_BASE', l_ana);
  end entry;

  -- Ruido uniforme 0..1 determinista (un hash entero): datos variados como los reales, pero
  -- idénticos en cada semilla para que los resultados del panel se puedan citar.
  function noise(p_a number, p_b number, p_c number) return number is
    c_m constant number := 2147483647;
    l_h number := mod(p_a * 73856093 + p_b * 19349663 + p_c * 83492791, c_m);
  begin
    l_h := mod(l_h * 48271, c_m);
    l_h := mod(l_h * 48271, c_m);
    return l_h / c_m;
  end noise;

  -- Episodios "en clase": entre semana hay clases, el fin de semana casi no (lunes = 0).
  function weekday_factor(p_date date) return number is
  begin
    return case when mod(p_date - date '2026-08-31', 7) >= 5 then 0.55 else 1.15 end;
  end weekday_factor;

  -- Deja una conducta en B1 con A1 del p_a_start al día anterior a p_b_start: fases, tareas
  -- retiradas y número de fase de cada registro, como si el terapeuta hubiera cambiado de fase.
  procedure set_ab_phases(p_behavior number, p_a_start date, p_b_start date) is
    l_change timestamp with time zone := from_tz(cast(p_b_start as timestamp), 'America/Bogota');
  begin
    update usb_behaviors set baseline_open = false, phase_seq = 2, baseline_closed_at = l_change
     where behavior_id = p_behavior;
    delete from usb_behavior_phases where behavior_id = p_behavior;
    insert into usb_behavior_phases (behavior_id, phase_seq, phase, started_at, ended_at, started_by)
    values (p_behavior, 1, 'LINEA_BASE', from_tz(cast(p_a_start as timestamp), 'America/Bogota'), l_change, l_therapist);
    insert into usb_behavior_phases (behavior_id, phase_seq, phase, started_at, started_by)
    values (p_behavior, 2, 'INTERVENCION', l_change, l_therapist);
    update usb_tasks set phase_seq = case when phase = 'LINEA_BASE' then 1 else 2 end,
                         retired_at = case when phase = 'LINEA_BASE' then l_change end
     where behavior_id = p_behavior;
    update usb_task_entries e set phase_seq = (select t.phase_seq from usb_tasks t where t.task_id = e.task_id)
     where task_id in (select task_id from usb_tasks where behavior_id = p_behavior);
  end set_ab_phases;

  procedure widget_entry(p_task number, p_widget varchar2, p_date date, p_time varchar2,
                         p_value number, p_note varchar2 default null) is
  begin
    insert into usb_widget_entries (task_id, widget_id, entry_date, entry_time, numeric_value, note, recorded_by)
    values (p_task, p_widget, p_date, p_time, p_value, p_note, l_ana);
  end widget_entry;
begin
  l_therapist := user_id('e.patino@usb.edu.co');
  l_ana := user_id('ana.torres@usbbog.edu.co');
  l_daniel := user_id('daniel.ruiz@usbbog.edu.co');

  -- Ana Torres · ansiedad social ------------------------------------------------
  l_case := ensure_patient(l_ana, date '2006-05-10', 'Psicología · 4.º sem', 'Ansiedad social',
                           4286277314, 'Caso de ansiedad social');
  select count(*) into l_count from usb_behaviors where case_id = l_case;
  if l_count = 0 then
    insert into usb_patient_diagnoses (patient_id, code, label, assigned_by)
    values (l_ana, 'F40.1', 'Fobia social', l_therapist);

    -- Conducta desadaptativa con línea base cerrada
    l_b := behavior(l_case, 'Episodios de ansiedad en clase',
                    'Episodios observables de ansiedad durante actividades académicas.',
                    'DESADAPTATIVA', false, date '2026-09-04');
    update usb_behaviors set function_id = 'fn-escape' where behavior_id = l_b;
    l_t := task(l_b, 'Registrar episodios de ansiedad (línea base)',
                'Al terminar el día, anota cuántos episodios de ansiedad tuviste y cuántos minutos observaste.',
                'REGISTRO_AUTONOMO', 'FRECUENCIA', 'LINEA_BASE', false, null, false, p_assigned => '1/9/2026');
    entry(l_t, date '2026-09-01', 5, 'Cinco episodios en 45 minutos.', 'LINEA_BASE', 45);
    entry(l_t, date '2026-09-02', 6, 'Seis episodios en 45 minutos.', 'LINEA_BASE', 45);
    entry(l_t, date '2026-09-03', 5, 'Cinco episodios en 45 minutos.', 'LINEA_BASE', 45);
    entry(l_t, date '2026-09-04', 4, 'Cuatro episodios en 30 minutos.', 'LINEA_BASE', 30);
    l_t := task(l_b, 'Registrar episodios de ansiedad del día',
                'Al terminar el día, anota cuántas veces sentiste un episodio de ansiedad marcado durante las clases.',
                'REGISTRO_AUTONOMO', 'FRECUENCIA', 'INTERVENCION', true, 'Diaria', true);
    l_t := task(l_b, 'Escala de malestar antes de exponer en clase',
                'Justo antes de tu presentación, registra tu nivel de malestar de 0 (nada) a 10 (máximo).',
                'REGISTRO_AUTONOMO', 'INTENSIDAD', 'INTERVENCION', true, 'Personalizada', true,
                p_due => 'Mié 3 sep');
    l_t := task(l_b, 'Registrar episodios de ansiedad (primera semana)',
                'Al terminar el día, anota cuántas veces sentiste un episodio de ansiedad marcado.',
                'REGISTRO_AUTONOMO', 'FRECUENCIA', 'INTERVENCION', false, null, true, p_assigned => '5/9/2026');
    entry(l_t, date '2026-09-05', 3, 'Solo en la clase de la tarde.', 'INTERVENCION', 45);
    entry(l_t, date '2026-09-06', 3, 'La sesión observada duró una hora.', 'INTERVENCION', 60);
    entry(l_t, date '2026-09-07', 2, 'Dos episodios breves.', 'INTERVENCION', 45);
    entry(l_t, date '2026-09-08', 2, 'Mejor regulación durante la clase.', 'INTERVENCION', 60);

    l_t := task(l_b, 'Medir intensidad de ansiedad',
                'Después de cada clase, indica la intensidad percibida de ansiedad (1 = mínima, 10 = máxima).',
                'MEDICION_CONFIGURABLE', 'INTENSIDAD', 'INTERVENCION', true, 'Diaria', true, 'w-escala',
                'Observar si la ansiedad disminuye con la práctica de regulación.');
    widget_entry(l_t, 'w-escala', date '2026-09-07', '16:00', 9, 'Ansiedad alta antes de la exposición.');
    widget_entry(l_t, 'w-escala', date '2026-09-08', '16:00', 8);
    widget_entry(l_t, 'w-escala', date '2026-09-09', '16:00', 6, 'Usé respiración diafragmática.');
    widget_entry(l_t, 'w-escala', date '2026-09-10', '16:00', 4, 'Pude participar con menos malestar.');

    l_t := task(l_b, 'Registrar duración de la crisis',
                'Anota cuántos minutos duró cada episodio de ansiedad.',
                'MEDICION_CONFIGURABLE', 'DURACION', 'INTERVENCION', true, 'Diaria', true, 'w-duracion',
                'Reducir el tiempo de recuperación después de un episodio.');
    widget_entry(l_t, 'w-duracion', date '2026-09-07', '17:00', 30);
    widget_entry(l_t, 'w-duracion', date '2026-09-08', '17:00', 22);
    widget_entry(l_t, 'w-duracion', date '2026-09-09', '17:00', 12, 'La crisis terminó más rápido.');

    l_t := task(l_b, 'Cumplir la estrategia de respiración',
                'Registra qué porcentaje de las veces realizaste la estrategia acordada.',
                'MEDICION_CONFIGURABLE', 'FRECUENCIA', 'INTERVENCION', true, 'Diaria', true, 'w-cumplimiento',
                'Conocer la adherencia a la estrategia terapéutica.');
    widget_entry(l_t, 'w-cumplimiento', date '2026-09-07', '20:00', 40);
    widget_entry(l_t, 'w-cumplimiento', date '2026-09-08', '20:00', 65);
    widget_entry(l_t, 'w-cumplimiento', date '2026-09-09', '20:00', 90, 'La estrategia ya forma parte de mi rutina.');

    merge into usb_widgets w using (select 'cw-control-emocional-demo' id from dual) s on (w.widget_id = s.id)
     when not matched then
       insert (widget_id, name, value_kind, unit, min_value, max_value, description, is_preset, created_by)
       values (s.id, 'Control emocional', 'NUMERIC', '/7', 1, 7,
               'Ejemplo personalizado: 1 = poco control y 7 = alto control.', false, l_therapist);
    l_t := task(l_b, 'Valorar control emocional',
                'Al finalizar el día, califica tu control emocional de 1 (poco) a 7 (alto).',
                'MEDICION_CONFIGURABLE', 'INTENSIDAD', 'INTERVENCION', true, 'Diaria', true,
                'cw-control-emocional-demo',
                'Personalizar la evaluación cuando una escala estándar no describe el caso.');
    widget_entry(l_t, 'cw-control-emocional-demo', date '2026-09-07', '21:00', 2);
    widget_entry(l_t, 'cw-control-emocional-demo', date '2026-09-08', '21:00', 4);
    widget_entry(l_t, 'cw-control-emocional-demo', date '2026-09-09', '21:00', 6, 'Pude identificar y regular mis emociones.');

    -- Conducta adaptativa: participación
    l_b := behavior(l_case, 'Participar en clase a pesar de la ansiedad',
                    'Intervenciones o participaciones realizadas durante la clase aunque exista ansiedad.',
                    'ADAPTATIVA', false, date '2026-09-03');
    l_t := task(l_b, 'Registrar participaciones (línea base)',
                'Cuenta cuántas veces participaste en clase.',
                'REGISTRO_AUTONOMO', 'FRECUENCIA', 'LINEA_BASE', false, null, false, p_assigned => '1/9/2026');
    entry(l_t, date '2026-09-01', 1, null, 'LINEA_BASE', 45);
    entry(l_t, date '2026-09-02', 2, null, 'LINEA_BASE', 45);
    entry(l_t, date '2026-09-03', 1, null, 'LINEA_BASE', 45);
    l_t := task(l_b, 'Registrar participaciones en clase',
                'Cada día, cuenta cuántas veces participaste en clase aunque sintieras ansiedad.',
                'REGISTRO_AUTONOMO', 'FRECUENCIA', 'INTERVENCION', true, 'Diaria', true);

    -- Conducta adaptativa: regulación
    l_b := behavior(l_case, 'Regular la ansiedad durante la exposición',
                    'Usar una estrategia de respiración y continuar la exposición sin abandonar la actividad.',
                    'ADAPTATIVA', false, date '2026-09-04');
    l_t := task(l_b, 'Registrar participaciones sin estrategia (línea base)',
                'Cuenta cuántas veces participaste y permaneciste en la actividad.',
                'REGISTRO_AUTONOMO', 'FRECUENCIA', 'LINEA_BASE', false, null, false, p_assigned => '1/9/2026');
    entry(l_t, date '2026-09-01', 2, 'Participó 2 veces sin estrategia definida.', 'LINEA_BASE');
    entry(l_t, date '2026-09-02', 1, 'Participó una vez.', 'LINEA_BASE');
    entry(l_t, date '2026-09-03', 2, 'Abandonó la exposición después de dos intentos.', 'LINEA_BASE');
    entry(l_t, date '2026-09-04', 1, 'Necesitó retirarse del salón.', 'LINEA_BASE');
    l_t := task(l_b, 'Registrar participaciones usando respiración',
                'Después de cada clase, cuenta cuántas veces participaste y lograste permanecer en la actividad usando respiración.',
                'REGISTRO_AUTONOMO', 'FRECUENCIA', 'INTERVENCION', true, 'Diaria', true);
    entry(l_t, date '2026-09-05', 2, 'Usé respiración antes de responder.', 'INTERVENCION');
    entry(l_t, date '2026-09-06', 3, 'Permanecí toda la clase.', 'INTERVENCION');
    entry(l_t, date '2026-09-07', 4, 'Participé sin abandonar el salón.', 'INTERVENCION');
    entry(l_t, date '2026-09-08', 5, 'Me sentí capaz de continuar pese a la ansiedad.', 'INTERVENCION');

    -- La conducta adaptativa de regulación reemplaza a la de ansiedad.
    update usb_behaviors set replacement_behavior_id = l_b
     where case_id = l_case and name = 'Episodios de ansiedad en clase';
  end if;

  -- Serie A-B de "Episodios de ansiedad en clase" (caso individual de Ana): A1 = 1–30 sep
  -- sin tratamiento, B1 = 1–30 oct con tratamiento; un total diario por día. Se rehace en
  -- cada semilla para que la gráfica del terapeuta y el panel estadístico cuenten lo mismo.
  select b.behavior_id into l_b from usb_behaviors b
   where b.case_id = l_case and b.name = 'Episodios de ansiedad en clase';
  update usb_behaviors set baseline_dimensions = 'FRECUENCIA' where behavior_id = l_b;
  select min(task_id) into l_t_base from usb_tasks
   where behavior_id = l_b and task_type = 'REGISTRO_AUTONOMO' and dimension = 'FRECUENCIA' and phase = 'LINEA_BASE';
  select min(task_id) into l_t_treatment from usb_tasks
   where behavior_id = l_b and task_type = 'REGISTRO_AUTONOMO' and dimension = 'FRECUENCIA' and phase = 'INTERVENCION';
  delete from usb_task_entries where task_id in (
    select task_id from usb_tasks where behavior_id = l_b and task_type = 'REGISTRO_AUTONOMO');
  -- Sobran registros autónomos que medían otra dimensión sin línea base o duplicaban el principal.
  delete from usb_tasks
   where behavior_id = l_b and task_type = 'REGISTRO_AUTONOMO' and task_id not in (l_t_base, l_t_treatment);
  -- Nivel propio de 6,4 episodios por día de clase; el tratamiento lo reduce hasta un 55 % con una
  -- curva de aprendizaje (mejora rápido y se estabiliza), con repunte en parciales (12–16 oct).
  for d in 0..29 loop
    entry(l_t_base, date '2026-09-01' + d,
          greatest(0, floor(6.4 * weekday_factor(date '2026-09-01' + d)
                            + (noise(900, d, 11) + noise(900, d, 13) - 1) * 2.2 + 0.5)),
          'Dato ficticio: total diario de episodios sin tratamiento.', 'LINEA_BASE', 60);
    entry(l_t_treatment, date '2026-10-01' + d,
          greatest(0, floor(6.4 * (1 - 0.55 * (1 - exp(-d / 7))) * weekday_factor(date '2026-10-01' + d)
                            * case when d between 11 and 15 then 1.25 else 1 end
                            + (noise(900, d, 17) + noise(900, d, 19) - 1) * 1.8 + 0.5)),
          'Dato ficticio: total diario de episodios durante tratamiento.', 'INTERVENCION', 60);
  end loop;
  -- Las mediciones configurables de la intervención no pueden ser anteriores al tratamiento.
  update usb_widget_entries set entry_date = entry_date + 30
   where task_id in (select task_id from usb_tasks where behavior_id = l_b) and entry_date < date '2026-10-01';
  set_ab_phases(l_b, date '2026-09-01', date '2026-10-01');

  -- Daniel Ruiz · insomnio ------------------------------------------------------
  l_case := ensure_patient(l_daniel, date '2003-02-20', 'Ing. Industrial · 8.º sem',
                           'Insomnio de conciliación', 4294929238, 'Caso de insomnio');
  select count(*) into l_count from usb_behaviors where case_id = l_case;
  if l_count = 0 then
    insert into usb_patient_diagnoses (patient_id, code, label, assigned_by)
    values (l_daniel, 'F51.0', 'Insomnio no orgánico', l_therapist);
    l_b := behavior(l_case, 'Minutos para conciliar el sueño',
                    'Tiempo entre apagar la luz y quedarse dormido.', 'DESADAPTATIVA', true);
    update usb_behaviors set baseline_dimensions = 'DURACION' where behavior_id = l_b;
    l_t := task(l_b, 'Tiempo que tardaste en dormirte',
                'Cada mañana estima cuántos minutos pasaron desde que apagaste la luz hasta quedarte dormido.',
                'REGISTRO_AUTONOMO', 'DURACION', 'LINEA_BASE', true, 'Diaria', false);
  end if;
  commit;
end;
/

-- -----------------------------------------------------------------------------
-- Caso ficticio de seguimiento longitudinal (3 meses) para pruebas integrales.
-- Paciente: Laura Mendoza · 01-jul-2026 a 30-sep-2026.
-- -----------------------------------------------------------------------------
declare
  l_therapist number;
  l_patient number;
  l_case number;
  l_b_sleep number;
  l_b_routine number;
  l_t_base number;
  l_t_sleep number;
  l_t_routine number;
  l_count number;
  function uid(p_email varchar2) return number is l_id number; begin
    select user_id into l_id from usb_users where email = p_email; return l_id;
  end;
begin
  l_therapist := uid('e.patino@usb.edu.co');
  begin
    l_patient := uid('laura.mendoza@usbbog.edu.co');
    usb_auth.set_password(l_patient, '&dev_pwd');
    usb_auth.reset_mfa(l_patient);
  exception when no_data_found then
    l_patient := usb_auth.create_user('laura.mendoza@usbbog.edu.co', 'Laura Mendoza (Paciente demo)', 'PATIENT', '&dev_pwd');
  end;
  merge into usb_patient_profiles p using (select l_patient user_id from dual) s on (p.user_id=s.user_id)
    when not matched then insert (user_id, therapist_id, birth_date, program, condition, color_argb)
      values (l_patient, l_therapist, date '2001-11-14', 'Programa de habilidades para la vida', 'Dificultad de conciliacion del sueno y habitos irregulares', 4294929238);
  begin select case_id into l_case from usb_cases where patient_id=l_patient;
  exception when no_data_found then insert into usb_cases(patient_id,title) values(l_patient,'Estudio longitudinal de habitos de sueno (3 meses)') returning case_id into l_case; end;
  select count(*) into l_count from usb_behaviors where case_id=l_case;
  if l_count = 0 then
    insert into usb_patient_diagnoses(patient_id,code,label,assigned_by) values(l_patient,'F51.0','Insomnio no organico (caso ficticio)',l_therapist);
    insert into usb_behaviors(case_id,name,operational_definition,behavior_type,baseline_open,baseline_closed_at,baseline_dimensions,created_by)
      values(l_case,'Minutos para conciliar el sueno','Minutos desde apagar la luz hasta quedarse dormida.','DESADAPTATIVA',false,from_tz(cast(date '2026-07-14' as timestamp),'America/Bogota'),'DURACION',l_therapist) returning behavior_id into l_b_sleep;
    insert into usb_behaviors(case_id,name,operational_definition,behavior_type,baseline_open,baseline_closed_at,baseline_dimensions,created_by)
      values(l_case,'Rutina de desconexion nocturna','Realizar lectura breve, respiracion y apagar pantallas antes de dormir.','ADAPTATIVA',false,from_tz(cast(date '2026-07-14' as timestamp),'America/Bogota'),'FRECUENCIA',l_therapist) returning behavior_id into l_b_routine;
    update usb_behaviors set replacement_behavior_id=l_b_routine where behavior_id=l_b_sleep;
    insert into usb_tasks(behavior_id,title,instructions,task_type,dimension,metric_unit,phase,is_recurring,frequency,created_by)
      values(l_b_sleep,'Línea base: tiempo para dormir','Registrar cada mañana los minutos estimados para conciliar el sueño.','REGISTRO_AUTONOMO','DURACION','MINUTOS','LINEA_BASE',true,'Diaria',l_therapist) returning task_id into l_t_base;
    insert into usb_tasks(behavior_id,title,instructions,task_type,dimension,metric_unit,phase,is_recurring,frequency,reminder_active,created_by)
      values(l_b_sleep,'Seguimiento del tiempo para dormir','Registrar los minutos y anotar qué estrategia ayudó.','REGISTRO_AUTONOMO','DURACION','MINUTOS','INTERVENCION',true,'Diaria',true,l_therapist) returning task_id into l_t_sleep;
    insert into usb_tasks(behavior_id,title,instructions,task_type,dimension,phase,is_recurring,frequency,reminder_active,widget_id,therapeutic_goal,created_by)
      values(l_b_routine,'Cumplimiento de rutina nocturna','Completar lectura, respiración y desconexión de pantallas; registrar el porcentaje.','MEDICION_CONFIGURABLE','FRECUENCIA','INTERVENCION',true,'Diaria',true,'w-cumplimiento','Consolidar una rutina sostenible antes de dormir.',l_therapist) returning task_id into l_t_routine;
    for i in 0..13 loop
      insert into usb_task_entries(task_id,entry_date,value,note,phase,observation_minutes,is_baseline,recorded_by)
        values(l_t_base,date '2026-07-01'+i,58-i*2,'Semana de línea base; sin rutina estable.','LINEA_BASE',480,true,l_patient);
    end loop;
    for i in 0..76 loop
      insert into usb_task_entries(task_id,entry_date,value,note,phase,observation_minutes,is_baseline,recorded_by)
        values(l_t_sleep,date '2026-07-15'+i,round(28- i*0.20 + mod(i,3)*2),'Aplicó respiración y dejó el teléfono fuera de la habitación.','INTERVENCION',480,false,l_patient);
      insert into usb_widget_entries(task_id,widget_id,entry_date,entry_time,numeric_value,note,recorded_by)
        values(l_t_routine,'w-cumplimiento',date '2026-07-15'+i,'21:30',least(100,round(45+i*0.65+mod(i,4)*5)),'Registro diario de la rutina nocturna.',l_patient);
    end loop;
  else
    select min(behavior_id), max(behavior_id) into l_b_sleep, l_b_routine from usb_behaviors where case_id=l_case;
  end if;
  -- Normaliza textos de la ficha demo para que no dependan de la codificacion del cliente SQL.
  update usb_cases set title='Estudio longitudinal de habitos de sueno (3 meses)' where case_id=l_case;
  update usb_patient_profiles set condition='Dificultad de conciliacion del sueno y habitos irregulares' where user_id=l_patient;
  update usb_patient_diagnoses set label='Insomnio no organico (caso ficticio)' where patient_id=l_patient and code='F51.0';
  update usb_behaviors set name='Minutos para conciliar el sueno', operational_definition='Minutos desde apagar la luz hasta quedarse dormida.' where behavior_id=l_b_sleep;
  update usb_behaviors set name='Rutina de desconexion nocturna', operational_definition='Realizar lectura breve, respiracion y apagar pantallas antes de dormir.' where behavior_id=l_b_routine;
  update usb_tasks set title=replace(title, chr(65533), ''), instructions=replace(instructions, chr(65533), '')
    where behavior_id in (l_b_sleep, l_b_routine);
  update usb_tasks set title='Linea base: tiempo para dormir', instructions='Registrar cada manana los minutos estimados para conciliar el sueno.'
    where behavior_id=l_b_sleep and phase='LINEA_BASE';
  update usb_tasks set title='Seguimiento del tiempo para dormir', instructions='Registrar los minutos y anotar que estrategia ayudo.'
    where behavior_id=l_b_sleep and phase='INTERVENCION';
  update usb_tasks set title='Cumplimiento de rutina nocturna', instructions='Completar lectura, respiracion y desconexion de pantallas; registrar el porcentaje.'
    where behavior_id=l_b_routine;

  -- A1 del 1 al 14 de julio, estable (sin tendencia previa: si la línea base ya viniera bajando,
  -- la mejora no se podría atribuir al tratamiento); B1 desde el 15 de julio, bajando.
  select min(task_id) into l_t_base from usb_tasks where behavior_id=l_b_sleep and phase='LINEA_BASE';
  select min(task_id) into l_t_sleep from usb_tasks where behavior_id=l_b_sleep and phase='INTERVENCION';
  delete from usb_task_entries where task_id in (l_t_base, l_t_sleep);
  for i in 0..13 loop
    insert into usb_task_entries(task_id,entry_date,value,note,phase,phase_seq,observation_minutes,is_baseline,recorded_by)
      values(l_t_base,date '2026-07-01'+i,48+(mod(i*5,7)-3)*2,'Semana de línea base; sin rutina estable.','LINEA_BASE',1,480,true,l_patient);
  end loop;
  for i in 0..76 loop
    insert into usb_task_entries(task_id,entry_date,value,note,phase,phase_seq,observation_minutes,is_baseline,recorded_by)
      values(l_t_sleep,date '2026-07-15'+i,greatest(10,round(44-i*0.28+(mod(i*3,5)-2)*2.5)),'Aplicó respiración y dejó el teléfono fuera de la habitación.','INTERVENCION',2,480,false,l_patient);
  end loop;
  for b in (select behavior_id from usb_behaviors where behavior_id in (l_b_sleep, l_b_routine)) loop
    update usb_behaviors set baseline_open=false, phase_seq=2, baseline_closed_at=from_tz(timestamp '2026-07-15 00:00:00','America/Bogota')
     where behavior_id=b.behavior_id;
    delete from usb_behavior_phases where behavior_id=b.behavior_id;
    insert into usb_behavior_phases(behavior_id,phase_seq,phase,started_at,ended_at,started_by)
      values(b.behavior_id,1,'LINEA_BASE',from_tz(timestamp '2026-07-01 00:00:00','America/Bogota'),from_tz(timestamp '2026-07-15 00:00:00','America/Bogota'),l_therapist);
    insert into usb_behavior_phases(behavior_id,phase_seq,phase,started_at,started_by)
      values(b.behavior_id,2,'INTERVENCION',from_tz(timestamp '2026-07-15 00:00:00','America/Bogota'),l_therapist);
    update usb_tasks set phase_seq=case when phase='LINEA_BASE' then 1 else 2 end,
                         retired_at=case when phase='LINEA_BASE' then from_tz(timestamp '2026-07-15 00:00:00','America/Bogota') end
     where behavior_id=b.behavior_id;
  end loop;
  commit;
end;
/

-- -----------------------------------------------------------------------------
-- Estudio de grupo con datos sintéticos: 200 participantes con "Episodios de ansiedad en
-- clase", 30 días sin tratamiento (A1, 1–30 sep) y 30 con tratamiento (B1, 1–30 oct).
--   · study_code agrupa sus conductas: el panel hace una prueba pareada (n = participantes)
--     y nunca las mezcla con el caso individual de Ana, aunque se llamen igual.
--   · Cuentas sintéticas: deshabilitadas, sin contraseña, fuera de la app clínica y de la
--     lista de usuarios. Las asigna un terapeuta propio de la cohorte.
--   · Valores deterministas (sin aleatorios): la semilla da siempre el mismo resultado.
-- -----------------------------------------------------------------------------
declare
  c_study    constant varchar2(40) := 'ANSIEDAD_COHORTE_2026';
  c_count    constant pls_integer := 200;
  l_therapist number;
  l_patient  number;
  l_case     number;
  l_behavior number;
  l_base     number;
  l_treat    number;
  l_program  varchar2(200);
  l_extra    number;
  l_value    number;
  l_level    number;
  l_response number;

  -- Ruido uniforme 0..1 determinista (un hash entero): datos variados como los reales, pero
  -- idénticos en cada semilla para que los resultados del panel se puedan citar.
  function noise(p_a number, p_b number, p_c number) return number is
    c_m constant number := 2147483647;
    l_h number := mod(p_a * 73856093 + p_b * 19349663 + p_c * 83492791, c_m);
  begin
    l_h := mod(l_h * 48271, c_m);
    l_h := mod(l_h * 48271, c_m);
    return l_h / c_m;
  end noise;

  -- Episodios "en clase": entre semana hay clases, el fin de semana casi no (lunes = 0).
  function weekday_factor(p_date date) return number is
  begin
    return case when mod(p_date - date '2026-08-31', 7) >= 5 then 0.55 else 1.15 end;
  end weekday_factor;

  -- Cuenta sintética sin acceso (hash vacío: ninguna contraseña coincide).
  function synthetic_user(p_email varchar2, p_name varchar2, p_role varchar2) return number is
    l_id number;
  begin
    select user_id into l_id from usb_users where email = p_email;
    update usb_users set full_name = p_name, status = 'DISABLED', is_synthetic = true where user_id = l_id;
    return l_id;
  exception
    when no_data_found then
      insert into usb_users (email, full_name, role, status, password_hash, password_salt,
                             password_iterations, is_synthetic)
      values (p_email, p_name, p_role, 'DISABLED', hextoraw('00'), hextoraw('00'), 0, true)
      returning user_id into l_id;
      return l_id;
  end synthetic_user;

  -- Borra a un participante sintético y todo su caso.
  procedure purge_user(p_user_id number) is
  begin
    for c in (select case_id from usb_cases where patient_id = p_user_id) loop
      delete from usb_task_entries where task_id in
        (select t.task_id from usb_tasks t join usb_behaviors b on b.behavior_id = t.behavior_id where b.case_id = c.case_id);
      delete from usb_widget_entries where task_id in
        (select t.task_id from usb_tasks t join usb_behaviors b on b.behavior_id = t.behavior_id where b.case_id = c.case_id);
      delete from usb_tasks where behavior_id in (select behavior_id from usb_behaviors where case_id = c.case_id);
      update usb_behaviors set replacement_behavior_id = null where case_id = c.case_id;
      delete from usb_behaviors where case_id = c.case_id;
      delete from usb_cases where case_id = c.case_id;
    end loop;
    delete from usb_patient_diagnoses where patient_id = p_user_id;
    delete from usb_patient_profiles where user_id = p_user_id;
    delete from usb_sessions where user_id = p_user_id;
    delete from usb_login_challenges where user_id = p_user_id;
    delete from usb_recovery_codes where user_id = p_user_id;
    delete from usb_auth_events where user_id = p_user_id;
    delete from usb_users where user_id = p_user_id;
  end purge_user;
begin
  -- Versión anterior de la semilla: correos de 2 dígitos con contraseña de demo. Se eliminan.
  for u in (select user_id from usb_users where regexp_like(email, '^cohorte\.ansiedad\.[0-9]{2}@demo\.usbrain\.local$')) loop
    purge_user(u.user_id);
  end loop;

  l_therapist := synthetic_user('terapeuta.cohorte@demo.usbrain.local', 'Terapeuta de cohorte (demo)', 'THERAPIST');

  for i in 1..c_count loop
    l_program := case mod(i, 4) when 0 then 'Psicología' when 1 then 'Ingeniería de Sistemas'
                                when 2 then 'Administración de Empresas' else 'Ingeniería Industrial' end;
    l_extra := case when mod(i, 4) = 2 then 1 else 0 end;
    l_patient := synthetic_user('cohorte.ansiedad.' || lpad(i, 3, '0') || '@demo.usbrain.local',
                                'Participante sintético ' || lpad(i, 3, '0'), 'PATIENT');
    merge into usb_patient_profiles p using (select l_patient user_id from dual) s on (p.user_id = s.user_id)
      when matched then update set p.therapist_id = l_therapist,
        p.birth_date = add_months(date '2008-06-15', -12 * mod(i * 7, 9)),
        p.program = l_program, p.condition = 'Estudio sintético de ansiedad'
      when not matched then insert (user_id, therapist_id, birth_date, program, condition, color_argb)
        values (l_patient, l_therapist, add_months(date '2008-06-15', -12 * mod(i * 7, 9)), l_program,
                'Estudio sintético de ansiedad', 4286277314);
    begin
      select case_id into l_case from usb_cases where patient_id = l_patient;
    exception when no_data_found then
      insert into usb_cases (patient_id, title) values (l_patient, 'Estudio sintético: episodios de ansiedad')
      returning case_id into l_case;
    end;
    begin
      select behavior_id into l_behavior from usb_behaviors where case_id = l_case and study_code = c_study;
    exception when no_data_found then
      insert into usb_behaviors (case_id, name, operational_definition, behavior_type, baseline_open,
                                 baseline_dimensions, study_code, created_by)
      values (l_case, 'Episodios de ansiedad en clase',
              'Total diario sintético de episodios observables durante actividades académicas.',
              'DESADAPTATIVA', false, 'FRECUENCIA', c_study, l_therapist)
      returning behavior_id into l_behavior;
    end;
    delete from usb_task_entries where task_id in (select task_id from usb_tasks where behavior_id = l_behavior);
    delete from usb_tasks where behavior_id = l_behavior;
    insert into usb_tasks (behavior_id, title, instructions, task_type, dimension, phase, phase_seq,
                           is_recurring, frequency, retired_at, created_by)
    values (l_behavior, 'Registro diario de ansiedad (A1)', 'Registrar el total diario de episodios.',
            'REGISTRO_AUTONOMO', 'FRECUENCIA', 'LINEA_BASE', 1, true, 'Diaria',
            from_tz(timestamp '2026-10-01 00:00:00', 'America/Bogota'), l_therapist)
    returning task_id into l_base;
    insert into usb_tasks (behavior_id, title, instructions, task_type, dimension, phase, phase_seq,
                           is_recurring, frequency, created_by)
    values (l_behavior, 'Registro diario de ansiedad (B1)', 'Registrar el total diario de episodios durante el tratamiento.',
            'REGISTRO_AUTONOMO', 'FRECUENCIA', 'INTERVENCION', 2, true, 'Diaria', l_therapist)
    returning task_id into l_treat;
    -- Cada participante tiene su nivel (4 a 8 episodios por día de clase) y su respuesta al
    -- tratamiento (30 a 75 % menos al final); uno de cada diez no responde. Hay días que afectan
    -- a todo el grupo (entregas, parciales del 12 al 16 de octubre) y ruido propio de cada día.
    l_level := 4 + 4 * noise(i, 1, 7) + l_extra;
    l_response := case when mod(i, 10) = 0 then 0 else 0.30 + 0.45 * noise(i, 2, 7) end;
    for d in 0..29 loop
      l_value := greatest(0, floor(l_level * weekday_factor(date '2026-09-01' + d)
                                   * (1 + 0.30 * (noise(0, d, 23) - 0.5))
                                   + (noise(i, d, 11) + noise(i, d, 13) - 1) * 2.2 + 0.5));
      insert into usb_task_entries (task_id, entry_date, value, note, phase, phase_seq, observation_minutes, is_baseline, recorded_by)
      values (l_base, date '2026-09-01' + d, l_value, 'Dato sintético de estudio.', 'LINEA_BASE', 1, 60, true, l_patient);
      l_value := greatest(0, floor(l_level * (1 - l_response * (1 - exp(-d / 7)))
                                   * weekday_factor(date '2026-10-01' + d)
                                   * case when d between 11 and 15 then 1.25 else 1 end
                                   * (1 + 0.30 * (noise(0, d + 30, 23) - 0.5))
                                   + (noise(i, d, 17) + noise(i, d, 19) - 1) * 1.8 + 0.5));
      insert into usb_task_entries (task_id, entry_date, value, note, phase, phase_seq, observation_minutes, is_baseline, recorded_by)
      values (l_treat, date '2026-10-01' + d, l_value, 'Dato sintético de estudio.', 'INTERVENCION', 2, 60, false, l_patient);
    end loop;
    update usb_behaviors set baseline_open = false, phase_seq = 2,
                             baseline_closed_at = from_tz(timestamp '2026-10-01 00:00:00', 'America/Bogota')
     where behavior_id = l_behavior;
    delete from usb_behavior_phases where behavior_id = l_behavior;
    insert into usb_behavior_phases (behavior_id, phase_seq, phase, started_at, ended_at)
    values (l_behavior, 1, 'LINEA_BASE', from_tz(timestamp '2026-09-01 00:00:00', 'America/Bogota'),
            from_tz(timestamp '2026-10-01 00:00:00', 'America/Bogota'));
    insert into usb_behavior_phases (behavior_id, phase_seq, phase, started_at)
    values (l_behavior, 2, 'INTERVENCION', from_tz(timestamp '2026-10-01 00:00:00', 'America/Bogota'));
  end loop;
  commit;
end;
/

-- Completa las fases de todo lo que la semilla creó con el helper genérico (A1/B1, tareas retiradas).
begin
  usb_sync_phases;
  commit;
end;
/

prompt Usuarios de prueba listos (contraseña: DEV_SEED_PASSWORD en backend/.env).
exit
