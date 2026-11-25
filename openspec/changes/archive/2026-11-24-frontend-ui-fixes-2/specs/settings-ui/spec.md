## ADDED Requirements

### Requirement: Índice de secciones con sección actual
El índice «En esta página» de `/configuracion` SHALL marcar con `aria-current` un único enlace, el de la sección en la que está la persona. Al activar un enlace SHALL marcarlo como actual y desplazar la vista hasta su sección sin provocar una navegación del router ni re-renderizar la vista. Al desplazarse, el índice SHALL marcar la sección visible y, al llegar al final de la página, la última sección.

#### Scenario: Estado inicial
- **WHEN** se abre `/configuracion`
- **THEN** «Consultorio y reserva» es el único enlace del índice marcado como actual

#### Scenario: Clic en un enlace del índice
- **GIVEN** la vista de configuración cargada
- **WHEN** se activa el enlace «Bloqueos»
- **THEN** «Bloqueos» queda como único enlace actual, la vista se desplaza a esa sección y no se re-renderiza ni cambia la ruta

#### Scenario: Desplazamiento manual
- **GIVEN** la vista de configuración cargada
- **WHEN** la persona se desplaza hasta que la sección de horarios queda en la parte superior visible
- **THEN** el índice marca «Horarios de atención» como actual

#### Scenario: Final de la página
- **GIVEN** una última sección demasiado corta para llegar a la parte superior visible
- **WHEN** la persona se desplaza hasta el final de la página
- **THEN** el índice marca la última sección como actual
