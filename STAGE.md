# Entorno stage: Render + ngrok + Docker local

*Stage* es la app web publicada en Render usando como backend la base de datos que corre en
Docker en el computador de Alejandro, expuesta a internet con un túnel de ngrok. Sirve para
demostraciones y pruebas con el asesor o el equipo, mientras no haya un servidor en la nube.

```
Navegador ─▶ https://usbrainwebprototipo.onrender.com         (app web, archivos estáticos en Render)
    │
    └──llamadas a la API──▶ https://slinging-overjoyed-landlord.ngrok-free.dev   (túnel ngrok)
                                │
                                └──▶ localhost:8080 en el Mac (ORDS en Docker) ─▶ Oracle (Docker)
```

**Stage solo funciona mientras el Mac está encendido, con Docker y ngrok corriendo.** Si
cualquiera de los tres se apaga, la página de Render abre pero no puede iniciar sesión.

| Pieza | Dirección | Dónde se configura |
|---|---|---|
| Frontend | `https://usbrainwebprototipo.onrender.com` | Panel de Render |
| Túnel | `https://slinging-overjoyed-landlord.ngrok-free.dev` | Cuenta de ngrok (dominio fijo gratuito) |
| API | `https://slinging-overjoyed-landlord.ngrok-free.dev/ords/usbrain/v1` | Se fija al compilar el dist de stage |
| Permiso CORS | Render + `http://localhost:8081` | `backend/sql/05_ords_auth.sql`, `09_ords_app.sql`, `12_ords_admin.sql` |

---

## 1. Encender stage

```bash
# 1. Docker Desktop abierto, y los contenedores:
docker start oracle-db ords

# 2. Esperar a que la API responda localmente (401 = correcto, sin sesión):
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/ords/usbrain/v1/app/bootstrap

# 3. Abrir el túnel (deja esta terminal abierta; Ctrl + C lo cierra):
backend/scripts/stage-tunnel.sh
```

El script revisa que ngrok esté instalado, que no haya otro túnel abierto y que la API local
responda; si algo falta, te dice qué hacer. Luego ejecuta
`ngrok http --url=slinging-overjoyed-landlord.ngrok-free.dev 8080`. El dominio se puede cambiar
con `NGROK_DOMAIN` en `backend/.env`.

Comprobar que el túnel llega a la API (debe responder el JSON `"Se requiere iniciar sesión."`):

```bash
curl -s -H 'ngrok-skip-browser-warning: true' \
  https://slinging-overjoyed-landlord.ngrok-free.dev/ords/usbrain/v1/app/bootstrap
```

### Ver las peticiones en vivo

Mientras el túnel está abierto, **http://127.0.0.1:4040** muestra cada petición que llega por
ngrok: método, ruta, estado, encabezados y cuerpo, con un botón para repetirla. Es la forma más
rápida de saber si la app de Render está llegando a la API y qué le responde.

## 2. Apagar stage

- El túnel: `Ctrl + C` en la terminal de ngrok. Desde ese momento nadie en internet llega a tu base.
- La base: `docker stop ords oracle-db` (los datos se conservan).

---

## 3. Publicar una versión nueva de la web en stage

La URL de la API queda **dentro** de la app al compilar, así que el dist de stage se compila
indicándole el túnel. La variable de entorno no toca `local.properties`, que sigue apuntando a
tu Docker local:

```bash
USBRAIN_WEB_API_BASE_URL=https://slinging-overjoyed-landlord.ngrok-free.dev/ords/usbrain/v1 \
  ./gradlew :shared:wasmJsBrowserDistribution
```

El resultado queda en `shared/build/dist/wasmJs/productionExecutable/` (unos 17 MB):
`index.html`, `usbrain.js`, dos `.wasm` y `composeResources/`. Se publica la carpeta completa.

Render publica sitios estáticos desde el repositorio de GitHub. Hay dos formas:

