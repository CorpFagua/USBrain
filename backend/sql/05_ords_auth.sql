-- =============================================================================
-- USBrain · 05_ords_auth.sql
-- Publica la API de autenticación en ORDS:
--   http://<host>:8080/ords/usbrain/v1/auth/...
-- Los handlers solo delegan en usb_api; toda la lógica vive en los paquetes.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

declare
  c_module constant varchar2(30) := 'usbrain.auth.v1';

  procedure endpoint(p_pattern varchar2, p_method varchar2, p_source varchar2,
                     p_needs_auth boolean default false) is
  begin
    ords.define_template(p_module_name => c_module, p_pattern => p_pattern);
    ords.define_handler(p_module_name => c_module, p_pattern => p_pattern, p_method => p_method,
                        p_source_type => ords.source_type_plsql, p_source => p_source);
    if p_needs_auth then
      ords.define_parameter(p_module_name => c_module, p_pattern => p_pattern, p_method => p_method,
                            p_name => 'Authorization', p_bind_variable_name => 'authorization',
                            p_source_type => 'HEADER', p_param_type => 'STRING', p_access_method => 'IN');
    end if;
  end endpoint;
begin
  -- define_module reemplaza el módulo completo: este script es idempotente.
  ords.define_module(p_module_name => c_module, p_base_path => '/v1/auth/',
                     p_items_per_page => 0, p_status => 'PUBLISHED',
                     p_comments => 'Login con contraseña + TOTP, sesiones persistentes por dispositivo.');

  endpoint('login',        'POST',   'begin usb_api.login(:body_text); end;');
  endpoint('mfa/verify',   'POST',   'begin usb_api.verify_mfa(:body_text); end;');
  endpoint('mfa/recovery', 'POST',   'begin usb_api.verify_recovery_code(:body_text); end;');
  endpoint('refresh',      'POST',   'begin usb_api.refresh(:body_text); end;');
  endpoint('logout',       'POST',   'begin usb_api.logout(:authorization); end;', true);
  endpoint('logout-all',   'POST',   'begin usb_api.logout_all(:authorization); end;', true);
  endpoint('me',           'GET',    'begin usb_api.me(:authorization); end;', true);
  endpoint('sessions',     'GET',    'begin usb_api.list_sessions(:authorization); end;', true);
  endpoint('sessions/:id', 'DELETE', 'begin usb_api.revoke_session(:authorization, :id); end;', true);
  endpoint('password',     'POST',   'begin usb_api.change_password(:authorization, :body_text); end;', true);
  endpoint('password/forgot', 'POST', 'begin usb_api.forgot_password(:body_text); end;');
  endpoint('password/reset',  'POST', 'begin usb_api.reset_password(:body_text); end;');

  -- CORS: orígenes web que pueden llamar a la API desde el navegador:
  --   http://localhost:8081                     servidor de desarrollo (Kotlin/Wasm)
  --   https://usbrainwebprototipo.onrender.com  frontend de stage en Render (ver STAGE.md)
  ords.set_module_origins_allowed(p_module_name => c_module,
    p_origins_allowed => 'http://localhost:8081,https://usbrainwebprototipo.onrender.com');

  commit;
end;
/

prompt Módulo ORDS usbrain.auth.v1 publicado en /ords/usbrain/v1/auth/.
exit
