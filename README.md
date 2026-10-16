# USBrain

Aplicación móvil para el **registro, seguimiento y análisis de conducta bajo diseño de caso único**,
para los servicios de atención psicológica de la Universidad de San Buenaventura, sede Bogotá.

Proyecto Kotlin Multiplatform (Android + iOS) con UI compartida en Compose Multiplatform.

## Login con Oracle APEX

El frontend se autentica contra Oracle por medio de ORDS. Para importar el esquema de login en
tu instancia APEX y conectar la app, sigue estos pasos:

1. Cambia a la rama `Apexconnection` y descarga/abre el proyecto. Inicia sesión en el workspace
   APEX que usarás y selecciona un **esquema de parsing vacío**. El instalador es para un esquema
   nuevo, no para sobrescribir una base existente.
2. En APEX abre **SQL Workshop > SQL Scripts > Upload**. Selecciona
   [`docs/oracle-apex-login.sql`](./docs/oracle-apex-login.sql), cárgalo y luego pulsa **Run**.
   El script crea `USBRAIN_USERS`, `USBRAIN_SESSIONS`, las dos cuentas de prueba y publica el
   módulo ORDS `usbrain` con `POST /login`. Ejecútalo una sola vez.
3. Confirma que la ejecución terminó sin errores. En **SQL Workshop > Object Browser** deben
   aparecer `USBRAIN_USERS` y `USBRAIN_SESSIONS`. La ruta REST publicada por el script es
   `/ords/usbrain/usbrain/login` (alias del esquema + módulo + plantilla).
4. Antes de abrir la app, prueba el backend en PowerShell. Sustituye el host por el de tu ORDS;
   si usas APEX alojado, no uses `localhost`:

   ```powershell
   $ordsBase = "http://localhost:8080/ords/usbrain/usbrain"
   $body = @{ email = "ana.torres@usbbog.edu.co"; password = "UsBrain-Demo-2026!" } | ConvertTo-Json
   Invoke-RestMethod -Uri "$ordsBase/login" -Method Post -ContentType "application/json" -Body $body
   ```

   La respuesta debe incluir `userId`, `role` con valor `patient` y `token`. Si obtienes `401`,
   revisa el correo y la contraseña; si obtienes `404`, revisa el alias ORDS y la ruta del módulo.
5. Configura en el frontend la misma URL base que probaste, **sin** `/login` al final. Cambia el
   valor de `authBaseUrl()` en `shared/src/androidMain/kotlin/org/usbrain/project/Platform.android.kt`,
   `shared/src/iosMain/kotlin/org/usbrain/project/Platform.ios.kt` y
   `shared/src/wasmJsMain/kotlin/org/usbrain/project/HttpClient.wasmJs.kt`. En el emulador Android,
   `10.0.2.2` representa el computador; en un celular físico usa una dirección accesible desde ese
   celular. Para navegador configura CORS en ORDS/proxy para el origen del frontend.

   URLs base predeterminadas (sin `/login`):

   | Plataforma | URL |
   |---|---|
   | Emulador Android | `http://10.0.2.2:8080/ords/usbrain/usbrain` |
   | Simulador iOS | `http://localhost:8080/ords/usbrain/usbrain` |
   | Navegador WASM | `http://localhost:8082/ords/usbrain/usbrain` |

6. Ejecuta el frontend con el backend ORDS activo: en Android, abre el proyecto en Android Studio
   y pulsa **Run**; en iOS, abre `iosApp` en Xcode; para navegador, ejecuta:

   ```powershell
   .\gradlew.bat :shared:wasmJsBrowserDevelopmentRun
   ```

   En WASM, la URL ORDS local configurada por defecto usa el puerto `8082`; el servidor web del
   frontend usa `8080`. Si tus servicios usan otros puertos, cambia las URL del paso 5.

Las cuentas de demostración son `ana.torres@usbbog.edu.co` y `e.patino@usb.edu.co`, ambas con
`UsBrain-Demo-2026!`. Cámbialas y usa HTTPS antes de trabajar con datos reales. Los detalles del
contrato están en [`docs/ords-login-contract.md`](./docs/ords-login-contract.md).

El alcance de base de datos actual es autenticacion: pacientes, tareas y registros del prototipo
siguen en memoria y no se exportan con este script. El 2FA tambien conserva el codigo de demo y
no se valida en ORDS.

---

## Prototipo interactivo (entrega para el asesor)

Está implementado un **prototipo navegable** de los flujos principales, dentro de la misma app KMP
(`shared/src/commonMain`). No tiene backend: todo el estado vive en memoria y se reinicia desde el menú.

### Qué se puede probar

