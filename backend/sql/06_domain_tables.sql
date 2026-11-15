-- =============================================================================
-- USBrain · 06_domain_tables.sql
-- Modelo clínico de diseño de caso único (refleja PrototypeModel.kt):
--   paciente ─ caso ─ conductas ─ actividades ─ registros
-- más los catálogos que el terapeuta puede ampliar (funciones conductuales,
-- mediciones configurables) y el catálogo de apoyo CIE-10.
-- Los ids de catálogo son texto ('fn-escape', 'w-escala', …) porque la app los
-- usa como constantes; el resto son numéricos.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

-- Datos del consultante (1:1 con usb_users de rol PATIENT).
create table if not exists usb_patient_profiles (
  user_id      number primary key references usb_users,
  therapist_id number references usb_users,
  birth_date   date,
  program      varchar2(200),
  condition    varchar2(400),
  color_argb   number(12) default 4286277314 not null,
  created_at   timestamp with time zone default systimestamp not null
);
create index if not exists usb_patient_therapist_ix on usb_patient_profiles (therapist_id);

-- Un caso único por paciente.
create table if not exists usb_cases (
  case_id      number generated always as identity primary key,
  patient_id   number not null references usb_users,
  title        varchar2(300) not null,
  created_at   timestamp with time zone default systimestamp not null,
  constraint usb_cases_patient_uk unique (patient_id)
);

create table if not exists usb_cie10 (
  code  varchar2(10)  primary key,
  label varchar2(300) not null
);

create table if not exists usb_patient_diagnoses (
  patient_id  number        not null references usb_users,
  code        varchar2(10)  not null,
  label       varchar2(300) not null,
  assigned_by number references usb_users,
  assigned_at timestamp with time zone default systimestamp not null,
  constraint usb_patient_diagnoses_pk primary key (patient_id, code)
);

create table if not exists usb_behavioral_functions (
  function_id varchar2(40)  primary key,
  label       varchar2(200) not null,
  is_seed     boolean default false not null,
  created_by  number references usb_users,
  created_at  timestamp with time zone default systimestamp not null
);

-- Variable de medición configurable (WidgetConfig): predefinida o creada por un terapeuta.
create table if not exists usb_widgets (
  widget_id   varchar2(40)  primary key,
  name        varchar2(200) not null,
  value_kind  varchar2(20)  not null,
  unit        varchar2(40),
  min_value   number,
  max_value   number,
  options     json,
  description varchar2(1000),
  is_preset   boolean default false not null,
  created_by  number references usb_users,
  created_at  timestamp with time zone default systimestamp not null,
  constraint usb_widgets_kind_ck check (value_kind in ('NUMERIC', 'YES_NO', 'TEXT', 'MULTI_SELECT'))
);

create sequence if not exists usb_catalog_seq start with 100;

create table if not exists usb_behaviors (
  behavior_id             number generated always as identity primary key,
  case_id                 number        not null references usb_cases,
  name                    varchar2(300) not null,
  operational_definition  varchar2(2000) not null,
  behavior_type           varchar2(20)  not null,
  baseline_open           boolean default true not null,
  baseline_closed_at      timestamp with time zone,
  function_id             varchar2(40) default 'fn-no-aplica' not null references usb_behavioral_functions,
  replacement_behavior_id number references usb_behaviors,
  baseline_dimensions     varchar2(100) default 'FRECUENCIA' not null,
  created_by              number references usb_users,
  created_at              timestamp with time zone default systimestamp not null,
  constraint usb_behaviors_type_ck check (behavior_type in ('ADAPTATIVA', 'DESADAPTATIVA'))
);
create index if not exists usb_behaviors_case_ix on usb_behaviors (case_id);

