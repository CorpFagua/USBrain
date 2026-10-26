-- =============================================================================
-- USBrain · 04_usb_api.sql
-- Adaptador HTTP entre ORDS y usb_auth: lee el JSON del cuerpo, llama la lógica,
-- escribe la respuesta JSON y traduce errores a códigos HTTP.
--   · Errores esperados (-204xx): commit (persisten contadores, bloqueos, bitácora)
--     y respuesta {"error": slug, "message": texto}.
--   · Errores inesperados: rollback, se registran en usb_error_log y el cliente
--     solo ve un 500 genérico.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

create or replace package usb_api authid definer as

  procedure login(p_body clob);
  procedure verify_mfa(p_body clob);
  procedure verify_recovery_code(p_body clob);
  procedure refresh(p_body clob);
  procedure forgot_password(p_body clob);
  procedure reset_password(p_body clob);

  procedure logout(p_authorization varchar2);
  procedure logout_all(p_authorization varchar2);
  procedure me(p_authorization varchar2);
  procedure list_sessions(p_authorization varchar2);
  procedure revoke_session(p_authorization varchar2, p_session_id varchar2);
  procedure change_password(p_authorization varchar2, p_body clob);

  -- Utilidades compartidas con los demás adaptadores HTTP (usb_app_api).
  procedure respond(p_status pls_integer, p_body clob);
  procedure respond_ok(p_json json_element_t);
  procedure respond_ok_clob(p_body clob);
  procedure handle_error(p_endpoint varchar2);
  procedure start_authenticated(p_authorization varchar2);
  function parse(p_body clob) return json_object_t;

end usb_api;
/

