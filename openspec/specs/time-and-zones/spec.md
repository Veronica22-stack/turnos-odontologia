# time-and-zones Specification

## Purpose
Fija una única fuente de la hora actual, reemplazable en tests, y las reglas de conversión entre los instantes guardados en UTC y la hora local del consultorio (`America/Argentina/Buenos_Aires`), para que agenda, reservas y notificaciones calculen horarios igual (RN-AG-07, SU-08).

## Requirements

### Requirement: Hora actual desde un reloj único
Todo cálculo que dependa de la hora actual SHALL obtenerla de un único reloj del sistema en UTC, que los tests MUST poder reemplazar por un reloj fijo.

#### Scenario: Reloj fijo en tests
- **GIVEN** el reloj del sistema reemplazado por uno fijo en `2026-03-10T12:00:00Z`
- **WHEN** se consulta la hora actual
- **THEN** el resultado es `2026-03-10T12:00:00Z` y la fecha local del consultorio es `2026-03-10`

### Requirement: Conversión de UTC a hora local del consultorio
El sistema SHALL convertir un instante UTC a fecha y hora local aplicando la zona del consultorio.

#### Scenario: Mañana en Buenos Aires
- **WHEN** se convierte `2026-03-10T12:00:00Z` a la zona `America/Argentina/Buenos_Aires`
- **THEN** el resultado es `2026-03-10T09:00`

#### Scenario: Cambio de día
- **WHEN** se convierte `2026-03-11T02:00:00Z` a la zona `America/Argentina/Buenos_Aires`
- **THEN** el resultado es `2026-03-10T23:00`

### Requirement: Conversión de hora local del consultorio a UTC
El sistema SHALL convertir una fecha y hora local del consultorio a un instante UTC. Una hora local inexistente en la zona (salto de horario) MUST rechazarse y una hora ambigua MUST resolverse con el desfase más temprano.

#### Scenario: Hora local a UTC
- **WHEN** se convierte `2026-03-10T09:00` en `America/Argentina/Buenos_Aires` a UTC
- **THEN** el resultado es `2026-03-10T12:00:00Z`

#### Scenario: Hora local inexistente
- **WHEN** se convierte `2026-03-08T02:30` en la zona `America/New_York` a UTC
- **THEN** la conversión se rechaza indicando que la hora local no existe en esa zona

#### Scenario: Hora local ambigua
- **WHEN** se convierte `2026-11-01T01:30` en la zona `America/New_York` a UTC
- **THEN** el resultado es `2026-11-01T05:30:00Z`

### Requirement: Límites de un día local en UTC
El sistema SHALL calcular, para una fecha local del consultorio, el intervalo semiabierto de instantes UTC que la cubre `[inicio, fin)`.

#### Scenario: Día completo en Buenos Aires
- **WHEN** se calculan los límites del día `2026-03-10` en `America/Argentina/Buenos_Aires`
- **THEN** el intervalo es `[2026-03-10T03:00:00Z, 2026-03-11T03:00:00Z)`

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
