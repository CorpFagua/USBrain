# Guion del video USBrain

Video de 8 minutos que presenta USBrain de punta a punta: el problema, el flujo del terapeuta y del
paciente, el análisis de cada caso y el panorama estadístico por conducta. Cada escena trae la
narración lista para leer y lo que se muestra en pantalla.

## Ficha del video

| Dato | Valor |
|---|---|
| Duración | 8 min |
| Audiencia | Asesor, jurado y equipo de los servicios de atención psicológica de la USB Bogotá |
| Formato | Grabación de pantalla con voz en off; láminas solo en la apertura y el cierre |
| Ritmo de lectura | ~140 palabras por minuto (cada bloque ya está medido para su tiempo) |
| Terapeuta | `e.patino@usb.edu.co` — José E. Patiño |
| Paciente | `laura.mendoza@usbbog.edu.co` (sueño, 3 meses) |
| Super administrador | `superadmin@usb.edu.co` (único con la pestaña *Análisis*) |
| Contraseña de demo | `DEV_SEED_PASSWORD` de `backend/.env` |

Los valores entre corchetes, como **[media A1]**, se leen de la pantalla el día de la grabación
(ver *Preparación*).

## Escaleta

| # | Escena | Tiempo | Duración |
|---|---|---|---|
| 1 | El problema y la propuesta | 0:00 – 0:50 | 50 s |
| 2 | Acceso seguro | 0:50 – 1:30 | 40 s |
| 3 | El terapeuta construye el caso | 1:30 – 2:50 | 1 min 20 s |
| 4 | El paciente registra | 2:50 – 3:30 | 40 s |
| 5 | Fases y análisis del caso | 3:30 – 4:30 | 1 min |
| 6 | Panorama por conducta: estadística descriptiva | 4:30 – 5:40 | 1 min 10 s |
| 7 | Panorama por conducta: prueba de hipótesis | 5:40 – 6:50 | 1 min 10 s |
| 8 | Administración y herramientas extra | 6:50 – 7:35 | 45 s |
| 9 | Cierre | 7:35 – 8:00 | 25 s |

## Guion

### Escena 1 · El problema y la propuesta (0:00 – 0:50)

**En pantalla:** lámina con el logo de USBrain → lámina con las fases A1 línea base → B1
intervención → A2 → B2.

**Narración:**
> En los servicios de atención psicológica de la universidad, el seguimiento de un consultante
> depende casi siempre de la memoria: entre una cita y otra, el paciente intenta recordar cuántas
> veces tuvo un episodio o cuánto tardó en dormirse. Así es difícil responder lo que importa: ¿el
> tratamiento está funcionando? USBrain registra, sigue y analiza la conducta con diseño de caso
> único. Primero se observa sin intervenir, la línea base o fase A; luego se aplica el tratamiento,
> la fase B, y se compara a la persona consigo misma. Funciona en Android, iOS y web desde una sola
> base de código, con los datos en Oracle.

### Escena 2 · Acceso seguro (0:50 – 1:30)

**En pantalla:** login con `e.patino@usb.edu.co` → código de 6 dígitos → celular con la app
autenticadora → inicio del terapeuta.

**Narración:**
> Entrar funciona como en la banca en línea: contraseña y un código de una app autenticadora. La
> primera vez, el usuario cambia su contraseña temporal y recibe códigos de recuperación. Cinco
> intentos fallidos bloquean la cuenta quince minutos, cada usuario tiene una sola sesión activa y,
> tras treinta minutos sin actividad, la sesión expira. Cada acceso queda en una bitácora, porque
> son datos clínicos de estudiantes.

### Escena 3 · El terapeuta construye el caso (1:30 – 2:50)

**En pantalla:** cartera de pacientes → abrir a Laura → *Nueva conducta* → nombre, definición
operacional, tipo desadaptativa, función conductual y conducta de reemplazo → dimensiones de la
línea base → tarjeta "A1 · Línea base" → *Nueva tarea* con una medición configurable →
*Previsualizar como paciente*.

**Narración:**
> El terapeuta ve a sus pacientes, sus casos y sus tareas pendientes. Para empezar define una
> conducta objetivo, y no basta con un nombre: escribe una definición operacional para que paciente
> y terapeuta cuenten lo mismo. Indica si es adaptativa, que queremos que aumente, o desadaptativa,
> que queremos que disminuya; en ese caso registra su función, por ejemplo escape, y la conducta
> que la reemplazará.
>
> Luego elige cómo se mide: frecuencia, duración o intensidad, cada una con su propia gráfica,
> porque no tiene sentido mezclar episodios con minutos. La conducta empieza en A1 y la app crea
> sola el registro diario. Durante la línea base solo se observa: no se pueden asignar ejercicios
> hasta que empiece la intervención. Si hace falta otra medición, el terapeuta la crea a su medida y
> puede verla exactamente como la verá su paciente.

### Escena 4 · El paciente registra (2:50 – 3:30)

**En pantalla:** cuenta de Laura en otro navegador o en el celular → tareas del día y racha →
"Seguimiento del tiempo para dormir" → registrar minutos y una nota → pestaña *Progreso* con el
punto nuevo.

**Narración:**
> Del otro lado, el consultante ve sus tareas del día agrupadas por conducta y su racha de días
> registrando. Abre una tarea, lee las indicaciones y registra; el formulario cambia según lo que
> se mide. Solo se acepta un registro por día en cada fase y nunca con fecha futura. Registrar el
> mismo día, y no de memoria en la siguiente cita, es lo que hace confiables los datos.

### Escena 5 · Fases y análisis del caso (3:30 – 4:30)

**En pantalla:** cuenta del terapeuta → Laura → "Minutos para conciliar el sueño" → tarjeta de fase
con los días con registro → pestaña *Análisis gráfico* → secciones "A1 · Línea base" y
"B1 · Intervención" → "Promedio por fase".

