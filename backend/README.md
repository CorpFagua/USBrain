# USBrain · Backend (Oracle + ORDS)

API REST de la app servida por **ORDS** con la lógica en **PL/SQL**, sobre el Oracle
Database Free 26ai + APEX que corre en Docker (`oracle-db` y `ords`).

- Login en dos pasos: **contraseña → código TOTP** (Google/Microsoft Authenticator, Authy…).
- **Sesión persistente por dispositivo**, como en las apps bancarias: access token corto +
  refresh token rotativo atado al dispositivo, con detección de robo.
- **API clínica** de diseño de caso único: pacientes, casos, conductas, línea base,
  actividades, registros y mediciones configurables.
- **Roles y permisos dinámicos**: el super administrador crea roles y decide sus permisos;
  el código revisa permisos, nunca nombres de rol.
- **Olvido de contraseña** verificado con la app autenticadora o un código de recuperación.
- Base URL local: `http://localhost:8080/ords/usbrain/v1`

## Instalación

Paso a paso para un equipo nuevo (con Docker o con un Oracle/APEX propio): [INSTALACION.md](../INSTALACION.md).

```bash
docker start oracle-db ords          # si no están corriendo
backend/scripts/install.sh --seed    # esquema, paquetes, API, usuarios y casos de demostración

# Pruebas (solo base de desarrollo; usan usuarios exclusivos e2e.*@usbrain.test)
python3 backend/tests/auth_e2e.py          # autenticación: 32 verificaciones, ~1 min
python3 backend/tests/app_e2e.py           # API clínica y permisos: 43 verificaciones
python3 backend/tests/admin_e2e.py         # roles, usuarios, anti-escalada, olvido de contraseña: 42
backend/tests/client_integration.sh        # la app Kotlin (PrototypeState) contra la API real
```

`install.sh` es idempotente: se vuelve a correr cada vez que cambie un `.sql`. La primera vez crea
`backend/.env` (no versionado) con la contraseña aleatoria del esquema `USBRAIN` y la de los
usuarios de prueba (`DEV_SEED_PASSWORD`). La contraseña de SYS se lee del propio contenedor.

Usuarios de demostración (`--seed`, solo desarrollo), todos con la contraseña `DEV_SEED_PASSWORD`:

Las cuentas creadas por administración y las contraseñas restablecidas por el administrador son
temporales: en el primer login se debe cambiar la contraseña antes de configurar o verificar el
2FA. Toda contraseña nueva elegida por el usuario —cambio inicial, cambio autenticado o
recuperación— debe tener exactamente 10 caracteres, incluir al menos 3 de estos grupos
(mayúsculas, minúsculas, números y símbolos) y no contener el nombre de usuario del correo.
Las contraseñas temporales asignadas por administración conservan el límite general de 10–128
caracteres.
El cambio se verifica con un desafío de un solo uso ligado al dispositivo y la contraseña
temporal; no crea una sesión hasta completar después el flujo normal de 2FA.

| Correo | Rol | Datos |
|---|---|---|
| `e.patino@usb.edu.co` | Terapeuta | Tiene asignados a Ana y Daniel |
| `ana.torres@usbbog.edu.co` | Paciente | Caso de ansiedad social: 3 conductas, línea base cerrada, registros y mediciones |
| `daniel.ruiz@usbbog.edu.co` | Paciente | Caso de insomnio con línea base abierta |
| `admin@usb.edu.co` | Administrador | Gestiona usuarios; ve todos los casos y la bitácora |
| `superadmin@usb.edu.co` | Super administrador | Todo, incluido crear y editar roles y permisos |

Ninguno tiene autenticador: el primer login muestra el QR para enrolarlo. Volver a correr
`install.sh --seed` no cambia contraseñas ni datos existentes (no cierra tus sesiones).

> Las pruebas usan sus propios usuarios (`e2e.*@usbrain.test`), así que no tocan el 2FA de los
> usuarios de demostración.

## Ver y editar la base de datos

