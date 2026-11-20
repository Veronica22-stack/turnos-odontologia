# Spec Delta: api-access-policy

## Purpose

Define qué rutas HTTP son accesibles sin autenticación y cómo se rechaza todo lo demás, aplicando deny-by-default desde el primer commit para que ningún endpoint quede abierto por omisión.

## ADDED Requirements

### Requirement: Denegación por defecto
El sistema SHALL rechazar con `401` toda solicitud a una ruta que no esté declarada explícitamente como pública. En este change la única ruta pública es `GET /actuator/health`.

#### Scenario: Ruta de API no declarada
- **WHEN** un cliente sin credenciales hace `GET /api/v1/cualquier-cosa`
- **THEN** recibe `401` y el cuerpo no revela si la ruta existe

#### Scenario: Ruta inexistente fuera de la API
- **WHEN** un cliente sin credenciales hace `GET /no-existe`
- **THEN** recibe `401` (no `404`), sin enumerar rutas

#### Scenario: Ruta pública declarada
- **WHEN** un cliente sin credenciales hace `GET /actuator/health`
- **THEN** recibe una respuesta de salud (`200` o `503`), nunca `401`

### Requirement: Sin mecanismos de login implícitos
El sistema MUST NOT ofrecer formulario de login, autenticación HTTP Basic ni un usuario con contraseña generada automáticamente hasta que un change de autenticación los defina.

#### Scenario: Sin desafío Basic ni redirección a login
- **WHEN** un cliente sin credenciales hace `GET /api/v1/x`
- **THEN** la respuesta no incluye la cabecera `WWW-Authenticate: Basic` ni redirige a `/login`

#### Scenario: Sin contraseña generada en el log
- **WHEN** la aplicación arranca
- **THEN** el log no contiene una contraseña de usuario generada automáticamente

### Requirement: Métodos no seguros también denegados
El sistema SHALL aplicar la misma denegación a solicitudes `POST`, `PUT`, `PATCH` y `DELETE`, incluida la ruta de salud.

#### Scenario: POST a la ruta de salud
- **WHEN** un cliente sin credenciales hace `POST /actuator/health`
- **THEN** la respuesta es `401` o `403` y no modifica estado
