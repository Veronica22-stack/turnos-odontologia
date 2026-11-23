# Spec Delta

## ADDED Requirements

### Requirement: Booking token table
La migración SHALL crear `booking_token` con `id`, `appointment_id` (FK a `appointment`, `ON DELETE RESTRICT`), `token_hash` único, `expires_at`, `revoked_at`, `created_at`, `privacy_notice_version` y `privacy_accepted_at`. La columna `token_hash` SHALL almacenar únicamente un hash; NO SHALL existir ninguna columna con el token en claro.

#### Scenario: Esquema de booking_token
- **GIVEN** una base migrada con todas las migraciones del change
- **WHEN** se inspecciona `booking_token`
- **THEN** sus columnas y restricciones coinciden con lo declarado y no existe ninguna columna de token en claro

#### Scenario: Hash duplicado
- **GIVEN** un `token_hash` ya persistido
- **WHEN** se intenta insertar otro token con el mismo hash
- **THEN** la base rechaza la inserción por la restricción de unicidad

### Requirement: Booking token privileges
El rol de aplicación SHALL poder insertar, leer y actualizar `booking_token`, pero NO SHALL poder borrar filas ni truncar la tabla.

#### Scenario: Borrado denegado
- **GIVEN** una fila en `booking_token`
- **WHEN** el rol de aplicación ejecuta `DELETE` o `TRUNCATE`
- **THEN** la base rechaza la operación por permisos y la fila permanece

### Requirement: Migration numbering
La nueva migración SHALL usar la siguiente versión libre mayor que la última aplicada, sin reutilizar ni editar migraciones existentes.

#### Scenario: Flyway validate
- **GIVEN** una base con V1–V15 aplicadas
- **WHEN** arranca la aplicación con la migración nueva
- **THEN** Flyway aplica la versión mayor siguiente y `validate` pasa sin errores de checksum ni versiones resueltas sin aplicar
