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

## Fundamentación: qué variables se grafican en diseño de caso único

En diseño de caso único (*single-case/single-subject research design*) la variable dependiente
nunca es "la tarea" que se le asignó a alguien para registrar: es la **conducta objetivo**
(*target behavior*), operacionalizada en una **dimensión de medición** concreta. Una actividad
de registro es solo el instrumento con el que se captura una observación de esa dimensión; lo
que se analiza y se grafica —sesión a sesión— es la serie temporal de la conducta en esa
dimensión, comparando fase de línea base contra fase de intervención.

### Dimensiones de medición reconocidas en la literatura

| Dimensión | Qué mide | Unidad típica |
|---|---|---|
| **Frecuencia / conteo** | Número de veces que ocurre la conducta en un período de observación | episodios |
| **Tasa (rate)** | Frecuencia dividida por el tiempo de observación, para comparar sesiones de distinta duración | episodios/minuto |
| **Duración** | Cuánto dura la conducta, por ocurrencia o en total | minutos / segundos |
| **Latencia** | Tiempo entre un estímulo/instrucción y el inicio de la conducta | segundos |
| **Intensidad / magnitud** | Severidad o fuerza de la respuesta, normalmente por escala | escala (p. ej. 0–10) |
| **Porcentaje (de intervalos o de ensayos)** | Proporción de intervalos de observación o de oportunidades en que ocurrió la conducta (registro por intervalos: parcial, total o de muestreo de tiempo momentáneo) | % |
| **Tiempo entre respuestas (IRT)** | Intervalo entre una ocurrencia y la siguiente | segundos |
| **Producto permanente** | Conteo de un resultado tangible que deja la conducta (p. ej. tareas completadas, objetos rotos) | unidades |

Fuentes: Cooper, Heron & Heward, *Applied Behavior Analysis* (3.ª ed., Pearson, 2020) —
capítulos de medición conductual (cap. 4) y de representación gráfica de datos de caso único
(cap. 5); Kazdin, A.E., *Single-Case Research Designs: Methods for Clinical and Applied
Settings* (2.ª ed., Oxford University Press, 2011); Barlow, D.H., Nock, M.K. & Hersen, M.,
*Single Case Experimental Designs* (3.ª ed., Pearson, 2009); Kratochwill, T.R. et al.,
*Single-Case Design Technical Documentation*, What Works Clearinghouse — Institute of Education
Sciences (2010/2013).

### Cómo se lee la gráfica (convención del campo)

- Eje X: número de observación/sesión (orden, no siempre tiempo continuo real).
- Eje Y: la dimensión elegida, en su propia unidad — **nunca se mezclan dos dimensiones
  distintas en una misma serie** (no tiene sentido promediar "episodios" con "minutos").
- Un quiebre/línea vertical marca el cambio de fase (línea base → intervención); el trazo no
  se conecta a través de ese quiebre, precisamente para no sugerir una tendencia continua entre
  fases que se están comparando.
- El análisis es visual (nivel, tendencia, variabilidad, inmediatez del cambio, superposición
  entre fases), no estadístico-inferencial: por eso la app prioriza la lectura gráfica sobre
  cualquier resumen numérico agregado.

### Cómo aplica esto en la app

- `Dimension` (en [`PrototypeModel.kt`](shared/src/commonMain/kotlin/org/usbrain/project/PrototypeModel.kt))
  ya cubre tres de las dimensiones anteriores — `FRECUENCIA`, `DURACION`, `INTENSIDAD` (como
  escala de magnitud autoinformada 0–10) — quedando `tasa`, `porcentaje de intervalos`, `latencia`
  e `IRT` como extensiones naturales a futuro si un caso las requiere.
- La gráfica pertenece a la **conducta**, no a una actividad: `Behavior.dimensions` expone todas
  las dimensiones que efectivamente se están registrando para esa conducta (puede haber más de
  una actividad de registro autónomo, cada una midiendo un aspecto distinto del mismo episodio),
  y `Behavior.recordsFor(dimension)` filtra la serie para no mezclar escalas.
  `TherapistBehaviorScreen` muestra la evolución de la conducta ahí, con pestañas para alternar
  entre dimensiones cuando hay más de una — así la gráfica es intercambiable según lo que el
  terapeuta esté evaluando, en vez de fijarse a la actividad que se tocó para llegar a ella.

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
