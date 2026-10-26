-- =============================================================================
-- USBrain · 00_schema.sql
-- Se ejecuta como SYS en FREEPDB1. Crea el esquema de la aplicación, le da los
-- privilegios mínimos y lo habilita en ORDS bajo /ords/usbrain/.
-- Variables de sustitución: &app_pwd (contraseña del esquema USBRAIN).
-- Idempotente: se puede volver a correr sin perder datos.
-- =============================================================================
whenever sqlerror exit failure rollback
set define on verify off feedback off serveroutput on

create user if not exists usbrain identified by "&app_pwd"
  default tablespace users quota unlimited on users;

alter user usbrain identified by "&app_pwd";

grant create session, create table, create view, create sequence,
      create procedure, create type, create job to usbrain;

-- Criptografía: HMAC, SHA-256, AES y bytes aleatorios seguros.
grant execute on sys.dbms_crypto to usbrain;

begin
  ords_admin.enable_schema(
    p_enabled             => true,
    p_schema              => 'USBRAIN',
    p_url_mapping_type    => 'BASE_PATH',
    p_url_mapping_pattern => 'usbrain',
    -- AutoREST queda protegido: nada de tablas expuestas sin pasar por los módulos.
    p_auto_rest_auth      => true);
  commit;
end;
/

prompt Esquema USBRAIN listo y habilitado en ORDS (/ords/usbrain/).
exit
