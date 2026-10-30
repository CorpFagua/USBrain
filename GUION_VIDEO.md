# Guion del video USBrain

Video de unos 11 minutos que presenta USBrain de punta a punta: el problema que resuelve, por qué
está diseñado así, sus funciones principales y las herramientas extra. Cada escena trae la
narración lista para leer y lo que se muestra en pantalla. La escaleta marca qué recortar para una
versión de 7 minutos.

## Ficha del video

| Dato | Valor |
|---|---|
| Duración | ~11 min (versión corta: ~6 min 30 s) |
| Audiencia | Asesor, jurado y equipo de los servicios de atención psicológica de la USB Bogotá |
| Formato | Grabación de pantalla con voz en off; láminas solo en la apertura, la arquitectura y el cierre |
| Ritmo de lectura | ~140 palabras por minuto (cada bloque de narración ya está medido para su tiempo) |
| Terapeuta | `e.patino@usb.edu.co` — José E. Patiño |
| Pacientes | `laura.mendoza@usbbog.edu.co` (sueño, 3 meses) · `ana.torres@usbbog.edu.co` (ansiedad) |
| Administrador | `admin@usb.edu.co` |
| Super administrador | `superadmin@usb.edu.co` (único con la pestaña *Análisis*) |
| Contraseña de demo | `DEV_SEED_PASSWORD` de `backend/.env` |

## Funciones principales

| Función | Qué hace | Por qué está diseñada así |
|---|---|---|
| Diseño de caso único | Cada conducta pasa por fases A (línea base) y B (intervención), en orden A1 → B1 → A2 → B2 | Es el método de la psicología conductual para saber si *este* paciente cambia con el tratamiento: se compara consigo mismo |
| Conducta objetivo | Nombre, definición operacional, tipo (adaptativa o desadaptativa), función conductual y conducta de reemplazo | Lo que se mide es la conducta, no la tarea; la definición operacional hace que dos personas cuenten lo mismo |
| Dimensiones de medición | Frecuencia, duración o intensidad, cada una en su propia gráfica | No se mezclan unidades: promediar episodios con minutos no tiene sentido |
| Registro del paciente | Formulario que cambia según la dimensión; un registro por día y fase | Registrar en el momento evita depender de la memoria en la siguiente sesión |
| Reglas de fase | Una fase a la vez; la decide el terapeuta; exige un mínimo de días por fase; en la línea base no hay tratamiento | Sin suficientes datos o con tratamiento en la línea base, la comparación pierde validez |
| Análisis visual | Una sección de la gráfica por fase y el promedio de cada una | El campo analiza nivel, tendencia y variabilidad mirando la gráfica antes que cualquier número |
| Panel estadístico | Caso individual (t de Welch) y estudio de grupo (t pareada, n = participantes) | Complementa la lectura visual con una prueba exploratoria, sin mezclar un caso con una muestra |
| Seguridad | 2FA, una sesión por usuario, cierre por inactividad, bloqueo por intentos, bitácora | Son datos clínicos de estudiantes: se protegen como en la banca |
| Roles y permisos | Roles dinámicos y permisos individuales con motivo y vencimiento | Cada persona ve y hace solo lo que su función requiere |

## Herramientas extra

| Herramienta | Para qué sirve |
|---|---|
| Mediciones configurables | El terapeuta crea su propia variable: numérica, sí/no, texto o selección múltiple |
| Catálogo CIE-10 y funciones conductuales | Diagnóstico estandarizado y un catálogo de funciones que el terapeuta puede ampliar |
| Previsualizar como paciente | El terapeuta ve la tarea exactamente como la verá el consultante (solo lectura) |
| Panel mensual y vista global | Navegar 3 meses de seguimiento por mes o en promedios semanales |
| PIN y biometría | En el celular, la app se bloquea tras 60 s en segundo plano |
| Parámetros clínicos | La administración define el mínimo de días por fase sin tocar código |
| Tema claro y oscuro | Sigue al sistema o se cambia desde el menú |
| Una sola base de código | Android, iOS y web desde Kotlin Multiplatform |
| APEX | Consultar y administrar la misma base Oracle desde el navegador |
| Entorno de pruebas (stage) | La versión web publicada en Render usa la base real a través de un túnel |

