# Sistema de Gestión Odontológica (nombre a definir)

Aplicación web para un odontólogo independiente en Argentina: agenda, reserva online sin cuenta e historia clínica inmutable.
Monolito modular: `backend/` (Spring Boot + Gradle), `frontend/` (SPA en TypeScript vanilla con Vite) e `infra/` (scripts de base de datos).
La documentación de dominio vive en `knowledge-base/`; el plan de trabajo en `CHANGES.md`.

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

#### Primer arranque: cuenta del odontólogo

En el **primer** arranque contra una base nueva, el backend crea la cuenta del odontólogo (rol `DENTIST`). Para eso necesita
dos variables en tu `.env` (ya están en `.env.example` con el marcador `change-me`), definidas **antes** del primer `bootRun`:

- `BOOTSTRAP_ADMIN_EMAIL`: email de la cuenta (se guarda en minúsculas).
- `BOOTSTRAP_ADMIN_PASSWORD`: contraseña inicial de **al menos 12 caracteres**.

Si faltan o no son válidas, el arranque falla con un mensaje que nombra la variable (nunca su valor). La contraseña inicial
es provisoria: la cuenta queda marcada para **cambiarla en el primer login** (pantalla de acceso: change C-04). Los arranques
siguientes no leen estas variables: si ya existe un `DENTIST` no se crea ni se modifica nada, por eso conviene **quitar las
variables del `.env` después del primer arranque**. El log solo muestra el id de la cuenta, nunca el email ni la contraseña.

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
