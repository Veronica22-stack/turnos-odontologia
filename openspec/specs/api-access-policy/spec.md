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

### Requirement: Protección de endpoints de gestión de prestaciones
Todos los endpoints bajo `/api/v1/services/**` SHALL exigir una sesión autenticada con rol `DENTIST`. Toda solicitud sin sesión MUST ser rechazada con `401`, y las mutaciones (`POST`, `PUT`, `DELETE`) MUST requerir la cabecera y cookie de validación CSRF (`X-XSRF-TOKEN`).

#### Scenario: Petición anónima a prestaciones
- **WHEN** un cliente sin sesión envía cualquier solicitud a `/api/v1/services` o `/api/v1/services/{id}`
- **THEN** el sistema responde `401` con `application/problem+json` y `code = UNAUTHENTICATED`

#### Scenario: Mutación de prestación sin CSRF
- **GIVEN** una sesión autenticada con rol `DENTIST`
- **WHEN** envía un `POST`, `PUT` o `DELETE` a `/api/v1/services` sin la cabecera `X-XSRF-TOKEN`
- **THEN** el sistema rechaza la operación con `403` y `code = CSRF_REQUIRED`, sin crear ni modificar prestaciones

#### Scenario: Sesión con cambio de contraseña pendiente
- **GIVEN** una sesión de `DENTIST` con cambio de contraseña obligatorio
- **WHEN** solicita cualquier ruta de `/api/v1/services/**`
- **THEN** el sistema responde `403` con `code = PASSWORD_CHANGE_REQUIRED` y no entrega ni modifica prestaciones

### Requirement: Catálogo público de prestaciones declarado
`GET /api/v1/public/services` SHALL estar declarado explícitamente como ruta pública, accesible sin sesión y limitado por tasa por dirección de origen. El sistema MUST NOT exponer por esa ruta ningún otro método ni ninguna operación de escritura.

#### Scenario: Acceso público permitido
- **WHEN** un cliente sin sesión hace `GET /api/v1/public/services`
- **THEN** la solicitud se evalúa como pública y no se rechaza por falta de sesión

#### Scenario: Escritura en la ruta pública rechazada
- **WHEN** un cliente envía `POST`, `PUT` o `DELETE` a `/api/v1/public/services`
- **THEN** el sistema rechaza la solicitud con `403` o `405` según corresponda y no modifica prestaciones

#### Scenario: Límite de tasa en la ruta pública
- **GIVEN** un cliente que superó el límite de solicitudes permitidas en la ventana vigente
- **WHEN** vuelve a solicitar `GET /api/v1/public/services`
- **THEN** el sistema responde `429` con `code = RATE_LIMITED` y `Retry-After`

### Requirement: Protección de endpoints de turnos
Todos los endpoints bajo `/api/v1/appointments/**` SHALL exigir sesión autenticada con rol `DENTIST` y MUST NOT declararse públicos. Sin sesión SHALL responderse `401` con `code = UNAUTHENTICATED`; con otro rol, `403` con `code = FORBIDDEN`. Las mutaciones (`POST`) SHALL exigir token CSRF válido (`403` `CSRF_REQUIRED` si falta); las consultas (`GET`) MUST NOT exigirlo.

#### Scenario: Consulta anónima de turnos
- **WHEN** un cliente sin sesión solicita `GET /api/v1/appointments` o `GET /api/v1/appointments/{id}`
- **THEN** el sistema responde `401` y no revela si el turno existe

#### Scenario: Mutación anónima o con rol insuficiente
- **GIVEN** un cliente sin sesión y luego uno autenticado sin rol `DENTIST`
- **WHEN** envían `POST /api/v1/appointments` o una acción sobre un turno
- **THEN** el primero recibe `401` y el segundo `403`, sin crear ni modificar turnos

#### Scenario: Mutación sin token CSRF
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** envía `POST /api/v1/appointments`, `.../cancel`, `.../reschedule`, `.../complete` o `.../no-show` sin cabecera `X-XSRF-TOKEN` válida
- **THEN** el sistema responde `403` con `code = CSRF_REQUIRED` y el turno no cambia

#### Scenario: Consulta autenticada sin token CSRF
- **GIVEN** una sesión autenticada de odontólogo
- **WHEN** solicita `GET /api/v1/appointments?from=...&to=...` sin cabecera CSRF
- **THEN** el sistema responde `200`

### Requirement: Consulta pública de disponibilidad declarada
`GET /api/v1/public/availability` SHALL declararse explícitamente como ruta pública, accesible sin sesión y limitada por tasa por dirección de origen. El sistema MUST NOT exponer en esa ruta otros métodos ni operaciones de escritura. La ruta canónica SHALL ser `/api/v1/public/availability` (bajo el prefijo `/api/v1/public/**` ya declarado como público), sin un alias `/public/availability`.