## Escaleta

| # | Escena | Tiempo | En la versión de 7 min |
|---|---|---|---|
| 1 | El problema | 0:00 – 1:00 | Sí, recortada a 40 s |
| 2 | Qué es USBrain y por qué caso único | 1:00 – 1:50 | Sí, 30 s |
| 3 | Arquitectura | 1:50 – 2:20 | No (una frase dentro de la escena 2) |
| 4 | Acceso seguro | 2:20 – 3:20 | Sí, 40 s |
| 5 | El terapeuta construye el caso | 3:20 – 5:00 | Sí, 1 min 10 s |
| 6 | El paciente registra | 5:00 – 6:00 | Sí, 40 s |
| 7 | Fases A-B-A-B y análisis visual | 6:00 – 7:15 | Sí, 50 s |
| 8 | Panel estadístico | 7:15 – 8:45 | Sí, 1 min 10 s |
| 9 | Administración | 8:45 – 9:50 | Sí, 30 s (solo permisos individuales) |
| 10 | Herramientas extra | 9:50 – 10:30 | No (una frase en el cierre) |
| 11 | Cierre | 10:30 – 11:00 | Sí, 20 s |

## Guion parte 1: el problema y la propuesta

### Escena 1 · El problema (0:00 – 1:00)

**En pantalla:** lámina con el logo de USBrain. Luego, una hoja de papel con anotaciones de
conducta y una agenda de citas.

**Narración:**
> En los servicios de atención psicológica de la universidad, el seguimiento de un consultante
> pasa casi siempre por la memoria. Entre una sesión y otra, el paciente intenta recordar cuántas
> veces tuvo un episodio de ansiedad o cuánto tardó en dormirse. Los registros terminan en papel o
> en hojas de cálculo, sin fecha exacta, sin forma de saber si se tomaron antes o después del
> tratamiento. Con esos datos es difícil responder la pregunta que importa: ¿el tratamiento está
> funcionando para esta persona? USBrain nace para responderla con datos confiables, recogidos
> el mismo día, y protegidos como lo que son: información clínica.

*Versión corta:* desde "Los registros terminan en papel" hasta el final.

### Escena 2 · Qué es USBrain y por qué caso único (1:00 – 1:50)

**En pantalla:** lámina con el diagrama de fases: A1 línea base → B1 intervención → A2 → B2.
Luego, la gráfica de Laura con sus dos tramos.

**Narración:**
> USBrain es una aplicación para registrar, seguir y analizar conducta con diseño de caso único,
> el método que usa la psicología conductual para evaluar un tratamiento en una sola persona.
> Primero se observa la conducta sin intervenir: es la línea base, la fase A. Después se aplica el
> tratamiento: la fase B. Si la conducta cambia al introducir el tratamiento, y vuelve a cambiar
> si se retira y se aplica otra vez, hay evidencia de que el cambio se debe a la intervención. Por
> eso toda la aplicación gira alrededor de tres ideas: la conducta objetivo, cómo se mide y en qué
> fase está.

*Versión corta:* agregar al final "Funciona en Android, iOS y web, y guarda todo en una base
Oracle de la universidad."

### Escena 3 · Arquitectura (1:50 – 2:20)

**En pantalla:** lámina con el diagrama de la sección *Arquitectura*.

**Narración:**
> Por dentro, una sola base de código en Kotlin Multiplatform genera la app de Android, la de iOS
> y la versión web. Las tres hablan con la misma API, publicada con Oracle REST Data Services, que
> guarda todo en Oracle. Las reglas importantes viven en el servidor y no en el celular: aunque
> alguien modifique la app, no puede saltarse un permiso ni registrar en una fase cerrada.

