-- =============================================================================
-- USBrain · 80_apex_workspace.sql  (SYS, desarrollo)
-- Crea el workspace APEX "USBRAIN" sobre el esquema USBRAIN y un usuario
-- desarrollador para ver y editar la base desde el navegador:
--   http://localhost:8080/ords/apex   → Workspace USBRAIN
-- Variables: &apex_user, &apex_pwd, &apex_email. Idempotente.
-- =============================================================================
whenever sqlerror exit failure rollback
set define on verify off feedback off

declare
  l_workspace_id number;
begin
  begin
    l_workspace_id := apex_util.find_security_group_id(p_workspace => 'USBRAIN');
  exception
    when others then l_workspace_id := null;
  end;
  if l_workspace_id is null or l_workspace_id = 0 then
    apex_instance_admin.add_workspace(p_workspace => 'USBRAIN', p_primary_schema => 'USBRAIN');
    l_workspace_id := apex_util.find_security_group_id(p_workspace => 'USBRAIN');
  end if;

  apex_util.set_security_group_id(l_workspace_id);
  if apex_util.get_user_id('&apex_user') is null then
    apex_util.create_user(
      p_user_name                    => '&apex_user',
      p_email_address                => '&apex_email',
      p_web_password                 => '&apex_pwd',
      p_developer_privs              => 'ADMIN:CREATE:DATA_LOADER:EDIT:HELP:MONITOR:SQL',
      p_default_schema               => 'USBRAIN',
      p_change_password_on_first_use => 'N');
  end if;
  -- Si el usuario ya existe no se toca: APEX obliga a cambiar la contraseña en el
  -- primer ingreso y la nueva solo la conoce el desarrollador.
  commit;
end;
/

prompt Workspace APEX USBRAIN listo (usuario &apex_user).
exit
