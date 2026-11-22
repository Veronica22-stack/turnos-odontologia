# Proposal: C-04 `auth-session`

## Why

C-02 creó la cuenta `DENTIST` (con `failed_attempts`, `locked_until`, `must_change_password` y hash Argon2id) y C-03 el puerto de auditoría, pero la API sigue con `denyAll`: todavía no hay forma de que el odontólogo entre al sistema. Sin este change no se puede implementar la UI de login (C-06) ni ninguna ruta privada, y quedarían sin resolver la política de sesión, el bloqueo progresivo, el cambio obligatorio de contraseña y la no enumeración de cuentas (US-001, US-002, RN-AU-01/03/04, RN-AD-01, PA-04).

Governance: **CRÍTICO** (autenticación, sesión y seguridad). Este change solo produce artefactos de planificación; el líder aprueba el plan antes del apply.

## What Changes

- **Cuatro endpoints REST** bajo `/api/v1/auth`: `POST /login`, `POST /logout`, `GET /me` y `POST /change-password`. Se mantienen las rutas de `CHANGES.md` (no hay convención previa en `auth.api`, que hoy solo tiene `package-info.java`). DTOs `record` con Bean Validation; nunca entidades JPA en la API (regla dura 2).
- **PA-04 resuelta en este propose**: se adopta **sesión de servidor** (sesión servlet con cookie dedicada `SESSIONID`), no JWT. Cookie `HttpOnly; SameSite=Strict` y `Secure` según entorno/HTTPS; CSRF de doble envío (`XSRF-TOKEN` legible por JS + cabecera `X-XSRF-TOKEN`) en endpoints autenticados que cambian estado; login queda exento de CSRF por no existir autoridad ambiental aún. Motivo: invalidación inmediata, sin dependencias nuevas y acorde con la preferencia de KB 08 §Seguridad. **No se agrega 2FA, recuperación de contraseña ni persistencia de sesión en BD en v1** (ver Open Questions).
- **Expiración por inactividad** configurable con `AUTH_SESSION_TIMEOUT_MIN` (default 30), comprobada con el `Clock` inyectado de C-02 contra un instante `expiresAt` guardado en la sesión; timeout del contenedor como defensa adicional. Reinicio del contador en cada request autenticado; sesión caducada ⇒ 401 con el mismo formato de error.
- **Contraseñas y bloqueo progresivo**: se reutiliza el `PasswordHasher` Argon2id existente (sin dependencia nueva; Bouncy Castle ya aprobada en C-02). Política ≥ 12 caracteres (y tope superior para evitar coste desproporcionado de hash). Tras N fallos consecutivos se escala `locked_until` de forma temporal usando `failed_attempts`/`locked_until` ya presentes en `app_user`; un login correcto resetea el contador y actualiza `last_login_at`, todo en UTC.
- **Errores genéricos y sin enumeración**: credenciales inválidas y cuenta bloqueada devuelven el mismo 401 con código estable; para usuarios inexistentes se ejecuta una verificación de hash *dummy* y se emite el mismo `LOGIN_FAIL`. El `change-password` exige la contraseña actual y responde 400/422 con códigos sin revelar valores. `429` para rate limit con `Retry-After`, también sin PII.
- **Autorización deny-by-default**: públicos solo `GET /actuator/health`, `POST /api/v1/auth/login` y `/api/v1/public/**`; todo lo demás exige sesión autenticada con rol `DENTIST` (`@PreAuthorize("hasRole('DENTIST')")` + regla global), preparado para `SECRETARY` en v2. Sin sesión ⇒ 401; con sesión pero rol/cambio pendiente ⇒ 403 con formato `application/problem+json`.
- **Cambio obligatorio de contraseña**: con `must_change_password = true`, la sesión solo alcanza `/auth/me`, `/auth/change-password` y `/auth/logout`; el resto devuelve 403 `PASSWORD_CHANGE_REQUIRED`. Al cambiarla se guarda el nuevo hash, se limpia el flag y se rotan/invalidan las sesiones del usuario.
- **Rate limit de login por IP + email** (normalizado) como puerto reutilizable de `shared.api` para futuros endpoints públicos; límite en memoria por proceso, suficiente para v1 single-tenant/single-instance, con `Retry-After` y sin datos personales en la respuesta ni en logs.
- **Auditoría obligatoria (C-03)**: `LOGIN_OK`/`LOGIN_FAIL` vía `auditoria.api.AuditLogger` con `userId` (nulo si el email no existe), IP y `user-agent`; `details` solo claves de la allowlist de C-03 (`outcome`, `reason_code`, `attempt`, `source`). Nunca contraseñas, tokens, email, DNI, teléfono ni datos clínicos. No se agrega una acción nueva al `CHECK` de V3 (logout no se audita en C-04).
- **Cabeceras de seguridad y CORS**: CSP `default-src 'self'`, `X-Content-Type-Options`, `X-Frame-Options`/`frame-ancestors`, `Referrer-Policy: no-referrer`, HSTS solo bajo HTTPS y orígenes restringidos por `CORS_ALLOWED_ORIGINS` con credenciales. IP de cliente solo desde `getRemoteAddr()` con proxy de confianza configurado (nunca `X-Forwarded-For` directo).
- **Fronteras (ArchUnit)**: `auth` puede depender de `shared` y de `auditoria.api`; **no** puede depender de `agenda`, `reservas`, `pacientes`, `historiaclinica` ni `notificaciones`, directa ni transitivamente. Se agrega el borde `auth → auditoria` al grafo permitido con fixtures positivos y negativos (patrón de C-03).
- **Tests (Strict TDD)**: unitarios (política de contraseña, máquina de estados de lockout, expiración, CSRF, mapeo de errores genéricos), ArchUnit e `*IT` con PostgreSQL embebido (zonky): login OK/fail, lockout, expiración, CSRF, 401 en rutas protegidas, cambio forzado, rate limit y filas `LOGIN_OK`/`LOGIN_FAIL` sin datos sensibles. RED verificado antes de cada código de producción.

