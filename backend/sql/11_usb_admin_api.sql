-- =============================================================================
-- USBrain · 11_usb_admin_api.sql
-- Adaptador HTTP de la administración (/v1/admin/...). Todas las rutas exigen
-- Bearer; los permisos los revisa usb_admin.
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

create or replace package usb_admin_api authid definer as

  procedure overview(p_authorization varchar2);
  procedure create_user(p_authorization varchar2, p_body clob);
  procedure update_user(p_authorization varchar2, p_user_id varchar2, p_body clob);
  procedure user_action(p_authorization varchar2, p_user_id varchar2, p_body clob);
  procedure set_user_permissions(p_authorization varchar2, p_user_id varchar2, p_body clob);
  procedure create_role(p_authorization varchar2, p_body clob);
  procedure update_role(p_authorization varchar2, p_role_id varchar2, p_body clob);
  procedure delete_role(p_authorization varchar2, p_role_id varchar2);

end usb_admin_api;
/

create or replace package body usb_admin_api as

  procedure respond_ok(p_id varchar2 default null) is
    l_body json_object_t := json_object_t();
  begin
    l_body.put('ok', true);
    if p_id is not null then
      l_body.put('id', p_id);
    end if;
    usb_api.respond_ok(l_body);
  end respond_ok;

  function user_id(p_value varchar2) return number is
    l_id number := usb_domain.to_id(p_value);
  begin
    if l_id is null then
      raise_application_error(usb_auth.e_not_found, 'not_found|El usuario no existe.');
    end if;
    return l_id;
  end user_id;

  procedure overview(p_authorization varchar2) is
  begin
    usb_api.start_authenticated(p_authorization);
    usb_api.respond_ok_clob(usb_admin.overview);
  exception
    when others then usb_api.handle_error('GET /admin/overview');
  end overview;

  procedure create_user(p_authorization varchar2, p_body clob) is
  begin
    usb_api.start_authenticated(p_authorization);
    respond_ok(to_char(usb_admin.create_user(usb_api.parse(p_body))));
  exception
    when others then usb_api.handle_error('POST /admin/users');
  end create_user;

  procedure update_user(p_authorization varchar2, p_user_id varchar2, p_body clob) is
  begin
    usb_api.start_authenticated(p_authorization);
    usb_admin.update_user(user_id(p_user_id), usb_api.parse(p_body));
    respond_ok;
  exception
    when others then usb_api.handle_error('POST /admin/users/:id');
  end update_user;

  procedure user_action(p_authorization varchar2, p_user_id varchar2, p_body clob) is
    l_req json_object_t;
  begin
    usb_api.start_authenticated(p_authorization);
    l_req := usb_api.parse(p_body);
    usb_admin.user_action(user_id(p_user_id), l_req.get_string('action'), l_req.get_string('password'));
    respond_ok;
  exception
    when others then usb_api.handle_error('POST /admin/users/:id/actions');
  end user_action;

  procedure set_user_permissions(p_authorization varchar2, p_user_id varchar2, p_body clob) is
  begin
    usb_api.start_authenticated(p_authorization);
    usb_admin.set_user_permissions(user_id(p_user_id), usb_api.parse(p_body));
    respond_ok;
  exception
    when others then usb_api.handle_error('POST /admin/users/:id/permissions');
  end set_user_permissions;

  procedure create_role(p_authorization varchar2, p_body clob) is
  begin
    usb_api.start_authenticated(p_authorization);
    usb_admin.create_role(usb_api.parse(p_body));
    respond_ok;
  exception
    when others then usb_api.handle_error('POST /admin/roles');
  end create_role;

  procedure update_role(p_authorization varchar2, p_role_id varchar2, p_body clob) is
  begin
    usb_api.start_authenticated(p_authorization);
    usb_admin.update_role(upper(p_role_id), usb_api.parse(p_body));
    respond_ok;
  exception
    when others then usb_api.handle_error('POST /admin/roles/:id');
  end update_role;

  procedure delete_role(p_authorization varchar2, p_role_id varchar2) is
  begin
    usb_api.start_authenticated(p_authorization);
    usb_admin.delete_role(upper(p_role_id));
    respond_ok;
  exception
    when others then usb_api.handle_error('DELETE /admin/roles/:id');
  end delete_role;

end usb_admin_api;
/

show errors package body usb_admin_api
begin
  for e in (select 1 from user_errors where name = 'USB_ADMIN_API' and rownum = 1) loop
    raise_application_error(-20000, 'USB_ADMIN_API no compiló; revisa los errores de arriba.');
  end loop;
end;
/

prompt Paquete USB_ADMIN_API listo.
exit