**En el navegador (APEX):** `http://localhost:8080/ords/apex` → Workspace `USBRAIN`, usuario `dev`,
contraseña `APEX_DEV_PASSWORD` de `backend/.env`. En *SQL Workshop*: **Object Browser** (ver tablas
y datos), **SQL Commands** (ejecutar SQL), **SQL Scripts** (correr archivos). En *App Builder* se
pueden crear pantallas de administración sobre estas mismas tablas.

**Desde VS Code:** extensión *Oracle SQL Developer* → conexión con usuario `USBRAIN`, contraseña
`USBRAIN_DB_PASSWORD` de `backend/.env`, host `localhost`, puerto `1521`, servicio `FREEPDB1`.

**Regla:** lo que se crea a mano en APEX/SQL Developer es para explorar. Lo que la app necesita
(tablas, paquetes, endpoints) va en un archivo de `sql/` y se aplica con `install.sh`, para que
quede en git y se pueda reconstruir la base desde cero.

## Archivos

| Archivo | Contenido |
|---|---|
| `sql/00_schema.sql` | (SYS) Usuario `USBRAIN`, privilegios mínimos, habilitación en ORDS |
| `sql/01_tables.sql` | Usuarios, roles, permisos y excepciones por usuario (vista `usb_effective_permissions`), desafíos 2FA, sesiones, tokens, códigos de recuperación, bitácora, configuración |
| `sql/02_usb_crypto.sql` | PBKDF2-HMAC-SHA256, TOTP (RFC 6238), Base32, tokens aleatorios, AES-256 |
| `sql/03_usb_auth.sql` | Lógica: login, 2FA, enrolamiento, recuperación, refresh, sesiones, bloqueo, administración |
| `sql/04_usb_api.sql` | Adaptador HTTP: JSON ↔ PL/SQL, errores → códigos HTTP |
| `sql/05_ords_auth.sql` | Publica los endpoints en ORDS |
| `sql/06_domain_tables.sql` | Perfiles de paciente, casos, diagnósticos, conductas, actividades, registros, catálogos (funciones, mediciones, CIE-10) |
| `sql/07_usb_domain.sql` | Lógica clínica: bootstrap por rol y cada acción de las pantallas, con control de acceso |
| `sql/08_usb_app_api.sql` | Adaptador HTTP de la API clínica |
| `sql/09_ords_app.sql` | Publica `/v1/app/…` en ORDS |
| `sql/10_usb_admin.sql` | Administración: usuarios, roles, permisos y permisos individuales (`POST /v1/admin/users/:id/permissions`), con las reglas anti-escalada |
| `sql/11_usb_admin_api.sql` | Adaptador HTTP de la administración |
| `sql/12_ords_admin.sql` | Publica `/v1/admin/…` en ORDS |
| `sql/80_apex_workspace.sql` | (SYS, `--seed`) Workspace APEX `USBRAIN` con el usuario `dev` (no cambia su contraseña si ya existe) |
| `sql/90_seed_dev.sql` | Usuarios y casos de demostración (Ana y Daniel, los mismos del prototipo) |

## Flujo

```
App                                   API
 │ POST /auth/login {email,password,device_id}
 │ ────────────────────────────────▶ contraseña OK
 │ ◀──── status=password_change_required          (usuario/clave temporal)
 │ POST /auth/password/first-login {challenge_id,current_password,new_password}
 │ ◀──── status=mfa_enrollment_required + QR      (después de cambiarla)
 │ ◀──── status=mfa_required                      (cuentas ya enroladas)
 │ POST /auth/mfa/verify {challenge_id, code}
 │ ────────────────────────────────▶ TOTP OK
 │ ◀──── access_token (10 min) + refresh_token (+ 10 códigos de recuperación si enroló)
 │
 │ GET /auth/me   Authorization: Bearer <access_token>
 │ … 401 token_expired →
 │ POST /auth/refresh {refresh_token, device_id}
 │ ◀──── par nuevo (el refresh anterior queda inservible)
```

## Endpoints (`/ords/usbrain/v1/auth/…`)

