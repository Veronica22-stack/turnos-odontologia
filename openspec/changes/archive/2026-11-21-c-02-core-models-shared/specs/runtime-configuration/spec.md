# Spec Delta

## MODIFIED Requirements

### Requirement: Arranque falla ante configuración obligatoria faltante
La aplicación SHALL negarse a arrancar si falta una variable obligatoria (URL o credenciales de base de datos, credenciales de migración), informando el nombre de la variable sin mostrar valores de otras. Las variables de bootstrap (`BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`) SHALL ser obligatorias solo mientras el consultorio no tenga ninguna cuenta `DENTIST`.

#### Scenario: Falta la contraseña de la base
- **GIVEN** `DB_PASSWORD` no está definida
- **WHEN** se inicia la aplicación
- **THEN** el arranque falla y el error menciona `DB_PASSWORD`

#### Scenario: Configuración completa
- **GIVEN** todas las variables obligatorias definidas
- **WHEN** se inicia la aplicación
- **THEN** el arranque es exitoso

#### Scenario: Falta una variable de bootstrap en una instalación nueva
- **GIVEN** una base sin cuentas `DENTIST` y `BOOTSTRAP_ADMIN_PASSWORD` no definida
- **WHEN** se inicia la aplicación
- **THEN** el arranque falla, el error menciona `BOOTSTRAP_ADMIN_PASSWORD` y no muestra el valor de `BOOTSTRAP_ADMIN_EMAIL`

#### Scenario: Variables de bootstrap innecesarias en una instalación existente
- **GIVEN** una base con una cuenta `DENTIST` y sin variables de bootstrap definidas
- **WHEN** se inicia la aplicación
- **THEN** el arranque es exitoso
