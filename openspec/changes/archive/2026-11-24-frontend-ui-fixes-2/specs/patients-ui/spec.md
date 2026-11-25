## MODIFIED Requirements

### Requirement: Baja lógica del paciente
La vista SHALL solicitar confirmación antes de dar de baja a un paciente y SHALL ejecutarla con `DELETE /api/v1/patients/{id}`. La baja MUST NOT ofrecer ni realizar borrado físico: tras el éxito el paciente queda inactivo en el servidor, desaparece de los resultados habituales y la interfaz MUST NOT exponer controles para eliminarlo definitivamente. El diálogo de confirmación SHALL advertir que los turnos futuros confirmados se cancelarán. Antes del `DELETE`, la vista SHALL consultar los turnos desde el momento actual hasta 365 días en tramos que no superen el rango máximo del listado, SHALL cancelar con `POST /api/v1/appointments/{id}/cancel` cada turno distinto del paciente en estado `CONFIRMED` que empiece en el futuro, y SHALL informar en el mensaje de éxito cuántos turnos se liberaron. Si la consulta de turnos falla, MUST NOT cancelar ningún turno ni dar de baja la ficha. Si alguna cancelación falla, la baja SHALL completarse igual y el aviso SHALL indicar los turnos que quedaron sin liberar.

#### Scenario: Confirmación y baja correcta
- **GIVEN** la ficha de un paciente activo
- **WHEN** se elige «Dar de baja» y se confirma en el diálogo accesible
- **THEN** se envía una única petición `DELETE`, se confirma en pantalla que la ficha quedó inactiva y se ofrece volver al listado sin el paciente

#### Scenario: Cancelación de la baja
- **GIVEN** el diálogo de confirmación abierto
- **WHEN** se elige cancelar o se cierra con Escape
- **THEN** no se realiza ninguna petición y el foco vuelve al control que abrió el diálogo

#### Scenario: Error durante la baja
- **GIVEN** una respuesta de red fallida, timeout o error inesperado al eliminar
- **WHEN** se confirma la baja
- **THEN** se muestra un aviso que no afirma que la baja se completó, se conserva la ficha en pantalla y se permite reintentar

#### Scenario: Baja que libera turnos futuros
- **GIVEN** un paciente con dos turnos futuros confirmados, un turno pasado y un turno cancelado
- **WHEN** se confirma la baja
- **THEN** se cancelan solo los dos turnos futuros confirmados, luego se envía el `DELETE` y el mensaje de éxito informa que se liberaron 2 turnos

#### Scenario: Fallo al consultar los turnos
- **GIVEN** una respuesta fallida al listar los turnos del período
- **WHEN** se confirma la baja
- **THEN** no se cancela ningún turno, no se envía el `DELETE`, la ficha sigue activa y se muestra un aviso con «Reintentar»

#### Scenario: Cancelación parcial de turnos
- **GIVEN** un paciente con dos turnos futuros confirmados y una cancelación que falla
- **WHEN** se confirma la baja
- **THEN** la ficha se da de baja y se muestra una advertencia que indica que se liberó 1 turno y cuál quedó sin liberar, sin datos personales del paciente
