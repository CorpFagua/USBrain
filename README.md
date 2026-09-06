# USBrain

Aplicación móvil para el **registro, seguimiento y análisis de conducta bajo diseño de caso único**,
para los servicios de atención psicológica de la Universidad de San Buenaventura, sede Bogotá.

Proyecto Kotlin Multiplatform (Android + iOS + **Web**) con UI compartida en Compose Multiplatform.

---

## Prototipo interactivo (entrega para el asesor)

Está implementado un **prototipo navegable** de los flujos principales, dentro de la misma app KMP
(`shared/src/commonMain`). No tiene backend: todo el estado vive en memoria y se reinicia desde el menú.

### Qué se puede probar

| Flujo | Pantallas |
|---|---|
| **Inicio de sesión (2 roles)** | Un login único → se elige **Paciente** o **Terapeuta** (credenciales autocompletadas) → **verificación 2FA** (código `304 917` precargado) → home del rol |
| **Home del paciente** | Saludo, racha, registros de la semana, tareas agrupadas por conducta, clasificación de conductas (adaptativas/desadaptativas) y gráficas de progreso |
| **El paciente llena una tarea** | Se abren las indicaciones del terapeuta → el formulario cambia según la dimensión: **frecuencia** = contador, **intensidad** = escala 0–10, **duración** = minutos → nota opcional → *Guardar registro* → confirmación y el punto nuevo aparece en la gráfica de la conducta |
| **Home del terapeuta** | Cartera de pacientes con casos, conductas y tareas pendientes; detalle del caso con conductas, estado de línea base y progreso |
| **Creación de conducta** | El terapeuta crea una conducta con nombre, definición operacional, clasificación (adaptativa/desadaptativa) y dimensión de medición. La conducta inicia automáticamente con línea base abierta |
| **Revisión y cierre de línea base** | El terapeuta puede revisar el número de observaciones de línea base de cada conducta y cerrarla cuando corresponda. Al cerrar, los datos quedan fijos como referencia |
| **Creación de tareas vinculadas** | Después de crear la conducta, el terapeuta puede crear tareas. La fase se asigna automáticamente según el estado de la línea base: línea base mientras esté abierta, intervención después de cerrarla |
| **Asignación de tareas** | Confirmación + **previsualización de la tarjeta tal como la verá el paciente** + resumen de conducta, clasificación, fase, dimensión y tarea; la tarea queda en la lista real del paciente (botón *Previsualizar como paciente*) |
| **Análisis de tendencias** | Gráficas cartesianas por conducta, separadas en conductas adaptativas y desadaptativas, con línea base como referencia fija y registros de intervención diferenciados |

### Datos de prueba

- Paciente: `ana.torres@usbbog.edu.co` — Ana Torres, ansiedad social, caso único con conductas adaptativas y desadaptativas.
- Terapeuta: `e.patino@usb.edu.co` — José E. Patiño.
- Contraseña: cualquiera (precargada) · Código 2FA: `304 917` (precargado).
- Segundo paciente de ejemplo: Daniel Ruiz, insomnio de conciliación, con línea base abierta.

### Menú (arriba a la derecha)

- **Ir a sesión paciente / terapeuta** — salta el login para revisar rápido.
- **Tema claro / oscuro** — alterna el tema (por defecto sigue el del sistema).
- **Reiniciar demo** — vuelve al estado inicial.
- **Cerrar sesión** — regresa al login.

### Recorrido sugerido para la sustentación

**Terapeuta:** Ir a sesión terapeuta → seleccionar consultante → *Nueva conducta* (crear una adaptativa y otra desadaptativa) → guardar → revisar línea base (observaciones) → *Cerrar línea base* → *Nueva tarea* (verá que la fase cambió automáticamente a intervención) → asignar → ver previsualización → *Previsualizar como paciente* → llenar esa tarea → *Ver progreso* (muestra conductas separadas por clasificación, con línea base como zona fija).

**Resultado:** Las gráficas de progreso mostrarán conductas adaptativas y desadaptativas por separado, la línea base aparecerá como una zona o referencia fija con su contador de observaciones, y los registros de intervención se graficarán en el eje temporal sin contaminar la línea base cerrada.

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

### Web (Kotlin/Wasm)

```bash
./gradlew :shared:wasmJsBrowserDevelopmentRun
```

Levanta un servidor de desarrollo (con recarga en caliente) en `http://localhost:8080`.
Requiere un navegador reciente con soporte de WebAssembly GC (Chrome/Edge 119+, Firefox 120+, Safari 18.2+).

Para generar los archivos estáticos listos para publicar (por ejemplo en GitHub Pages, Netlify o cualquier
hosting estático):

```bash
./gradlew :shared:wasmJsBrowserDistribution
```

El resultado queda en `shared/build/dist/wasmJs/productionExecutable/`.

El punto de entrada web vive en `shared/src/wasmJsMain/` (`main.kt` monta `App()` con `ComposeViewport`,
`Platform.wasmJs.kt` implementa el `expect` de plataforma, `resources/index.html` es la página anfitriona).
Es el mismo `App()` que corre en Android e iOS — no hay una versión de UI aparte para web.

### Pruebas

```bash
./gradlew :shared:testAndroidHostTest
./gradlew :shared:iosSimulatorArm64Test
```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
