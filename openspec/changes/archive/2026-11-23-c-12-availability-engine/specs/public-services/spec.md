# Spec Delta

## ADDED Requirements

### Requirement: Prestación publicable como filtro de disponibilidad
La disponibilidad pública SHALL ofrecer slots únicamente para prestaciones activas y visibles públicamente del consultorio, con la misma duración con la que se publican en el catálogo. Una prestación inactiva MUST NOT generar slots aunque su marca de visibilidad pública siga activa. La ausencia total de prestaciones publicables SHALL indicarse según la regla de «Sin prestaciones públicas activas» de `availability`, no como un error de parámetros.

#### Scenario: Prestación visible y activa
- **GIVEN** una prestación activa, visible y de 30 minutos
- **WHEN** se consulta su disponibilidad pública
- **THEN** los slots tienen 30 minutos de duración y coinciden con los del catálogo

#### Scenario: Prestación activa pero no visible
- **GIVEN** una prestación activa con visibilidad pública desactivada
- **WHEN** se consulta su disponibilidad pública con ese `serviceId`
- **THEN** no se ofrecen slots y la respuesta es `422 SERVICE_INACTIVE` o `404 SERVICE_NOT_FOUND`, sin exponer los datos internos de la prestación

#### Scenario: Catálogo vacío
- **GIVEN** un consultorio sin prestaciones activas y visibles
- **WHEN** se consulta la disponibilidad pública
- **THEN** la respuesta indica `NO_ACTIVE_SERVICES` con lista de slots vacía
