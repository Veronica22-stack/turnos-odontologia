# Spec Delta

## Purpose

Define las cuentas de usuario interno (en v1 solo el odontólogo, DENTIST): su pertenencia al consultorio, su rol, la unicidad del email y cómo se almacenan las contraseñas, como base para la autenticación de C-04 (RN-AU-01, RN-AU-03, DD-06).

## ADDED Requirements

### Requirement: Usuario pertenece al consultorio
Toda cuenta de usuario interno SHALL referenciar al consultorio de la instalación; la base de datos MUST rechazar una cuenta sin consultorio o con un consultorio inexistente.

#### Scenario: Consultorio inexistente
- **WHEN** se intenta guardar una cuenta que referencia un consultorio que no existe
- **THEN** la base de datos rechaza la operación por violación de clave foránea

### Requirement: Roles cerrados y extensibles
El rol de una cuenta SHALL ser uno de los valores admitidos; en v1 el único valor admitido es `DENTIST`. La base de datos MUST rechazar cualquier otro valor, de modo que sumar un rol (por ejemplo `SECRETARY` en v2) requiera una migración explícita.

#### Scenario: Rol DENTIST aceptado
- **WHEN** se guarda una cuenta con rol `DENTIST`
- **THEN** la cuenta queda persistida con ese rol

#### Scenario: Rol no admitido rechazado
- **WHEN** se intenta guardar una cuenta con rol `SECRETARY`
- **THEN** la base de datos rechaza la operación

### Requirement: Email único sin distinguir mayúsculas
El email de una cuenta SHALL guardarse normalizado (sin espacios al inicio o al final, en minúsculas) y MUST ser único en la instalación sin distinguir mayúsculas de minúsculas.

#### Scenario: Email normalizado
- **WHEN** se crea una cuenta con el email `  Dr.Perez@Ejemplo.COM `
- **THEN** la cuenta queda guardada con el email `dr.perez@ejemplo.com`

#### Scenario: Email duplicado con otras mayúsculas
- **GIVEN** una cuenta con el email `dr@ejemplo.com`
- **WHEN** se intenta guardar otra cuenta con el email `DR@ejemplo.com`
- **THEN** la base de datos rechaza la operación por violación de unicidad

### Requirement: Contraseñas almacenadas con Argon2id
El sistema SHALL almacenar solo un hash Argon2id de cada contraseña, con sal aleatoria por hash; la contraseña en claro MUST NOT persistirse ni registrarse en logs.

#### Scenario: Hash Argon2id con sal
- **WHEN** se calcula el hash de la misma contraseña dos veces
- **THEN** ambos resultados empiezan con `$argon2id$`, son distintos entre sí y ninguno contiene la contraseña en claro

#### Scenario: Verificación de contraseña
- **GIVEN** el hash de una contraseña
- **WHEN** se verifica la contraseña original y luego una contraseña distinta
- **THEN** la primera verificación es exitosa y la segunda falla

### Requirement: Estado de seguridad inicial de la cuenta
Una cuenta nueva SHALL tener cero intentos fallidos, ningún bloqueo vigente, fecha de creación en UTC y ningún último acceso; el indicador de cambio obligatorio de contraseña MUST ser explícito.

#### Scenario: Cuenta recién creada
- **WHEN** se crea una cuenta con el reloj fijado en `2026-03-10T12:00:00Z`
- **THEN** la cuenta tiene `failed_attempts = 0`, `locked_until` vacío, `last_login_at` vacío y `created_at = 2026-03-10T12:00:00Z`

#### Scenario: Intentos fallidos negativos rechazados
- **WHEN** se intenta guardar una cuenta con intentos fallidos negativos
- **THEN** la base de datos rechaza la operación

### Requirement: Identificadores no secuenciales
Las cuentas SHALL identificarse con un UUID generado por la aplicación y ordenable por tiempo (versión 7), de modo que el identificador no revele cuántas cuentas existen.

#### Scenario: Identificador versión 7
- **WHEN** se persiste una cuenta nueva
- **THEN** su identificador es un UUID de versión 7