Fuera de alcance: UI/login (C-06), 2FA y recuperación de contraseña, sesiones persistentes en BD/Redis o agrupación multi-instancia, API de consulta de auditoría (C-24), hosting/HTTPS real (PA-03), retención de logs (PA-01/PA-03) y cualquier dependencia de runtime nueva.

## Capabilities

### New Capabilities
- `auth-session`: autenticación con sesión de servidor — endpoints de login/logout/consulta/cambio de contraseña, cookie y CSRF, expiración por inactividad, bloqueo progresivo, no enumeración, autorización DENTIST y eventos `LOGIN_OK`/`LOGIN_FAIL`.

### Modified Capabilities
- `api-access-policy`: pasa de "solo `/actuator/health` es público" a la lista exacta de rutas públicas y exige sesión `DENTIST` para el resto; agrega CSRF y cabeceras de seguridad.
- `error-responses`: agrega 401/403/429 de autenticación con códigos estables, `Retry-After` y ausencia de enumeración, en el mismo `ProblemDetail`.
- `user-accounts`: suma el ciclo de vida de la seguridad de la cuenta — política de contraseña, intentos fallidos, bloqueo temporal, reseteo, `last_login_at` y `must_change_password`.
- `time-and-zones`: fija que `locked_until`, `last_login_at` y el vencimiento de sesión usen el `Clock` UTC y se comparen como instantes.
- `module-boundaries`: declara el borde `auth → auditoria.api` y `shared.api` como puerto de rate limit; prohíbe a `auth` alcanzar módulos de negocio (directa o transitivamente) con verificación ArchUnit.
- `runtime-configuration`: documenta `AUTH_SESSION_TIMEOUT_MIN`, ajustes de cookie/CSRF y el límite de login como variables de entorno sin secretos (se agregarán a `.env.example` durante el apply).

## Impact

- **Código** (`backend/`): `auth.api` (DTOs, puertos), `auth.application` (casos de uso de login/logout/me/cambio de contraseña, política, lockout), `auth.domain` (reglas puras y estado de cuenta), `auth.infrastructure` (cadena de seguridad, cookie/CSRF, sesión, rate limit, adaptador HTTP); `shared.api` (puerto de rate limit) y `shared.infrastructure` (implementación en memoria); `ModuleBoundaries.java` y sus fixtures. Se reutilizan `AppUser`, `PasswordHasher`, `Clock`, `ProblemDetails` y `AuditLogger`. **Sin migración Flyway nueva**: V1/V2/V3 están aplicadas y no se editan (regla dura 3).
- **Dependencias**: ninguna nueva. Ya están `spring-boot-starter-security`, Argon2id/Bouncy Castle, `spring-security-test` y zonky; un `spring-session-*` (con tabla y migración) queda fuera de alcance y requiere aprobación explícita (regla dura 12).
- **Configuración**: nuevas variables no sensibles (`AUTH_SESSION_TIMEOUT_MIN`, límite de login, flags de cookie segura/CSRF); el apply las documenta en `.env.example` sin valores reales. Sin credenciales nuevas.
- **API**: cuatro endpoints nuevos y cambio de comportamiento de toda `/api/v1/**` (401/403 con `ProblemDetail`); `/api/v1/public/**` queda declarado como público para changes futuros.
- **Changes que dependen de este**: C-06 (`login-ui`) y, indirectamente, todas las rutas privadas; C-24 consume el rastro de acceso.

## Supuestos y preguntas residuales (PA-04)

- **Resuelta**: mecanismo de autenticación = sesión de servidor con cookie + CSRF (no JWT). Se registra como decisión D1 de `design.md`.
- **Supuestos de v1** (no bloquean el change): umbral de bloqueo 5 intentos con escalada a 15/30/60 min; límite de login 10 req/min por IP+email; política de contraseña 12–128 caracteres; cookie `SESSIONID` con `Secure` activo salvo perfil `dev`/HTTP local.
- **Preguntas abiertas que se difieren sin bloquear**: (a) 2FA TOTP — mejora a evaluar, no incluida; (b) recuperación de contraseña sin secretaria ni segundo administrador (PA-04) — requiere decisión de producto, en v1 solo se resetea por bootstrap/reactivación controlada por el profesional; (c) estrategia exacta de CSRF — se adopta doble envío por cookie, con alternativa de endpoint dedicado si la SPA lo requiere; (d) persistencia/agrupación de sesiones (Spring Session + Redis/JDBC) — exigiría nueva dependencia y migración, se reevalúa con PA-03/multi-instancia; (e) cabecera `X-Forwarded-For` y proxies de confianza — se define al elegir hosting (PA-03).
