# Contrato de autenticacion USBrain

La app no se conecta directamente a Oracle. El frontend Kotlin Multiplatform llama a ORDS, que valida las credenciales en el esquema Oracle.

## Importar y publicar

1. En Oracle APEX, crea un workspace asociado a un esquema de parsing nuevo y verifica que ORDS este habilitado para la instancia.
2. En ese workspace, abre **SQL Workshop > SQL Scripts**, carga [oracle-apex-login.sql](oracle-apex-login.sql) y ejecutalo. El script crea `USBRAIN_USERS`, `USBRAIN_SESSIONS`, las dos cuentas de demostracion y el modulo ORDS `usbrain` con `POST /login`.
3. La ruta publicada es `/ords/usbrain/usbrain/login`: el primer `usbrain` es el alias ORDS del esquema y el segundo es la ruta base del modulo. Si el administrador asigna otro alias, actualiza tambien el `p_url_mapping_pattern` del script y las URLs de la app.
4. Comprueba la ruta con el ejemplo de abajo. Un `200` debe devolver JSON con `userId`, `role` y `token`; credenciales incorrectas deben devolver `401`.
5. Para despliegues web, configura CORS en ORDS o en el proxy para permitir solo el origen del frontend. No habilites origenes comodin con credenciales.

El script esta pensado para un esquema limpio y se ejecuta una vez. No es una exportacion de una instancia APEX existente ni contiene tablas de tareas/pacientes: esos datos del prototipo todavia viven en memoria. Si ya existe un esquema USBrain, prepara una migracion antes de ejecutar el instalador para no sobrescribir ni duplicar objetos.

## Cuentas locales

| Rol | Correo | Contrasena de demostracion |
|---|---|---|
| Paciente | `ana.torres@usbbog.edu.co` | `UsBrain-Demo-2026!` |
| Terapeuta | `e.patino@usb.edu.co` | `UsBrain-Demo-2026!` |

Son cuentas de desarrollo. Cambia sus contrasenas y elimina o desactiva las cuentas de demostracion antes de desplegar con datos reales. El instalador aplica SHA-256 con sal por usuario para que la contrasena no quede guardada en texto plano; para produccion, migra a un mecanismo de almacenamiento de contrasenas resistente a fuerza bruta y agrega limitacion de intentos.

## URL del frontend

La URL se define mediante `authBaseUrl()` en el archivo de plataforma correspondiente. Los valores locales actuales son:

| Plataforma | URL ORDS |
|---|---|
| Emulador Android | `http://10.0.2.2:8080/ords/usbrain/usbrain` |
| Simulador iOS | `http://localhost:8080/ords/usbrain/usbrain` |
| Navegador (WASM) | `http://localhost:8082/ords/usbrain/usbrain` |

Para una instancia compartida, cambia esos valores por la URL HTTPS del entorno. En un dispositivo Android fisico no uses `10.0.2.2`: usa una URL accesible desde el dispositivo. El backend ORDS debe estar disponible antes de iniciar el frontend; el puerto del servidor web WASM no es el puerto ORDS.

## Request

```http
POST /ords/usbrain/usbrain/login
Content-Type: application/json

{"email":"ana.torres@usbbog.edu.co","password":"UsBrain-Demo-2026!"}
```

## Response 200

```json
{
  "userId": "...",
  "role": "patient",
  "token": "...",
  "expiresIn": 3600
}
```

El backend devuelve `patient` o `therapist`; la app tambien acepta `paciente` o `terapeuta`. El token es aleatorio, se guarda en `USBRAIN_SESSIONS` como hash y vence en una hora. Todavia no hay otros endpoints que consuman ese token.

## Limites antes de produccion

- La pantalla 2FA conserva el codigo de demostracion `304 917`; no hay envio ni validacion del segundo factor en el servidor.
- Los pacientes, tareas y registros siguen siendo datos de demostracion en memoria, no tablas importables.
- El endpoint de login no limita intentos ni ofrece recuperacion de contrasena. Usa HTTPS y un esquema separado con privilegios minimos para cada ambiente.
