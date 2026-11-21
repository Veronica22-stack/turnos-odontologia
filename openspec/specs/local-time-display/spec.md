# local-time-display Specification

## Purpose
Fija cómo la SPA muestra los instantes UTC que entrega la API en la hora del consultorio (`America/Argentina/Buenos_Aires`) y cómo calcula días locales, con los mismos resultados que el backend (RN-AG-07, `time-and-zones`) y sin depender de la zona del navegador.

## Requirements

### Requirement: Instantes de la API en UTC
La SPA SHALL aceptar instantes ISO 8601 con zona explícita (`Z` u offset) y MUST rechazar textos sin zona o inválidos con un error, en lugar de interpretarlos en la zona del navegador.

#### Scenario: Instante válido
- **WHEN** se interpreta `2026-03-10T12:00:00Z`
- **THEN** se obtiene ese instante

#### Scenario: Sin zona
- **WHEN** se interpreta `2026-03-10T12:00:00`
- **THEN** se rechaza indicando que falta la zona

### Requirement: Formato en hora del consultorio
Fechas y horas SHALL mostrarse en la zona `America/Argentina/Buenos_Aires` con formato `es-AR` (`dd/mm/aaaa`, `HH:mm` de 24 h), independientemente de la zona del navegador.

#### Scenario: Mañana en Buenos Aires
- **GIVEN** un navegador configurado en `Europe/Madrid`
- **WHEN** se muestra `2026-03-10T12:00:00Z`
- **THEN** la fecha es `10/03/2026` y la hora `09:00`

#### Scenario: Cambio de día
- **WHEN** se muestra `2026-03-11T02:00:00Z`
- **THEN** la fecha es `10/03/2026` y la hora `23:00`

### Requirement: Fecha local del consultorio
La SPA SHALL calcular la fecha local del consultorio (`aaaa-mm-dd`) de un instante, y la fecha de "hoy" a partir de un reloj reemplazable en tests.

#### Scenario: Hoy con reloj fijo
- **GIVEN** el reloj fijado en `2026-03-11T02:30:00Z`
- **WHEN** se consulta la fecha de hoy del consultorio
- **THEN** el resultado es `2026-03-10`

### Requirement: Límites UTC de un día local
La SPA SHALL calcular, para una fecha local del consultorio, el intervalo semiabierto UTC `[inicio, fin)` que la cubre, y MUST rechazar fechas inexistentes.

#### Scenario: Día en Buenos Aires
- **WHEN** se calculan los límites de `2026-03-10`
- **THEN** el intervalo es `[2026-03-10T03:00:00Z, 2026-03-11T03:00:00Z)`

#### Scenario: Fecha inválida
- **WHEN** se calculan los límites de `2026-02-30`
- **THEN** se rechaza indicando fecha inválida