## Guion parte 2: acceso seguro

### Escena 4 · Acceso seguro (2:20 – 3:20)

**En pantalla:** login con `e.patino@usb.edu.co` → pantalla del código de 6 dígitos → el
celular con la app autenticadora → entra al inicio del terapeuta. Mostrar 2 s el campo de correo
rechazando un carácter especial.

**Narración:**
> Entrar a USBrain funciona como entrar a la banca en línea. Además de la contraseña, se pide un
> código de una app autenticadora como Google o Microsoft Authenticator. La primera vez, el
> usuario cambia la contraseña temporal y escanea un código QR; recibe diez códigos de
> recuperación por si pierde el teléfono. Cinco intentos fallidos bloquean la cuenta quince
> minutos. Cada usuario tiene una sola sesión activa: si alguien entra con su cuenta en otro
> dispositivo, la sesión anterior se cierra y se le avisa. Y tras treinta minutos sin actividad,
> la sesión expira. Las contraseñas se guardan cifradas con un hash que no se puede revertir, y
> cada acceso queda en una bitácora.

*Versión corta:* desde "Además de la contraseña" hasta "quince minutos", y la frase de la sesión
única.

## Guion parte 3: el terapeuta construye el caso

### Escena 5 · El terapeuta construye el caso (3:20 – 5:00)

**En pantalla:** inicio del terapeuta con su cartera de pacientes → abrir a Laura → *Nueva
conducta* → llenar nombre, definición operacional, tipo desadaptativa, función conductual y
conducta de reemplazo → elegir dimensiones de la línea base → ver la tarjeta de fase "A1 · Línea
base" → *Nueva tarea* con una medición configurable → *Previsualizar como paciente*.

**Narración:**
> El terapeuta ve a sus pacientes con sus casos y sus tareas pendientes. Para empezar, define una
> conducta objetivo. No basta con un nombre: escribe una definición operacional, para que el
> paciente y el terapeuta cuenten lo mismo. Indica si es adaptativa, que queremos que aumente, o
> desadaptativa, que queremos que disminuya. Si es desadaptativa, registra su función, por
> ejemplo escape o búsqueda de atención, y la conducta que la va a reemplazar.
>
> Luego elige cómo se mide: frecuencia, duración o intensidad. Cada dimensión tendrá su propia
> gráfica, porque no tiene sentido mezclar episodios con minutos. La conducta empieza en la fase
> A1, la línea base, y la app crea sola el registro diario de cada dimensión.
>
> Durante la línea base solo se observa: la app no deja asignar ejercicios ni actividades
> terapéuticas hasta que empiece la intervención. Si el terapeuta necesita una medición distinta,
> la crea a su medida: una escala, un sí o no, un texto libre o una lista de opciones. Antes de
> asignarla, puede ver la tarea exactamente como la verá su paciente.

*Versión corta:* el primer y el segundo párrafo, más la frase "Durante la línea base solo se
observa".

## Guion parte 4: el paciente registra

### Escena 6 · El paciente registra (5:00 – 6:00)

**En pantalla:** otro navegador o el celular con la cuenta de Laura → inicio con la racha y las
tareas del día → abrir "Seguimiento del tiempo para dormir" → registrar los minutos y una nota →
confirmación → pestaña *Progreso* con el punto nuevo.

**Narración:**
> Del otro lado está el consultante. Al abrir la app ve sus tareas del día agrupadas por conducta y
> su racha de días registrando. Abre una tarea, lee las indicaciones de su terapeuta y registra.
> El formulario cambia según lo que se mide: episodios y minutos observados, una escala de cero a
> diez o la duración en minutos. Solo se acepta un registro por día en cada fase, y nunca con fecha
> futura. Al guardar, el punto aparece en su gráfica de progreso. Registrar el mismo día, y no de
> memoria en la siguiente cita, es lo que hace confiables los datos.

