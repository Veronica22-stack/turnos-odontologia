# Spec Delta

## Purpose

Permite que una persona sin cuenta reserve un turno online 24/7 con datos mínimos de contacto, protegiendo el consultorio contra abuso y garantizando que la confirmación llegue por email con un enlace de gestión privado.

## ADDED Requirements

### Requirement: Public booking endpoint
El sistema SHALL exponer `POST /api/v1/public/bookings` sin autenticación, bajo `/api/v1/public/**`, aceptando prestación, inicio del turno, nombre, DNI, teléfono, email, campo honeypot y aceptación del aviso de privacidad. El servidor MUST calcular el fin del turno con la duración vigente de la prestación.

#### Scenario: Reserva pública correcta
- **GIVEN** una prestación activa y pública con duración 30 minutos y un slot libre dentro del horario de atención
- **WHEN** se envía `POST /api/v1/public/bookings` con datos válidos y aceptación del aviso
- **THEN** la respuesta es 201 con la confirmación del turno, sin incluir ningún token en claro, y el turno queda `CONFIRMED` con `origin = PUBLIC`

#### Scenario: El cliente no elige la duración ni el fin
- **GIVEN** un cuerpo que intenta fijar `endsAt` o `durationMin`
- **WHEN** se procesa la reserva
- **THEN** esos campos se ignoran y el servidor calcula el rango desde la duración de la prestación

### Requirement: Booking validation
El sistema MUST rechazar reservas fuera de la ventana permitida, con una prestación inexistente, inactiva o no pública, o con datos de contacto con formato inválido. El inicio MUST respetar la anticipación mínima, el horizonte máximo y la granularidad configurados del consultorio.

#### Scenario: Slot demasiado próximo
- **GIVEN** `min_notice_min = 120` y un inicio a 60 minutos
- **WHEN** se envía la reserva
- **THEN** la respuesta es 422 con un código estable y no se crea ningún turno

#### Scenario: Prestación no pública
- **GIVEN** una prestación activa pero con `public_visible = false`
- **WHEN** se envía la reserva
- **THEN** la respuesta es 404 o 422 con un código genérico y no se crea ningún turno

### Requirement: Slot contention
Si el slot elegido deja de estar disponible antes de confirmarse, la reserva MUST fallar con 409 `SLOT_TAKEN` y conservar intacto cualquier estado previo. No se permite sobreescribir un turno ajeno.

#### Scenario: Carrera por el mismo slot
- **GIVEN** dos reservas concurrentes para el mismo inicio y prestación
- **WHEN** ambas se procesan
- **THEN** una devuelve 201 y la otra 409 `SLOT_TAKEN`, y solo existe un turno `CONFIRMED` en ese rango

### Requirement: Anti-abuse controls
El endpoint MUST aplicar rate limit por IP, rechazar solicitudes con el campo honeypot completo y limitar la cantidad de turnos futuros activos por DNI, email y teléfono. Ante honeypot o rate limit la respuesta MUST ser genérica y NO MUST revelar si la reserva se procesó.

#### Scenario: Honeypot completo
- **GIVEN** un cuerpo con el campo oculto honeypot con valor no vacío
- **WHEN** se envía la reserva
- **THEN** la respuesta es genérica de éxito aparente, no se crea turno, paciente ni token y no se registra el valor del honeypot

#### Scenario: Rate limit excedido
- **GIVEN** un IP que superó `RATE_LIMIT_PUBLIC_PER_MIN` en la ventana
- **WHEN** se envía una nueva reserva
- **THEN** la respuesta es 429 `RATE_LIMITED` con `Retry-After` en segundos enteros y no se crea ningún turno

#### Scenario: Límite por contacto
- **GIVEN** un DNI con tres turnos futuros activos
- **WHEN** se intenta una nueva reserva con ese DNI
- **THEN** la respuesta es 422 con un código estable y no se crea ningún turno

### Requirement: Privacy notice acceptance
Toda reserva pública MUST exigir la aceptación explícita del aviso de privacidad y MUST registrar la versión del aviso aceptado junto con el token emitido. El texto del aviso queda pendiente de PA-10.

#### Scenario: Aceptación ausente
- **GIVEN** un cuerpo sin el indicador de aceptación o con valor falso
- **WHEN** se envía la reserva
- **THEN** la respuesta es 400 `VALIDATION_FAILED` con el campo correspondiente y no se crea ningún turno

#### Scenario: Aceptación registrada
- **GIVEN** una reserva válida con aceptación verdadera
- **WHEN** se confirma la reserva
- **THEN** el registro del token guarda la versión del aviso y el instante de aceptación, sin datos personales adicionales

### Requirement: Management token issuance and email link
El sistema SHALL generar un token aleatorio criptográficamente seguro de al menos 128 bits de entropía, almacenar únicamente su hash y asociarlo al turno. El token en claro MUST viajar solo en el enlace del email (`APP_BASE_URL/turno/{token}`) y NO MUST persistirse ni loguearse. La SPI de notificaciones SHALL poder emitir un token nuevo por correo para el mismo turno.

#### Scenario: Token emitido al reservar
- **GIVEN** una reserva confirmada
- **WHEN** se envía el email de confirmación
- **THEN** el cuerpo contiene un enlace con un token Base64URL de al menos 22 caracteres, la base de datos contiene solo su hash y los logs no contienen el token

#### Scenario: Token para un recordatorio días después
- **GIVEN** un turno dentro del horizonte máximo y un recordatorio programado antes del turno
- **WHEN** el proveedor de enlaces emite un token para ese correo
- **THEN** se crea un nuevo token hasheado con vigencia hasta después del turno y el email contiene ese token sin que se pueda recuperar el original desde la base

### Requirement: Patient reuse without overwrite
La reserva SHALL asociar el turno a un paciente existente cuando el DNI ya esté registrado en el consultorio, sin sobreescribir sus datos, y SHALL crear uno nuevo con origen `PUBLIC_BOOKING` en caso contrario.

#### Scenario: DNI ya existente
- **GIVEN** un paciente con datos cargados por el odontólogo y el mismo DNI
- **WHEN** se reserva con datos de contacto distintos
- **THEN** el turno se asocia a ese paciente, sus datos permanecen intactos y la respuesta no revela la divergencia

### Requirement: Booking privacy in logs
Los logs, respuestas y registros de auditoría generados por la reserva NO MUST contener el token en claro, DNI, email, teléfono, nombre del paciente ni notas. Solo SHALL usar identificadores opacos y códigos estables.

#### Scenario: Registro de una reserva
- **GIVEN** una reserva con datos personales completos
- **WHEN** se completa la operación
- **THEN** los logs contienen como máximo `appointmentId`, `patientId`, `serviceId` y `practiceId`, y la respuesta no contiene datos de contacto
