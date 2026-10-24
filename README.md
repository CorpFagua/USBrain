# USBrain

Aplicación móvil para el **registro, seguimiento y análisis de conducta bajo diseño de caso único**,
para los servicios de atención psicológica de la Universidad de San Buenaventura, sede Bogotá.

Proyecto Kotlin Multiplatform (Android + iOS + **Web**) con UI compartida en Compose Multiplatform.

---

## Prototipo interactivo (entrega para el asesor)

Los flujos principales están implementados en la misma app KMP (`shared/src/commonMain`) y
funcionan **contra la base de datos real** (Oracle + ORDS en Docker, ver [`backend/`](backend/README.md)):
login con 2FA, sesión persistente y todos los datos clínicos se leen y guardan en el servidor.

### Qué se puede probar

| Flujo | Pantallas |
|---|---|
| **Inicio de sesión** | Correo y contraseña → **2FA con app autenticadora** (la primera vez se escanea un QR y se entregan 10 códigos de recuperación) → home según el rol de la cuenta. Al reabrir la app, la sesión se restaura sola |
| **Home del paciente** | Saludo, racha, registros de la semana, tareas agrupadas por conducta, clasificación de conductas (adaptativas/desadaptativas) y gráficas de progreso |
| **El paciente llena una tarea** | Se abren las indicaciones del terapeuta → el formulario cambia según la dimensión: **frecuencia** = episodios y minutos observados, **intensidad** = escala 0–10, **duración** = minutos → nota opcional → *Guardar registro* → confirmación y el punto nuevo aparece en la gráfica de la conducta |
| **Home del terapeuta** | Cartera de pacientes con casos, conductas y tareas pendientes; detalle del caso con conductas, estado de línea base y progreso |
| **Creación de conducta** | El terapeuta crea una conducta con nombre, definición operacional, clasificación (adaptativa/desadaptativa) y dimensión de medición. La conducta inicia automáticamente con línea base abierta |
| **Revisión y cierre de línea base** | El terapeuta puede revisar el número de observaciones de línea base de cada conducta y cerrarla cuando corresponda. Al cerrar, los datos quedan fijos como referencia |
| **Creación de tareas vinculadas** | Después de crear la conducta, el terapeuta puede crear tareas. La fase se asigna automáticamente según el estado de la línea base: línea base mientras esté abierta, intervención después de cerrarla |
| **Asignación de tareas** | Confirmación + **previsualización de la tarjeta tal como la verá el paciente** + resumen de conducta, clasificación, fase, dimensión y tarea; la tarea queda en la lista real del paciente (botón *Previsualizar como paciente*) |
| **Análisis de tendencias** | Gráficas cartesianas por conducta, separadas en conductas adaptativas y desadaptativas, con línea base como referencia fija y registros de intervención diferenciados |

### Datos de prueba

Se cargan con `backend/scripts/install.sh --seed`. Contraseña de todos: `DEV_SEED_PASSWORD` en
`backend/.env`. El primer ingreso de cada cuenta pide enrolar Google o Microsoft Authenticator.

- Terapeuta: `e.patino@usb.edu.co` — José E. Patiño (tiene asignados a Ana y Daniel).
- Paciente: `ana.torres@usbbog.edu.co` — Ana Torres, ansiedad social, caso único con conductas adaptativas y desadaptativas.
- Paciente: `daniel.ruiz@usbbog.edu.co` — Daniel Ruiz, insomnio de conciliación, con línea base abierta.
- Administrador: `admin@usb.edu.co` — gestiona usuarios; ve todos los casos y la bitácora.
- Super administrador: `superadmin@usb.edu.co` — además crea y edita roles y permisos.

Tras el 2FA, en Android e iOS se crea un PIN de 6 dígitos (y, si quieres, se activa la huella o el
rostro). La app lo pide cada vez que se abre. En el login está "¿Olvidaste tu contraseña?".

### Menú (arriba a la derecha)

- **Tema claro / oscuro** — alterna el tema (por defecto sigue el del sistema).
- **Cerrar sesión** — cierra la sesión en el servidor y borra la guardada en el dispositivo.

En la vista previa "como paciente" del terapeuta aparece una franja para volver a su vista; es de
solo lectura (el servidor no deja que el terapeuta registre por el paciente).

### Recorrido sugerido para la sustentación

**Terapeuta:** iniciar sesión como `e.patino@usb.edu.co` → seleccionar consultante → *Nueva conducta* (crear una adaptativa y otra desadaptativa) → guardar → revisar línea base (observaciones) → *Cerrar línea base* → *Nueva tarea* (verá que la fase cambió automáticamente a intervención) → asignar → ver previsualización → *Previsualizar como paciente* → llenar esa tarea → *Ver progreso* (muestra conductas separadas por clasificación, con línea base como zona fija).