**Narración:**
> Cuando la línea base tiene suficientes datos, el terapeuta decide iniciar la intervención: es una
> decisión clínica, pero la app exige un mínimo de días con registro, que fija la administración.
> Al cambiar de fase, las tareas anteriores se cierran y quedan como historial. El diseño admite
> retirar el tratamiento y reintroducirlo: A1, B1, A2, B2, siempre con una sola fase activa.
>
> El análisis de cada caso vive aquí, en la ficha del paciente: una sección de la gráfica por fase
> y el promedio de cada una. Laura pasa de cuarenta y ocho minutos para dormirse en la línea base a
> treinta y tres en la intervención. Como su línea base era estable, el cambio se puede atribuir al
> tratamiento.

### Escena 6 · Panorama por conducta: estadística descriptiva (4:30 – 5:40)

**En pantalla:** cuenta del super administrador → pestaña *Análisis* → selector *Conducta* con
"Episodios de ansiedad · Frecuencia · n=200" → tabla de estadística descriptiva (A1, B1 y
d = B1 - A1) → indicadores de mejora → diagramas de caja → evolución del promedio del grupo.

**Narración:**
> La pestaña de análisis ya no mira a una persona: muestra el panorama general de una conducta que
> se repite en muchos participantes, con datos seudonimizados. Aquí, episodios de ansiedad en
> doscientos participantes.
>
> Primero, la estadística descriptiva, como un describe de Python: cada participante aporta su
> media diaria en la línea base y en la intervención, y la tabla da conteo, media, desviación
> estándar, mínimo, cuartiles y máximo. La media pasa de **[media A1]** a **[media B1]** episodios
> por día, y la mediana del cambio es de **[mediana d]**. El **[porcentaje]** por ciento de los
> participantes mejoró en la dirección esperada. Los diagramas de caja muestran la distribución:
> la caja de la intervención está más abajo, de modo que la mayoría del grupo bajó, no solo el
> promedio. Y la gráfica de evolución muestra cómo cambia el promedio día a día.

### Escena 7 · Panorama por conducta: prueba de hipótesis (5:40 – 6:50)

**En pantalla:** tarjeta "2. Prueba de hipótesis · t pareada" → H0 y H1 → bloque "Números que
intervienen" → gráfica de la distribución t con el área alfa, el área p y el punto del resultado →
resultado → comparación por programa y por rango de edad.

**Narración:**
> Después, la prueba de hipótesis. La hipótesis nula dice que el cambio medio no es negativo, es
> decir, que el tratamiento no reduce la conducta. Se usa una t pareada: el cambio de cada persona
> es una observación, así que n son los participantes.
>
> La app muestra los números que intervienen. El cambio medio es **[media d]**; su error estándar
> es la desviación de los cambios entre la raíz de n; y t es el cambio medio entre ese error, con
> ciento noventa y nueve grados de libertad. En la gráfica, el área azul es alfa, el cinco por
> ciento, y el área morada es el valor p, medida desde nuestro resultado. Aquí p es menor que cero
> coma cero cero uno: se rechaza la hipótesis nula. También se compara por programa y por edad,
> solo en grupos de tres o más, y el panel advierte que es exploratorio.

### Escena 8 · Administración y herramientas extra (6:50 – 7:35)

**En pantalla:** *Usuarios* → un terapeuta → *Permisos individuales* → otorgar un permiso con motivo
y vencimiento → *Parámetros clínicos* → cortes rápidos: catálogo CIE-10, PIN y huella en el celular,
tema oscuro, APEX.

**Narración:**
> La administración controla quién hace qué. Los roles se crean desde la app y, si una persona
> necesita algo extra, se le otorga un permiso individual con motivo y fecha de vencimiento. Nadie
> puede dar un permiso que no tiene. Además, USBrain trae diagnósticos con el catálogo CIE-10,
> bloqueo con PIN o huella, tema claro y oscuro, y la base se consulta desde APEX.

### Escena 9 · Cierre (7:35 – 8:00)

**En pantalla:** lámina final con el logo y tres líneas: "El paciente registra · El terapeuta
decide · Los datos responden".

**Narración:**
> USBrain convierte el seguimiento en datos confiables: el paciente registra el mismo día, el
> terapeuta decide cada fase con evidencia en la ficha de cada caso, y el panorama estadístico
> muestra cómo responde una conducta al tratamiento en todo el grupo, con la información clínica
> protegida. Gracias.

## Preparación antes de grabar

- [ ] Encender Docker (`docker start oracle-db ords`) y correr `backend/scripts/install.sh --seed` **el día anterior**: la semilla restablece la contraseña y el 2FA de las cuentas de demo.
- [ ] Entrar con cada cuenta, cambiar la contraseña temporal y configurar el autenticador.
- [ ] Usar un navegador o perfil distinto por cuenta: con una sola sesión por usuario, entrar dos veces con la misma cierra la primera.
- [ ] En APEX, subir `REFRESH_IDLE_MINUTES` a `'120'` mientras se graba; volver a `'30'` al terminar.
- [ ] Recompilar la app web (`./gradlew :shared:wasmJsBrowserDevelopmentRun`) o la de Android.
- [ ] Abrir *Análisis* con el estudio de ansiedad y anotar los valores de los corchetes: media A1, media B1, mediana de d, porcentaje que mejoró y media de d. Confirmar que p sale menor a 0,001; si no, ajustar esa frase.
- [ ] Tener a mano el celular con el autenticador, el PIN y la huella configurados.
- [ ] Ensayar con cronómetro: cada escena debe caber en su tiempo de la escaleta.
- [ ] Grabar un respaldo de cada escena por si algo falla en vivo.