#### Scenario: Acceso anónimo permitido
- **WHEN** un cliente sin sesión solicita `GET /api/v1/public/availability?serviceId=<uuid>&date=2026-03-10`
- **THEN** la solicitud se evalúa como pública y no se rechaza por falta de sesión

#### Scenario: Escritura rechazada
- **WHEN** un cliente envía `POST`, `PUT` o `DELETE` a `/api/v1/public/availability`
- **THEN** la solicitud se rechaza con `403` o `405` y no modifica turnos, bloqueos ni prestaciones

#### Scenario: Límite de tasa aplicado
- **GIVEN** un origen que superó el límite de la ventana vigente
- **WHEN** vuelve a consultar la disponibilidad pública
- **THEN** responde `429` con `code = RATE_LIMITED` y `Retry-After`, sin incluir la dirección de origen en el cuerpo

### Requirement: Consulta interna de disponibilidad protegida
`GET /api/v1/availability` SHALL exigir sesión autenticada con rol `DENTIST` y MUST NOT declararse pública. Sin sesión SHALL responderse `401` con `code = UNAUTHENTICATED`; con otro rol, `403` con `code = FORBIDDEN`. Al ser una consulta `GET`, MUST NOT exigir token CSRF.

#### Scenario: Consulta anónima rechazada
- **WHEN** un cliente sin sesión solicita `GET /api/v1/availability?serviceId=<uuid>&date=2026-03-10`
- **THEN** responde `401` con `application/problem+json`, `code = UNAUTHENTICATED`, sin revelar si la prestación existe

#### Scenario: Rol insuficiente
- **GIVEN** una sesión autenticada sin rol `DENTIST`
- **WHEN** solicita la disponibilidad interna
- **THEN** responde `403` con `code = FORBIDDEN`

#### Scenario: Consulta autenticada sin CSRF
- **GIVEN** una sesión `DENTIST`
- **WHEN** solicita `GET /api/v1/availability` sin cabecera `X-XSRF-TOKEN`
- **THEN** responde `200` con los slots correspondientes

### Requirement: Public booking endpoint policy
`POST /api/v1/public/bookings` SHALL ser accesible sin sesión por formar parte de `/api/v1/public/**`. La petición SHALL exigir el token CSRF de doble envío (cabecera `X-XSRF-TOKEN` y cookie `XSRF-TOKEN`) y la respuesta SHALL incluir `Referrer-Policy: no-referrer`.

#### Scenario: Reserva anónima permitida
- **GIVEN** un cliente sin sesión de odontólogo
- **WHEN** envía `POST /api/v1/public/bookings` con CSRF válido
- **THEN** la petición alcanza el controlador y no devuelve 401 ni 403

#### Scenario: Mutación sin CSRF rechazada
- **GIVEN** un cliente sin sesión que omite la cabecera `X-XSRF-TOKEN`
- **WHEN** envía la reserva
- **THEN** la respuesta es 403 con el código `CSRF_REQUIRED` y no se crea ningún turno

#### Scenario: Sin exención de CSRF
- **GIVEN** la cadena de seguridad de la aplicación
- **WHEN** se inspeccionan las exenciones de CSRF
- **THEN** solo `POST /api/v1/auth/login` está exento; la reserva pública no lo está

### Requirement: Token management endpoints policy
Los endpoints `GET /api/v1/public/bookings/{token}`, `DELETE /api/v1/public/bookings/{token}` y `POST /api/v1/public/bookings/{token}/reschedule` SHALL ser públicos por `/api/v1/public/**`, sin sesión de odontólogo. Las mutaciones SHALL exigir token CSRF de doble envío, sin exenciones nuevas, y todas las respuestas SHALL conservar `Referrer-Policy: no-referrer`.

#### Scenario: Consulta anónima con token
- **GIVEN** un cliente sin sesión y un token vigente
- **WHEN** consulta `GET /api/v1/public/bookings/{token}`
- **THEN** recibe 200 con los datos acotados del turno y la cabecera `Referrer-Policy: no-referrer`

#### Scenario: Mutación anónima con CSRF
- **GIVEN** un cliente sin sesión que reenvía la cabecera `X-XSRF-TOKEN` y la cookie obtenidas de una respuesta GET pública
- **WHEN** cancela o reprograma con un token vigente
- **THEN** la petición alcanza el controlador y no devuelve 401 ni 403

#### Scenario: Sin exención de CSRF
- **GIVEN** la cadena de seguridad
- **WHEN** se inspeccionan las exenciones
- **THEN** solo `POST /api/v1/auth/login` está exento; los tres endpoints de gestión no lo están
