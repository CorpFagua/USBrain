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
