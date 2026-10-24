-- =============================================================================
-- USBrain · 02_usb_crypto.sql
-- Primitivas criptográficas sobre DBMS_CRYPTO:
--   · PBKDF2-HMAC-SHA256 (RFC 8018) para contraseñas
--   · TOTP (RFC 6238, HMAC-SHA1, 6 dígitos, 30 s) para el segundo factor
--   · Base32 (RFC 4648) para el secreto que se escanea con el QR
--   · Tokens opacos aleatorios y su hash SHA-256
--   · AES-256-CBC con IV aleatorio para cifrar secretos en reposo
-- =============================================================================
whenever sqlerror exit failure rollback
set define off feedback off

create or replace package usb_crypto authid definer as

  function random_bytes(p_length pls_integer) return raw;

  function sha256(p_text varchar2) return raw;

  -- HMAC-SHA256 con la llave PEPPER: para valores de baja entropía (códigos de recuperación).
  function peppered_hash(p_text varchar2) return raw;

  -- Token opaco: prefijo legible + 256 bits aleatorios en base64url.
  function new_token(p_prefix varchar2) return varchar2;

  function base64url(p_raw raw) return varchar2;
  function base32_encode(p_raw raw) return varchar2;
  function base32_decode(p_text varchar2) return raw;

  function pbkdf2_sha256(p_password varchar2, p_salt raw, p_iterations pls_integer) return raw;

  function equals(p_a raw, p_b raw) return boolean;

  -- Paso TOTP actual: floor(segundos Unix UTC / 30).
  function current_step return number;
  function totp(p_secret raw, p_step number) return varchar2;

  function encrypt(p_plain raw) return raw;
  function decrypt(p_cipher raw) return raw;

end usb_crypto;
/

