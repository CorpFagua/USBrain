-- =============================================================================
-- USBrain · 08_usb_app_api.sql
-- Adaptador HTTP de la API clínica (/v1/app/...). Todas las rutas exigen
-- "Authorization: Bearer usb_at_…". Las acciones devuelven {"id": "…"}; la app
-- vuelve a pedir el bootstrap para quedar sincronizada.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

create or replace package usb_app_api authid definer as

  procedure bootstrap(p_authorization varchar2);
  procedure create_behavior(p_authorization varchar2, p_body clob);
  procedure configure_baseline(p_authorization varchar2, p_behavior_id varchar2, p_body clob);
  procedure close_baseline(p_authorization varchar2, p_behavior_id varchar2);
  procedure create_task(p_authorization varchar2, p_body clob);
  procedure record_entry(p_authorization varchar2, p_task_id varchar2, p_body clob);
  procedure record_widget_entry(p_authorization varchar2, p_task_id varchar2, p_body clob);
  procedure create_widget(p_authorization varchar2, p_body clob);
  procedure create_function(p_authorization varchar2, p_body clob);
  procedure assign_diagnosis(p_authorization varchar2, p_patient_id varchar2, p_body clob);
  procedure remove_diagnosis(p_authorization varchar2, p_patient_id varchar2, p_code varchar2);

end usb_app_api;
/

create or replace package body usb_app_api as

  procedure respond_id(p_id varchar2) is
    l_body json_object_t := json_object_t();
  begin
    l_body.put('id', p_id);
    usb_api.respond_ok(l_body);
  end respond_id;

  procedure respond_ok is
    l_body json_object_t := json_object_t();
  begin
    l_body.put('ok', true);
    usb_api.respond_ok(l_body);
  end respond_ok;

  function required_id(p_value varchar2) return number is
    l_id number := usb_domain.to_id(p_value);
  begin
    if l_id is null then
      raise_application_error(usb_auth.e_not_found, 'not_found|El recurso no existe.');
    end if;
    return l_id;
  end required_id;

  -- ---------------------------------------------------------------------------

  procedure bootstrap(p_authorization varchar2) is
  begin
    usb_api.start_authenticated(p_authorization);
    usb_api.respond_ok_clob(usb_domain.bootstrap);
  exception
    when others then usb_api.handle_error('GET /app/bootstrap');
  end bootstrap;

  procedure create_behavior(p_authorization varchar2, p_body clob) is
    l_req json_object_t;
  begin
    usb_api.start_authenticated(p_authorization);
    l_req := usb_api.parse(p_body);
    respond_id(to_char(usb_domain.create_behavior(
      p_patient_id     => required_id(l_req.get_string('patient_id')),
      p_name           => l_req.get_string('name'),
      p_definition     => l_req.get_string('operational_definition'),
      p_type           => l_req.get_string('type'),
      p_function_id    => l_req.get_string('function_id'),
      p_replacement_id => usb_domain.to_id(l_req.get_string('replacement_behavior_id')))));
  exception
    when others then usb_api.handle_error('POST /app/behaviors');
  end create_behavior;

  procedure configure_baseline(p_authorization varchar2, p_behavior_id varchar2, p_body clob) is
    l_req json_object_t;
  begin
    usb_api.start_authenticated(p_authorization);
    l_req := usb_api.parse(p_body);
    usb_domain.configure_baseline(required_id(p_behavior_id), l_req.get_array('dimensions'));
    respond_ok;
  exception
    when others then usb_api.handle_error('POST /app/behaviors/:id/baseline');
  end configure_baseline;

  procedure close_baseline(p_authorization varchar2, p_behavior_id varchar2) is
  begin
    usb_api.start_authenticated(p_authorization);
    usb_domain.close_baseline(required_id(p_behavior_id));
    respond_ok;
  exception
    when others then usb_api.handle_error('POST /app/behaviors/:id/close-baseline');
  end close_baseline;

  procedure create_task(p_authorization varchar2, p_body clob) is
  begin
    usb_api.start_authenticated(p_authorization);
    respond_id(to_char(usb_domain.create_task(usb_api.parse(p_body))));
  exception
    when others then usb_api.handle_error('POST /app/tasks');
  end create_task;

  procedure record_entry(p_authorization varchar2, p_task_id varchar2, p_body clob) is
    l_req json_object_t;
  begin
    usb_api.start_authenticated(p_authorization);
    l_req := usb_api.parse(p_body);
    respond_id(to_char(usb_domain.record_entry(
      p_task_id             => required_id(p_task_id),
      p_date                => usb_domain.to_iso_date(l_req.get_string('date')),
      p_value               => l_req.get_number('value'),
      p_note                => l_req.get_string('note'),
      p_observation_minutes => l_req.get_number('observation_minutes'))));
  exception
    when others then usb_api.handle_error('POST /app/tasks/:id/entries');
  end record_entry;

  procedure record_widget_entry(p_authorization varchar2, p_task_id varchar2, p_body clob) is
  begin
    usb_api.start_authenticated(p_authorization);
    respond_id('we' || usb_domain.record_widget_entry(required_id(p_task_id), usb_api.parse(p_body)));
  exception
    when others then usb_api.handle_error('POST /app/tasks/:id/widget-entries');
  end record_widget_entry;

  procedure create_widget(p_authorization varchar2, p_body clob) is
  begin
    usb_api.start_authenticated(p_authorization);
    respond_id(usb_domain.create_widget(usb_api.parse(p_body)));
  exception
    when others then usb_api.handle_error('POST /app/widgets');
  end create_widget;

  procedure create_function(p_authorization varchar2, p_body clob) is
  begin
    usb_api.start_authenticated(p_authorization);
    respond_id(usb_domain.create_function(usb_api.parse(p_body).get_string('label')));
  exception
    when others then usb_api.handle_error('POST /app/behavioral-functions');
  end create_function;

  procedure assign_diagnosis(p_authorization varchar2, p_patient_id varchar2, p_body clob) is
    l_req json_object_t;
  begin
    usb_api.start_authenticated(p_authorization);
    l_req := usb_api.parse(p_body);
    usb_domain.assign_diagnosis(required_id(p_patient_id), l_req.get_string('code'), l_req.get_string('label'));
    respond_ok;
  exception
    when others then usb_api.handle_error('POST /app/patients/:id/diagnoses');
  end assign_diagnosis;

  procedure remove_diagnosis(p_authorization varchar2, p_patient_id varchar2, p_code varchar2) is
  begin
    usb_api.start_authenticated(p_authorization);
    usb_domain.remove_diagnosis(required_id(p_patient_id), p_code);
    respond_ok;
  exception
    when others then usb_api.handle_error('DELETE /app/patients/:id/diagnoses/:code');
  end remove_diagnosis;

end usb_app_api;
/

show errors package body usb_app_api
begin
  for e in (select 1 from user_errors where name = 'USB_APP_API' and rownum = 1) loop
    raise_application_error(-20000, 'USB_APP_API no compiló; revisa los errores de arriba.');
  end loop;
end;
/

prompt Paquete USB_APP_API listo.
exit
