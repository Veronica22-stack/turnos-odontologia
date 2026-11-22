# Spec Delta

## MODIFIED Requirements

### Requirement: Instantes persistidos en UTC
Las fechas y horas que el sistema persiste SHALL guardarse como instantes con zona (`timestamptz`) y leerse sin alteración, independientemente de la zona horaria del host. Los instantes de autenticación (creación, último acceso, vencimiento de sesión y eventos de auditoría) SHALL calcularse con el mismo reloj UTC del servidor y expresarse como `Instant`.

#### Scenario: Ida y vuelta por la base
- **GIVEN** una cuenta creada con el reloj fijado en `2026-03-10T12:00:00Z`
- **WHEN** se lee de la base con una conexión de la aplicación
- **THEN** su fecha de creación es exactamente `2026-03-10T12:00:00Z`

#### Scenario: Instante de auditoría
- **GIVEN** un evento de auditoría generado por el servidor
- **WHEN** se persiste
- **THEN** su marca de tiempo es un `Instant` UTC equivalente al reloj del servidor, sin depender de la zona del host

#### Scenario: Vencimiento de sesión en UTC
- **GIVEN** un login correcto a las `2026-03-10T12:00:00Z` con un plazo de 30 minutos
- **WHEN** se consulta el estado de la sesión
- **THEN** el vencimiento informado es `2026-03-10T12:30:00Z` y cualquier renovación se calcula sobre ese mismo instante UTC
