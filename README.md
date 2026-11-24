# Sistema de Gestión Odontológica (nombre a definir)

Aplicación web para un odontólogo independiente en Argentina: agenda, reserva online sin cuenta e historia clínica inmutable.
Monolito modular: `backend/` (Spring Boot + Gradle), `frontend/` (SPA en TypeScript vanilla con Vite) e `infra/` (scripts de base de datos).
La documentación de dominio vive en `knowledge-base/`; el plan de trabajo en `CHANGES.md`.

## Clonar el repositorio

```bash
git clone -c core.longpaths=true https://github.com/Veronica22-stack/turnos-odontologia
```

En **Windows** la opción `-c core.longpaths=true` es necesaria: algunos tests tienen rutas largas y, sin ella, el clone termina con "Filename too long". Si ya clonaste sin la opción, entrá a la carpeta y corré `git config core.longpaths true` y `git restore --source=HEAD :/`.

## Desarrollo local

Ninguna etapa requiere contenedores ni herramientas de virtualización: la aplicación se ejecuta con un PostgreSQL instalado en tu máquina y los tests
usan un PostgreSQL embebido que el propio proceso de tests inicia y destruye.

### Requisitos

| Herramienta | Versión | Para qué |
|-------------|---------|----------|
| JDK | 21 (o cualquier JDK 17+ instalado: Gradle descarga solo la toolchain 21 la primera vez, requiere red) | backend |
| Node.js | 22 (ver `frontend/.nvmrc`) | frontend |
| PostgreSQL | 17 o superior (la app se verificó con 18; los tests usan un 17 embebido), **solo para ejecutar la aplicación** (los tests no lo necesitan) | base de datos local |

### Instalar PostgreSQL (solo para ejecutar la app)

En los comandos de abajo, `<versión>` es la versión mayor que instalaste (por ejemplo `17` o `18`).

**Windows**

1. Descargá el instalador oficial (EDB) desde <https://www.postgresql.org/download/windows/>, versión **17 o superior**. Incluye pgAdmin.
2. Dejá el puerto `5432`. Elegí vos la contraseña del superusuario `postgres` (no se versiona ni se comparte).
3. **Que solo acepte conexiones de tu máquina**: el instalador deja `listen_addresses = '*'` (escucha en todas las interfaces de red). Abrí como administrador `C:\Program Files\PostgreSQL\<versión>\data\postgresql.conf`, cambiá esa línea por `listen_addresses = 'localhost'` y reiniciá el servicio `postgresql-x64-<versión>` (desde `services.msc`, o en una consola de administrador: `Restart-Service postgresql-x64-<versión>`). Para comprobarlo: `netstat -ano | findstr :5432` debe mostrar solo `127.0.0.1` y `[::1]`, no `0.0.0.0`.
4. Agregá `C:\Program Files\PostgreSQL\<versión>\bin` al `PATH` para poder usar `psql` desde la consola.

