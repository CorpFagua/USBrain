-- =============================================================================
-- USBrain · 12_ords_admin.sql
-- Publica la administración: http://<host>:8080/ords/usbrain/v1/admin/...
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

declare
  c_module constant varchar2(30) := 'usbrain.admin.v1';

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
  ords.define_module(p_module_name => c_module, p_base_path => '/v1/admin/',
                     p_items_per_page => 0, p_status => 'PUBLISHED',
                     p_comments => 'Usuarios, roles y permisos (roles dinámicos).');

  endpoint('overview',          'GET',    'begin usb_admin_api.overview(:authorization); end;');
  endpoint('analytics',         'GET',    'begin usb_admin_api.analytics(:authorization); end;');
  endpoint('users',             'POST',   'begin usb_admin_api.create_user(:authorization, :body_text); end;');
  endpoint('users/:id',         'POST',   'begin usb_admin_api.update_user(:authorization, :id, :body_text); end;');
  endpoint('users/:id/actions', 'POST',   'begin usb_admin_api.user_action(:authorization, :id, :body_text); end;');
  endpoint('roles',             'POST',   'begin usb_admin_api.create_role(:authorization, :body_text); end;');
  endpoint('roles/:id',         'POST',   'begin usb_admin_api.update_role(:authorization, :id, :body_text); end;');
  endpoint('roles/:id',         'DELETE', 'begin usb_admin_api.delete_role(:authorization, :id); end;');

  -- CORS: orígenes web que pueden llamar a la API desde el navegador:
  --   http://localhost:8081                     servidor de desarrollo (Kotlin/Wasm)
  --   https://usbrainwebprototipo.onrender.com  frontend de stage en Render (ver STAGE.md)
  ords.set_module_origins_allowed(p_module_name => c_module,
    p_origins_allowed => 'http://localhost:8081,https://usbrainwebprototipo.onrender.com');

  commit;
end;
/

prompt Módulo ORDS usbrain.admin.v1 publicado en /ords/usbrain/v1/admin/.
exit
