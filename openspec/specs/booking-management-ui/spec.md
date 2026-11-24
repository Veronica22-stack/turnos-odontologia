# booking-management-ui Specification

## Purpose
Pantalla pública `/turno/{token}` de la SPA con la que el paciente, sin cuenta y desde el enlace del email, consulta su turno, lo cancela con confirmación previa o lo reprograma eligiendo otro horario de la misma prestación, sin exponer el token ni datos personales.

## Requirements

### Requirement: Consulta del turno por enlace
Al abrir `/turno/{token}` la SPA SHALL consultar `GET /api/v1/public/bookings/{token}` y, mientras espera, mostrar un estado de carga anunciado a lectores de pantalla. Con respuesta 200 SHALL mostrar la prestación, el día y el horario de inicio y fin en `America/Argentina/Buenos_Aires`, el consultorio (si no está vacío) y el estado como insignia con texto. La pantalla MUST NOT mostrar el identificador de la reserva.

#### Scenario: Turno confirmado futuro
- **GIVEN** el servidor responde 200 con `{ serviceName: 'Limpieza', practiceName: 'Consultorio La Muela', startsAt: '2026-11-24T13:30:00Z', endsAt: '2026-11-24T14:15:00Z', status: 'CONFIRMED', cancellable: true, reschedulable: true }`
- **WHEN** se abre `/turno/{token}`
- **THEN** se muestran "Limpieza", "martes 24 de noviembre", "10:30 – 11:15", "Consultorio La Muela", la insignia "Confirmado" y los botones "Reprogramar" y "Cancelar turno", y no aparece el `bookingId`

#### Scenario: Cargando
- **GIVEN** la consulta del turno no respondió todavía
- **WHEN** se muestra la pantalla
- **THEN** hay un mensaje "Cargando tu turno…" con `role="status"` y ninguna acción disponible

#### Scenario: Consultorio sin nombre
- **GIVEN** la respuesta trae `practiceName: ""`
- **WHEN** se muestra el turno
- **THEN** se omite la línea del consultorio

### Requirement: Acciones habilitadas por el servidor
Las acciones "Reprogramar" y "Cancelar turno" SHALL mostrarse solo cuando la respuesta trae `reschedulable` y `cancellable` en verdadero, respectivamente; la SPA MUST NOT decidirlo con el reloj del navegador.

#### Scenario: Banderas del servidor mandan
- **GIVEN** el reloj del navegador está atrasado un día y la respuesta trae `cancellable: false` y `reschedulable: false` para un turno `CONFIRMED`
- **WHEN** se muestra el turno
- **THEN** no hay botones "Reprogramar" ni "Cancelar turno"

### Requirement: Enlace no válido genérico
Ante 404 de la consulta (cualquier `code`, incluido `INVALID_OR_EXPIRED_TOKEN`) o un token cuya forma no puede ser válida (caracteres fuera de Base64URL o longitud fuera de rango), la SPA SHALL mostrar un único mensaje genérico "Este enlace no es válido" que no indique si el turno existió ni el motivo, con un enlace para reservar un turno. Un token con forma imposible MUST NOT enviarse a la API.

#### Scenario: Token vencido o revocado
- **GIVEN** el servidor responde 404 `INVALID_OR_EXPIRED_TOKEN`
- **WHEN** se abre `/turno/{token}`
- **THEN** se muestra "Este enlace no es válido" con el mismo texto que para un token inexistente y un enlace a `/reservar`

#### Scenario: Token con forma imposible
- **WHEN** se abre `/turno/abc$%25`
- **THEN** no se envía ninguna petición a `/api/v1/public/bookings/` y no se muestra ningún dato de turno (el router no enlaza símbolos como token y muestra la página genérica "Página no encontrada"; un token de longitud fuera de rango muestra "Este enlace no es válido")

### Requirement: Turno en solo lectura
Cuando la respuesta trae `cancellable` y `reschedulable` en falso, la SPA SHALL mostrar el turno sin acciones de modificación y con un aviso de que no se puede cambiar desde el enlace. El estado `CANCELLED` SHALL mostrarse como "Cancelado"; un turno no modificable en cualquier otro estado (`CONFIRMED` ya comenzado, `COMPLETED`, `NO_SHOW`) SHALL mostrarse como "Turno pasado", sin distinguir asistencia de ausencia.

#### Scenario: Turno pasado
- **GIVEN** la respuesta trae `status: 'CONFIRMED'`, `cancellable: false`, `reschedulable: false`
- **WHEN** se muestra el turno
- **THEN** se ve la insignia "Turno pasado", el aviso "Este turno ya pasó…" y no hay botones "Reprogramar" ni "Cancelar turno"

#### Scenario: Ausente no se revela
- **GIVEN** la respuesta trae `status: 'NO_SHOW'` con las banderas en falso
- **WHEN** se muestra el turno
- **THEN** la insignia dice "Turno pasado" y el texto de la página no contiene "Ausente"