**macOS**: [Postgres.app](https://postgresapp.com/) (versión 17) o `brew install postgresql@17 && brew services start postgresql@17`.

**Linux**: paquete `postgresql-17` de tu distribución o del repositorio PGDG (<https://www.postgresql.org/download/linux/>); verificá que el servicio esté iniciado.

### 1. Variables de entorno

```bash
cp .env.example .env        # PowerShell: Copy-Item .env.example .env
```

Editá `.env` y reemplazá todos los `change-me` por contraseñas propias. `.env` no se versiona.
**Spring Boot no lee `.env` por sí solo**: hay que cargar las variables en la consola (o en el IDE) antes de iniciar el backend (ver paso 3).

Variables de la sesión (C-04):

| Variable | Default | Significado |
|---|---|---|
| `AUTH_SESSION_TIMEOUT_MIN` | `30` | Minutos de inactividad permitidos. Debe ser un entero mayor que 0; un valor inválido hace fallar el arranque y nombra la variable. |
| `AUTH_SESSION_COOKIE_NAME` | `SESSIONID` | Nombre de la cookie opaca de sesión. No contiene datos de la cuenta. |
| `AUTH_SESSION_COOKIE_SECURE` | `true` (`false` en el perfil `dev`) | Si la cookie exige HTTPS. `HttpOnly`, `SameSite=Strict` y CSRF no se relajan en `dev`. |
| `CORS_ALLOWED_ORIGINS` | — en producción | Lista explícita de orígenes con credenciales (separados por coma). Es obligatoria fuera de `dev` y nunca admite `*`. |

La cookie CSRF es fija: `XSRF-TOKEN`; la cabecera exigida en escrituras autenticadas es `X-XSRF-TOKEN`.

Emails de notificación (C-13):

En desarrollo (`SPRING_PROFILES_ACTIVE=dev`) los emails **no se envían**: el perfil `dev` fija `MAIL_PROVIDER=log` y el log de la aplicación solo registra, por cada envío simulado, los identificadores de la notificación y del turno, el tipo y el nombre de la plantilla (nunca el destinatario, el asunto ni el cuerpo). Para enviar emails de verdad, definí `MAIL_PROVIDER=smtp` junto con `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD` y `MAIL_FROM` (STARTTLS obligatorio, timeouts de 10 s).

| Variable | Default | Significado |
|---|---|---|
| `MAIL_PROVIDER` | `log` en `dev`; obligatoria fuera de `dev` | `log` (simulado) o `smtp`. Un valor distinto, o `smtp` sin alguna de sus variables, hace fallar el arranque y nombra solo la variable. Con `log` fuera de `dev` el arranque registra una advertencia. |
| `NOTIFICATION_POLL_INTERVAL` | `PT5M` | Cada cuánto se despachan las notificaciones vencidas (ISO-8601, mayor que 0). Además, tras confirmar o cancelar un turno se despacha enseguida. |
| `NOTIFICATION_MAX_ATTEMPTS` | `5` | Intentos de envío (backoff de 5, 10, 20 y 40 min) antes de dejar la notificación en `FAILED`. Debe ser al menos 1. |
| `NOTIFICATION_DISPATCH_ENABLED` | `true` | Con `false` las notificaciones se encolan pero no se envían. |

La anticipación del recordatorio no es una variable de entorno: sale de `reminder_hours_before` del consultorio (configuración del consultorio, 1 a 168 horas, 24 por defecto).

### 2. Crear la base y los roles (una sola vez)

`infra/postgres/init/01-roles.sql` crea el rol de migración (`DB_MIGRATION_USER`, con DDL, usado solo por Flyway), el rol de aplicación
(`DB_USER`, sin DDL) y la base `DB_NAME`. No contiene credenciales: las recibe como variables de psql. Es idempotente: podés volver a
ejecutarlo sin perder datos, y sirve para **rotar contraseñas** (cambiás `.env` y lo ejecutás de nuevo).

Si falta alguna variable, el script se detiene con error antes de crear nada. Ejecutalo siempre con `ON_ERROR_STOP=1`.

**PowerShell** (desde la raíz del repo; primero se cargan las variables de `.env`):

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }
psql -U postgres -h localhost -v ON_ERROR_STOP=1 -v "db_name=$env:DB_NAME" -v "migrator_user=$env:DB_MIGRATION_USER" -v "migrator_password=$env:DB_MIGRATION_PASSWORD" -v "app_user=$env:DB_USER" -v "app_password=$env:DB_PASSWORD" -f infra/postgres/init/01-roles.sql
```

**bash / zsh** (macOS, Linux, Git Bash):

```bash
set -a; . ./.env; set +a
psql -U postgres -h localhost -v ON_ERROR_STOP=1 -v db_name="$DB_NAME" -v migrator_user="$DB_MIGRATION_USER" -v migrator_password="$DB_MIGRATION_PASSWORD" -v app_user="$DB_USER" -v app_password="$DB_PASSWORD" -f infra/postgres/init/01-roles.sql
```

`psql` pide la contraseña del superusuario `postgres` (o definí `PGPASSWORD`). Las contraseñas pasadas con `-v` son visibles en la lista de procesos de tu propia máquina mientras corre el comando; es aceptable en desarrollo local.

**Alternativa con pgAdmin**: abrí la herramienta **PSQL Tool** (no la "Query Tool", que no admite metacomandos), conectada como `postgres`, y ejecutá:

```
\set db_name 'consultorio'
\set migrator_user 'migrator'
\set migrator_password 'la-contraseña-de-tu-.env'
\set app_user 'app'
\set app_password 'la-contraseña-de-tu-.env'
\i 'C:/ruta/al/repo/infra/postgres/init/01-roles.sql'
```

### 3. Backend

Con las variables de `.env` cargadas en la consola:

- **PowerShell**: `Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }`
- **bash / zsh**: `set -a; . ./.env; set +a`
- **IntelliJ IDEA**: run configuration → *Environment variables* (pegá el contenido de `.env`).
- **VS Code**: `"envFile": "${workspaceFolder}/.env"` en `launch.json`.

```bash
cd backend
./gradlew bootRun          # PowerShell/cmd: .\gradlew bootRun
```

La aplicación arranca en <http://localhost:8080>, aplica las migraciones con el rol de migración y responde:

```bash
curl http://localhost:8080/actuator/health      # {"status":"UP"}
```

Toda otra ruta responde `401` (deny-by-default).

#### Nota operativa: migración V4 en bases locales ya migradas

`V4__service.sql` (catálogo de prestaciones, C-07) está registrado por delante de `V5`/`V7`/`V11`. En una base local que ya los
aplicó, Flyway falla con "Detected resolved migration not applied to database: 4" porque `V4` se ve como pendiente anterior a la
última migración aplicada. En desarrollo local (los tests usan una base nueva y nunca necesitan esto), una solución única es:

- recrear la base local con `infra/postgres/init/01-roles.sql` (los datos locales no son productivos), o
- correr una vez con `spring.flyway.out-of-order=true` en el perfil local **sin commitear esa propiedad** (no se habilita de
  forma permanente en `application.yml`).

#### Primer arranque: cuenta del odontólogo

En el **primer** arranque contra una base nueva, el backend crea la cuenta del odontólogo (rol `DENTIST`). Para eso necesita
dos variables en tu `.env` (ya están en `.env.example` con el marcador `change-me`), definidas **antes** del primer `bootRun`:

- `BOOTSTRAP_ADMIN_EMAIL`: email de la cuenta (se guarda en minúsculas).
- `BOOTSTRAP_ADMIN_PASSWORD`: contraseña inicial de **al menos 12 caracteres**.

Si faltan o no son válidas, el arranque falla con un mensaje que nombra la variable (nunca su valor). La contraseña inicial
es provisoria: la cuenta queda marcada para **cambiarla en el primer login** (pantalla de acceso: change C-04). Los arranques
siguientes no leen estas variables: si ya existe un `DENTIST` no se crea ni se modifica nada, por eso conviene **quitar las
variables del `.env` después del primer arranque**. El log solo muestra el id de la cuenta, nunca el email ni la contraseña.

#### API de autenticación (C-04)

El frontend es una SPA que llama a la API con credenciales (cookies). Los cuatro endpoints son:

| Método y ruta | CSRF | Respuesta | Uso |
|---|---|---|---|
| `POST /api/v1/auth/login` | No (es pública) | `200` + `AuthMeResponse`; cookies `SESSIONID` (HttpOnly) y `XSRF-TOKEN` | Iniciar sesión con `{ "email", "password" }` |
| `GET /api/v1/auth/me` | No (GET) | `200` + `userId`, `role`, `mustChangePassword`, `expiresAt` (UTC) | Consultar la sesión actual; sin email, DNI ni teléfono |
| `POST /api/v1/auth/change-password` | Sí | `200` | Enviar `{ "currentPassword", "newPassword" }` (12–128 caracteres); invalida todas las sesiones |
| `POST /api/v1/auth/logout` | Sí | `204` | Cierra solo la sesión actual; es idempotente |

La sesión viaja en la cookie `SESSIONID` (`HttpOnly`, `SameSite=Strict`; `Secure` salvo en `dev`). El valor de la token CSRF se entrega en la cookie legible `XSRF-TOKEN` **y** en la cabecera de respuesta `X-CSRF-TOKEN`. En cada `POST` autenticado (salvo `/login`) el cliente debe reenviar ese mismo valor en la cabecera `X-XSRF-TOKEN` **y** en la cookie `XSRF-TOKEN`; si falta o no coincide, la respuesta es `403` con `code = CSRF_REQUIRED` y la sesión sigue activa. En `/login` se rota la token: el cliente debe reemplazar la que tenía por la devuelta en esa respuesta.

Ejemplo de primer login (sin secretos en el comando: se toman de las variables cargadas desde `.env`):

```bash
# La cookie de sesión se guarda en cookies.txt (no versionarlo).
curl -sS -c cookies.txt -H 'Content-Type: application/json' \
  -d "{\"email\":\"$BOOTSTRAP_ADMIN_EMAIL\",\"password\":\"$BOOTSTRAP_ADMIN_PASSWORD\"}" \
  http://localhost:8080/api/v1/auth/login

