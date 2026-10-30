# Guía de instalación de USBrain

Esta guía deja funcionando, en tu computador, la base de datos con la API y la app
(web, Android e iOS). Hay dos caminos según lo que ya tengas:

| Caso | Para quién | Qué haces |
|---|---|---|
| **A. Desde cero con Docker** | Solo tienes el código (los mocks) | Docker crea Oracle y ORDS con un comando |
| **B. Ya tengo Oracle/APEX local** | Tienes Oracle con ORDS/APEX instalado | El instalador se conecta a tu base |

Al final los dos quedan igual: la API en `http://localhost:8080/ords/usbrain/v1` y la app conectada a ella.

---

## 1. Requisitos

| Herramienta | Para qué | Cómo comprobarlo |
|---|---|---|
| Git | Bajar el código | `git --version` |
| JDK 17 o superior | Compilar la app (Gradle) | `java -version` |
| Python 3.10+ | Pruebas automáticas (opcional) | `python3 --version` |
| OpenSSL | El instalador genera contraseñas | `openssl version` |
| **Caso A:** Docker Desktop, con al menos 6 GB de RAM asignados | Correr Oracle y ORDS | `docker compose version` |
| **Caso B:** SQLcl (`sql`) o SQL\*Plus (`sqlplus`) | Instalar el backend en tu base | `sql -V` o `sqlplus -V` |
| Android Studio | App Android (emulador o teléfono) | — |
| Xcode (solo Mac) | App iOS | — |

**Windows:** los scripts son de bash. Usa **WSL2** (recomendado) o Git Bash, y Docker Desktop con WSL2.

---

## 2. Bajar el código

```bash
git clone https://github.com/CorpFagua/USBrain.git
cd USBrain
git checkout dev
```

---

## 3A. Caso A: desde cero con Docker

### 3A.1 Configurar

```bash
cd backend
cp .env.example .env
```

Abre `backend/.env` y escribe la contraseña de SYS que tendrá tu base:

```properties
ORACLE_MODE=docker
ORACLE_PWD=TuClaveOracle2026
```

Debe tener al menos 8 caracteres, con mayúscula, minúscula y número. Lo demás déjalo como está:
el instalador genera las otras contraseñas.

> Si ya usas los puertos 1521 u 8080 para otra cosa, cámbialos en `DB_PORT` y `ORDS_PORT`
> (y ajusta `API_BASE` al nuevo puerto de ORDS). APEX usa `APEX_PORT` (por defecto `8182`);
> ambos puertos apuntan al mismo servicio ORDS.

### 3A.2 (Opcional) APEX

La app **no necesita APEX**; sirve para ver y editar la base desde el navegador. Si lo quieres:

1. Descarga `apex-latest.zip` de https://download.oracle.com/otn_software/apex/apex-latest.zip
2. Descomprímelo en `backend/apex/`, de modo que quede `backend/apex/apexins.sql` y `backend/apex/images/`.

Para una base nueva, antes del siguiente paso cambia temporalmente en
`backend/docker-compose.yml` el volumen de imágenes
`./apex/images:/opt/oracle/apex/images:ro` por `./apex:/opt/oracle/apex`. Levanta ORDS una vez
para que instale APEX. Cuando termine, vuelve a dejar el volumen de imágenes como estaba y
recrea ORDS; así las siguientes arrancadas no intentarán reinstalar APEX.

### 3A.3 Levantar la base y ORDS

```bash
docker compose up -d
```

La primera vez descarga las imágenes de Oracle (unos 15 GB) y crea la base; después, ORDS se
instala solo dentro de ella (unos segundos). Sabrás que está lista cuando:

```bash
docker compose ps                      # oracle-db debe decir "healthy"
curl -I http://localhost:8080/ords/    # debe responder (HTTP 302)
```

Si `curl` aún no responde, ORDS se está instalando dentro de la base: espera unos minutos.
Puedes ver el avance con `docker compose logs -f ords`.

Sigue con el paso 4.

---

## 3B. Caso B: ya tengo Oracle/APEX local