create or replace package body usb_crypto as

  c_b32_alphabet constant varchar2(32) := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  c_aes          constant pls_integer  := sys.dbms_crypto.encrypt_aes256
                                         + sys.dbms_crypto.chain_cbc
                                         + sys.dbms_crypto.pad_pkcs5;

  function app_key(p_name varchar2) return raw is
    l_key raw(32);
  begin
    select key_value into l_key from usb_app_keys where key_name = p_name;
    return l_key;
  end app_key;

  function random_bytes(p_length pls_integer) return raw is
  begin
    return sys.dbms_crypto.randombytes(p_length);
  end random_bytes;

  function sha256(p_text varchar2) return raw is
  begin
    return sys.dbms_crypto.hash(utl_i18n.string_to_raw(p_text, 'AL32UTF8'),
                                sys.dbms_crypto.hash_sh256);
  end sha256;

  function peppered_hash(p_text varchar2) return raw is
  begin
    return sys.dbms_crypto.mac(utl_i18n.string_to_raw(p_text, 'AL32UTF8'),
                               sys.dbms_crypto.hmac_sh256, app_key('PEPPER'));
  end peppered_hash;

  function base64url(p_raw raw) return varchar2 is
    l_b64 varchar2(32767);
  begin
    l_b64 := utl_raw.cast_to_varchar2(utl_encode.base64_encode(p_raw));
    l_b64 := replace(replace(l_b64, chr(13)), chr(10));
    return translate(rtrim(l_b64, '='), '+/', '-_');
  end base64url;

  function new_token(p_prefix varchar2) return varchar2 is
  begin
    return p_prefix || base64url(random_bytes(32));
  end new_token;

  function base32_encode(p_raw raw) return varchar2 is
    l_hex    varchar2(32767) := rawtohex(p_raw);
    l_buffer pls_integer := 0;
    l_bits   pls_integer := 0;
    l_out    varchar2(32767);
  begin
    for i in 1 .. length(l_hex) / 2 loop
      l_buffer := l_buffer * 256 + to_number(substr(l_hex, 2 * i - 1, 2), 'XX');
      l_bits   := l_bits + 8;
      while l_bits >= 5 loop
        l_out    := l_out || substr(c_b32_alphabet, trunc(l_buffer / power(2, l_bits - 5)) + 1, 1);
        l_buffer := mod(l_buffer, power(2, l_bits - 5));
        l_bits   := l_bits - 5;
      end loop;
    end loop;
    if l_bits > 0 then
      l_out := l_out || substr(c_b32_alphabet, l_buffer * power(2, 5 - l_bits) + 1, 1);
    end if;
    return l_out;
  end base32_encode;

  function base32_decode(p_text varchar2) return raw is
    l_text   varchar2(32767) := upper(replace(replace(rtrim(p_text, '='), ' '), '-'));
    l_buffer pls_integer := 0;
    l_bits   pls_integer := 0;
    l_value  pls_integer;
    l_hex    varchar2(32767);
  begin
    for i in 1 .. nvl(length(l_text), 0) loop
      l_value := instr(c_b32_alphabet, substr(l_text, i, 1)) - 1;
      if l_value < 0 then
        raise_application_error(-20000, 'Carácter base32 inválido');
      end if;
      l_buffer := l_buffer * 32 + l_value;
      l_bits   := l_bits + 5;
      if l_bits >= 8 then
        l_hex    := l_hex || to_char(trunc(l_buffer / power(2, l_bits - 8)), 'FM0X');
        l_buffer := mod(l_buffer, power(2, l_bits - 8));
        l_bits   := l_bits - 8;
      end if;
    end loop;
    return hextoraw(l_hex);
  end base32_decode;

  function pbkdf2_sha256(p_password varchar2, p_salt raw, p_iterations pls_integer) return raw is
    l_key raw(2000) := utl_i18n.string_to_raw(p_password, 'AL32UTF8');
    l_u   raw(32);
    l_t   raw(32);
  begin
    -- Un solo bloque (dkLen = 32 = salida de SHA-256): T1 = U1 xor U2 xor … xor Uc
    l_u := sys.dbms_crypto.mac(utl_raw.concat(p_salt, hextoraw('00000001')),
                               sys.dbms_crypto.hmac_sh256, l_key);
    l_t := l_u;
    for i in 2 .. p_iterations loop
      l_u := sys.dbms_crypto.mac(l_u, sys.dbms_crypto.hmac_sh256, l_key);
      l_t := utl_raw.bit_xor(l_t, l_u);
    end loop;
    return l_t;
  end pbkdf2_sha256;

  function equals(p_a raw, p_b raw) return boolean is
    l_diff raw(2000);
  begin
    if p_a is null or p_b is null or utl_raw.length(p_a) != utl_raw.length(p_b) then
      return false;
    end if;
    -- Compara todos los bytes siempre, sin cortocircuito.
    l_diff := utl_raw.bit_xor(p_a, p_b);
    return l_diff = utl_raw.copies(hextoraw('00'), utl_raw.length(p_a));
  end equals;

  function current_step return number is
  begin
    return floor(round((cast(sys_extract_utc(systimestamp) as date) - date '1970-01-01') * 86400) / 30);
  end current_step;

  function totp(p_secret raw, p_step number) return varchar2 is
    l_counter raw(8);
    l_mac     raw(20);
    l_offset  pls_integer;
    l_binary  number;
  begin
    l_counter := hextoraw(lpad(trim(to_char(p_step, 'XXXXXXXXXXXXXXXX')), 16, '0'));
    l_mac     := sys.dbms_crypto.mac(l_counter, sys.dbms_crypto.hmac_sh1, p_secret);
    -- Truncamiento dinámico (RFC 4226 §5.3).
    l_offset  := mod(to_number(rawtohex(utl_raw.substr(l_mac, 20, 1)), 'XX'), 16);
    l_binary  := mod(to_number(rawtohex(utl_raw.substr(l_mac, l_offset + 1, 4)), 'XXXXXXXX'),
                     2147483648);
    return lpad(to_char(mod(l_binary, 1000000)), 6, '0');
  end totp;

  function encrypt(p_plain raw) return raw is
    l_iv raw(16) := random_bytes(16);
  begin
    return utl_raw.concat(l_iv, sys.dbms_crypto.encrypt(p_plain, c_aes, app_key('TOTP_ENC'), l_iv));
  end encrypt;

  function decrypt(p_cipher raw) return raw is
  begin
    return sys.dbms_crypto.decrypt(utl_raw.substr(p_cipher, 17), c_aes, app_key('TOTP_ENC'),
                                   utl_raw.substr(p_cipher, 1, 16));
  end decrypt;

end usb_crypto;
/

show errors package body usb_crypto
-- Un paquete inválido detiene la instalación.
begin
  for e in (select 1 from user_errors where name = 'USB_CRYPTO' and rownum = 1) loop
    raise_application_error(-20000, 'USB_CRYPTO no compiló; revisa los errores de arriba.');
  end loop;
end;
/
prompt Paquete USB_CRYPTO listo.
exit
