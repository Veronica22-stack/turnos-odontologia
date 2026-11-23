# Spec Delta

## ADDED Requirements

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