*Versión corta:* desde "Abre una tarea" hasta "con fecha futura", y la última frase.

## Guion parte 5: fases A-B-A-B y análisis visual

### Escena 7 · Fases A-B-A-B y análisis visual (6:00 – 7:15)

**En pantalla:** cuenta del terapeuta → Laura → "Minutos para conciliar el sueño" → tarjeta de fase
con "Duración: 14 de 3 días con registro ✓" → pestaña *Análisis gráfico* → secciones "A1 · Línea
base · 1/7 – 15/7" y "B1 · Intervención · desde 15/7" → panel mensual → "Promedio por fase". Luego,
en una conducta de prueba en A1 con pocos registros, mostrar el botón deshabilitado y el aviso de
días faltantes.

**Narración:**
> Cuando la línea base tiene suficientes datos, el terapeuta decide iniciar la intervención. La
> app no lo hace sola: es una decisión clínica. Pero sí exige un mínimo de días con registro en
> cada dimensión, que define la administración. Al cambiar de fase, las tareas de la fase anterior
> se cierran y quedan como historial; nadie puede seguir registrando en ellas.
>
> El diseño admite volver a una línea base retirando el tratamiento, y luego reintroducirlo: A1,
> B1, A2, B2. Siempre hay una sola fase activa. La gráfica muestra una sección por fase y el
> promedio de cada una. En el caso de Laura, el tiempo para dormirse pasa de un promedio de
> cuarenta y ocho minutos en la línea base a treinta y tres en la intervención, y al final del
> seguimiento ronda los veinte. Como la línea base era estable, ese cambio se puede atribuir al
> tratamiento.

*Versión corta:* el primer párrafo, más las dos últimas frases sobre Laura.

## Guion parte 6: panel estadístico

### Escena 8 · Panel estadístico (7:15 – 8:45)

**En pantalla:** cuenta del super administrador → pestaña *Análisis* → "Caso individual ·
Episodios de ansiedad en clase" (Ana) → gráfica de 60 días con la marca de inicio del tratamiento
→ tarjeta de la prueba (H₀, H₁, medias, valor p) → cambiar a "Estudio de grupo · n=200" → gráfica
del promedio del grupo → prueba pareada → comparación por programa y por rango de edad.

**Narración:**
> El super administrador tiene un panel estadístico que complementa la lectura visual. Trabaja con
> datos seudonimizados: sin nombres, correos, diagnósticos ni notas.
>
> Hay dos análisis que no se mezclan. El primero es el caso individual. Ana registró episodios de
> ansiedad treinta días sin tratamiento y treinta con tratamiento. La hipótesis nula dice que el
> tratamiento no los reduce. En la gráfica se ven los fines de semana, cuando no hay clases, y un
> repunte en la semana de parciales. La prueba t de Welch compara las dos fases: la media baja de
> seis coma tres a cuatro episodios por día, una reducción del treinta y siete por ciento, con un
> valor p menor a cero coma cero cero uno. Se rechaza la hipótesis nula.
>
> El segundo es el estudio de grupo, con doscientos participantes sintéticos. Cada uno tiene su
> propio nivel de ansiedad y su propia respuesta, y uno de cada diez no mejora. Aquí cada persona
> aporta su cambio y se aplica una t pareada, para que todas pesen lo mismo: en promedio bajan dos
> episodios por día. También se
> compara por programa académico y por edad, solo en grupos de tres o más personas, para que nadie
> pueda ser identificado. El panel advierte que es un análisis exploratorio: no demuestra causalidad
> ni reemplaza el criterio clínico.

*Versión corta:* el segundo párrafo completo, más la primera frase del tercero y la última.

## Guion parte 7: administración, herramientas extra y cierre

### Escena 9 · Administración (8:45 – 9:50)

