# Spec Delta

## Purpose

Garantiza que una instalación nueva tenga su odontólogo (DENTIST) inicial, creado al arrancar a partir de credenciales de bootstrap no versionadas, de forma idempotente y sin exponer esas credenciales (KB 04 §Seed data inicial, Ley 25.326).

## ADDED Requirements

### Requirement: Alta inicial del odontólogo
Al arrancar, si el consultorio de la instalación no tiene ninguna cuenta `DENTIST`, el sistema SHALL crear una con el email de `BOOTSTRAP_ADMIN_EMAIL`, el hash Argon2id de `BOOTSTRAP_ADMIN_PASSWORD` y el cambio de contraseña obligatorio activado.

#### Scenario: Primera puesta en marcha
- **GIVEN** una base migrada sin cuentas y las variables de bootstrap definidas con un email y una contraseña válidos
- **WHEN** la aplicación arranca
- **THEN** existe exactamente una cuenta `DENTIST` con ese email normalizado, `must_change_password = true` y un hash que empieza con `$argon2id$`

### Requirement: Bootstrap idempotente
Si ya existe al menos una cuenta `DENTIST` en el consultorio, el bootstrap SHALL no crear, modificar ni reemplazar ninguna cuenta, aunque las variables de bootstrap estén definidas o tengan otros valores, y MUST NOT exigirlas.

#### Scenario: Re-arranque con las mismas variables
- **GIVEN** una instalación en la que el bootstrap ya creó la cuenta `DENTIST`
- **WHEN** la aplicación arranca otra vez con las mismas variables
- **THEN** sigue existiendo exactamente una cuenta `DENTIST` y su hash de contraseña no cambió

#### Scenario: Re-arranque con otra contraseña
- **GIVEN** una instalación con la cuenta `DENTIST` ya creada
- **WHEN** la aplicación arranca con `BOOTSTRAP_ADMIN_PASSWORD` distinta
- **THEN** la contraseña de la cuenta existente no cambia

#### Scenario: Re-arranque sin variables
- **GIVEN** una instalación con la cuenta `DENTIST` ya creada
- **WHEN** la aplicación arranca sin `BOOTSTRAP_ADMIN_EMAIL` ni `BOOTSTRAP_ADMIN_PASSWORD`
- **THEN** el arranque es exitoso

### Requirement: Credenciales de bootstrap válidas
El bootstrap SHALL rechazar un email sin formato de email y una contraseña de menos de 12 caracteres, haciendo fallar el arranque con un error que nombra la variable inválida y MUST NOT incluir su valor.

#### Scenario: Contraseña corta
- **GIVEN** una base sin cuentas `DENTIST` y `BOOTSTRAP_ADMIN_PASSWORD` de 8 caracteres
- **WHEN** la aplicación arranca
- **THEN** el arranque falla, el error menciona `BOOTSTRAP_ADMIN_PASSWORD`, no contiene la contraseña y no se crea ninguna cuenta

#### Scenario: Email inválido
- **GIVEN** una base sin cuentas `DENTIST` y `BOOTSTRAP_ADMIN_EMAIL` igual a `no-es-un-email`
- **WHEN** la aplicación arranca
- **THEN** el arranque falla, el error menciona `BOOTSTRAP_ADMIN_EMAIL` y no contiene el valor

### Requirement: Credenciales de bootstrap fuera de los logs
El bootstrap MUST NOT escribir en los logs ni en mensajes de error el email ni la contraseña de bootstrap; solo SHALL registrar el resultado (cuenta creada u omitida) y el identificador de la cuenta creada.

#### Scenario: Log del alta
- **GIVEN** una base sin cuentas `DENTIST` y variables de bootstrap válidas
- **WHEN** la aplicación arranca y crea la cuenta
- **THEN** la salida de log contiene el identificador de la cuenta y no contiene ni el email ni la contraseña de bootstrap
