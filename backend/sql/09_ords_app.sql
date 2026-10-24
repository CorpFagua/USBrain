-- =============================================================================
-- USBrain · 09_ords_app.sql
-- Publica la API clínica: http://<host>:8080/ords/usbrain/v1/app/...
-- Todas las rutas reciben el header Authorization.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

declare
  c_module constant varchar2(30) := 'usbrain.app.v1';

  -- define_template REEMPLAZA una plantilla existente (y borra sus handlers), así que
  -- cada ruta se define una sola vez aunque tenga varios métodos.
  type t_defined is table of boolean index by varchar2(200);
  l_defined t_defined;

  procedure endpoint(p_pattern varchar2, p_method varchar2, p_source varchar2) is
  begin
    if not l_defined.exists(p_pattern) then
      ords.define_template(p_module_name => c_module, p_pattern => p_pattern);
      l_defined(p_pattern) := true;
    end if;
    ords.define_handler(p_module_name => c_module, p_pattern => p_pattern, p_method => p_method,
                        p_source_type => ords.source_type_plsql, p_source => p_source);
    ords.define_parameter(p_module_name => c_module, p_pattern => p_pattern, p_method => p_method,
                          p_name => 'Authorization', p_bind_variable_name => 'authorization',
                          p_source_type => 'HEADER', p_param_type => 'STRING', p_access_method => 'IN');
  end endpoint;
begin
  ords.define_module(p_module_name => c_module, p_base_path => '/v1/app/',
                     p_items_per_page => 0, p_status => 'PUBLISHED',
                     p_comments => 'Casos, conductas, actividades y registros de diseño de caso único.');

  endpoint('bootstrap', 'GET',
    'begin usb_app_api.bootstrap(:authorization); end;');
  endpoint('behaviors', 'POST',
    'begin usb_app_api.create_behavior(:authorization, :body_text); end;');
  endpoint('behaviors/:id/baseline', 'POST',
    'begin usb_app_api.configure_baseline(:authorization, :id, :body_text); end;');
  endpoint('behaviors/:id/close-baseline', 'POST',
    'begin usb_app_api.close_baseline(:authorization, :id); end;');
  endpoint('tasks', 'POST',
    'begin usb_app_api.create_task(:authorization, :body_text); end;');
  endpoint('tasks/:id/entries', 'POST',
    'begin usb_app_api.record_entry(:authorization, :id, :body_text); end;');
  endpoint('tasks/:id/widget-entries', 'POST',
    'begin usb_app_api.record_widget_entry(:authorization, :id, :body_text); end;');
  endpoint('widgets', 'POST',
    'begin usb_app_api.create_widget(:authorization, :body_text); end;');
  endpoint('behavioral-functions', 'POST',
    'begin usb_app_api.create_function(:authorization, :body_text); end;');
  endpoint('patients/:id/diagnoses', 'POST',
    'begin usb_app_api.assign_diagnosis(:authorization, :id, :body_text); end;');
  endpoint('patients/:id/diagnoses/:code', 'DELETE',
    'begin usb_app_api.remove_diagnosis(:authorization, :id, :code); end;');

  -- CORS: solo el servidor de desarrollo de la app web (Kotlin/Wasm) puede llamar desde el navegador.
  ords.set_module_origins_allowed(p_module_name => c_module, p_origins_allowed => 'http://localhost:8081');

  commit;
end;
/

prompt Módulo ORDS usbrain.app.v1 publicado en /ords/usbrain/v1/app/.
exit
