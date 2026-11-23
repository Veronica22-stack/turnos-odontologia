# appointment-notifications Specification

## Purpose

Avisar por email al paciente (y, ante cancelaciones del paciente, al odontólogo) de la confirmación, recordatorio, reprogramación y cancelación de sus turnos mediante una cola persistente con reintentos, sin datos clínicos ni datos personales en logs, y sin que un fallo de envío afecte nunca al turno.

## Requirements

### Requirement: Confirmación encolada al confirmar un turno
Cuando un turno se confirma con éxito, el sistema SHALL encolar una notificación `CONFIRMATION` para el paciente, con envío inmediato. Si la operación del turno se revierte, MUST NOT encolarse nada.

#### Scenario: Turno confirmado
- **WHEN** se crea un turno con éxito
- **THEN** existe una notificación `CONFIRMATION` `PENDING` para ese turno con `scheduled_for` igual al instante del servidor y `attempts = 0`

#### Scenario: Alta rechazada
- **WHEN** un alta de turno se rechaza por solapamiento, bloqueo u horario
- **THEN** no se encola ninguna notificación

### Requirement: Recordatorio programado según la anticipación del consultorio
Al confirmar un turno, el sistema SHALL encolar un único `REMINDER` con `scheduled_for = starts_at − reminder_hours_before` del consultorio vigente en ese momento. Si ese instante no es posterior al actual, MUST NOT encolarse recordatorio.

#### Scenario: Recordatorio a 24 h
- **GIVEN** un consultorio con `reminder_hours_before = 24`
- **WHEN** se confirma un turno que empieza el 2026-12-10T13:00:00Z y el instante actual es 2026-12-01T12:00:00Z
- **THEN** existe un `REMINDER` `PENDING` con `scheduled_for = 2026-12-09T13:00:00Z`

#### Scenario: Turno dentro de la ventana del recordatorio
- **GIVEN** un consultorio con `reminder_hours_before = 24`
- **WHEN** se confirma un turno que empieza dentro de 3 horas
- **THEN** se encola la `CONFIRMATION` y no se encola ningún `REMINDER`

### Requirement: Reprogramación notifica y recalcula el recordatorio
Al reprogramar un turno, el sistema SHALL dejar una notificación `RESCHEDULE` pendiente de envío inmediato y SHALL recalcular el `REMINDER` con el nuevo inicio, reiniciando sus intentos, aunque el anterior ya se hubiera enviado. Si el nuevo instante de recordatorio no es posterior al actual, el recordatorio pendiente MUST quedar `DISCARDED`.

#### Scenario: Recordatorio recalculado
- **GIVEN** un turno con un `REMINDER` `PENDING` para el 2026-12-09T13:00:00Z
- **WHEN** el turno se reprograma para empezar el 2026-12-15T13:00:00Z con `reminder_hours_before = 24`
- **THEN** sigue existiendo un único `REMINDER` para el turno, `PENDING`, con `scheduled_for = 2026-12-14T13:00:00Z` y `attempts = 0`, y existe un `RESCHEDULE` `PENDING`

#### Scenario: Recordatorio ya enviado y turno movido más adelante
- **GIVEN** un turno cuyo `REMINDER` está `SENT`
- **WHEN** el turno se reprograma a una fecha cuya ventana de recordatorio todavía no llegó
- **THEN** el `REMINDER` vuelve a `PENDING` con el nuevo `scheduled_for`

#### Scenario: Segunda reprogramación
- **GIVEN** un turno con un `RESCHEDULE` ya `SENT`
- **WHEN** el turno se reprograma otra vez
- **THEN** el mismo `RESCHEDULE` vuelve a `PENDING` para envío inmediato y no se crea una segunda fila de ese tipo

#### Scenario: Reprogramado dentro de la ventana
- **GIVEN** un turno con un `REMINDER` `PENDING`
- **WHEN** el turno se reprograma para empezar dentro de 3 horas con `reminder_hours_before = 24`
- **THEN** el `REMINDER` queda `DISCARDED` y el `RESCHEDULE` queda `PENDING`

### Requirement: Cancelación notifica y anula pendientes
Al cancelarse un turno, el sistema SHALL encolar una notificación `CANCELLATION` para el paciente con envío inmediato y SHALL marcar `DISCARDED` toda otra notificación `PENDING` del turno, de modo que nunca se envíe un recordatorio de un turno cancelado.

#### Scenario: Cancelación anula el recordatorio
- **GIVEN** un turno con `REMINDER` `PENDING`
- **WHEN** el odontólogo cancela el turno
- **THEN** el `REMINDER` queda `DISCARDED`, existe una `CANCELLATION` `PENDING` y ningún recordatorio de ese turno se envía después

#### Scenario: Confirmación aún pendiente al cancelar
- **GIVEN** un turno cuya `CONFIRMATION` sigue `PENDING` por fallas del proveedor
- **WHEN** el turno se cancela
- **THEN** la `CONFIRMATION` queda `DISCARDED` y solo se envía la `CANCELLATION`

