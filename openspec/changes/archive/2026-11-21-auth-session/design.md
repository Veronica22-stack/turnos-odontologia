# Design

## Context

Ver `proposal.md` para la motivación y el alcance funcional. Este documento solo fija las decisiones técnicas que faltaban para poder implementar C-04 sin volver a decidir durante el apply.

Estado del repositorio que condiciona el diseño:

- `SecurityConfig` hoy es `denyAll` con única ruta pública `GET /actuator/health` (C-01). No hay filtro de login ni mecanismo de sesión.
- `AppUser` ya persiste `failed_attempts`, `locked_until`, `must_change_password`, `created_at` y `last_login_at` (V2), pero no expone mutadores de estado de seguridad. **No hace falta una migración Flyway nueva**.
- `PasswordHasher` / `Argon2PasswordHasher` (Argon2id + Bouncy Castle) y `BootstrapDentist` ya existen (C-02). La cuenta bootstrap nace con `must_change_password = true`.
- `auditoria.api.AuditLogger` ya expone `LOGIN_OK` / `LOGIN_FAIL`, y `AuditLoggerService` sanitiza `details`, fija `practiceId`/`occurredAt` y falla cerrado si no puede persistir.
- `shared.api.ProblemDetails` y `ApiExceptionHandler` ya producen RFC 9457 sin `detail` dinámico.
- `ModuleBoundaries.ALLOWED_DEPENDENCIES` todavía no incluye `auth → auditoria`; la arista debe agregarse en el mismo change.
- PA-04 quedó resuelta por el usuario: **sesión de servidor con cookie + CSRF**. No se adopta JWT.

Restricción transversal: ninguna credencial, token ni dato clínico en logs; fechas en UTC; `domain` sin Spring; inyección por constructor.

## Goals / Non-Goals

**Goals:**

- Convertir la cadena `denyAll` en una cadena autenticada con cuatro endpoints propios y reglas explícitas para el resto de `/api/v1/**`.
- Hacer que la sesión viva solo en el servidor y que el cliente tenga únicamente un identificador opaco.
- Reutilizar sin duplicar los componentes ya presentes (hasher, repositorio de usuarios, auditoría, errores, reloj UTC).
- Mantener `auth` aislado de los módulos de negocio, con una sola arista nueva hacia `auditoria.api`.
- Dejar un limitador de tasa en `shared.api` reutilizable por C-08 y demás rutas públicas.

**Non-Goals (decisiones de diseño, no recortes del proposal):**

- No se agrega Spring Session, Redis, JWT ni ninguna dependencia de runtime nueva.
- No se implementa persistencia, replicación ni clustering de sesiones; el MVP corre en una sola instancia.
- No se crean tablas ni migraciones: los estados ya tienen columna.
- No se implementa 2FA, recuperación de contraseña ni alta de usuarios por UI.
- No se modifica el frontend en este change; el contrato HTTP queda listo para C-05.

## Decisions

### D1. Sesión `HttpSession` en memoria del servidor

Se usa la `HttpSession` del contenedor embebido. El `SecurityContext` (con un principal propio `AuthenticatedUser` que solo expone `userId`, `practiceId`, `role` y `mustChangePassword`) se guarda en la sesión mediante `SecurityContextRepository` estándar.

- **Por qué:** ya viene con Spring Security, no agrega dependencias ni esquema, y cumple literalmente "sesión en el servidor".
- **Alternativas consideradas:**
  - *JWT stateless*: descartado; no permite invalidación inmediata y contradice PA-04.
  - *Spring Session + Redis*: descartado por ahora; introduce infraestructura nueva y depende de PA-03 (hosting). Queda como evolución futura.
- **Consecuencia:** al reiniciar la app se pierden las sesiones. Está documentado como limitación aceptada del MVP.

### D2. Cookie de sesión opaca y rotación

Nombre `SESSIONID`, valor opaco generado por el contenedor, `HttpOnly`, `SameSite=Strict`, `Secure` salvo perfil `dev`, y `Path=/`. No lleva rol, email ni vencimiento.

En login correcto se llama a `sessionFixation().migrateSession()`: se crea un id nuevo y se descarta el anónimo previo. En logout se invalida la sesión y se envía `Set-Cookie: SESSIONID=; Max-Age=0` con los mismos atributos.

- **Por qué:** `migrateSession` es la defensa estándar contra fijación sin cambiar el modelo de sesión.
- **Alternativa considerada:** `newSession()`; descartada porque pierde atributos no sensibles que pueda necesitar el flujo y `changeSessionId()` no crea una sesión nueva cuando no existe.

### D3. CSRF double-submit con rotación en login