# La respuesta incluye X-CSRF-TOKEN: copiar ese valor a $CSRF antes de escribir.
# Con mustChangePassword=true solo responden /me, /change-password y /logout;
# cualquier otra ruta privada devuelve 403 PASSWORD_CHANGE_REQUIRED.
curl -sS -b cookies.txt -c cookies.txt \
  -H "X-XSRF-TOKEN: $CSRF" -b "XSRF-TOKEN=$CSRF" \
  -H 'Content-Type: application/json' \
  -d "{\"currentPassword\":\"$BOOTSTRAP_ADMIN_PASSWORD\",\"newPassword\":\"$NUEVA_CONTRASENA\"}" \
  http://localhost:8080/api/v1/auth/change-password

# El cambio invalida la sesión: hay que volver a /login con la nueva contraseña.
```

Errores: `401 UNAUTHENTICATED` (sin sesión o expirada), `401 INVALID_CREDENTIALS` (credenciales, cuenta bloqueada o inexistente — el cuerpo es idéntico), `403 FORBIDDEN`, `403 PASSWORD_CHANGE_REQUIRED`, `403 CSRF_REQUIRED`, `400 VALIDATION_FAILED` y `429 RATE_LIMITED` (con `Retry-After`). Todas usan `application/problem+json` y no incluyen `WWW-Authenticate: Basic` ni redirecciones a `/login`.

### 4. Frontend

```bash
cd frontend
npm install
npm run dev                # http://localhost:5173, con proxy de /api y /actuator hacia http://localhost:8080
```

Para apuntar el proxy a otro backend: variable `BACKEND_URL` (por defecto `http://localhost:8080`). El servidor de desarrollo escucha solo en `localhost`.