| Método | Ruta | Auth | Cuerpo | Respuesta |
|---|---|---|---|---|
| POST | `login` | — | `email`, `password`, `device_id`, `device_name` | `status` (`password_change_required` \| `mfa_required` \| `mfa_enrollment_required`), `challenge_id`, `expires_in`; si enrola: `enrollment.{secret, otpauth_uri, issuer, account}` |
| POST | `password/first-login` | desafío ligado al dispositivo | `challenge_id`, `current_password`, `new_password` | Guarda la contraseña y devuelve el desafío del flujo MFA (nunca emite sesión) |
| POST | `mfa/verify` | — | `challenge_id`, `code` | Tokens + `user`; al enrolar también `recovery_codes` (10, se muestran una sola vez) |
| POST | `mfa/recovery` | — | `challenge_id`, `recovery_code` | Tokens + `user` + `recovery_codes_remaining` |
| POST | `refresh` | — | `refresh_token`, `device_id` | Tokens nuevos + `user` |
| GET | `me` | Bearer | — | Usuario + sesión actual |
| GET | `sessions` | Bearer | — | `items[]`: dispositivos con sesión (`current` marca este) |
| DELETE | `sessions/{id}` | Bearer | — | Cierra la sesión de otro dispositivo |
| POST | `password` | Bearer | `current_password`, `new_password`, `code` | Cambia contraseña (exige 2FA) y cierra los demás dispositivos |
| POST | `logout` | Bearer | — | Cierra esta sesión |
| POST | `logout-all` | Bearer | — | Cierra todas las sesiones |

Respuesta con tokens:

```json
{
  "access_token": "usb_at_…", "token_type": "Bearer", "expires_in": 600,
  "refresh_token": "usb_rt_…", "refresh_expires_at": "2026-11-16T15:00:00Z",
  "session_id": 42, "session_expires_at": "2027-01-15T15:00:00Z",
  "user": { "id": 1, "email": "…", "full_name": "…", "role": "PATIENT", "mfa_enabled": true }
}
```

Errores: siempre `{"error": "<slug>", "message": "<texto para mostrar>"}`.

| HTTP | `error` | Qué hace la app |
|---|---|---|
| 400 | `missing_fields`, `invalid_json`, `invalid_device`, `weak_password`, `mfa_not_enrolled` | Mostrar `message` |
| 401 | `invalid_credentials` | Mostrar `message` (no distingue correo inexistente de contraseña mala) |
| 401 | `invalid_code` | Pedir el código otra vez (máx. 5 por desafío) |
| 401 | `challenge_expired` | Volver a la pantalla de contraseña |
| 401 | `token_expired` | Llamar a `refresh` y reintentar |
| 401 | `session_expired`, `unauthorized` | Borrar tokens y volver al login |
| 403 | `account_disabled`, `invalid_credentials`/`invalid_code` (cambio de contraseña) | Mostrar `message` |
| 404 | `session_not_found` | — |
| 423 | `account_locked` | Mostrar `message` (incluye los minutos restantes) |
| 500 | `internal_error` | Reintentar; el detalle queda en `usb_error_log` |

## API clínica (`/ords/usbrain/v1/app/…`, todas con `Authorization: Bearer`)