**En pantalla:** cuenta del super administrador → *Usuarios* → abrir un terapeuta → sección
*Permisos individuales* con los grupos "Ya incluidos en su rol" y "No incluidos en su rol" →
otorgar "Ver todos los casos" con motivo y vencimiento → *Roles* → *Inicio* con la tarjeta
"Parámetros clínicos" y la bitácora mostrando el cambio.

**Narración:**
> La administración controla quién puede hacer qué. Los roles no están escritos en el código: el
> super administrador los crea y elige sus permisos. Y si una sola persona necesita algo extra, por
> ejemplo un terapeuta que cubre la agenda de un colega, se le otorga ese permiso sin cambiar su
> rol, con un motivo obligatorio y una fecha de vencimiento. Nadie puede dar un permiso que no tiene,
> ni modificar a alguien con más permisos que él. Desde aquí también se fija el mínimo de días por
> fase, y cada cambio queda registrado en la bitácora con quién lo hizo.

*Versión corta:* desde "si una sola persona necesita algo extra" hasta "fecha de vencimiento".

### Escena 10 · Herramientas extra (9:50 – 10:30)

**En pantalla:** cortes rápidos de 3 a 4 s: catálogo CIE-10 en la ficha del paciente → PIN y huella
en el celular → tema oscuro → la misma app en el navegador → APEX con las tablas.

**Narración:**
> Además, USBrain trae herramientas que facilitan el trabajo diario: diagnósticos con el catálogo
> CIE-10, mediciones a la medida, bloqueo con PIN o huella en el celular, tema claro y oscuro, y la
> misma aplicación en Android, iOS y el navegador. Para el equipo técnico, la base se consulta desde
> APEX, y existe un entorno de pruebas publicado en la web.

### Escena 11 · Cierre (10:30 – 11:00)

**En pantalla:** lámina final con el logo y tres líneas: "El paciente registra · El terapeuta
decide · Los datos responden".

**Narración:**
> USBrain convierte el seguimiento de un consultante en datos confiables: el paciente registra el
> mismo día, el terapeuta decide cada fase con evidencia y el análisis responde si el tratamiento
> funciona, todo con la información clínica protegida. Gracias.

*Versión corta:* agregar antes de "Gracias": "Y suma herramientas como mediciones a la medida,
CIE-10 y bloqueo con huella."

## Arquitectura

Lámina para la escena 3:

```
  App Android ─┐
  App iOS ─────┼──▶  API ORDS (/v1/auth · /v1/app · /v1/admin)  ──▶  Oracle (esquema USBRAIN)
  App web ─────┘          reglas, permisos y fases                       │
  (Kotlin Multiplatform)                                                 └──▶  APEX (consulta y
                                                                               administración)
```

Las tres apps comparten el código de pantallas y de cálculo estadístico. Toda regla de seguridad o
de fases se valida en el servidor.

## Preparación antes de grabar

- [ ] Encender Docker (`docker start oracle-db ords`) y correr `backend/scripts/install.sh --seed` **el día anterior**: la semilla restablece la contraseña y el 2FA de las cuentas de demo.
- [ ] Entrar con cada cuenta, cambiar la contraseña temporal y configurar el autenticador.
- [ ] Usar un navegador o perfil distinto por cuenta: con una sola sesión por usuario, entrar dos veces con la misma cierra la primera.
- [ ] En APEX, subir `REFRESH_IDLE_MINUTES` a `'120'` mientras se graba; volver a `'30'` al terminar.
- [ ] Recompilar la app web (`./gradlew :shared:wasmJsBrowserDevelopmentRun`) o la de Android.
- [ ] Crear una conducta de prueba en A1 con 1 o 2 registros, para mostrar el botón de fase deshabilitado.
- [ ] Tener a mano el celular con el autenticador y, para la escena 10, el PIN y la huella configurados.
- [ ] Ensayar con cronómetro; si una escena se pasa de tiempo, usar su versión corta.
- [ ] Grabar también un respaldo en video de cada escena por si algo falla en vivo.
