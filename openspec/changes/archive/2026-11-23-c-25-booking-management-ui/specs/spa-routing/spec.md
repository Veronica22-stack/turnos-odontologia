# Spec Delta: spa-routing

## MODIFIED Requirements

### Requirement: Token de gestión fuera de títulos y registros
El parámetro `token` de `/turno/{token}` MUST NOT aparecer en el título del documento, en el parámetro `next` ni en mensajes de consola o de error. La vista de gestión MUST NOT mostrarlo en el texto de la página, guardarlo en `localStorage`/`sessionStorage`, incluirlo en enlaces de la página ni enviarlo en otra petición que no sea la ruta de la API de gestión por token; el título de `/turno/{token}` SHALL ser fijo ("Gestionar turno" con la marca) en todos sus estados. Las rutas `/reservar` y `/reservar/confirmado` MUST NOT llevar en la URL, el título del documento ni la consola datos personales del paciente (nombre, DNI, teléfono, email) ni el identificador de la reserva.

#### Scenario: Título de gestión de turno
- **WHEN** se navega a `/turno/AbC-123_x`
- **THEN** el título del documento no contiene `AbC-123_x` y no se escribe nada en la consola que lo contenga

#### Scenario: Título fijo en todos los estados de gestión
- **GIVEN** un token válido
- **WHEN** la vista pasa por cargando, detalle, reprogramación, "Turno cancelado" y "Enlace no válido"
- **THEN** el título del documento es siempre "Gestionar turno" con la marca y nunca contiene el token ni el nombre de la prestación

#### Scenario: Token fuera de otras peticiones
- **GIVEN** la reprogramación consulta la disponibilidad y las prestaciones públicas
- **WHEN** se inspeccionan las URL y cuerpos de esas peticiones
- **THEN** ninguna contiene el token; solo las peticiones a `/api/v1/public/bookings/{token}` lo llevan, en la ruta

#### Scenario: Confirmación de reserva sin datos en la URL
- **GIVEN** una reserva confirmada con DNI `30456789` e identificador de reserva `7b1e…`
- **WHEN** se muestra `/reservar/confirmado`
- **THEN** la URL es exactamente `/reservar/confirmado`, el título es "Reserva confirmada" con la marca y ni la URL, ni el título, ni la consola contienen el DNI, el email ni el identificador

## ADDED Requirements

### Requirement: Acción de reprogramar en la URL
La vista `/turno/{token}` SHALL reflejar la pantalla de reprogramación con la query `accion=reprogramar`, apilando una entrada de historial al abrirla (`pushState`), de modo que atrás del navegador vuelve al detalle. La URL MUST NOT contener nada más que el token en la ruta y esa acción (ni horario ni prestación). Tras una reprogramación correcta la URL SHALL pasar a `/turno/{token}` con `replaceState`.

#### Scenario: Atrás desde la reprogramación
- **GIVEN** el detalle en `/turno/{token}` y la persona tocó "Reprogramar" (URL `/turno/{token}?accion=reprogramar`)
- **WHEN** usa atrás del navegador
- **THEN** la URL es `/turno/{token}` y se muestra el detalle sin volver a consultar el turno

### Requirement: Normalización de la acción de reprogramar
Al cargar `?accion=reprogramar` directamente, la SPA SHALL consultar el turno y mostrar la reprogramación solo si el servidor la habilita; si no, SHALL normalizar la URL a `/turno/{token}` con `replaceState` y mostrar el detalle. Cualquier otro valor de `accion` SHALL tratarse como el detalle.

#### Scenario: Recarga en la reprogramación de un turno pasado
- **GIVEN** un turno con `reschedulable: false`
- **WHEN** se carga `/turno/{token}?accion=reprogramar`
- **THEN** la URL pasa a `/turno/{token}` sin entrada nueva de historial y se muestra el turno en solo lectura

#### Scenario: Acción desconocida
- **WHEN** se carga `/turno/{token}?accion=borrar`
- **THEN** se muestra el detalle del turno y no se envía ninguna cancelación