- **Render compila:** en *Settings* del sitio, *Build Command*
  `./gradlew :shared:wasmJsBrowserDistribution`, *Publish Directory*
  `shared/build/dist/wasmJs/productionExecutable` y, en *Environment*, la variable
  `USBRAIN_WEB_API_BASE_URL` con la URL de la API de arriba. El entorno de build de Render
  necesita Java 17 para Gradle.
- **Se sube ya compilado:** se copia la carpeta del dist a una carpeta o rama del repositorio
  que Render publique tal cual (sin build command).

> Un dist compilado **sin** la variable apunta a `http://localhost:8080`: en Render intentaría
> conectarse al computador de quien abre la página y fallaría.

---

## 4. Por qué funciona así (detalles técnicos)

- **CORS.** El navegador solo deja que la página de Render llame a otra dirección si esa
  dirección lo autoriza. Los tres módulos de ORDS permiten
  `https://usbrainwebprototipo.onrender.com` y `http://localhost:8081`. Está en los archivos SQL,
  así que `install.sh` lo mantiene. Para agregar otro origen: edita esa línea en los tres
  archivos y corre `backend/scripts/install.sh` (sin `--seed`).
- **Aviso de ngrok gratuito.** ngrok responde a los navegadores con una página de advertencia,
  salvo que la petición traiga el encabezado `ngrok-skip-browser-warning`. La app lo envía en
  todas sus peticiones (`UsBrainApi.kt`, `defaultRequest`). Sin él, la API devolvería HTML y el
  login fallaría.
- **HTTPS.** Render sirve la página por `https`, y los navegadores bloquean llamadas de una página
  `https` a una API `http`. ngrok pone el HTTPS; ORDS sigue en `http` dentro del Mac.
- **Dominio fijo.** Las cuentas gratuitas de ngrok tienen un dominio estático asignado; por eso
  la URL no cambia entre reinicios y no hay que recompilar la web cada vez.

---

## 5. Seguridad: lee esto antes de abrir el túnel

- **Con el túnel abierto, tu base de desarrollo es accesible desde internet.** Ábrelo solo cuando
  lo necesites y ciérralo al terminar.
- Los usuarios de demostración tienen contraseñas de desarrollo. Si alguien fuera del equipo va a
  usar stage, cambia `DEV_SEED_PASSWORD` en `backend/.env` y corre `install.sh --seed`, o
  restablece las contraseñas desde la administración de la app.
- No cargues datos reales de pacientes en stage: es una base de desarrollo.
- El login, el 2FA, el bloqueo por intentos y los permisos funcionan igual por el túnel.
- El plan gratuito de ngrok tiene límites mensuales de tráfico; para un uso continuo conviene un
  servidor en la nube.

---

## 6. Problemas comunes

| Síntoma | Causa | Solución |
|---|---|---|
| La página de Render dice "No se pudo conectar con el servidor" | Túnel, Docker o Mac apagados | Paso 1; revisa http://127.0.0.1:4040 |
| ngrok muestra `ERR_NGROK_3200` (endpoint offline) | El túnel no está abierto | `backend/scripts/stage-tunnel.sh` |
| ngrok responde 502 / `ERR_NGROK_8012` | El túnel está abierto pero ORDS no responde | `docker start oracle-db ords` y espera un minuto |
| Error de CORS en la consola del navegador | La página no está en un origen permitido | Usa la URL exacta de Render, o agrega el origen (sección 4) |
| La API devuelve HTML de ngrok en vez de JSON | Dist viejo, sin el encabezado de ngrok | Recompila el dist (sección 3) |
| La página llama a `localhost:8080` | El dist se compiló sin `USBRAIN_WEB_API_BASE_URL` | Recompila con la variable |
| Pruebas en Python por el túnel: `CERTIFICATE_VERIFY_FAILED` | Python de python.org en macOS sin certificados | `SSL_CERT_FILE=/etc/ssl/cert.pem python3 …`, o ejecuta *Install Certificates.command* de tu Python |
| Funciona en tu Mac pero no en Render tras dormir el Mac | Al suspenderse el Mac, el túnel se cae | Vuelve a abrir ngrok |