### Requirement: Aviso al odontólogo ante cancelación del paciente
Cuando el turno lo cancela el paciente, el sistema SHALL encolar además una notificación `DENTIST_CANCELLATION` dirigida al email de contacto del consultorio, con fecha, hora y prestación y sin datos personales del paciente. Las cancelaciones hechas por el odontólogo MUST NOT generarla.

#### Scenario: Cancelación por el paciente
- **WHEN** un turno se cancela con quién canceló = paciente
- **THEN** existen una `CANCELLATION` y una `DENTIST_CANCELLATION` `PENDING` para el turno

#### Scenario: Cancelación por el odontólogo
- **WHEN** el odontólogo cancela un turno
- **THEN** existe una `CANCELLATION` y no existe `DENTIST_CANCELLATION`

### Requirement: Encolado idempotente por turno y tipo
El sistema SHALL mantener como máximo una notificación por turno y tipo. Procesar dos veces el mismo evento de confirmación o de cancelación MUST NOT crear filas adicionales ni reiniciar una notificación ya enviada.

#### Scenario: Evento de confirmación procesado dos veces
- **GIVEN** un turno con su `CONFIRMATION` ya `SENT`
- **WHEN** el mismo evento de confirmación se procesa de nuevo
- **THEN** sigue existiendo una única `CONFIRMATION`, en estado `SENT`, y no se envía otro email

#### Scenario: Inserción duplicada concurrente
- **WHEN** dos transacciones intentan encolar la misma combinación de turno y tipo
- **THEN** queda una sola fila y ninguna de las dos operaciones del turno falla

### Requirement: Fallos de notificación aislados del turno
Un error al encolar, renderizar o enviar una notificación MUST NOT revertir la operación del turno ni cambiar la respuesta que recibe quien la pidió. El error SHALL registrarse en el log solo con identificadores.

#### Scenario: Proveedor caído al reservar
- **GIVEN** el proveedor de email rechaza todos los envíos por indisponibilidad
- **WHEN** el odontólogo crea un turno
- **THEN** la API responde con éxito, el turno queda `CONFIRMED` y su `CONFIRMATION` queda `PENDING` con un intento fallido y un próximo intento programado

#### Scenario: Error al encolar
- **GIVEN** que el encolado falla por un error inesperado de la base
- **WHEN** se cancela un turno
- **THEN** la API responde con éxito, el turno queda `CANCELLED` y el log registra el error con el identificador del turno y sin datos personales

### Requirement: Despacho de notificaciones vencidas
El sistema SHALL procesar periódicamente (y además inmediatamente después de encolar) las notificaciones `PENDING` con `scheduled_for` menor o igual al instante actual, en orden de `scheduled_for`. Al enviarse con éxito una notificación SHALL quedar `SENT` con `sent_at`.

#### Scenario: Recordatorio vencido enviado
- **GIVEN** un `REMINDER` `PENDING` vencido de un turno `CONFIRMED` futuro y un paciente con email
- **WHEN** corre el despacho
- **THEN** se envía un email al paciente y la notificación queda `SENT` con `sent_at` igual al instante del servidor

#### Scenario: Recordatorio todavía no vencido
- **GIVEN** un `REMINDER` `PENDING` con `scheduled_for` en el futuro
- **WHEN** corre el despacho
- **THEN** no se envía nada y la notificación sigue `PENDING`

### Requirement: Un solo envío ante despachadores concurrentes
Dos ejecuciones simultáneas del despacho, en el mismo o en distintos procesos, MUST NOT tomar la misma notificación: cada una SHALL saltear las que otra está procesando.

#### Scenario: Dos despachadores a la vez
- **GIVEN** diez notificaciones `PENDING` vencidas
- **WHEN** dos despachos corren en paralelo
- **THEN** se envían exactamente diez emails, uno por notificación, y las diez quedan `SENT`

### Requirement: Revalidación del turno antes de enviar
Antes de enviar, el sistema SHALL releer el turno y descartar (`DISCARDED`, con un código de motivo) las notificaciones que ya no corresponden: recordatorio, confirmación o reprogramación de un turno no `CONFIRMED` o ya iniciado, turno inexistente, o destinatario sin email.

#### Scenario: Recordatorio de turno cancelado entre medio
- **GIVEN** un `REMINDER` `PENDING` vencido cuyo turno está `CANCELLED`
- **WHEN** corre el despacho
- **THEN** no se envía email y el `REMINDER` queda `DISCARDED`

#### Scenario: Recordatorio tardío
- **GIVEN** un `REMINDER` `PENDING` vencido de un turno cuyo inicio ya pasó (la aplicación estuvo detenida)
- **WHEN** corre el despacho
- **THEN** no se envía email y el `REMINDER` queda `DISCARDED`

#### Scenario: Paciente sin email
- **GIVEN** una `CONFIRMATION` vencida de un paciente sin email registrado
- **WHEN** corre el despacho
- **THEN** no se intenta enviar, la notificación queda `DISCARDED` y no se reintenta

