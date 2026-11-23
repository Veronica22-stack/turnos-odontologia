# Spec Delta

## ADDED Requirements

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
