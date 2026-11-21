-- Crea la base y los roles de la aplicacion en un PostgreSQL 17 instalado localmente.
-- Es IDEMPOTENTE: se puede volver a ejecutar (tambien para rotar contrasenas) sin perder datos.
-- No contiene credenciales: recibe todo por variables de psql (-v), definidas por cada integrante en su `.env`.
--
--   db_name            base de la aplicacion (dueno: el rol de migracion)     <- DB_NAME
--   migrator_user      rol con DDL, usado solo por Flyway                     <- DB_MIGRATION_USER
--   migrator_password                                                         <- DB_MIGRATION_PASSWORD
--   app_user           rol de la aplicacion (solo DML, sin DDL)               <- DB_USER
--   app_password                                                              <- DB_PASSWORD
--
-- Ejecutar como superusuario, p. ej. (ver README, "Desarrollo local"):
--   psql -U postgres -h localhost -v ON_ERROR_STOP=1 -v db_name=... -v migrator_user=... \
--        -v migrator_password=... -v app_user=... -v app_password=... -f infra/postgres/init/01-roles.sql
--
-- Dialecto acotado (los tests ejecutan este mismo archivo con un interprete propio, ver RolesScript):
-- comentarios `--`, sentencias terminadas en `;`, sentencias terminadas en `\gexec`, el metacomando `\connect`
-- y referencias :'var' / :"var". No agregar otros metacomandos ni bloques $$.
--
-- Los privilegios sobre tablas y secuencias del rol de aplicacion se conceden en V1__baseline.sql (Flyway).
-- `CREATE EXTENSION btree_gist` tampoco va aqui: lo hace V1.

-- Falla ANTES de crear nada si falta alguna variable: psql deja sin sustituir la referencia que falta
-- y la sentencia es un error de sintaxis (con ON_ERROR_STOP=1 el script se detiene).
-- Las variables van solo en el WHERE: psql imprime el resultado y las contrasenas no deben verse en pantalla.
-- Una variable por linea: ante una variable faltante psql muestra la linea del error, que solo contiene esa referencia.
SELECT 'variables completas' AS verificacion
WHERE :'db_name' <> ''
  AND :'migrator_user' <> ''
  AND :'migrator_password' <> ''
  AND :'app_user' <> ''
  AND :'app_password' <> '';

-- Roles: se crean solo si no existen (CREATE ROLE no tiene IF NOT EXISTS) ...
SELECT format('CREATE ROLE %I', :'migrator_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migrator_user')
\gexec

SELECT format('CREATE ROLE %I', :'app_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')
\gexec

-- ... y siempre se (re)definen sus atributos y contrasena (permite rotar credenciales).
SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'migrator_user', :'migrator_password')
\gexec

SELECT format('ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'app_user', :'app_password')
\gexec

-- Base: solo si no existe (CREATE DATABASE no puede ir dentro de un bloque DO). UTF8 sin depender del idioma del SO.
SELECT format('CREATE DATABASE %I OWNER %I ENCODING ''UTF8'' TEMPLATE template0', :'db_name', :'migrator_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')
\gexec

REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db_name" TO :"app_user";

\connect :"db_name"

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO :"migrator_user";
GRANT USAGE ON SCHEMA public TO :"app_user";
