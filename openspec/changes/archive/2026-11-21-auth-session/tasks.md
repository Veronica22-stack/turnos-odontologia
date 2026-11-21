# Tasks

> Orden obligatorio en **cada** grupo: RED → GREEN → TRIANGULATE → REFACTOR. No se escribe código de producción hasta ver el test fallar por la razón esperada. Todos los comandos se ejecutan desde `backend/` salvo `openspec`.

## 1. Arquitectura, configuración y contratos base

- [x] 1.1 RED: agregar fixtures `archfixture/ok_auth/...` con `auth.application → auditoria.api` y `auth.api → pacientes.api`, y asserts en `ModuleBoundariesRulesTest` / `ProductionArchitectureTest` esperando que la primera pase y la segunda falle; verificar que `./gradlew test --tests '*ModuleBoundariesRulesTest'` falla porque `auth → auditoria` no está en el grafo.
- [x] 1.2 RED: ampliar `MissingConfigurationTest` y `ConfigurationHygieneTest` con: `AUTH_SESSION_TIMEOUT_MIN=abc` debe fallar nombrando la variable, perfil productivo sin `CORS_ALLOWED_ORIGINS` debe fallar, y `.env.example` debe contener `AUTH_SESSION_COOKIE_NAME` y `AUTH_SESSION_COOKIE_SECURE` sin valores sensibles; verificar que fallan.
- [x] 1.3 GREEN: agregar `auth → auditoria` a `ALLOWED_DEPENDENCIES`, extender el recorrido transitivo para prohibir `auth → módulos de negocio`, agregar la regla de confinamiento de seguridad en `auth.infrastructure`, y validar las propiedades nuevas en `RequiredConfiguration`; verificar que los tests de 1.1 y 1.2 pasan.
- [x] 1.4 GREEN: documentar las dos variables nuevas en `.env.example` y el significado de `AUTH_SESSION_TIMEOUT_MIN` / `CORS_ALLOWED_ORIGINS` en el README de configuración; verificar con `grep` que aparecen y que no hay valores reales.
- [x] 1.5 TRIANGULATE: agregar los casos de arista transitiva `auth → auditoria → pacientes` y de `auth.api` usando `shared.api.RateLimiter`, verificar que fallan y ajustar las reglas hasta que pasen sin permitir la dependencia prohibida.
- [x] 1.6 TRIANGULATE: agregar caso de timeout `0` y negativo, y caso de perfil `dev` con cookie `Secure=false`; verificar que fallan y ajustar la validación/perfil hasta que pasen.
- [x] 1.7 REFACTOR: extraer constantes de nombres de variables y módulos, ejecutar `./gradlew test --tests '*ModuleBoundariesRulesTest' --tests '*ProductionArchitectureTest' --tests '*MissingConfigurationTest' --tests '*ConfigurationHygieneTest'` y verificar todo en verde sin cambios de comportamiento.

## 2. Dominio de seguridad de la cuenta

- [x] 2.1 RED: escribir en `AppUserTest` los casos de primer fallo (incrementa `failed_attempts` sin bloqueo), quinto fallo (bloqueo de 15 min), undécimo y decimoquinto fallo (30 y 60 min), intento durante bloqueo (no incrementa) y login correcto (reinicia contador, limpia bloqueo y fija `last_login_at = clock.instant()`); verificar que la clase no compila por los métodos ausentes.
- [x] 2.2 RED: agregar casos de contraseña: nueva de 11 caracteres rechazada, de 12 aceptada, y limpieza de `must_change_password`; verificar que fallan.
- [x] 2.3 GREEN: crear `LockoutPolicy` con umbrales `5/10/15`, duraciones `15/30/60` min y techo, y agregar los mutadores `registerFailedAttempt`, `registerSuccessfulLogin`, `changePassword` y `clearMustChangePassword` en `AppUser`; verificar que 2.1 y 2.2 pasan sin Spring ni JPA.
- [x] 2.4 TRIANGULATE: agregar casos de fallo 16 y 17 (techo de 60 min sin crecimiento), bloqueo vencido reanudando escalada y `Clock` fijo en UTC; verificar que fallan y ajustar la política hasta que pasen.
- [x] 2.5 TRIANGULATE: agregar caso de `must_change_password = true` que permanece intacto ante un fallo de login; verificar que falla y corregir el mutador.
- [x] 2.6 REFACTOR: eliminar duplicación en el cálculo de duraciones, ejecutar `./gradlew test --tests '*AppUserTest'` y verificar cobertura de los escenarios del spec `user-accounts` sin tocar la migración V2.

