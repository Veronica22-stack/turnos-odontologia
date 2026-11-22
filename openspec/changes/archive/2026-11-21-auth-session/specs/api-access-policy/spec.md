# Spec Delta

## MODIFIED Requirements

### Requirement: Denegación por defecto
El sistema SHALL rechazar con `401` toda solicitud a una ruta que no esté declarada explícitamente como pública. Las únicas rutas públicas son `GET /actuator/health`, `POST /api/v1/auth/login` y las rutas bajo `/api/v1/public/**`; toda otra ruta bajo `/api/v1/**` SHALL exigir una sesión autenticada con rol `DENTIST`, salvo `/api/v1/auth/me`, `/api/v1/auth/logout` y `/api/v1/auth/change-password`, que exigen sesión autenticada sin importar el cambio de contraseña pendiente. Un rol distinto SHALL recibir `403`.

#### Scenario: Ruta de API no declarada
- **WHEN** un cliente sin credenciales hace `GET /api/v1/cualquier-cosa`
- **THEN** recibe `401` y el cuerpo no revela si la ruta existe

#### Scenario: Ruta inexistente fuera de la API
- **WHEN** un cliente sin credenciales hace `GET /no-existe`
- **THEN** recibe `401` (no `404`), sin enumerar rutas

#### Scenario: Ruta pública declarada
- **WHEN** un cliente sin credenciales hace `GET /actuator/health`
- **THEN** recibe una respuesta de salud (`200` o `503`), nunca `401`

#### Scenario: Login público
- **WHEN** un cliente sin sesión hace `POST /api/v1/auth/login` con credenciales
- **THEN** la solicitud se evalúa por credenciales y no se rechaza por falta de sesión

#### Scenario: Ruta privada con sesión vencida
- **GIVEN** una sesión caducada
- **WHEN** se hace `GET /api/v1/agenda/cualquier-cosa`
- **THEN** se recibe `401` con `application/problem+json` y `code = UNAUTHENTICATED`

#### Scenario: Sesión con cambio de contraseña pendiente
- **GIVEN** una sesión autenticada de `DENTIST` con cambio de contraseña obligatorio
- **WHEN** se solicita una ruta privada distinta de `/auth/me`, `/auth/logout` o `/auth/change-password`
- **THEN** se recibe `403` con `code = PASSWORD_CHANGE_REQUIRED` y no se entrega el recurso

### Requirement: Sin mecanismos de login implícitos
El sistema MUST NOT ofrecer formulario de login, autenticación HTTP Basic ni un usuario con contraseña generada automáticamente. La única forma de autenticarse SHALL ser `POST /api/v1/auth/login` con credenciales y sesión de servidor resultado, y MUST NOT emitir una contraseña por defecto ni desafío `WWW-Authenticate: Basic`.

#### Scenario: Sin desafío Basic ni redirección a login
- **WHEN** un cliente sin credenciales hace `GET /api/v1/x`
- **THEN** la respuesta no incluye la cabecera `WWW-Authenticate: Basic` ni redirige a `/login`

#### Scenario: Sin contraseña generada en el log
- **WHEN** la aplicación arranca
- **THEN** el log no contiene una contraseña de usuario generada automáticamente

#### Scenario: Login explícito
- **WHEN** un cliente sin sesión hace `POST /api/v1/auth/login` con credenciales inválidas
- **THEN** recibe un error de credenciales en formato de problema, sin desafío Basic ni redirección HTML

## ADDED Requirements

### Requirement: Cabeceras de seguridad y orígenes permitidos
Toda respuesta de la API SHALL incluir cabeceras de seguridad básicas: `X-Content-Type-Options: nosniff`, `X-Frame-Options` o `frame-ancestors` que impidan incrustar la aplicación, `Referrer-Policy: no-referrer` y una política de seguridad de contenido restrictiva; `Strict-Transport-Security` SHALL emitirse solo cuando la petición usa HTTPS. El sistema SHALL aceptar solicitudes con credenciales únicamente desde los orígenes declarados en `CORS_ALLOWED_ORIGINS` y MUST NOT usar un comodín en producción.

#### Scenario: Respuesta con cabeceras de seguridad
- **WHEN** un cliente hace una solicitud a cualquier ruta de la API
- **THEN** la respuesta incluye las cabeceras de seguridad exigidas, con valores que no permiten marcos externos ni seguimiento de referrer

#### Scenario: Origen no permitido
- **GIVEN** un origen no incluido en `CORS_ALLOWED_ORIGINS`
- **WHEN** se intenta una solicitud con credenciales desde ese origen
- **THEN** la solicitud es rechazada por la política de CORS y no se emite una cabecera de origen comodín

#### Scenario: HSTS solo con HTTPS
- **GIVEN** una petición HTTP local de desarrollo
- **WHEN** se inspecciona la respuesta
- **THEN** no se emite `Strict-Transport-Security`; en una petición HTTPS sí se emite