| Método | Ruta | Quién | Cuerpo | Efecto |
|---|---|---|---|---|
| GET | `bootstrap` | todos | — | Usuario, `today` (hora de Bogotá), pacientes visibles con su caso completo y catálogos |
| POST | `behaviors` | terapeuta | `patient_id`, `name`, `operational_definition`, `type`, `function_id`, `replacement_behavior_id` | Crea la conducta (línea base abierta) |
| POST | `behaviors/{id}/baseline` | terapeuta | `dimensions[]` | Fija las dimensiones y crea una actividad diaria por cada una |
| POST | `behaviors/{id}/close-baseline` | terapeuta | — | Cierra la línea base; sus observaciones quedan fijas |
| POST | `tasks` | terapeuta | campos de la actividad; `widget_id` la vuelve medición configurable | Una actividad de intervención cierra la línea base sola |
| POST | `tasks/{id}/entries` | paciente | `date`, `value`, `note`, `observation_minutes` | Registro clásico (uno por día, fase y dimensión) |
| POST | `tasks/{id}/widget-entries` | paciente | `date`, `time`, `value` (`{value}` o `{values[]}`), `note` | Registro de medición configurable, validado contra el rango u opciones |
| POST | `widgets` | terapeuta | `name`, `value_kind`, `unit`, `min_value`, `max_value`, `options[]`, `description` | Medición personalizada (queda en el catálogo de todos) |
| POST | `behavioral-functions` | terapeuta | `label` | Función conductual nueva (no se duplica por nombre) |
| POST | `patients/{id}/diagnoses` | terapeuta | `code`, `label` | Asigna un CIE-10 (se normaliza a mayúsculas) |
| DELETE | `patients/{id}/diagnoses/{code}` | terapeuta | — | Quita el diagnóstico |

Las acciones devuelven `{"id": …}` o `{"ok": true}` y la app vuelve a pedir el bootstrap.
Permisos: el **paciente** ve solo su caso y es el único que registra sus actividades; el
**terapeuta** ve y edita solo los pacientes asignados (un id ajeno responde 404, como si no
existiera); el **administrador** ve todo en modo lectura. Una actividad recurrente aparece
pendiente cada día hasta que se registra ese día.

## Exportación analítica segura (`/ords/usbrain/v1/admin/analytics`)

`GET admin/analytics` requiere Bearer y el permiso sensible `analytics.view` (por defecto solo
`SUPERADMIN`). APEX y ORDS consultan el mismo esquema `USBRAIN`. La respuesta tiene dos partes:

- **`series`**: casos individuales (conductas sin `study_code`). Cada uno trae un identificador
  seudónimo (SHA-256), conducta, dimensión, unidad, dirección esperada y los totales diarios de
  **A1** y de **B1** con su día relativo y su fecha (hace falta para ordenarlos). Solo aparecen
  con al menos 3 días en cada fase.
- **`studies`**: estudios de grupo (conductas con el mismo `study_code`). Por participante solo
  dos números, sin identificador: su media diaria en A1 y en B1. Además, la serie diaria promedio
  del grupo (sin fechas) y grupos por programa y edad con n ≥ 3.

Nunca exporta nombres, correos, documentos, diagnósticos ni notas. El seudónimo es un hash del id
interno con una sal fija: sirve para no mostrar identidades, pero no es anonimización.

## Fases y parámetros clínicos

- `POST /v1/app/behaviors/:id/next-phase`: termina la fase actual y abre la siguiente
  (A1 → B1 → A2…). Exige el mínimo de días con registro por dimensión (409
  `insufficient_observations`) y retira las actividades de la fase que termina.
- `POST /v1/app/behaviors/:id/baseline`: crea el registro diario de la fase actual. Las
  dimensiones solo se eligen en A1 antes del primer registro; después se ignoran.
- `POST /v1/admin/settings` `{"min_phase_observations": 3..30}`: requiere `settings.clinical`.
  El valor vive en la tabla `usb_app_settings` (también editable desde APEX).
- Reglas del servidor: la fase de una tarea es la fase actual de la conducta (se ignora la que
  envíe el cliente); en la línea base solo se crean mediciones (409 `baseline_no_treatment`); el
  registro autónomo usa una dimensión de la línea base (400 `dimension_not_measured`); no se
  registra en una actividad de una fase terminada (409 `phase_closed`).

Alta de pacientes (SQL o APEX):

```sql
usb_domain.create_patient(email, nombre, contraseña, correo_terapeuta, fecha_nacimiento, programa, condición)
usb_domain.assign_therapist(patient_id, correo_terapeuta)
```

## Roles y permisos dinámicos

Tablas `usb_roles`, `usb_permissions` y `usb_role_permissions` (en `01_tables.sql`). Un rol es un
conjunto de permisos más la **interfaz** que ve en la app (`app_profile`: paciente, terapeuta o
administración). Por eso un rol nuevo, por ejemplo "Supervisor" con interfaz de terapeuta y
permiso de ver todos los casos, funciona sin tocar código.