`CookieCsrfTokenRepository.withHttpOnlyFalse()` con cookie `XSRF-TOKEN` y cabecera `X-XSRF-TOKEN`. El token se emite en el login (inyectando el `CsrfToken` en el controlador para forzar su generación) y se devuelve además como cabecera de respuesta `X-CSRF-TOKEN` para que el SPA no dependa de leer cookies. En cada login correcto se llama explícitamente a `saveNewToken(...)` para rotar la token junto con el id de sesión.

Reglas: `/auth/login` es CSRF-exempt; `GET`/`HEAD`/`OPTIONS` no exigen token; el resto de métodos sobre rutas autenticadas sí. Un token ausente o distinto se rechaza con `403` / `CSRF_REQUIRED` antes de ejecutar el caso de uso.

- **Por qué:** es el mecanismo que fijó el usuario para PA-04 y encaja con un SPA que hace `fetch` con credenciales.
- **Alternativas consideradas:**
  - *Confiar solo en `SameSite=Strict`*: insuficiente; no cubre todos los navegadores ni subdominios.
  - *Token en la sesión + endpoint para leerlo*: más acoplamiento con la API y obliga al frontend a hacer un request extra.
  - *XOR token de Spring Security*: añade ofuscación contra BREACH, pero complica el contrato del SPA; se puede incorporar después sin cambiar los specs.

### D4. Bloqueo progresivo como comportamiento de dominio

`AppUser` gana métodos explícitos: `registerFailedAttempt(Clock, LockoutPolicy)`, `registerSuccessfulLogin(Clock)`, `changePassword(String hash)` y `clearMustChangePassword()`. `LockoutPolicy` es un valor inmutable con umbrales `5 / 10 / 15` y duraciones `15 / 30 / 60` minutos (techo). Con un solo contador:

- fallos 1–4: solo incrementa `failed_attempts`;
- fallo 5 → `locked_until = now + 15 min`;
- fallos 6–9: incrementa sin nuevo bloqueo;
- fallo 10 → `now + 30 min`;
- fallo 15 o más → `now + 60 min` (techo, nunca bloqueo permanente);
- durante un bloqueo vigente la petición se rechaza sin incrementar ni consultar el hasher;
- login correcto reinicia contador, limpia `locked_until` y fija `last_login_at = clock.instant()`.

- **Por qué:** el estado ya existe en la tabla y la regla RN-AU debe ser verificable con tests puros de dominio, sin PostgreSQL ni Spring.
- **Alternativa considerada:** persistir `last_locked_at` para reiniciar ventanas; descartado porque exigiría migración y el techo de 60 min ya evita bloqueo indefinido.

### D5. Limitador de tasa en `shared`

Nuevo puerto `shared.api.RateLimiter` con método `tryAcquire(String key, Duration window)` que devuelve el instante de reintento o vacío. Implementación `InMemoryRateLimiter` en `shared.infrastructure` con `ConcurrentHashMap<String, Ventana>` y expiración perezosa por comparación de `Instant` (sin hilos nuevos ni dependencias).

Clave: digest SHA-256 de `ip + "|" + emailNormalizado`; ni la IP ni el email se guardan en claro, se loguean o se devuelven. Límite propuesto: **10 intentos por minuto por combinación**. Excederlo devuelve `429` / `RATE_LIMITED` con `Retry-After` calculado en segundos.

- **Por qué:** el scope pide infraestructura reutilizable por rutas públicas; ponerlo en `shared.api` evita que `reservas` dependa de `auth`.
- **Alternativa considerada:** usar el mismo contador de `AppUser` como rate limit; descartado porque no protege contra un atacante que prueba muchos emails y porque mezcla dos defensas distintas.
- **Trusted proxy:** la IP se toma de `request.getRemoteAddr()` salvo que esté configurado `server.forward-headers-strategy=framework` con proxies de confianza; nunca se confía en `X-Forwarded-For` provisionado por el cliente.

### D6. Autorización y manejo de errores de seguridad

`authorizeHttpRequests` con este orden:

1. `permitAll`: `GET /actuator/health`, `POST /api/v1/auth/login`, `/api/v1/public/**`;
2. `authenticated` sin importar el cambio de contraseña: `/api/v1/auth/me`, `/logout`, `/change-password`;
3. `hasRole("DENTIST")`: cualquier otra ruta bajo `/api/v1/**`;
4. `anyRequest().denyAll()`.

Se habilita `@EnableMethodSecurity` y se usa `@PreAuthorize("hasRole('DENTIST')")` en los controladores privados como segunda barrera. `AuthenticationEntryPoint` y `AccessDeniedHandler` propios delegan en `ProblemDetails.of(...)` con códigos `UNAUTHENTICATED` / `FORBIDDEN` / `PASSWORD_CHANGE_REQUIRED`. Se desactivan `formLogin`, `httpBasic` y el logout por defecto de Spring.