-- Actividad (TrackTask). assigned_label / due_label guardan el texto que elige el
-- terapeuta en el calendario de la app ("Hoy", "Mié 3 sep"…).
create table if not exists usb_tasks (
  task_id          number generated always as identity primary key,
  behavior_id      number        not null references usb_behaviors,
  title            varchar2(300) not null,
  instructions     varchar2(2000),
  task_type        varchar2(30)  not null,
  dimension        varchar2(20)  default 'FRECUENCIA' not null,
  metric_unit      varchar2(30),
  widget_id        varchar2(40) references usb_widgets,
  therapeutic_goal varchar2(1000),
  assigned_label   varchar2(60) default 'Hoy' not null,
  due_label        varchar2(60),
  is_recurring     boolean default false not null,
  frequency        varchar2(60),
  reminder_active  boolean default false not null,
  phase            varchar2(20) not null,
  created_by       number references usb_users,
  created_at       timestamp with time zone default systimestamp not null,
  constraint usb_tasks_type_ck check (task_type in
    ('REGISTRO_AUTONOMO', 'EJERCICIO_PRACTICA', 'PSICOEDUCATIVA', 'CONDUCTUAL', 'MEDICION_CONFIGURABLE')),
  constraint usb_tasks_dimension_ck check (dimension in ('FRECUENCIA', 'DURACION', 'INTENSIDAD')),
  constraint usb_tasks_metric_ck check (metric_unit is null or metric_unit in
    ('SOLO_COMPLETADO', 'MINUTOS', 'REPETICIONES', 'ESCALA_1_5')),
  constraint usb_tasks_phase_ck check (phase in ('LINEA_BASE', 'INTERVENCION'))
);
create index if not exists usb_tasks_behavior_ix on usb_tasks (behavior_id);

-- Registro clásico (TaskEntry). is_baseline: se tomó con la línea base abierta, así
-- que cuenta como observación de línea base (queda fija al cerrarla).
create table if not exists usb_task_entries (
  entry_id            number generated always as identity primary key,
  task_id             number not null references usb_tasks,
  entry_date          date   not null,
  value               number not null,
  note                varchar2(2000),
  phase               varchar2(20) not null,
  observation_minutes number(6),
  is_baseline         boolean default false not null,
  recorded_by         number references usb_users,
  created_at          timestamp with time zone default systimestamp not null,
  constraint usb_task_entries_uk unique (task_id, entry_date, phase)
);

-- Registro de una actividad con medición configurable (WidgetEntry).
create table if not exists usb_widget_entries (
  entry_id            number generated always as identity primary key,
  task_id             number not null references usb_tasks,
  widget_id           varchar2(40) not null references usb_widgets,
  entry_date          date   not null,
  entry_time          varchar2(5) not null,
  numeric_value       number,
  observation_minutes number(6),
  bool_value          boolean,
  text_value          varchar2(2000),
  selected_options    json,
  note                varchar2(2000),
  recorded_by         number references usb_users,
  created_at          timestamp with time zone default systimestamp not null,
  constraint usb_widget_entries_uk unique (task_id, entry_date),
  constraint usb_widget_entries_time_ck check (regexp_like(entry_time, '^[0-2][0-9]:[0-5][0-9]$'))
);

-- --------------------------------------------------------------------------
-- Fases del diseño de caso único (A-B, A-B-A-B…)
-- Cada conducta está en UNA fase a la vez: phase_seq 1 = A1 (línea base), 2 = B1
-- (intervención), 3 = A2, 4 = B2… (impar = línea base, par = intervención). Solo avanza
-- hacia adelante y la decide el terapeuta. Cada tarea y cada registro pertenecen a la fase
-- en que se crearon; al terminar una fase sus tareas se retiran (retired_at).
-- study_code agrupa conductas de varios pacientes de un mismo estudio (análisis de grupo);
-- es null en los casos clínicos individuales.
-- --------------------------------------------------------------------------
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
  ddl_if(q'[select 1 - count(*) from user_tab_columns where table_name = 'USB_BEHAVIORS' and column_name = 'PHASE_SEQ']',
         'alter table usb_behaviors add (phase_seq number(3) default 1 not null)');
  ddl_if(q'[select 1 - count(*) from user_tab_columns where table_name = 'USB_BEHAVIORS' and column_name = 'STUDY_CODE']',
         'alter table usb_behaviors add (study_code varchar2(40))');
  ddl_if(q'[select 1 - count(*) from user_tab_columns where table_name = 'USB_TASKS' and column_name = 'PHASE_SEQ']',
         'alter table usb_tasks add (phase_seq number(3))');
  ddl_if(q'[select 1 - count(*) from user_tab_columns where table_name = 'USB_TASKS' and column_name = 'RETIRED_AT']',
         'alter table usb_tasks add (retired_at timestamp with time zone)');
  ddl_if(q'[select 1 - count(*) from user_tab_columns where table_name = 'USB_TASK_ENTRIES' and column_name = 'PHASE_SEQ']',
         'alter table usb_task_entries add (phase_seq number(3))');
