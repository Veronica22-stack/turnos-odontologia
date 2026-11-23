# Spec Delta

## ADDED Requirements

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