Para el cambio obligatorio se usa un `AuthorizationManager`/filtro corto después de la autenticación que permite solo las tres rutas de auth cuando `mustChangePassword == true`; devuelve `403` / `PASSWORD_CHANGE_REQUIRED` sin entregar el recurso.

### D7. Igualdad de respuesta y tiempo ante emails inexistentes

El caso de uso busca la cuenta por email normalizado. Si no existe, ejecuta `passwordHasher.matches(raw, DUMMY_HASH)` con un hash Argon2id generado una vez al arrancar desde un valor aleatorio descartado. Luego emite el mismo `401 INVALID_CREDENTIALS` y el mismo `LOGIN_FAIL` con `userId = null`.

- **Por qué:** sin el hash falsificado, un email inexistente responde notablemente más rápido y enumeraría cuentas (spec de no enumeración).
- **Alternativa considerada:** dormir un tiempo fijo; descartado porque el costo de Argon2 depende de la máquina y un `sleep` no iguala la distribución.

### D8. Cambio de contraseña e invalidación de sesiones

El endpoint exige `currentPassword` y `newPassword` (12–128 caracteres, validados con Bean Validation antes de tocar el hasher). Si la actual no coincide → `401` genérico. Si la nueva es inválida → `400 VALIDATION_FAILED` con `errors[].field` sin valores.

Tras guardar el hash y limpiar `must_change_password`, se invalidan **todas** las sesiones de ese usuario mediante un `SessionRegistryImpl` (spring-security-core, sin dependencia nueva) y un `HttpSessionEventPublisher` registrado como listener. La sesión actual también se invalida, de modo que el cliente debe volver a loguearse.

- **Por qué:** evita que una sesión capturada antes del cambio siga siendo válida; es una de las reglas duras del change.
- **Alternativa considerada:** guardar un `passwordVersion` en la sesión; descartado porque exigiría columna nueva y una lectura extra por request.

### D9. Auditoría sin datos personales

El caso de uso llama a `AuditLogger.log` con:

- `action`: `LOGIN_OK` / `LOGIN_FAIL`;
- `userId`: id de la cuenta o `null`;
- `entityType = "app_user"`, `entityId = userId.toString()` o `null`;
- `ip`: IP efectiva validada por `AuditEvent.of`;
- `userAgent`: `"ua:" + SHA-256(userAgent).substring(0,16)` en lugar del texto original;
- `details`: solo `outcome`, `reason_code` y `attempt`, claves ya permitidas por `SensitiveDataSanitizer`. Nunca email, contraseña, token ni el agente crudo.

El evento se escribe en la misma transacción del cambio de estado de la cuenta; si la auditoría falla, el login falla con el error genérico (fail-closed heredado de C-03).

### D10. Configuración, CORS y cabeceras

Propiedades nuevas en `application.yml` con valores por defecto seguros y reflejo en `.env.example`:

| Propiedad | Valor | Observación |
|---|---|---|
| `AUTH_SESSION_TIMEOUT_MIN` | `30` | ya presente en `.env.example`; se valida que sea entero > 0 |
| `AUTH_SESSION_COOKIE_NAME` | `SESSIONID` | nueva |
| `AUTH_SESSION_COOKIE_SECURE` | `true` (`false` en `dev`) | nueva |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` en `dev` | ya presente; obligatoria en producción, sin `*`

`RequiredConfiguration` se extiende para validar `AUTH_SESSION_TIMEOUT_MIN` y, fuera del perfil `dev`, `CORS_ALLOWED_ORIGINS`; el mensaje nombra solo la variable inválida. CORS usa `setAllowedOrigins` con la lista configurada y `setAllowCredentials(true)`; nunca `*` con credenciales.

Cabeceras añadidas a toda respuesta: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` y una CSP restrictiva; `Strict-Transport-Security` solo cuando `request.isSecure()`.

### D11. Estructura de paquetes y arista de módulos

- `auth.api`: DTOs de request/response (`LoginRequest`, `AuthMeResponse`, `ChangePasswordRequest`) y contratos de salida que otros módulos puedan necesitar; sin entidades.
- `auth.application`: `AuthenticateUser`, `ChangeOwnPassword`, `LogoutUser`, `LockoutPolicy`, puertos de sesión/registry.
- `auth.domain`: mutadores de `AppUser`, `AuthenticatedUser`, `LoginAttempt` y `PasswordHasher` (ya existente).
- `auth.infrastructure`: `SecurityConfig`, controladores, filtros/handlers, adaptador de `SessionRegistry`, configuración de cookies/CSRF/headers.
- `shared.api`: `RateLimiter`; `shared.infrastructure`: `InMemoryRateLimiter`.
- `ModuleBoundaries`: agregar `auth → auditoria` a `ALLOWED_DEPENDENCIES`, extender el chequeo transitivo para que `auth` tampoco alcance módulos de negocio, y verificar que la configuración de seguridad quede confinada en `auth.infrastructure`.