| Permiso | Qué permite | Sensible |
|---|---|---|
| `case.view.own` | Ver su propio caso | |
| `case.view.assigned` | Ver pacientes asignados (lo hace elegible como terapeuta) | |
| `case.view.all` | Ver todos los casos | sí |
| `case.edit.assigned` | Editar casos de sus pacientes asignados | |
| `case.edit.all` | Editar cualquier caso | sí |
| `entry.record` | Registrar sus propias actividades | |
| `catalog.manage` | Crear mediciones y funciones conductuales | |
| `users.view` / `users.manage` | Ver / gestionar usuarios | sí |
| `roles.manage` | Crear y editar roles y permisos | sí |
| `audit.view` | Ver la bitácora de seguridad | sí |

Roles base: `SUPERADMIN` (todos los permisos, **bloqueado**), `ADMIN`, `THERAPIST`, `PATIENT`.

Reglas (en `usb_admin`, validadas antes de escribir):
1. Nadie asigna un rol con permisos **sensibles** que él no tiene (un admin no puede crear un super admin).
2. Nadie modifica a un usuario con más permisos sensibles que él.
3. Nadie cambia su propio rol ni se deshabilita.
4. Siempre queda al menos un usuario activo con `roles.manage`.
5. El rol `SUPERADMIN` no se edita ni se borra; los roles base no se borran; un rol con usuarios no se borra.
6. Cambiar el rol de un usuario cierra sus sesiones; cambiar los permisos de un rol aplica en la siguiente petición.

### API de administración (`/ords/usbrain/v1/admin/…`, Bearer)

| Método | Ruta | Permiso | Cuerpo |
|---|---|---|---|
| GET | `overview` | `users.view` o `roles.manage` | — (usuarios, roles, permisos, terapeutas y, con `audit.view`, bitácora) |
| POST | `users` | `users.manage` | `email`, `full_name`, `role`, `password` (+ `therapist_id`, `birth_date`, `program`, `condition` si es paciente) |
| POST | `users/{id}` | `users.manage` | `full_name`, `role`, `status`, `therapist_id` |
| POST | `users/{id}/actions` | `users.manage` | `action`: `unlock` \| `reset_mfa` \| `set_password` (+ `password`) |
| POST | `roles` | `roles.manage` | `id`, `name`, `description`, `profile`, `permissions[]` |
| POST | `roles/{id}` | `roles.manage` | los mismos campos, menos `id` |
| DELETE | `roles/{id}` | `roles.manage` | — |

## Olvido de contraseña

No hay servidor de correo, así que la identidad se prueba con el segundo factor:

1. `POST /auth/password/forgot {email, device_id}` → `{reset_id, expires_in, message}`. La respuesta
   es **idéntica** exista o no el correo.
2. `POST /auth/password/reset {reset_id, code | recovery_code, new_password}` → contraseña cambiada,
   contador de fallos en cero y **todas las sesiones cerradas**.

El `reset_id` (prefijo `usb_rs_`) vence en 5 minutos, admite 5 intentos y nunca sirve como desafío
de login. Sin teléfono ni códigos de recuperación, el administrador usa `reset_mfa` o `set_password`.

## Seguridad

- **Contraseñas**: PBKDF2-HMAC-SHA256, sal de 16 bytes, 310.000 iteraciones (~0,6 s). Si se
  suben las iteraciones en la configuración, cada contraseña se re-hashea en su siguiente login.
  Las contraseñas temporales asignadas por administración admiten 10–128 caracteres. Toda
  contraseña nueva elegida por el usuario (primer ingreso, cambio autenticado o recuperación)
  exige exactamente 10 caracteres, al menos 3 grupos (mayúsculas, minúsculas, números y símbolos)
  y no incluir el usuario del correo.