create or replace package body usb_api as

  procedure respond(p_status pls_integer, p_body clob) is
    l_offset pls_integer := 1;
    l_length pls_integer := nvl(dbms_lob.getlength(p_body), 0);
  begin
    owa_util.status_line(p_status, null, false);
    owa_util.mime_header('application/json', false, 'UTF-8');
    -- Las respuestas llevan tokens: que ningún proxy ni caché las guarde.
    htp.p('Cache-Control: no-store');
    htp.p('Pragma: no-cache');
    -- RFC 9110: toda respuesta 401 debe indicar el esquema de autenticación. Sin este
    -- encabezado, algunos clientes HTTP (HttpURLConnection) descartan el cuerpo del error.
    if p_status = 401 then
      htp.p('WWW-Authenticate: Bearer realm="usbrain"');
    end if;
    owa_util.http_header_close;
    while l_offset <= l_length loop
      htp.prn(dbms_lob.substr(p_body, 8000, l_offset));
      l_offset := l_offset + 8000;
    end loop;
  end respond;

  procedure respond_ok(p_json json_element_t) is
  begin
    commit;
    respond(200, p_json.to_clob);
  end respond_ok;

  procedure respond_ok_clob(p_body clob) is
  begin
    commit;
    respond(200, p_body);
  end respond_ok_clob;

  procedure respond_no_content is
  begin
    commit;
    respond(200, '{"ok":true}');
  end respond_no_content;

  procedure log_error(p_endpoint varchar2, p_message varchar2, p_backtrace varchar2) is
    pragma autonomous_transaction;
  begin
    insert into usb_error_log (endpoint, message, backtrace)
    values (p_endpoint, substr(p_message, 1, 4000), substr(p_backtrace, 1, 4000));
    commit;
  end log_error;

  procedure handle_error(p_endpoint varchar2) is
    l_code      pls_integer    := sqlcode;
    l_message   varchar2(4000) := regexp_replace(sqlerrm, '^ORA-[0-9]+: ');
    l_backtrace varchar2(4000) := dbms_utility.format_error_backtrace;
    l_status    pls_integer;
    l_body      json_object_t  := json_object_t();
  begin
    l_status := case l_code
                  when usb_auth.e_bad_request  then 400
                  when usb_auth.e_unauthorized then 401
                  when usb_auth.e_forbidden    then 403
                  when usb_auth.e_not_found    then 404
                  when usb_auth.e_conflict     then 409
                  when usb_auth.e_locked       then 423
                end;
    if l_status is not null and instr(l_message, '|') > 0 then
      commit;
      l_body.put('error', substr(l_message, 1, instr(l_message, '|') - 1));
      l_body.put('message', substr(l_message, instr(l_message, '|') + 1));
    else
      rollback;
      log_error(p_endpoint, sqlerrm, l_backtrace);
      l_status := 500;
      l_body.put('error', 'internal_error');
      l_body.put('message', 'Ocurrió un error inesperado. Intenta de nuevo.');
    end if;
    respond(l_status, l_body.to_clob);
  end handle_error;

  procedure start_request is
  begin
    usb_auth.begin_request(owa_util.get_cgi_env('REMOTE_ADDR'),
                           owa_util.get_cgi_env('HTTP_USER_AGENT'));
  end start_request;

  procedure start_authenticated(p_authorization varchar2) is
  begin
    start_request;
    usb_auth.authenticate(p_authorization);
  end start_authenticated;

  function parse(p_body clob) return json_object_t is
  begin
    if p_body is null or dbms_lob.getlength(p_body) = 0 then
      return json_object_t();
    end if;
    return json_object_t.parse(p_body);
  exception
    when others then
      raise_application_error(usb_auth.e_bad_request, 'invalid_json|El cuerpo de la petición no es JSON válido.');
  end parse;

  -- ---------------------------------------------------------------------------

  procedure login(p_body clob) is
    l_req json_object_t;
  begin
    start_request;
    l_req := parse(p_body);
    respond_ok(usb_auth.login(l_req.get_string('email'), l_req.get_string('password'),
                              l_req.get_string('device_id'), l_req.get_string('device_name')));
  exception
    when others then handle_error('POST /auth/login');
  end login;

  procedure verify_mfa(p_body clob) is
    l_req json_object_t;
  begin
    start_request;
    l_req := parse(p_body);
    respond_ok(usb_auth.verify_mfa(l_req.get_string('challenge_id'), l_req.get_string('code')));
  exception
    when others then handle_error('POST /auth/mfa/verify');
  end verify_mfa;

  procedure verify_recovery_code(p_body clob) is
    l_req json_object_t;
  begin
    start_request;
    l_req := parse(p_body);
    respond_ok(usb_auth.verify_recovery_code(l_req.get_string('challenge_id'),
                                             l_req.get_string('recovery_code')));
  exception
    when others then handle_error('POST /auth/mfa/recovery');
  end verify_recovery_code;

  procedure refresh(p_body clob) is
    l_req json_object_t;
  begin
    start_request;
    l_req := parse(p_body);
    respond_ok(usb_auth.refresh(l_req.get_string('refresh_token'), l_req.get_string('device_id')));
  exception
    when others then handle_error('POST /auth/refresh');
  end refresh;

  procedure forgot_password(p_body clob) is
    l_req json_object_t;
  begin
    start_request;
    l_req := parse(p_body);
    respond_ok(usb_auth.forgot_password(l_req.get_string('email'), l_req.get_string('device_id'),
                                        l_req.get_string('device_name')));
  exception
    when others then handle_error('POST /auth/password/forgot');
  end forgot_password;

  procedure reset_password(p_body clob) is
    l_req json_object_t;
  begin
    start_request;
    l_req := parse(p_body);
    usb_auth.reset_password(l_req.get_string('reset_id'), l_req.get_string('code'),
                            l_req.get_string('recovery_code'), l_req.get_string('new_password'));
    respond_no_content;
  exception
    when others then handle_error('POST /auth/password/reset');
  end reset_password;

  procedure logout(p_authorization varchar2) is
  begin
    start_authenticated(p_authorization);
    usb_auth.logout;
    respond_no_content;
  exception
    when others then handle_error('POST /auth/logout');
  end logout;

  procedure logout_all(p_authorization varchar2) is
  begin
    start_authenticated(p_authorization);
    usb_auth.logout_all;
    respond_no_content;
  exception
    when others then handle_error('POST /auth/logout-all');
  end logout_all;

  procedure me(p_authorization varchar2) is
  begin
    start_authenticated(p_authorization);
    respond_ok(usb_auth.me);
  exception
    when others then handle_error('GET /auth/me');
  end me;

  procedure list_sessions(p_authorization varchar2) is
    l_body json_object_t := json_object_t();
  begin
    start_authenticated(p_authorization);
    l_body.put('items', usb_auth.list_sessions);
    respond_ok(l_body);
  exception
    when others then handle_error('GET /auth/sessions');
  end list_sessions;

  procedure revoke_session(p_authorization varchar2, p_session_id varchar2) is
  begin
    start_authenticated(p_authorization);
    if not regexp_like(p_session_id, '^[0-9]{1,15}$') then
      raise_application_error(usb_auth.e_not_found, 'session_not_found|La sesión no existe o ya fue cerrada.');
    end if;
    usb_auth.revoke_session(to_number(p_session_id));
    respond_no_content;
  exception
    when others then handle_error('DELETE /auth/sessions/:id');
  end revoke_session;

  procedure change_password(p_authorization varchar2, p_body clob) is
    l_req json_object_t;
  begin
    start_authenticated(p_authorization);
    l_req := parse(p_body);
    usb_auth.change_password(l_req.get_string('current_password'), l_req.get_string('new_password'),
                             l_req.get_string('code'));
    respond_no_content;
  exception
    when others then handle_error('POST /auth/password');
  end change_password;

end usb_api;
/

show errors package body usb_api
-- Un paquete inválido detiene la instalación.
begin
  for e in (select 1 from user_errors where name = 'USB_API' and rownum = 1) loop
    raise_application_error(-20000, 'USB_API no compiló; revisa los errores de arriba.');
  end loop;
end;
/
prompt Paquete USB_API listo.
exit