end;
/

create table if not exists usb_behavior_phases (
  behavior_id number    not null references usb_behaviors on delete cascade,
  phase_seq   number(3) not null,
  phase       varchar2(20) not null,
  started_at  timestamp with time zone default systimestamp not null,
  ended_at    timestamp with time zone,
  started_by  number references usb_users on delete set null,
  constraint usb_behavior_phases_pk primary key (behavior_id, phase_seq),
  constraint usb_behavior_phases_ck check (
    phase = case when mod(phase_seq, 2) = 1 then 'LINEA_BASE' else 'INTERVENCION' end)
);

-- Parámetros del flujo clínico, editables por quien tenga settings.clinical.
create table if not exists usb_app_settings (
  setting_key varchar2(64)  primary key,
  value       varchar2(400) not null,
  description varchar2(400),
  updated_by  number references usb_users on delete set null,
  updated_at  timestamp with time zone default systimestamp not null
);
merge into usb_app_settings s
using (select 'MIN_PHASE_OBSERVATIONS' k, '3' v,
              'Días con registro que necesita cada dimensión medida para terminar una fase (mínimo 3).' d from dual) x
   on (s.setting_key = x.k)
 when not matched then insert (setting_key, value, description) values (x.k, x.v, x.d);

-- Completa las fases de datos anteriores a este diseño (línea base = A1, intervención = B1)
-- y deja todo coherente. Idempotente: la semilla también lo llama al final.
create or replace procedure usb_sync_phases authid definer as
begin
  update usb_behaviors set phase_seq = phase_seq + 1
   where not baseline_open and mod(phase_seq, 2) = 1;
  update usb_tasks set phase_seq = case when phase = 'LINEA_BASE' then 1 else 2 end
   where phase_seq is null;
  update usb_task_entries e
     set phase_seq = (select t.phase_seq from usb_tasks t where t.task_id = e.task_id)
   where phase_seq is null;
  update usb_task_entries set is_baseline = (phase = 'LINEA_BASE')
   where is_baseline != (phase = 'LINEA_BASE');

  -- A1 empezó con el primer registro de la conducta (o al crearla, si aún no tiene).
  insert into usb_behavior_phases (behavior_id, phase_seq, phase, started_at, ended_at)
  select b.behavior_id, 1, 'LINEA_BASE',
         least(b.created_at, coalesce(b.baseline_closed_at, b.created_at),
               coalesce((select from_tz(cast(min(e.entry_date) as timestamp), 'America/Bogota')
                           from usb_task_entries e join usb_tasks t on t.task_id = e.task_id
                          where t.behavior_id = b.behavior_id), b.created_at)),
         case when b.phase_seq > 1 then coalesce(b.baseline_closed_at, systimestamp) end
    from usb_behaviors b
   where not exists (select 1 from usb_behavior_phases p where p.behavior_id = b.behavior_id);
  insert into usb_behavior_phases (behavior_id, phase_seq, phase, started_at)
  select b.behavior_id, 2, 'INTERVENCION', coalesce(b.baseline_closed_at, b.created_at)
    from usb_behaviors b
   where b.phase_seq = 2
     and not exists (select 1 from usb_behavior_phases p where p.behavior_id = b.behavior_id and p.phase_seq = 2);

  -- Una fase nunca termina antes de empezar (datos migrados con la fecha de creación).
  update usb_behavior_phases p
     set started_at = least(p.ended_at,
           coalesce((select from_tz(cast(min(e.entry_date) as timestamp), 'America/Bogota')
                       from usb_task_entries e join usb_tasks t on t.task_id = e.task_id
                      where t.behavior_id = p.behavior_id and e.phase_seq = p.phase_seq), p.ended_at))
   where p.ended_at < p.started_at;

  -- Las tareas de fases ya terminadas no se pueden seguir registrando.
  update usb_tasks t
     set retired_at = coalesce((select p.ended_at from usb_behavior_phases p
                                 where p.behavior_id = t.behavior_id and p.phase_seq = t.phase_seq), systimestamp)
   where t.retired_at is null
     and t.phase_seq < (select b.phase_seq from usb_behaviors b where b.behavior_id = t.behavior_id);