- **TOTP**: secreto de 160 bits cifrado con AES-256 en reposo; ventana de ±30 s; un código ya
  usado se rechaza (anti-replay). 10 códigos de recuperación de 50 bits, guardados con HMAC.
- **Tokens**: 256 bits aleatorios, solo se guarda su SHA-256. Los prefijos `usb_at_`/`usb_rt_`/
  `usb_ch_` permiten detectarlos si se filtran en logs o repositorios.
- **Refresh rotativo**: cada refresh token sirve una vez. Si aparece uno ya usado (robo) o desde
  otro `device_id`, se revoca la sesión completa.
- **Bloqueo**: 5 fallos seguidos (contraseña o 2FA) → 15 min. Solo un 2FA exitoso reinicia el
  contador, así que conocer la contraseña no da intentos extra para adivinar el código.
- **Anti-enumeración**: un correo inexistente cuesta lo mismo (PBKDF2 ficticio) y responde igual.
- **Bitácora**: `usb_auth_events` registra todo (logins, fallos, bloqueos, revocaciones, IP, dispositivo).
- Respuestas con `Cache-Control: no-store`. AutoREST deshabilitado sin autenticación.

### Política de sesión (tabla `usb_auth_config`, editable desde APEX)

| Clave | Valor | Significado |
|---|---|---|
| `ACCESS_TTL_SECONDS` | 600 | Vida del access token (la app lo renueva sola) |
| `REFRESH_IDLE_MINUTES` | 30 | Minutos sin actividad antes de revocar la sesión y pedir login + 2FA |
| `SESSION_ABSOLUTE_DAYS` | 90 | Vida máxima de una sesión aunque se use a diario |
| `MAX_SESSIONS_PER_USER` | 1 | Sesiones activas por usuario. Con 1, entrar en otro dispositivo, navegador o pestaña cierra la sesión anterior, y esa sesión ve "se inició sesión con tu cuenta en otro dispositivo" |
| `MAX_FAILED_LOGINS` / `LOCKOUT_MINUTES` | 5 / 15 | Bloqueo |
| `CHALLENGE_TTL_SECONDS` / `CHALLENGE_MAX_ATTEMPTS` | 300 / 5 | Tiempo e intentos para el código 2FA |
| `PBKDF2_ITERATIONS` | 310000 | Costo del hash |

#### Cambiar un valor desde APEX

Los cambios aplican **de inmediato**, sin reinstalar ni reiniciar nada. `install.sh` **no**
sobrescribe estos valores (solo crea las claves que falten), así que lo que cambies en APEX se conserva.

1. Abre `http://localhost:8080/ords/apex` → Workspace `USBRAIN`, usuario `dev`, contraseña
   `APEX_DEV_PASSWORD` de `backend/.env`.
2. **Opción visual:** *SQL Workshop* → **Object Browser** → tabla `USB_AUTH_CONFIG` → pestaña
   **Data** → clic en el lápiz de la fila → cambia `CONFIG_VALUE` → **Apply Changes**.
3. **Opción SQL:** *SQL Workshop* → **SQL Commands**, pega y ejecuta (**Run**):
   ```sql
   update usb_auth_config set config_value = '1' where config_key = 'MAX_SESSIONS_PER_USER';
   commit;
   ```
4. Para revisar los valores actuales: `select * from usb_auth_config order by config_key;`

Cuándo aplica cada cambio:
- `MAX_SESSIONS_PER_USER`, `MAX_FAILED_LOGINS`, `LOCKOUT_MINUTES`, `CHALLENGE_*`: desde el
  siguiente login.
- `ACCESS_TTL_SECONDS`: para los tokens que se emitan desde ese momento (los ya entregados
  conservan su vencimiento).
- `REFRESH_IDLE_MINUTES`: se comprueba contra `last_seen_at` en cada petición autenticada y refresh;
  por eso también cierra sesiones existentes que lleven más de 30 minutos inactivas.
- `PBKDF2_ITERATIONS`: cada contraseña se re-hashea sola en el siguiente login correcto.

### Reglas de entrada y cambio de contraseña