| Flujo | Pantallas |
|---|---|
| **Inicio de sesión (2 roles)** | Un login único → se elige **Paciente** o **Terapeuta** (credenciales autocompletadas) → **verificación 2FA** (código `304 917` precargado) → home del rol |
| **Home del paciente** | Saludo, racha, registros de la semana, tareas de hoy y gráfica de progreso (línea base vs. intervención) |
| **El paciente llena una tarea** | Se abren las indicaciones del terapeuta → el formulario cambia según la dimensión: **frecuencia** = contador, **intensidad** = escala 0–10, **duración** = minutos → nota opcional → *Guardar registro* → confirmación y el punto nuevo aparece en la gráfica |
| **Home del terapeuta** | Cartera de pacientes con fase y pendientes; ficha individual con la gráfica del caso |
| **Creación / asignación de tarea** | Formulario del *módulo de planeación*: paciente, conducta a observar, dimensión de medición, frecuencia de registro, fase del caso único, fecha límite e indicaciones |
| **Cómo queda al asignarse** | Confirmación + **previsualización de la tarjeta tal como la verá el paciente** + resumen; la tarea queda en la lista real del paciente (botón *Previsualizar como paciente*) |
| **Análisis de tendencias** | Una gráfica cartesiana por paciente (*módulo de seguimiento*) |

### Datos de prueba

- Paciente: `ana.torres@usbbog.edu.co` — Ana Torres, ansiedad social, fase intervención.
- Terapeuta: `e.patino@usb.edu.co` — José E. Patiño.
- En el prototipo original la contraseña era cualquiera; el login actual valida contra ORDS.
- El código 2FA `304 917` sigue siendo de demostración y aún no se valida en el servidor.
- Segundo paciente de ejemplo: Daniel Ruiz, insomnio de conciliación, fase línea base.

### Menú (arriba a la derecha)

- **Ir a sesión paciente / terapeuta** — salta el login para revisar rápido.
- **Tema claro / oscuro** — alterna el tema (por defecto sigue el del sistema).
- **Reiniciar demo** — vuelve al estado inicial.
- **Cerrar sesión** — regresa al login.

### Recorrido sugerido para la sustentación

Terapeuta → *Asignar nueva tarea* → llenar el formulario para Ana → ver la previsualización →
*Previsualizar como paciente* → llenar esa tarea → ver el punto nuevo en la gráfica.

---

## Identidad visual

- Paleta oficial: `#FF7A00` · `#FF6B56` · `#7B66C2` · `#0F2540`.
- **Logotipo**: las cuatro fichas U·S·B (`logo-USBrain.jpeg`). En la app se muestra sobre una
  placa blanca redondeada para que se lea bien en tema claro y oscuro
  (`shared/src/commonMain/composeResources/drawable/usbrain_logo.jpg`).
- También hay una **reconstrucción vectorial adaptable** de la marca
  (`UsBrainMarkVector` en `UsBrainBrand.kt`) y los SVG editables en [`prototipo/`](./prototipo).
- `prototipo/index.html` es la versión web autónoma del mismo prototipo (por si se quiere mostrar en un navegador).

---

## Estructura del código del prototipo (`shared/src/commonMain/kotlin/org/usbrain/project`)

| Archivo | Contenido |
|---|---|
| `Theme.kt` | `UsBrainTheme` — esquemas de color claro/oscuro derivados de la paleta, formas, colores semánticos (éxito, fases, grilla) vía `LocalUsBrainSemantic` |
| `UsBrainBrand.kt` | Marca (`UsBrainMark`, `UsBrainMarkVector`, `UsBrainWordmark`, `UsBrainLockup`), `SuccessBadge`, íconos de navegación dibujados (`NavGlyph`) |
| `PrototypeModel.kt` | Modelo en memoria: `Role`, `Dimension`, `Phase`, `TaskStatus`, `Patient`, `TrackTask`, `PrototypeState` (máquina de estados + datos de ejemplo) |
| `PrototypeApp.kt` | Todas las pantallas, la barra superior, la barra de navegación y la gráfica de tendencia (`Canvas`) |
| `App.kt` | Punto de entrada: tema + `remember` del estado + `PrototypeApp` |

`Greeting.kt` / `GreetingUtil.kt` / `Platform*.kt` son del andamiaje original de KMP.

---

## Cómo ejecutar

### Android

```bash
./gradlew :androidApp:assembleDebug
```

Requiere el SDK de Android configurado en `local.properties` (`sdk.dir=/ruta/al/Android/sdk`)
o la variable `ANDROID_HOME`. Luego abrir el proyecto en Android Studio y usar el botón de *Run*.

### iOS

Abrir [`/iosApp`](./iosApp) en Xcode y ejecutar el esquema `iosApp` en un simulador,
o compilar la app KMP con la tarea `:shared:embedAndSignAppleFrameworkForXcode`.

### Pruebas

```bash
./gradlew :shared:testAndroidHostTest
./gradlew :shared:iosSimulatorArm64Test
```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