end usb_sync_phases;
/

begin
  usb_sync_phases;
end;
/

-- --------------------------------------------------------------------------
-- Catálogos semilla
-- --------------------------------------------------------------------------
merge into usb_behavioral_functions f
using (
  select 'fn-no-aplica' id, 'No aplica' label from dual union all
  select 'fn-atencion', 'Atención' from dual union all
  select 'fn-escape', 'Escape / evitación' from dual union all
  select 'fn-tangible', 'Tangible' from dual union all
  select 'fn-sensorial', 'Sensorial' from dual union all
  select 'fn-comunicacion', 'Comunicación' from dual
) s on (f.function_id = s.id)
when not matched then insert (function_id, label, is_seed) values (s.id, s.label, true);

merge into usb_widgets w
using (
  select 'w-escala' id, 'Escala de ansiedad (1–10)' name, 'NUMERIC' kind, '/10' unit, 1 min_v, 10 max_v,
         cast(null as varchar2(400)) opts,
         'Intensidad subjetiva de la conducta o emoción en una escala de 1 a 10.' descr from dual union all
  select 'w-frecuencia', 'Frecuencia de la conducta', 'NUMERIC', 'episodios', 0, null, null,
         'Cuántas veces ocurrió la conducta en el período observado.' from dual union all
  select 'w-duracion', 'Duración de la conducta', 'NUMERIC', 'minutos', 0, null, null,
         'Cuánto duró cada ocurrencia de la conducta, en minutos.' from dual union all
  select 'w-cumplimiento', 'Cumplimiento de la estrategia', 'NUMERIC', '%', 0, 100, null,
         'Porcentaje de realización de la estrategia terapéutica indicada.' from dual union all
  select 'w-si-no', 'Ocurrencia (sí/no)', 'YES_NO', null, null, null, null,
         '¿Ocurrió la conducta en el período observado?' from dual union all
  select 'w-seleccion', 'Síntomas o emociones presentes', 'MULTI_SELECT', null, null, null,
         '["Ansiedad","Tristeza","Irritabilidad","Evitación","Insomnio","Otro"]',
         'Selecciona todos los síntomas o emociones presentes.' from dual union all
  select 'w-texto', 'Observación cualitativa', 'TEXT', null, null, null, null,
         'Campo abierto para observaciones del consultante.' from dual
) s on (w.widget_id = s.id)
when not matched then insert (widget_id, name, value_kind, unit, min_value, max_value, options, description, is_preset)
values (s.id, s.name, s.kind, s.unit, s.min_v, s.max_v, json(s.opts), s.descr, true);

merge into usb_cie10 c
using (
  select 'F41.1' code, 'Trastorno de ansiedad generalizada' label from dual union all
  select 'F41.0', 'Trastorno de pánico' from dual union all
  select 'F40.1', 'Fobia social' from dual union all
  select 'F43.1', 'Trastorno de estrés postraumático' from dual union all
  select 'F32.9', 'Episodio depresivo, no especificado' from dual union all
  select 'F51.0', 'Insomnio no orgánico' from dual union all
  select 'F90.0', 'Trastorno de la actividad y la atención' from dual union all
  select 'F84.0', 'Autismo infantil' from dual
) s on (c.code = s.code)
when not matched then insert (code, label) values (s.code, s.label);

commit;
prompt Tablas del dominio y catálogos listos.
exit