## 3. Limitador de tasa compartido

- [x] 3.1 RED: crear `InMemoryRateLimiterTest` en `shared.infrastructure` con casos: primer intento permitido, décimo permitido, undécimo rechazado con `Retry-After` positivo, otra clave no afectada y expiración de la ventana tras avanzar el reloj; verificar que falla por `RateLimiter` inexistente.
- [x] 3.2 RED: agregar caso de clave con email en claro en la excepción/mensaje; verificar que falla porque la implementación propuesta no puede existir todavía.
- [x] 3.3 GREEN: definir `shared.api.RateLimiter` con `Optional<Instant> tryAcquire(String key, Duration window, Clock clock)` e implementar `InMemoryRateLimiter` con `ConcurrentHashMap` y digest SHA-256 de la clave; verificar que 3.1 y 3.2 pasan.
- [x] 3.4 TRIANGULATE: agregar caso de dos claves que colisionan solo por distinta IP y caso de ventana exactamente en el límite (`now == expiresAt`); verificar que fallan y ajustar hasta que pasen.
- [x] 3.5 REFACTOR: extraer el cálculo de `Retry-After` a un método puro, ejecutar `./gradlew test --tests '*InMemoryRateLimiterTest'` y verificar que no aparece email ni IP en mensajes ni logs.

## 4. Caso de uso de login y auditoría

- [x] 4.1 RED: crear `AuthenticateUserTest` en `auth.application` con fakes de repositorio, hasher, reloj, limitador y auditoría; casos: login correcto devuelve principal con `userId/role/mustChangePassword` y emite `LOGIN_OK`; contraseña incorrecta emite `LOGIN_FAIL` y `401 INVALID_CREDENTIALS`; email inexistente usa hash de referencia y emite `LOGIN_FAIL` con `userId=null`; cuenta bloqueada no ejecuta el hasher; límite excedido devuelve `429 RATE_LIMITED`; verificar que falla por clases ausentes.
- [x] 4.2 RED: agregar aserciones de que ni el email, ni la contraseña, ni el User-Agent crudo aparecen en `AuditEvent.details()` ni en `toString()`; verificar que fallan.
- [x] 4.3 GREEN: implementar `AuthenticateUser`, `AuthenticatedUser`, puerto de sesión y `LoginResult`, con normalización de email, dummy hash para cuenta inexistente, integración con `RateLimiter` y `AuditLogger`; verificar que 4.1 y 4.2 pasan.
- [x] 4.4 TRIANGULATE: agregar casos de auditoría que falla (propaga error genérico, no crea sesión), de IP inválida normalizada por `AuditEvent.of` y de `User-Agent` reemplazado por digest `ua:<16 hex>`; verificar que fallan y ajustar hasta que pasen.
- [x] 4.5 REFACTOR: separar la creación del evento de auditoría en un mapper privado, ejecutar `./gradlew test --tests '*AuthenticateUserTest'` y verificar que no hay `System.out`, logs con datos ni dependencias de módulos de negocio.

## 5. Cambio de contraseña y logout

- [x] 5.1 RED: crear `ChangeOwnPasswordTest` con casos: contraseña actual incorrecta → error genérico sin revelar cuál falló; nueva de 8 caracteres → `VALIDATION_FAILED` con `errors[].field` sin el valor; cambio correcto → hash actualizado, `must_change_password=false` y todas las sesiones invalidadas; verificar que falla.
- [x] 5.2 RED: crear `LogoutUserTest` con casos: logout con sesión la invalida en el registro; logout sin sesión es idempotente y no emite evento de auditoría nuevo; verificar que falla.
- [x] 5.3 GREEN: implementar `ChangeOwnPassword`, `LogoutUser` y el puerto `SessionInvalidation` con su adaptador de `SessionRegistry` en `auth.infrastructure`; verificar que 5.1 y 5.2 pasan.
- [x] 5.4 TRIANGULATE: agregar caso de segundo cambio de contraseña con bandera ya limpia y caso de sesión de otro usuario que NO debe invalidarse; verificar que fallan y ajustar hasta que pasen.
- [x] 5.5 REFACTOR: unificar la validación de longitud de contraseña con la política de 12–128 caracteres reutilizada por bootstrap, ejecutar `./gradlew test --tests '*ChangeOwnPasswordTest' --tests '*LogoutUserTest'` y verificar verde.

## 6. Capa HTTP: endpoints, sesión, CSRF y errores

