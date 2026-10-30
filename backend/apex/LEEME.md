# APEX (opcional)

Si quieres instalar APEX en la base de los contenedores de `docker-compose.yml`, descomprime aquí el
contenido de `apex-latest.zip` (https://download.oracle.com/otn_software/apex/apex-latest.zip),
de modo que queden `backend/apex/apexins.sql` y `backend/apex/images/`.

En una base nueva, cambia temporalmente en `backend/docker-compose.yml` el volumen de imágenes
`./apex/images:/opt/oracle/apex/images:ro` por `./apex:/opt/oracle/apex`. Inicia ORDS para que
instale APEX una sola vez. Después restaura el volumen de imágenes y recrea ORDS. Así puede
servir APEX en `http://localhost:8182/ords/apex` sin intentar reinstalarlo en cada inicio.

El puerto `8182` es un alias adicional del mismo servicio ORDS (configurable con `APEX_PORT`);
la API también está disponible a través de ORDS en `ORDS_PORT`. Sin APEX, la API y la app
funcionan igual.

Esta carpeta no se sube a git (salvo este archivo).
