# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Perfil de desarrollo
El sistema SHALL ofrecer un perfil `dev` que aporta valores no sensibles para el entorno local (host, puerto y nombre de la base local, nivel de logging), sin aportar contraseñas. El perfil `dev` SHALL desactivar el atributo `Secure` de la cookie de sesión para permitir HTTP local, sin relajar `HttpOnly`, `SameSite=Strict` ni la verificación CSRF.

#### Scenario: Perfil dev sin contraseñas
- **WHEN** se activa el perfil `dev` sin definir `DB_PASSWORD`
- **THEN** el arranque falla igual que sin perfil, mencionando `DB_PASSWORD`

#### Scenario: Dev con cookie no segura
- **GIVEN** el perfil `dev` activo
- **WHEN** se inspecciona la configuración de la cookie de sesión
- **THEN** `Secure` está desactivado, pero `HttpOnly` y `SameSite=Strict` permanecen activos y CSRF sigue exigiéndose