- [x] 6.1 RED: crear `AuthEndpointsIT` sobre `AbstractIntegrationTest` con MockMvc y casos: `GET /actuator/health` público; `GET /api/v1/agenda/...` sin sesión → `401 UNAUTHENTICATED`; `POST /api/v1/auth/login` correcto → `200`, cookies `SESSIONID`/`XSRF-TOKEN` con atributos y token en `X-CSRF-TOKEN`; verificar que falla por rutas inexistentes.
- [x] 6.2 RED: agregar casos HTTP de CSRF: `POST /api/v1/auth/change-password` y `/logout` sin `X-XSRF-TOKEN` → `403 CSRF_REQUIRED` con sesión intacta; `POST /api/v1/auth/login` sin CSRF no se rechaza; verificar que fallan.
- [x] 6.3 RED: agregar casos de contrato: `GET /api/v1/auth/me` sin DNI/email/teléfono y con `expiresAt` UTC; rol distinto de `DENTIST` → `403 FORBIDDEN`; sesión con `must_change_password=true` → `403 PASSWORD_CHANGE_REQUIRED` salvo en `/me`, `/logout`, `/change-password`; verificar que fallan.
- [x] 6.4 RED: agregar casos de cabeceras/CORS: `nosniff`, `DENY`, `no-referrer`, CSP, HSTS solo con `isSecure()`, y origen no listado rechazado sin `*`; verificar que fallan.
- [x] 6.5 GREEN: implementar `AuthController` con DTOs records + Bean Validation, reescribir `SecurityConfig` con la cadena D6, cookie `SESSIONID`, `CookieCsrfTokenRepository`, rotación de token, `sessionFixation().migrateSession()`, timeout configurable, handlers de 401/403 vía `ProblemDetails`, CORS y cabeceras; verificar que 6.1–6.4 pasan.
- [x] 6.6 GREEN: documentar en README el contrato de los cuatro endpoints, la cabecera `X-XSRF-TOKEN` y el flujo de primer login; verificar que el comando copiado del README funciona y no muestra secretos.
- [x] 6.7 TRIANGULATE: agregar casos de expiración por inactividad con reloj manipulado, logout sin sesión → `204`, credenciales inválidas e inexistente con cuerpo idéntico, y límite excedido → `429` con `Retry-After`; verificar que fallan y ajustar hasta que pasen.
- [x] 6.8 REFACTOR: extraer configuración de seguridad a `@Configuration` pequeños dentro de `auth.infrastructure`, ejecutar `./gradlew test --tests '*AuthEndpointsIT'` y verificar que no se agregó `WWW-Authenticate: Basic` ni redirección a `/login`.

## 7. Invalidación de sesiones y bloqueo de ruta por cambio obligatorio

- [x] 7.1 RED: crear `ForcedPasswordChangeIT` con casos: con `must_change_password=true`, `/me`, `/logout` y `/change-password` responden; cualquier otra ruta privada → `403 PASSWORD_CHANGE_REQUIRED`; tras el cambio, la cookie anterior → `401`; verificar que falla.
- [x] 7.2 RED: agregar caso de dos sesiones activas del mismo usuario: cambiar contraseña en una invalida ambas; verificar que falla.
- [x] 7.3 GREEN: registrar `SessionRegistryImpl` y `HttpSessionEventPublisher`, conectar `SessionInvalidation` y el `AuthorizationManager` del gate; verificar que 7.1 y 7.2 pasan.
- [x] 7.4 TRIANGULATE: agregar caso de sesión expirada que no se renueva y caso de logout que invalida solo la sesión actual; verificar que fallan y ajustar hasta que pasen.
- [x] 7.5 REFACTOR: ejecutar `./gradlew test --tests '*ForcedPasswordChangeIT' --tests '*ChangeOwnPasswordTest' --tests '*LogoutUserTest'` y verificar que el registro de sesiones no almacena email, rol en claro fuera del servidor ni datos clínicos.

## 8. Verificación final de arquitectura e integración

- [x] 8.1 Ejecutar `./gradlew test` completo y verificar que pasan unitarios, IT con PostgreSQL embebido, ArchUnit y los tests nuevos de todos los grupos, sin migraciones Flyway nuevas.
- [x] 8.2 Ejecutar `openspec validate auth-session --strict` y verificar que proposal, specs, design y tasks están coherentes y sin hallazgos.
- [x] 8.3 Verificar con `git status --short` que solo cambiaron archivos bajo `backend/`, `.env.example` y documentación permitida; confirmar que no se modificaron `knowledge-base/`, `CHANGES.md`, `AGENTS.md` ni se hicieron commits.
