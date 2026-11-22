# Spec Delta

## ADDED Requirements

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
