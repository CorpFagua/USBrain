# USBrain

Aplicación móvil para el **registro, seguimiento y análisis de conducta bajo diseño de caso único**,
para los servicios de atención psicológica de la Universidad de San Buenaventura, sede Bogotá.

Proyecto Kotlin Multiplatform (Android + iOS) con UI compartida en Compose Multiplatform.

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
- Contraseña: cualquiera (precargada) · Código 2FA: `304 917` (precargado).
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