El grafo resultante sigue acíclico: `auth → auditoria → shared`, sin aristas desde `auth` a `agenda`, `reservas`, `pacientes`, `historiaclinica` ni `notificaciones`.

## Risks / Trade-offs

- **[Sesión en memoria]** → Un reinicio o múltiples réplicas sin affinity rompen la sesión. Mitigación: desplegar una sola instancia en el MVP y documentar Spring Session como evolución ligada a PA-03.
- **[Cookie CSRF legible por JS]** → XSS podría leerla. Mitigación: `HttpOnly` en la cookie de sesión, CSP, `textContent` en el frontend (regla dura 10) y rotación en login; XSS queda fuera de este change.
- **[Argon2 como vector DoS]** → Coste de CPU por request de login. Mitigación: el limitador D5 corre **antes** del hasher y los DTOs acotan longitud de email/contraseña.
- **[Bloqueo por cuenta como DoS]** → Un atacante podría acumular fallos para molestar al odontólogo. Mitigación: duraciones con techo de 60 min, clave IP+email para el rate limit y sin bloqueo permanente.
- **[Auditoría fail-closed]** → Si la base de auditoría falla, el login falla aunque las credenciales sean correctas. Mitigación: comportamiento heredado de C-03, deliberado por cumplimiento; se reporta con código genérico.
- **[Metadatos de auditoría]** → IP y User-Agent pueden contener datos personales. Mitigación: IP validada por `AuditEvent.of`, User-Agent reemplazado por digest, `details` filtrados por `SensitiveDataSanitizer`.
- **[Configuración de proxy]** → Con `X-Forwarded-For` mal configurado, el rate limit podría agrupar a todos los usuarios. Mitigación: usar `RemoteAddr` por defecto y solo cabeceras reenviadas con trusted proxies explícitos (parte de PA-03).
- **[Sin migración]** → No hay columna para "versión de credencial"; la invalidación depende de `SessionRegistry` en memoria. Mitigación: cubierto por tests de integración; si se requiere persistencia futura, será un change nuevo con migración.

## Migration Plan

1. Sin cambios de esquema: no ejecutar Flyway nuevo ni tocar migraciones aplicadas.
2. Actualizar `.env.example` con `AUTH_SESSION_COOKIE_NAME` y `AUTH_SESSION_COOKIE_SECURE`; los valores sensibles siguen siendo marcadores.
3. Desplegar el backend con la nueva cadena de seguridad. El arranque valida configuración antes de crear beans; si falta algo, falla con el nombre de la variable.
4. Reiniciar: las sesiones previas desaparecen (no existían sesiones autenticadas antes de este change). La cuenta bootstrap ya tiene `must_change_password = true`; no hace falta update.
5. Verificación en despliegue: `GET /actuator/health` → 200/503 sin 401; `GET /api/v1/agenda/...` sin sesión → 401; login bootstrap → 200 + cookies; ruta privada → 403 hasta cambiar contraseña.
6. Rollback: revertir código y configuración y reiniciar. No hay datos nuevos que limpiar salvo los eventos `LOGIN_OK/LOGIN_FAIL` ya auditados, que se conservan por diseño.

## Open Questions

Todas son diferibles a changes futuros y **no alteran** specs, decisiones ni el desglose de tareas de C-04:

- **OQ-1 — 2FA TOTP:** ¿se incorpora como factor adicional al login o como paso posterior al cambio de contraseña? Requiere su propio change y probablemente una columna nueva.
- **OQ-2 — Recuperación de contraseña:** el flujo queda pendiente de definir una vez que exista un secretario o un segundo administrador (RN-AU-01 prohíbe que el odontólogo se cree otra cuenta).
- **OQ-3 — Topología de proxy (PA-03):** dirección IP pública y lista exacta de proxies de confianza; hoy se resuelve con `RemoteAddr`, y el valor concreto se configurará al definir hosting.
- **OQ-4 — Persistencia de sesiones (PA-03):** si el hosting exige múltiples réplicas, migrar a Spring Session con Redis en un change separado.
- **OQ-5 — Orígenes CORS por ambiente:** los valores concretos de producción se completarán al desplegar; el contrato (lista explícita, sin comodín) ya está fijado.