#### Scenario: Cancelado por el consultorio
- **GIVEN** la respuesta trae `status: 'CANCELLED'` con las banderas en falso
- **WHEN** se muestra el turno
- **THEN** se ve la insignia "Cancelado", un aviso de que el turno está cancelado y un enlace para reservar otro

### Requirement: Cancelación con confirmación previa
"Cancelar turno" SHALL abrir una confirmación modal que nombre la prestación, el día y el horario, advierta que no se puede deshacer y que el enlace dejará de funcionar, con el foco inicial en la opción que conserva el turno. Solo al confirmar SHALL enviarse `DELETE /api/v1/public/bookings/{token}` con la cabecera CSRF.

#### Scenario: Desistir de cancelar
- **GIVEN** un turno cancelable en pantalla
- **WHEN** se toca "Cancelar turno" y luego "No, mantener turno" (o Escape)
- **THEN** no se envía ninguna petición, el diálogo se cierra y el foco vuelve a "Cancelar turno"

### Requirement: Resultado de la cancelación
Mientras la cancelación espera respuesta, el botón SHALL indicar "Cancelando…" y no aceptar otro envío. Con 200 SHALL mostrarse "Turno cancelado" con los datos del turno, la insignia "Cancelado", el aviso de que se envió un email y de que el enlace ya no sirve, y un enlace para reservar otro turno.

#### Scenario: Cancelación confirmada
- **GIVEN** un turno cancelable y el servidor responde 200 con `status: 'CANCELLED'`
- **WHEN** se confirma la cancelación
- **THEN** se envía un único `DELETE` con `X-XSRF-TOKEN`, se muestra "Turno cancelado" con la insignia "Cancelado" y el aviso de email, y no quedan acciones de modificación

#### Scenario: Doble toque
- **GIVEN** la cancelación confirmada sigue esperando respuesta
- **WHEN** se vuelve a tocar el botón
- **THEN** se envía una sola petición

### Requirement: Reprogramación a un horario de la misma prestación
"Reprogramar" SHALL mostrar el selector de día y horario de la reserva pública con la disponibilidad pública de la misma prestación, con el día del turno actual elegido (o hoy si ese día ya pasó) y el turno actual siempre visible. "Volver" SHALL regresar al detalle sin cambios.

#### Scenario: Volver sin cambios
- **GIVEN** la pantalla de reprogramación con un horario elegido
- **WHEN** se toca "Volver"
- **THEN** se muestra el detalle con el turno original y no se envió ninguna reprogramación

### Requirement: Envío de la reprogramación
"Confirmar cambio" SHALL estar deshabilitado hasta elegir un horario y SHALL enviar `POST /api/v1/public/bookings/{token}/reschedule` con `{ startsAt }` igual al instante recibido en el horario elegido y la cabecera CSRF; mientras espera, SHALL indicar "Reprogramando…" sin aceptar otro envío. Con 200 SHALL mostrarse "¡Turno reprogramado!" con día y horario tomados de la respuesta, el horario anterior y el aviso de email y de que el mismo enlace sigue sirviendo.

#### Scenario: Reprogramación correcta
- **GIVEN** un turno reprogramable de Limpieza el martes 24 a las 10:30 y disponibilidad con 11:15 del jueves 26
- **WHEN** se toca "Reprogramar", se elige jueves 26, 11:15 y "Confirmar cambio", y el servidor responde 200 con `startsAt: '2026-11-26T14:15:00Z'`, `endsAt: '2026-11-26T15:00:00Z'`
- **THEN** se envía un único `POST …/reschedule` con `{"startsAt":"2026-11-26T14:15:00Z"}` y `X-XSRF-TOKEN`, y se muestra "¡Turno reprogramado!" con "jueves 26 de noviembre", "11:15 – 12:00" y el horario anterior "mar 24/11 · 10:30"

#### Scenario: Sin horario elegido
- **GIVEN** la pantalla de reprogramación sin horario elegido
- **WHEN** se toca "Confirmar cambio"
- **THEN** no se envía ninguna petición

### Requirement: Conflicto al reprogramar
Ante 409 `SLOT_TAKEN` (o 422 `APPOINTMENT_IN_PAST`) de la reprogramación, la SPA SHALL seguir en la pantalla de reprogramación del mismo día, mostrar un aviso que diga que el horario se ocupó y que el turno sigue en su día y horario originales, recargar la disponibilidad de ese día y destacar hasta tres horarios cercanos al rechazado, sin preselección.