### Requirement: Reintentos con backoff ante fallas transitorias
Ante una falla transitoria del proveedor, el sistema SHALL incrementar `attempts`, guardar un código de error y reprogramar con backoff exponencial (5, 10, 20, 40 minutos por defecto, con tope de 1 hora). Al alcanzar el máximo de intentos (5 por defecto) la notificación SHALL quedar `FAILED` y registrarse una alerta en el log.

#### Scenario: Primer fallo transitorio
- **GIVEN** una notificación `PENDING` vencida con `attempts = 0` y el proveedor no disponible
- **WHEN** corre el despacho en el instante T
- **THEN** la notificación queda `PENDING` con `attempts = 1` y `scheduled_for = T + 5 minutos`

#### Scenario: Tercer fallo transitorio
- **GIVEN** una notificación con `attempts = 2` y el proveedor no disponible
- **WHEN** corre el despacho en el instante T
- **THEN** queda `PENDING` con `attempts = 3` y `scheduled_for = T + 20 minutos`

#### Scenario: Agotados los intentos
- **GIVEN** una notificación con `attempts = 4` y el proveedor no disponible
- **WHEN** corre el despacho
- **THEN** queda `FAILED` con `attempts = 5`, no se vuelve a intentar y el log registra una advertencia con su identificador

#### Scenario: Proveedor recuperado
- **GIVEN** una notificación `PENDING` con `attempts = 2` cuyo próximo intento venció y el proveedor disponible
- **WHEN** corre el despacho
- **THEN** se envía y queda `SENT`

### Requirement: Fallas permanentes sin reintento
Una falla que el proveedor informa como permanente (por ejemplo, destinatario rechazado) SHALL dejar la notificación `FAILED` en el primer intento, con su código de error y sin reintentos.

#### Scenario: Destinatario rechazado
- **GIVEN** el proveedor rechaza la dirección del destinatario de forma permanente
- **WHEN** corre el despacho
- **THEN** la notificación queda `FAILED` con `attempts = 1` y no se reprograma

### Requirement: Contenido del email sin datos clínicos
Cada email SHALL incluir solo fecha y hora del turno en la zona horaria del consultorio, nombre de la prestación, nombre, dirección y teléfono de contacto del consultorio y, si existe, el link de gestión. MUST NOT incluir datos clínicos, notas del turno, ni nombre, DNI o teléfono del paciente.

#### Scenario: Confirmación en hora local
- **GIVEN** un turno que empieza el 2026-12-10T13:00:00Z en un consultorio con zona `America/Argentina/Buenos_Aires`
- **WHEN** se arma el email de confirmación
- **THEN** el texto muestra la fecha 10/12/2026 y la hora 10:00, el nombre de la prestación y los datos del consultorio

#### Scenario: Sin datos sensibles
- **GIVEN** un turno con notas y un paciente con nombre, DNI y teléfono
- **WHEN** se arma cualquiera de los emails del turno
- **THEN** el texto no contiene las notas, el nombre, el DNI ni el teléfono del paciente

### Requirement: Link de gestión opcional
Si existe un emisor de tokens de gestión, el email SHALL incluir el link `APP_BASE_URL/turno/{token}` armado al momento del envío; el link MUST NOT persistirse ni registrarse en logs. Si no existe emisor o no hay token para el turno, el email SHALL omitir el link e indicar que, para cancelar o reprogramar, se contacte al consultorio.

#### Scenario: Sin emisor de tokens
- **GIVEN** que no hay emisor de tokens de gestión configurado
- **WHEN** se arma un email de confirmación
- **THEN** el texto no contiene ningún link `/turno/` e indica contactar al consultorio con su teléfono

#### Scenario: Con emisor de tokens
- **GIVEN** un emisor que devuelve el token `abc123` para el turno y `APP_BASE_URL = https://turnos.ejemplo.com`
- **WHEN** se arma un email de confirmación
- **THEN** el texto contiene `https://turnos.ejemplo.com/turno/abc123`, y ni la fila de la notificación ni el log contienen `abc123`

### Requirement: Envío simulado en desarrollo
Con el proveedor `log`, el sistema SHALL registrar cada envío en el log de la aplicación sin enviarlo, con solo el identificador de la notificación y del turno, el tipo y la plantilla. MUST NOT registrar la dirección del destinatario, el asunto ni el cuerpo del email.

#### Scenario: Envío en desarrollo
- **GIVEN** `MAIL_PROVIDER = log`
- **WHEN** se despacha una confirmación
- **THEN** el log contiene una línea con el identificador de la notificación, del turno y el tipo `CONFIRMATION`, no contiene el email del paciente ni el texto del email, y la notificación queda `SENT`

### Requirement: Sin datos personales en logs ni en el registro de errores
Ningún log del módulo de notificaciones y ningún valor de `last_error` SHALL contener direcciones de email, nombres, DNI, teléfonos, tokens ni el texto devuelto por el proveedor; solo identificadores, tipos, estados y códigos de error de un catálogo cerrado.

#### Scenario: Error del proveedor que incluye la dirección
- **GIVEN** el proveedor responde con un error cuyo mensaje contiene la dirección del paciente
- **WHEN** corre el despacho
- **THEN** `last_error` guarda solo un código del catálogo y ni `last_error` ni el log contienen la dirección
