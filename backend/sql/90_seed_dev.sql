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
    -- Si ya existe no se toca: reinstalar no debe cerrar las sesiones abiertas en la app.
    select user_id into l_user_id from usb_users where email = p_email;
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

prompt Usuarios de prueba listos (contraseña: DEV_SEED_PASSWORD en backend/.env).
exit
