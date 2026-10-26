# APEX (opcional)

Si quieres APEX dentro de los contenedores de `docker-compose.yml`, descomprime aquí el
contenido de `apex-latest.zip` (https://download.oracle.com/otn_software/apex/apex-latest.zip),
de modo que queden `backend/apex/apexins.sql` y `backend/apex/images/`.

Al arrancar, el contenedor `ords` detecta APEX en esta carpeta y lo instala en la base
(tarda unos minutos más la primera vez). Sin APEX, la API y la app funcionan igual.

Esta carpeta no se sube a git (salvo este archivo).
