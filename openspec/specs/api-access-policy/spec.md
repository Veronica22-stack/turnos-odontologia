# api-access-policy Specification

## Purpose
Define qué rutas HTTP son accesibles sin autenticación y cómo se rechaza todo lo demás, aplicando deny-by-default desde el primer commit para que ningún endpoint quede abierto por omisión.

## Requirements

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

### Requirement: Métodos no seguros también denegados
El sistema SHALL aplicar la misma denegación a solicitudes `POST`, `PUT`, `PATCH` y `DELETE`, incluida la ruta de salud.

#### Scenario: POST a la ruta de salud
- **WHEN** un cliente sin credenciales hace `POST /actuator/health`
- **THEN** la respuesta es `401` o `403` y no modifica estado

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

### Requirement: Protección de endpoints de horarios y configuración de consultorio
Los endpoints `/api/v1/working-hours` y `/api/v1/practice/settings` SHALL exigir una sesión autenticada con rol `DENTIST`. Toda solicitud no autenticada MUST ser rechazada con 401, y las operaciones de mutación (PUT) MUST requerir la cabecera y cookie de validación CSRF (`X-XSRF-TOKEN`).

#### Scenario: Petición anónima a configuración o de horarios
- **WHEN** un cliente no autenticado intenta consultar o modificar `/api/v1/working-hours` o `/api/v1/practice/settings`
- **THEN** el sistema responde 401 con formato ProblemDetail

#### Scenario: Petición autenticada sin token CSRF en modificación
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** envía un `PUT /api/v1/working-hours` o `PUT /api/v1/practice/settings` sin la cabecera `X-XSRF-TOKEN` correspondiente
- **THEN** el sistema rechaza la solicitud con 403 CSRF_REQUIRED

### Requirement: Protección de los endpoints de bloqueos de agenda
Todos los endpoints bajo `/api/v1/schedule-blocks/**` SHALL exigir una sesión autenticada con rol `DENTIST` y MUST NOT declararse como públicos. Las solicitudes sin sesión SHALL rechazarse con `401` y `code = UNAUTHENTICATED`, y una sesión con rol distinto SHALL rechazarse con `403` y `code = FORBIDDEN`. Las operaciones de mutación (`POST`, `PUT`, `DELETE`) SHALL exigir la cabecera y cookie de validación CSRF (`X-XSRF-TOKEN`); su ausencia o invalidez SHALL responderse con `403` y `code = CSRF_REQUIRED`. Las consultas (`GET`) MUST NOT exigir token CSRF.

#### Scenario: Consulta anónima de bloqueos
- **WHEN** un cliente sin sesión solicita `GET /api/v1/schedule-blocks` o `GET /api/v1/schedule-blocks/{id}`
- **THEN** el sistema responde `401` con `application/problem+json` y no revela si el bloqueo existe

#### Scenario: Mutación anónima o con rol insuficiente
- **GIVEN** un cliente sin sesión y luego uno autenticado sin rol `DENTIST`
- **WHEN** envían `POST`, `PUT` o `DELETE` sobre `/api/v1/schedule-blocks`
- **THEN** el primero recibe `401` y el segundo `403`, sin modificar ningún bloqueo

#### Scenario: Mutación autenticada sin token CSRF
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** envía `POST`, `PUT` o `DELETE` a `/api/v1/schedule-blocks` sin la cabecera `X-XSRF-TOKEN` válida
- **THEN** el sistema rechaza cada operación con `403` y `code = CSRF_REQUIRED`

#### Scenario: Consulta autenticada sin token CSRF
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** solicita `GET /api/v1/schedule-blocks` sin cabecera CSRF
- **THEN** el sistema responde `200` con el listado correspondiente

### Requirement: Protección de endpoints de historia clínica
Todos los endpoints bajo `/api/v1/patients/{patientId}/clinical-record/**` SHALL exigir sesión autenticada con rol `DENTIST` y sin cambio de contraseña pendiente; las mutaciones (`POST`) MUST exigir el token CSRF. Ningún endpoint de historia clínica SHALL aceptar `PUT`, `PATCH` ni `DELETE`.

#### Scenario: Historia clínica sin sesión
- **WHEN** un cliente sin sesión envía cualquier solicitud a `/api/v1/patients/{patientId}/clinical-record/anamnesis`
- **THEN** recibe `401` en formato `application/problem+json`

#### Scenario: Alta clínica sin CSRF
- **GIVEN** una sesión autenticada con rol `DENTIST`
- **WHEN** envía `POST .../clinical-record/anamnesis` sin la cabecera `X-XSRF-TOKEN`
- **THEN** recibe `403` con `code = CSRF_REQUIRED` y no se crea ninguna entrada

#### Scenario: Cambio de contraseña pendiente
- **GIVEN** una sesión `DENTIST` con cambio de contraseña obligatorio
- **WHEN** consulta la historia clínica de un paciente
- **THEN** recibe `403` con `code = PASSWORD_CHANGE_REQUIRED` y no se registra `HC_READ`

### Requirement: Protección de endpoints de gestión de pacientes
Todos los endpoints bajo `/api/v1/patients/**` SHALL exigir una sesión autenticada con rol `DENTIST`. Toda solicitud no autenticada MUST ser rechazada con código 401, y las operaciones de mutación (POST, PUT, DELETE) MUST requerir la cabecera y cookie de validación CSRF (`X-XSRF-TOKEN`).

#### Scenario: Petición no autenticada a endpoints de pacientes
- **WHEN** un cliente no autenticado envía cualquier solicitud a `/api/v1/patients` o `/api/v1/patients/{id}`
- **THEN** el sistema responde con 401 y formato ProblemDetail

#### Scenario: Mutación de paciente sin CSRF
- **GIVEN** una sesión autenticada con rol `DENTIST`
- **WHEN** envía un POST, PUT o DELETE a `/api/v1/patients` sin la cabecera `X-XSRF-TOKEN`
- **THEN** el sistema rechaza la operación con código 403 CSRF_REQUIRED
