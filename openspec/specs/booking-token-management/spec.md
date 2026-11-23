# booking-token-management Specification

## Purpose
Permite que el paciente que recibió el link por email consulte, cancele o reprograme únicamente su propio turno sin iniciar sesión, con un token de alta entropía validado de forma segura y sin exponer datos de otras personas.

## Requirements

### Requirement: Token-based appointment lookup
El sistema SHALL exponer `GET /api/v1/public/bookings/{token}` sin autenticación. La búsqueda SHALL realizarse por el hash del token con comparación en tiempo constante y SHALL devolver únicamente datos del turno del paciente: referencia, `serviceId` de la prestación (para consultar disponibilidad al reprogramar), instantes UTC, nombre de la prestación, nombre del consultorio, estado y banderas de acción permitida.

#### Scenario: Consulta con token válido
- **GIVEN** un token no expirado ni revocado ligado a un turno `CONFIRMED` futuro
- **WHEN** se consulta `GET /api/v1/public/bookings/{token}`
- **THEN** la respuesta es 200 con los datos acotados del turno, incluido el `serviceId` de su prestación, y sin DNI, email, teléfono, notas ni datos clínicos

#### Scenario: Comparación en tiempo constante
- **GIVEN** un token almacenado y otro candidato de la misma longitud que difiere en un carácter
- **WHEN** se resuelve el token
- **THEN** la comparación no depende de la posición del carácter distinto y el candidato inválido se rechaza con el mismo error genérico

#### Scenario: Turno ajeno
- **GIVEN** un token válido de otro turno
- **WHEN** se intenta usar para acceder a un turno distinto
- **THEN** el sistema devuelve únicamente el turno asociado al token y nunca datos de otro paciente ni de historia clínica

### Requirement: Generic token errors
Todo token inválido, expirado o revocado SHALL producir exactamente la misma respuesta genérica, sin revelar si el turno existió ni el motivo del rechazo. Un turno ya pasado SHALL ser de solo lectura.

#### Scenario: Token expirado
- **GIVEN** un token cuyo `expires_at` es anterior al instante actual
- **WHEN** se consulta o se intenta cancelar con él
- **THEN** la respuesta es 404 con el mismo `code` genérico que un token inexistente

#### Scenario: Token revocado
- **GIVEN** un token revocado tras una cancelación
- **WHEN** se intenta reprogramar
- **THEN** la respuesta es el mismo 404 genérico y no se modifica ningún turno

#### Scenario: Turno pasado
- **GIVEN** un token válido cuyo turno ya comenzó
- **WHEN** se consulta, se cancela o se reprograma
- **THEN** la consulta devuelve 200 con las banderas de acción en falso y las mutaciones se rechazan con 422 sin cambiar el turno

### Requirement: Cancellation by token
`DELETE /api/v1/public/bookings/{token}` SHALL cancelar el turno asociado con `cancelledBy = PATIENT`, revocar todos los tokens de ese turno y dejar el slot libre. La operación SHALL publicar los eventos que hacen que C-13 envíe la cancelación al paciente y el aviso al odontólogo.

#### Scenario: Cancelación correcta
- **GIVEN** un turno `CONFIRMED` futuro con token vigente
- **WHEN** se envía `DELETE /api/v1/public/bookings/{token}` con CSRF válido
- **THEN** el turno queda `CANCELLED` con `cancelled_by = PATIENT`, todos sus tokens quedan revocados y se publica el evento de cancelación

#### Scenario: Cancelación sin CSRF
- **GIVEN** un token válido
- **WHEN** se envía el `DELETE` sin la cabecera `X-XSRF-TOKEN`
- **THEN** la respuesta es 403 `CSRF_REQUIRED` y el turno sigue `CONFIRMED`

### Requirement: Rescheduling by token
`POST /api/v1/public/bookings/{token}/reschedule` SHALL reprogramar el turno de forma atómica al nuevo inicio, reutilizando el motor de disponibilidad con audiencia pública y el puerto de reprogramación de turnos. Si el nuevo slot no está disponible, SHALL responder 409 `SLOT_TAKEN` y conservar el turno original. El token SHALL permanecer válido (sin rotación).

#### Scenario: Reprogramación correcta
- **GIVEN** un turno futuro con token vigente y un slot libre que cumple anticipación, horizonte, granularidad y horario
- **WHEN** se envía la reprogramación con CSRF válido
- **THEN** el mismo turno (mismo identificador) cambia de inicio, se publica el evento de reprogramación y el token sigue intacto

#### Scenario: Conflicto en el nuevo slot
- **GIVEN** un nuevo slot ocupado por otro turno
- **WHEN** se envía la reprogramación
- **THEN** la respuesta es 409 `SLOT_TAKEN`, el turno original conserva su inicio y no se publica ningún evento

#### Scenario: Reprogramación sin CSRF
- **GIVEN** un token válido
- **WHEN** se envía la reprogramación sin CSRF
- **THEN** la respuesta es 403 `CSRF_REQUIRED` y el turno no se modifica

### Requirement: Anti-enumeration rate limit
Las operaciones con token SHALL aplicar un límite de tasa por IP (dirección remota, nunca `X-Forwarded-For` directo) y SHALL devolver 429 `RATE_LIMITED` con `Retry-After` al superarlo. Además, la navegación SHALL conservar `Referrer-Policy: no-referrer` para que la URL con el token no se filtre a terceros.

#### Scenario: Enumeración de tokens
- **GIVEN** un IP que superó el límite de intentos con tokens inválidos
- **WHEN** intenta otra consulta con un token
- **THEN** la respuesta es 429 con `Retry-After` en segundos y un cuerpo que no revela el token ni el IP

#### Scenario: Política de referrer
- **GIVEN** una respuesta de cualquiera de los tres endpoints
- **WHEN** se inspeccionan sus cabeceras
- **THEN** contiene `Referrer-Policy: no-referrer`

### Requirement: Token use audit
Cada uso del token SHALL registrar una acción `BOOKING_TOKEN_USE` en `audit_log`, sin usuario autenticado, con el turno como entidad referenciada y `details` limitado a claves de estado/código. El token en claro, DNI, email y teléfono NO SHALL aparecer en la fila ni en los logs.

#### Scenario: Uso auditado
- **GIVEN** una consulta, cancelación o reprogramación con token válido
- **WHEN** se completa la operación
- **THEN** existe una fila de auditoría con acción `BOOKING_TOKEN_USE`, `user_id` nulo y `details` sin claves personales

#### Scenario: Fallo de auditoría
- **GIVEN** un fallo al escribir en `audit_log`
- **WHEN** se usa un token válido
- **THEN** la operación se rechaza de forma segura y no se escribe una fila parcial con datos personales
