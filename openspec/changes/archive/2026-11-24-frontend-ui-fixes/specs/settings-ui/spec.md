## ADDED Requirements

### Requirement: Alta de tramos para cualquier día de la semana
La sección de horarios SHALL mostrar siempre los siete días (lunes a domingo), también sin tramos cargados, y cada día SHALL ofrecer su propia acción «Agregar tramo» con nombre accesible «Agregar tramo el <día>». Un tramo agregado SHALL quedar en el día elegido y el guardado SHALL enviar los tramos ordenados por día.

#### Scenario: Estado vacío con los siete días
- **GIVEN** un consultorio sin tramos de atención
- **WHEN** se abre la sección de horarios
- **THEN** se ven los siete días, cada uno con su acción «Agregar tramo el <día>»

#### Scenario: Tramo agregado a un día distinto del lunes
- **GIVEN** un consultorio sin tramos de atención
- **WHEN** se agrega un tramo con «Agregar tramo el martes» y se guarda
- **THEN** se envía un único tramo con día de la semana 2 y ningún tramo para el lunes

#### Scenario: Tramo agregado junto a tramos existentes
- **GIVEN** tramos cargados para lunes y martes
- **WHEN** se agrega un tramo el viernes y se guarda
- **THEN** se envía la lista completa ordenada por día, con el nuevo tramo en el viernes