**Resultado:** Las gráficas de progreso mostrarán conductas adaptativas y desadaptativas por separado, la línea base aparecerá como una zona o referencia fija con su contador de observaciones, y los registros de intervención se graficarán en el eje temporal sin contaminar la línea base cerrada.

---

## Backend (autenticación)

Login con contraseña + 2FA (TOTP) y sesiones persistentes por dispositivo sobre Oracle + ORDS en
Docker. Instalación, contrato de la API y modelo de seguridad en [`backend/README.md`](backend/README.md).

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

### Ejemplo incluido en la demo

El caso de Ana incluye la conducta **Episodios de ansiedad en clase** con cuatro observaciones
de línea base y cuatro de intervención. Cada observación guarda los episodios y los minutos
observados; por eso la gráfica compara la tasa y no el conteo bruto:

- Línea base: 5/45, 6/45, 5/45 y 4/30 = aproximadamente 0,11–0,13 episodios/minuto.
- Intervención: 3/45, 3/60, 2/45 y 2/60 = aproximadamente 0,03–0,07 episodios/minuto.

Así, dos sesiones con diferente duración siguen siendo comparables y la reducción de la
conducta se puede leer sin confundir una sesión más larga con un empeoramiento.

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
- En frecuencia, cada registro nuevo puede incluir los minutos observados. Cuando todos los puntos
  de una serie los incluyen, el eje Y muestra la tasa en episodios/minuto; si una serie histórica
  tiene registros sin minutos, se conserva como conteo de episodios para no mezclar unidades.

---

## Estructura del código del prototipo (`shared/src/commonMain/kotlin/org/usbrain/project`)

| Archivo | Contenido |
|---|---|
| `Theme.kt` | `UsBrainTheme` — esquemas de color claro/oscuro derivados de la paleta, formas, colores semánticos (éxito, fases, grilla) vía `LocalUsBrainSemantic` |
| `UsBrainBrand.kt` | Marca (`UsBrainMark`, `UsBrainMarkVector`, `UsBrainWordmark`, `UsBrainLockup`), `SuccessBadge`, íconos de navegación dibujados (`NavGlyph`) |
| `PrototypeModel.kt` | Modelo (`Role`, `Dimension`, `Phase`, `Patient`, `Behavior`, `TrackTask`…) y `PrototypeState`: navegación, sesión y acciones, que llaman a la API y recargan los datos |
| `DataMapping.kt` | Convierte el JSON del bootstrap del servidor en el modelo de las pantallas |
| `data/UsBrainApi.kt` | Cliente HTTP (Ktor): login, 2FA, refresh automático y endpoints clínicos |
| `data/ApiModels.kt` | Contrato JSON de la API |
| `data/SecureStorage.kt` | Sesión guardada: Keystore (Android), Keychain (iOS), `sessionStorage` (web) |
| `PrototypeApp.kt` | Todas las pantallas, la barra superior, la barra de navegación y la gráfica de tendencia (`Canvas`) |
| `App.kt` | Punto de entrada: tema + `remember` del estado + `PrototypeApp` |

`Greeting.kt` / `GreetingUtil.kt` / `Platform*.kt` son del andamiaje original de KMP.

---

## Cómo ejecutar

> **¿Primera vez?** Sigue la [guía de instalación](INSTALACION.md): cubre desde cero con Docker y
> también si ya tienes Oracle/APEX instalado.

Primero el backend (una vez por sesión de trabajo):

```bash
docker start oracle-db ords
backend/scripts/install.sh --seed
```

La app usa `http://10.0.2.2:8080` en el emulador de Android y `http://localhost:8080` en el
simulador de iOS y en la web. En un teléfono físico, cambia `defaultApiBaseUrl` por la IP del computador.

### Ejemplos incluidos en la demo

La paciente Ana Torres inicia con actividades listas para probar el flujo completo. La actividad
de **intensidad** registra ansiedad de 1 a 10 (9 → 8 → 6 → 4); **duración** registra minutos
de crisis (30 → 22 → 12); **cumplimiento** registra la adherencia a respiración (40% → 65% →
90%); y **Control emocional** es una medición personalizada de 1 a 7 (2 → 4 → 6). También se
conservan ejemplos de frecuencia de episodios y participación en clase. Cada serie incluye fecha,
hora, nota opcional y una gráfica temporal desde el detalle de la actividad.

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

Levanta un servidor de desarrollo (con recarga en caliente) en `http://localhost:8081`
(el 8080 lo ocupa ORDS). Es el único origen que la API acepta desde el navegador (CORS).
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