### Tests

No requieren PostgreSQL instalado. Correlos desde una terminal **no elevada** (PostgreSQL se niega a arrancar con privilegios de Administrador en Windows).

```bash
cd backend
./gradlew test             # unitarios + reglas de arquitectura (ArchUnit); sin base de datos
./gradlew integrationTest  # *IT contra un PostgreSQL 17 embebido temporal (la primera vez extrae los binarios en %TEMP%)
./gradlew check            # todo
./gradlew check jacocoTestReport   # reporte en backend/build/reports/jacoco/test/html/index.html

cd ../frontend
npm test                   # Vitest
npm run typecheck
npm run build
```

La primera ejecución de `integrationTest` puede demorar por la extracción de binarios y el antivirus; si Windows Defender la bloquea, excluí la carpeta temporal `embedded-pg` de `%TEMP%`.

### Eliminar la base local

Desde `psql` (como `postgres`) o la PSQL Tool de pgAdmin; sustituí los nombres si los cambiaste en `.env`:

```sql
DROP DATABASE consultorio;
DROP ROLE app;
DROP ROLE migrator;
```

### Nota sobre OneDrive

Si el repo está en una carpeta sincronizada con OneDrive, `node_modules/`, `backend/build/` y `backend/.gradle/` generan muchísimos archivos pequeños y pueden volver lentos o bloquear los builds.
OneDrive no respeta el `.gitignore`: marcá esas carpetas como "Liberar espacio"/excluilas de la sincronización, o mové el repo fuera de OneDrive.