### Requisitos de tu base

- **Oracle Database 23ai o 26ai** (Oracle Free sirve). El backend usa columnas `BOOLEAN` y `JSON`
  y `CREATE … IF NOT EXISTS`, que **no existen en 19c ni en XE 21c**. Si tu base es más vieja,
  usa el caso A: puedes tener las dos instaladas cambiando los puertos.
- **ORDS instalado** en esa base (si tienes APEX funcionando en el navegador, ya lo tienes).
- La contraseña de **SYS** (o un usuario con privilegios de DBA conectado `as sysdba`).

El instalador revisa la versión y ORDS antes de tocar nada, y te avisa si falta algo.

### Configurar

```bash
cd backend
cp .env.example .env
```

En `backend/.env`:

```properties
ORACLE_MODE=local
ORACLE_PWD=la_clave_de_SYS
DB_HOST=localhost
DB_PORT=1521
ORACLE_SERVICE=FREEPDB1      # el servicio de tu PDB (en XE suele ser XEPDB1)
SQL_CLIENT=sql               # o sqlplus (si la ruta tiene espacios, va entre comillas)
API_BASE=http://localhost:8080/ords/usbrain/v1   # cambia el puerto si tu ORDS usa otro
```

**Si tu Oracle corre en Docker con otros nombres**, también puedes usar `ORACLE_MODE=docker` y poner
el nombre de tu contenedor en `ORACLE_CONTAINER` (el instalador entra con `docker exec`).

**Si tu ORDS no está en el puerto 8080**, la app debe saberlo. Agrega en `local.properties`
(en la raíz del proyecto) la URL de tu ORDS:

```properties
usbrain.web.apiBaseUrl=http://localhost:8181/ords/usbrain/v1
usbrain.ios.apiBaseUrl=http://localhost:8181/ords/usbrain/v1
usbrain.android.apiBaseUrl=http://10.0.2.2:8181/ords/usbrain/v1
```

Sigue con el paso 4.

---

## 4. Instalar el backend (los dos casos)

Desde la carpeta `backend`:

```bash
./scripts/install.sh --seed
```

Crea el esquema `USBRAIN`, las tablas, los paquetes, publica la API en ORDS y carga los usuarios y
casos de demostración. Termina con `Backend instalado.` Se puede volver a correr sin perder datos;
hazlo cada vez que alguien cambie un archivo de `backend/sql/`.

Comprueba que la API responde:

```bash
curl http://localhost:8080/ords/usbrain/v1/app/bootstrap
# {"error":"unauthorized","message":"Se requiere iniciar sesión."}   ← correcto: no hay sesión
```

### Pruebas automáticas (opcional, recomendado)

```bash
python3 tests/auth_e2e.py     # login, 2FA, sesiones (~1 min)
python3 tests/app_e2e.py      # API clínica y permisos
python3 tests/admin_e2e.py    # roles, usuarios, olvido de contraseña (~1 min)
```

Cada una termina con `✔ N verificaciones pasaron.` Usan usuarios propios (`e2e.*@usbrain.test`),
no los de demostración.

---

## 5. Correr la app

Desde la raíz del proyecto.

| Plataforma | Comando / acción | URL de la API que usa |
|---|---|---|
| **Web** | `./gradlew :shared:wasmJsBrowserDevelopmentRun` → abre http://localhost:8081 | `localhost:8080` |
| **Android (emulador)** | Android Studio → *Run* `androidApp`, o `./gradlew :androidApp:installDebug` | `10.0.2.2:8080` (el Mac visto desde el emulador) |
| **iOS (simulador)** | Abre `iosApp/iosApp.xcodeproj` en Xcode → *Run* | `localhost:8080` |

La web corre en el **8081** porque ORDS ya ocupa el 8080, y la API solo acepta llamadas del
navegador desde ese origen. La primera compilación tarda varios minutos.

### Teléfono físico

El teléfono no puede usar `localhost`: necesita la dirección de tu computador en la red. En
`local.properties`:

