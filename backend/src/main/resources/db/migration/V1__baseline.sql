-- Baseline (C-01). Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).

-- Necesaria para las restricciones de exclusion de turnos. Es una extension "trusted": la puede crear el dueno
-- de la base sin ser superusuario.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Toda tabla o secuencia que cree el rol de migracion en `public` queda con DML para el rol de aplicacion,
-- sin necesidad de un GRANT en cada migracion. El rol de aplicacion nunca recibe DDL.
ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_role};
ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ${app_role};

-- Patron para tablas inmutables (audit_log en C-03, entradas de historia clinica): en la migracion que crea la
-- tabla, revocar la mutacion al rol de aplicacion:
--     REVOKE UPDATE, DELETE ON <tabla> FROM ${app_role};
