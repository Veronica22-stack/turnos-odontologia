# runtime-configuration Specification

## Purpose
Establece de dónde obtiene la aplicación su configuración y credenciales, cómo reacciona cuando falta algo obligatorio y en qué zona horaria persiste las fechas, para cumplir con la Ley 25.326 (sin secretos en el repositorio) y con la regla de almacenamiento en UTC.

## Requirements

### Requirement: Credenciales solo por variables de entorno
Las credenciales y secretos (contraseñas de base de datos, de email y de bootstrap) SHALL leerse exclusivamente de variables de entorno. Ningún archivo versionado MUST contener un valor real ni un valor por defecto para ellos.

#### Scenario: Archivos de configuración sin secretos
- **WHEN** se inspeccionan los archivos de configuración versionados del backend
- **THEN** las contraseñas aparecen solo como referencias a variables de entorno sin valor por defecto

#### Scenario: Plantilla de entorno completa
- **WHEN** se inspecciona `.env.example`
- **THEN** contiene todas las variables de KB 08 §Variables de entorno, y las sensibles tienen un marcador explícito (por ejemplo `change-me`) en lugar de un valor real

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

### Requirement: Fechas en UTC
La aplicación SHALL operar con UTC como zona horaria de la JVM y de las sesiones de base de datos, independientemente de la zona horaria del host.

#### Scenario: Sesión de base en UTC
- **GIVEN** el host configurado en `America/Argentina/Buenos_Aires`
- **WHEN** la aplicación consulta `SHOW TimeZone` con su conexión
- **THEN** el resultado es `UTC`

### Requirement: Configuración de sesión y protección CSRF
El sistema SHALL leer la configuración de la sesión y de CSRF desde variables de entorno o propiedades de perfil, sin credenciales en el repositorio: `AUTH_SESSION_TIMEOUT_MIN` (minutos de inactividad, por defecto `30`), `AUTH_SESSION_COOKIE_NAME` (por defecto `SESSIONID`), `AUTH_SESSION_COOKIE_SECURE` (por defecto `true`, salvo en el perfil `dev`) y `CORS_ALLOWED_ORIGINS` (lista de orígenes permitidos, sin comodín en producción). El nombre de la cookie CSRF SHALL ser fijo (`XSRF-TOKEN`) y la cabecera exigida SHALL ser `X-XSRF-TOKEN`. Un valor inválido de `AUTH_SESSION_TIMEOUT_MIN` SHALL hacer fallar el arranque mencionando el nombre de la variable, sin mostrar otros valores.

#### Scenario: Valores por defecto
- **GIVEN** un entorno sin variables de sesión definidas
- **WHEN** arranca la aplicación con el perfil de producción
- **THEN** la sesión usa `SESSIONID`, 30 minutos de inactividad, cookie `Secure` y exige `X-XSRF-TOKEN`

#### Scenario: Timeout personalizado
- **GIVEN** `AUTH_SESSION_TIMEOUT_MIN = 5`
- **WHEN** arranca la aplicación
- **THEN** las nuevas sesiones vencen a los cinco minutos de inactividad

#### Scenario: Timeout inválido
- **GIVEN** `AUTH_SESSION_TIMEOUT_MIN = abc`
- **WHEN** arranca la aplicación
- **THEN** el arranque falla, el error menciona `AUTH_SESSION_TIMEOUT_MIN` y no muestra otras variables

#### Scenario: Desarrollo local HTTP
- **GIVEN** el perfil `dev` activo
- **WHEN** arranca la aplicación
- **THEN** `AUTH_SESSION_COOKIE_SECURE` es `false` y la cookie de sesión no exige HTTPS

#### Scenario: Producción sin orígenes declarados
- **GIVEN** el perfil de producción y `CORS_ALLOWED_ORIGINS` vacío
- **WHEN** arranca la aplicación
- **THEN** el arranque falla mencionando `CORS_ALLOWED_ORIGINS` y no se habilita un comodín

#### Scenario: Orígenes permitidos explícitos
- **GIVEN** `CORS_ALLOWED_ORIGINS = https://consultorio.example.ar`
- **WHEN** arranca la aplicación
- **THEN** solo ese origen puede enviar solicitudes con credenciales

### Requirement: Perfil de desarrollo
El sistema SHALL ofrecer un perfil `dev` que aporta valores no sensibles para el entorno local (host, puerto y nombre de la base local, nivel de logging), sin aportar contraseñas. El perfil `dev` SHALL desactivar el atributo `Secure` de la cookie de sesión para permitir HTTP local, sin relajar `HttpOnly`, `SameSite=Strict` ni la verificación CSRF.

#### Scenario: Perfil dev sin contraseñas
- **WHEN** se activa el perfil `dev` sin definir `DB_PASSWORD`
- **THEN** el arranque falla igual que sin perfil, mencionando `DB_PASSWORD`

#### Scenario: Dev con cookie no segura
- **GIVEN** el perfil `dev` activo
- **WHEN** se inspecciona la configuración de la cookie de sesión
- **THEN** `Secure` está desactivado, pero `HttpOnly` y `SameSite=Strict` permanecen activos y CSRF sigue exigiéndose