```properties
usbrain.android.apiBaseUrl=http://<IP de tu computador>:8080/ords/usbrain/v1
usbrain.ios.apiBaseUrl=http://<nombre-del-Mac>.local:8080/ords/usbrain/v1
```

- IP en Mac: `ipconfig getifaddr en0` · en Windows: `ipconfig` · nombre del Mac: `scutil --get LocalHostName`
- Teléfono y computador en el **mismo Wi-Fi**, y reinstala la app después de cambiar la URL.
- Prueba primero en el navegador del teléfono: `http://<IP>:8080/ords/usbrain/v1/app/bootstrap`.
  Si no carga, es la red: las redes de la universidad suelen aislar los dispositivos. Usa el
  **hotspot del teléfono** para conectar el computador.

---

## 6. Usuarios de demostración

Todos con la contraseña `DEV_SEED_PASSWORD` que quedó en `backend/.env` (cada uno tiene la suya).

| Correo | Rol |
|---|---|
| `superadmin@usb.edu.co` | Super administrador: roles, permisos y usuarios |
| `admin@usb.edu.co` | Administrador: usuarios y bitácora |
| `e.patino@usb.edu.co` | Terapeuta de Ana y Daniel |
| `ana.torres@usbbog.edu.co` | Paciente con datos de ejemplo |
| `daniel.ruiz@usbbog.edu.co` | Paciente con línea base abierta |

El primer ingreso de cada cuenta muestra un **QR** para Google o Microsoft Authenticator y 10
códigos de recuperación. En Android e iOS, después se crea un **PIN** de 6 dígitos (y, si quieres,
huella o rostro).

**APEX** (si lo instalaste): http://localhost:8182/ords/apex → workspace `USBRAIN`, usuario `dev`,
contraseña `APEX_DEV_PASSWORD` de `backend/.env` (APEX pide cambiarla la primera vez).

---

## 7. Problemas comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `El contenedor 'oracle-db' no está corriendo` | Docker apagado o contenedores detenidos | Abre Docker Desktop y `docker compose up -d` |
| `Falta la contraseña de SYS` | `ORACLE_PWD` vacío en `backend/.env` | Escríbela (caso A: la misma con la que se creó la base) |
| `No pude conectarme como SYS…` (con `ORA-01017`) | Clave de SYS equivocada | Corrige `ORACLE_PWD` |
| `No pude conectarme como SYS…` (otro error) | Host, puerto o servicio equivocados | Revisa `DB_HOST`, `DB_PORT`, `ORACLE_SERVICE` |
| `docker compose ps` muestra `ords` detenido | Faltan variables o la base no estaba lista | `docker compose logs ords` y luego `docker compose up -d` |
| `Se necesita Oracle Database 23ai/26ai…` | Tu base es 19c o XE 21c | Usa el caso A |
| `ORDS no está instalado en esta base` | Caso B sin ORDS | Instala ORDS o usa el caso A |
| `curl` a la API no responde | ORDS aún instalándose o apagado | `docker compose logs -f ords` y espera |
| La app dice "No se pudo conectar con el servidor" | URL de la API equivocada o red | Revisa la tabla del paso 5 y `local.properties` |
| La web no carga datos (error de CORS en la consola) | La abriste en otro puerto | Usa http://localhost:8081 |
| `Lock file was changed` al correr la web | Cambiaron dependencias de npm | `./gradlew kotlinWasmUpgradeYarnLock` |
| Perdí el autenticador de un usuario de demostración | — | Entra como `superadmin` → Usuarios → el usuario → *Restablecer 2FA* |

---

## 8. Comandos de todos los días

```bash
cd backend
docker compose up -d               # encender (caso A)
./scripts/install.sh               # aplicar cambios de backend/sql/
docker compose stop                # apagar sin perder datos
docker compose down -v             # BORRAR la base por completo (vuelve a empezar de cero)
```

Más detalle del backend (API, seguridad, roles) en [`backend/README.md`](backend/README.md).
Para publicar la web en Render usando el Docker de un computador como backend (entorno *stage*,
con ngrok), ver [`STAGE.md`](STAGE.md).