- **Correo:** solo letras, números, `@` y `.` (los correos los crea la administración). El login,
  "olvidé mi contraseña" y la creación de usuarios rechazan cualquier otro carácter con
  400 `invalid_email`, y el campo de la app ni siquiera deja escribirlos. Un intento de login
  con un correo inválido queda en la bitácora como `LOGIN_INVALID_EMAIL`.
- **Cambio de contraseña desde el perfil** (todos los perfiles: *Perfil → Seguridad →
  Contraseña → Cambiar*): exige la contraseña actual **y** el código de la app autenticadora,
  aplica la misma política que el primer ingreso, cuenta los fallos para el bloqueo, cierra la
  sesión en los demás dispositivos y queda en la bitácora (`PASSWORD_CHANGED`).

### Administración (SQL o APEX)

```sql
usb_auth.create_user(email, nombre, 'PATIENT'|'THERAPIST'|'ADMIN', contraseña)
usb_auth.set_password(user_id, contraseña)   -- además cierra sus sesiones
usb_auth.unlock_user(user_id)
usb_auth.reset_mfa(user_id)                  -- perdió el teléfono y los códigos: re-enrola en el próximo login
usb_auth.set_status(user_id, 'DISABLED')     -- cierra todas sus sesiones
```

## Cliente (la app KMP)

Implementado en `shared/src/commonMain/kotlin/org/usbrain/project/data/`:

- `UsBrainApi`: cliente Ktor. Access token solo en memoria; refresh token en `SecureStorage`;
  renueva sola cuando vence y **serializa los refresh con un mutex** (dos refresh en paralelo con
  el mismo token se interpretarían como robo). Motores: CIO (Android), Darwin (iOS), fetch (web).
- `SecureStorage`: Android Keystore (AES-GCM) · iOS Keychain (solo este dispositivo) · web
  `sessionStorage` (el refresh token dura lo que la pestaña).
- `device_id` aleatorio generado en la primera ejecución.
- **Bloqueo estilo banco** (`security/AppLock.kt`, Android e iOS): tras el primer login se crea un
  PIN de 6 dígitos (se rechazan repetidos y secuencias); la huella o el rostro son opcionales
  (`Biometrics`: BiometricPrompt en Android, Face ID / Touch ID en iOS). El PIN se pide al abrir
  la app y al volver tras 60 s en segundo plano. Se guarda solo su hash (SHA-256 iterado 20.000
  veces con sal) en el almacén seguro; nunca va al servidor. 5 fallos = se borran PIN y sesión.
  En la web no aplica: la sesión ya dura lo que la pestaña.
- Al abrir la app, si hay sesión guardada pide el PIN y luego la renueva (pantalla `SPLASH`); sin
  red, ofrece reintentar sin borrar la sesión.

URL de la API por plataforma (`defaultApiBaseUrl`): emulador Android `10.0.2.2:8080`, simulador iOS
y web `localhost:8080`. Para un teléfono físico hay que cambiarla por la IP del computador en la red.
El servidor web de desarrollo usa el puerto **8081** (ORDS ocupa el 8080) y es el único origen
permitido por CORS.

Notas de interoperabilidad:
- **POST sin cuerpo**: ORDS rechaza `Content-Type: application/json` con cuerpo vacío; la app envía `{}`.
- Las respuestas 401 incluyen `WWW-Authenticate: Bearer` (RFC 9110); sin él, `HttpURLConnection`
  descarta el cuerpo del error.

## Pendiente para producción

- HTTPS obligatorio delante de ORDS (proxy o el puerto 8443 con certificado real).
- Mover las llaves de `usb_app_keys` a Oracle Wallet / TDE.
- Respetar `X-Forwarded-For` si ORDS queda detrás de un proxy (hoy se registra `REMOTE_ADDR`).
- Recuperación de contraseña por correo además del TOTP (requiere configurar SMTP).
- Obligar a cambiar la contraseña inicial que asigna el administrador en el primer ingreso.
- CORS de ORDS con el dominio real de la app web (hoy solo `http://localhost:8081`).
