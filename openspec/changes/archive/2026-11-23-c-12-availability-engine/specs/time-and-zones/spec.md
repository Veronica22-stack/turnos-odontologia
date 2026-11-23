# Spec Delta

## ADDED Requirements

### Requirement: Día local aplicado al cálculo de disponibilidad
El cálculo de disponibilidad SHALL tratar la fecha solicitada como día local del consultorio y SHALL convertirla al intervalo UTC semiabierto que la cubre, usando la `timezone` configurada. Cada tramo de atención, bloqueo y turno SHALL compararse en UTC dentro de ese intervalo. El instante «ahora» SHALL provenir del reloj único inyectable. Una hora local inexistente MUST NOT generar slots y una hora ambigua SHALL resolverse con el desfase más temprano.

#### Scenario: Día local con desfase
- **GIVEN** la zona `America/Argentina/Buenos_Aires` y la fecha local 2026-03-10
- **WHEN** se calcula el rango de consulta de disponibilidad
- **THEN** el rango UTC es `[2026-03-10T03:00:00Z, 2026-03-11T03:00:00Z)` y los instantes de los slots se devuelven como `Instant` en UTC

#### Scenario: Instante UTC de día siguiente pertenece al día local pedido
- **GIVEN** un turno que empieza a las 2026-03-11T02:00:00Z
- **WHEN** se consulta la disponibilidad del 2026-03-10 local
- **THEN** ese turno se considera ocupación del día consultado

#### Scenario: Hora local inexistente o ambigua
- **GIVEN** un tramo de atención que cae en una hora local inexistente por cambio de horario y otro en una hora ambigua
- **WHEN** se calculan los slots
- **THEN** el tramo inexistente no genera slots y el ambiguo usa el desfase más temprano
