# Spec Delta: runtime-configuration

## Purpose

Establece de dónde obtiene la aplicación su configuración y credenciales, cómo reacciona cuando falta algo obligatorio y en qué zona horaria persiste las fechas, para cumplir con la Ley 25.326 (sin secretos en el repositorio) y con la regla de almacenamiento en UTC.

## ADDED Requirements

### Requirement: Credenciales solo por variables de entorno
Las credenciales y secretos (contraseñas de base de datos, de email y de bootstrap) SHALL leerse exclusivamente de variables de entorno. Ningún archivo versionado MUST contener un valor real ni un valor por defecto para ellos.

#### Scenario: Archivos de configuración sin secretos
- **WHEN** se inspeccionan los archivos de configuración versionados del backend
- **THEN** las contraseñas aparecen solo como referencias a variables de entorno sin valor por defecto

#### Scenario: Plantilla de entorno completa
- **WHEN** se inspecciona `.env.example`
- **THEN** contiene todas las variables de KB 08 §Variables de entorno, y las sensibles tienen un marcador explícito (por ejemplo `change-me`) en lugar de un valor real

### Requirement: Arranque falla ante configuración obligatoria faltante
La aplicación SHALL negarse a arrancar si falta una variable obligatoria (URL o credenciales de base de datos, credenciales de migración), informando el nombre de la variable sin mostrar valores de otras.

#### Scenario: Falta la contraseña de la base
- **GIVEN** `DB_PASSWORD` no está definida
- **WHEN** se inicia la aplicación
- **THEN** el arranque falla y el error menciona `DB_PASSWORD`

#### Scenario: Configuración completa
- **GIVEN** todas las variables obligatorias definidas
- **WHEN** se inicia la aplicación
- **THEN** el arranque es exitoso

### Requirement: Fechas en UTC
La aplicación SHALL operar con UTC como zona horaria de la JVM y de las sesiones de base de datos, independientemente de la zona horaria del host.

#### Scenario: Sesión de base en UTC
- **GIVEN** el host configurado en `America/Argentina/Buenos_Aires`
- **WHEN** la aplicación consulta `SHOW TimeZone` con su conexión
- **THEN** el resultado es `UTC`

### Requirement: Perfil de desarrollo
El sistema SHALL ofrecer un perfil `dev` que aporta valores no sensibles para el entorno local (host y puerto de la base y del servidor de email de prueba), sin aportar contraseñas.

#### Scenario: Perfil dev sin contraseñas
- **WHEN** se activa el perfil `dev` sin definir `DB_PASSWORD`
- **THEN** el arranque falla igual que sin perfil, mencionando `DB_PASSWORD`