#### Scenario: Horario ocupado en carrera
- **GIVEN** se eligió jueves 26 a las 11:15 y el servidor responde 409 `SLOT_TAKEN`
- **WHEN** se confirma el cambio
- **THEN** se ve "Ese horario se acaba de ocupar" con "Tu turno sigue el martes 24 de noviembre a las 10:30", la disponibilidad del jueves 26 se consulta de nuevo, aparecen los "Horarios cercanos" y ningún horario queda marcado

### Requirement: Errores de las acciones sobre el turno
La SPA SHALL traducir los errores a mensajes propios en español, sin mostrar nunca `title` ni `detail` del servidor: 422 `BOOKING_MODIFICATION_NOT_ALLOWED` → "Este turno ya no se puede modificar" y nueva consulta del estado; 404 en una acción → enlace no válido genérico; 429 `RATE_LIMITED` → aviso de espera con `Retry-After`, sin reintento automático; 403 `CSRF_REQUIRED` → página desactualizada, sin cambios hechos.

#### Scenario: Turno que empezó mientras tanto
- **GIVEN** un turno mostrado como cancelable y el servidor responde 422 `BOOKING_MODIFICATION_NOT_ALLOWED` al `DELETE`
- **WHEN** se confirma la cancelación
- **THEN** se ve "Este turno ya no se puede modificar", se vuelve a consultar el turno y se muestra en solo lectura

#### Scenario: Demasiados intentos al abrir
- **GIVEN** el servidor responde 429 con `Retry-After: 40` a la consulta
- **WHEN** se abre `/turno/{token}`
- **THEN** se ve "Demasiados intentos seguidos… Probá de nuevo en 1 minuto." y no hay datos de turno

### Requirement: Resultado desconocido y errores de conexión
Ante red, timeout, 5xx o respuesta ilegible de una cancelación o reprogramación, la SPA SHALL mostrar un aviso que no afirma ni niega el cambio, con la forma de comprobarlo (email o "Ver estado actual"). Ante esos fallos en la consulta inicial SHALL mostrar un aviso de conexión con "Reintentar".

#### Scenario: Timeout al reprogramar
- **GIVEN** la reprogramación no responde dentro del tiempo límite
- **WHEN** se confirma el cambio
- **THEN** se ve "No pudimos confirmar el cambio de horario" con un botón "Ver estado actual" que vuelve a consultar el turno

#### Scenario: Error de conexión al abrir
- **GIVEN** la consulta inicial falla por red
- **WHEN** se toca "Reintentar"
- **THEN** se repite la consulta y, con 200, se muestra el turno

### Requirement: Reprogramación online no disponible
Cuando la prestación del turno no tiene disponibilidad pública (la consulta de horarios responde 404 `SERVICE_NOT_FOUND`, 422 `SERVICE_INACTIVE` o `NO_ACTIVE_SERVICES`, o no se puede identificar la prestación pública del turno), la SPA SHALL mostrar en lugar del selector un aviso de que el turno no se puede reprogramar online, con la sugerencia de comunicarse con el consultorio o cancelar, y SHALL mantener disponible "Cancelar turno" si el turno es cancelable.

#### Scenario: Prestación ya no pública
- **GIVEN** un turno reprogramable cuya prestación responde 404 `SERVICE_NOT_FOUND` en la disponibilidad
- **WHEN** se toca "Reprogramar"
- **THEN** se ve "No se puede reprogramar este turno online" y la opción de volver al detalle para cancelar

### Requirement: Token y datos fuera de rastros
El token MUST viajar solo en la ruta de las peticiones a `/api/v1/public/bookings/{token}` (y su `/reschedule`), codificado como segmento de ruta, y MUST NOT aparecer en el título del documento, la consola, el texto de la página, `localStorage`/`sessionStorage`, la query de otras peticiones (disponibilidad, prestaciones) ni en enlaces de la página. La vista SHALL conservar el estado del turno solo en memoria.

#### Scenario: Sin rastros del token
- **GIVEN** el token `tok_Abc-123_xyz456789012345` y un recorrido que consulta, abre la reprogramación, recibe 409, reprograma y luego cancela
- **WHEN** se inspeccionan `document.title`, los mensajes de consola, el texto de la página, los `storage`, los `href` de la página y las URL de las peticiones que no son de gestión
- **THEN** ninguno contiene el token

### Requirement: Presentación accesible y responsive de la gestión
La pantalla SHALL usar el app shell público, solo variables del design system, objetivos táctiles de al menos 44 px, avisos con ícono y texto (no solo color), foco en el `h1` al cambiar de pantalla y lectura correcta en 320 px de ancho sin scroll horizontal. En mobile la acción de "Confirmar cambio" SHALL quedar fija al pie con el resumen del horario elegido.

#### Scenario: Foco al cambiar de pantalla
- **GIVEN** el detalle en pantalla
- **WHEN** se pasa a la reprogramación, a "Turno cancelado" o a "¡Turno reprogramado!"
- **THEN** el foco queda en el `h1` de la nueva pantalla
