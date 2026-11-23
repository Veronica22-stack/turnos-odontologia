# audit-log Specification

## Purpose

El rastro de auditoría deja evidencia verificable de los accesos a datos clínicos y de pacientes y de los eventos de seguridad, sin exponer los datos sensibles que motivaron el acceso, para sostener la trazabilidad exigida por las leyes 25.326 y 26.529.

## Requirements

### Requirement: Registro append-only de eventos
El sistema SHALL persistir cada evento de auditoría informado por un caso de uso como una fila nueva e inmutable, asociada a la instalación, a la acción, al instante UTC generado por el servidor y, cuando corresponda, al usuario y al paciente. El sistema MUST NOT ofrecer operación alguna para modificar o eliminar un registro ya escrito.

#### Scenario: Acceso a historia clínica queda registrado
- **GIVEN** un caso de uso que informa un acceso de lectura a una entrada de historia clínica con usuario, paciente, tipo y referencia de la entidad, dirección IP y agente de usuario
- **WHEN** se registra el evento
- **THEN** queda una fila con el `practice_id` de la instalación, la acción `HC_READ`, el `user_id` y `patient_id` indicados, la referencia de la entidad, la IP y el agente de usuario, y un `occurred_at` en UTC; esa fila no puede ser modificada ni eliminada desde la aplicación

#### Scenario: Evento sin usuario autenticado
- **GIVEN** un evento originado por el uso de un enlace de gestión de turno, sin usuario interno autenticado
- **WHEN** se registra con la acción `BOOKING_TOKEN_USE`
- **THEN** la fila se persiste con `user_id` nulo y la referencia del turno en los campos de entidad, sin exigir una cuenta de usuario

#### Scenario: Instante generado por el servidor
- **GIVEN** un evento que incluye un valor de fecha y hora enviado por el cliente
- **WHEN** se registra
- **THEN** `occurred_at` toma el instante del reloj del servidor en UTC y el valor del cliente es ignorado

### Requirement: Catálogo de acciones auditables
El sistema SHALL admitir únicamente acciones pertenecientes al catálogo `LOGIN_OK`, `LOGIN_FAIL`, `PATIENT_READ`, `PATIENT_UPDATE`, `HC_READ`, `HC_CREATE`, `HC_CORRECT`, `BOOKING_TOKEN_USE` y sus ampliaciones versionadas. Una acción fuera del catálogo MUST ser rechazada antes de escribir y MUST NOT producir una fila.

#### Scenario: Acción del catálogo
- **GIVEN** un evento con la acción `HC_CORRECT`
- **WHEN** se registra
- **THEN** la fila se persiste con esa acción

#### Scenario: Acción desconocida
- **GIVEN** un evento con la acción `DELETE_EVERYTHING`
- **WHEN** se intenta registrar
- **THEN** el registro es rechazado y no queda ninguna fila nueva

#### Scenario: Ampliación del catálogo
- **GIVEN** la necesidad de auditar una acción aún no incluida
- **WHEN** se intenta registrar sin haber ampliado el catálogo en la base de datos
- **THEN** el registro es rechazado; la ampliación SHALL hacerse en una migración nueva, sin editar la migración ya aplicada

### Requirement: Sin datos clínicos ni identificadores personales en claro
El sistema MUST NOT persistir en `details` ni escribir en los registros de la aplicación contenido clínico, DNI, correo electrónico, teléfono, nombre completo, contraseñas ni tokens de gestión en claro. Cada evento SHALL contener solo referencias (identificadores) y metadatos operacionales autorizados por una lista de permitidos; todo valor no autorizado MUST ser descartado o redactado antes de persistir y antes de registrar cualquier mensaje en el log.

#### Scenario: Contenido clínico convertido en referencia
- **GIVEN** un evento de creación de evolución clínica cuyo detalle incluye el texto de la evolución
- **WHEN** se registra
- **THEN** la fila guarda el tipo y el identificador de la entrada y de la historia clínica, y `details` no contiene el texto clínico ni parte de él

#### Scenario: Datos de contacto no persisten ni se loguean
- **GIVEN** un evento al que se le pasan un DNI, un correo electrónico, un teléfono y un nombre de paciente dentro de los detalles o dentro del mensaje de una excepción producida al registrarlo
- **WHEN** se procesa el evento y se registra el resultado
- **THEN** ninguno de esos valores aparece en la fila de auditoría ni en los logs de la aplicación; solo se registra el identificador del paciente

#### Scenario: Token de gestión nunca en claro
- **GIVEN** el uso de un enlace de gestión de turno con su token en claro
- **WHEN** se registra el evento `BOOKING_TOKEN_USE`
- **THEN** la fila referencia el turno mediante su identificador y no contiene el token; los mensajes de log tampoco lo contienen

#### Scenario: Metadatos permitidos
- **GIVEN** un evento con acción, tipo y referencia de entidad, dirección IP y agente de usuario
- **WHEN** se registra
- **THEN** esos metadatos quedan en sus columnas correspondientes y `details` contiene únicamente claves de la lista de permitidos, con valores escalares acotados

#### Scenario: Clave no permitida
- **GIVEN** un evento con una clave de detalle no incluida en la lista de permitidos
- **WHEN** se sanitiza el evento
- **THEN** la clave y su valor son descartados o redactados y el evento se persiste sin ellos

### Requirement: La operación auditada depende del registro
Para toda lectura o escritura de datos clínicos, el sistema SHALL completar la operación solo si su evento de auditoría fue persistido; si el registro falla, la operación MUST NOT ejecutarse y SHALL devolverse un error genérico sin datos personales.

#### Scenario: Falla el registro de una lectura de historia clínica
- **GIVEN** un caso de uso de lectura de historia clínica cuyo evento de auditoría no puede persistirse
- **WHEN** se ejecuta el caso de uso
- **THEN** no se entregan datos clínicos, la operación falla con un error genérico y el log no incluye identificadores personales ni contenido clínico

#### Scenario: Registro exitoso habilita la operación
- **GIVEN** un caso de uso de escritura en historia clínica cuyo evento de auditoría se persistió correctamente
- **WHEN** se ejecuta el caso de uso
- **THEN** la escritura se realiza y queda una sola fila de auditoría que la referencia

### Requirement: Auditoría de cada acceso a la historia clínica
El sistema SHALL registrar `HC_READ` en cada consulta de historia clínica o de sus entradas (aunque no esté abierta), `HC_CREATE` en cada alta de entrada y `HC_CORRECT` en cada corrección, con usuario, paciente, tipo e identificador de la entidad, IP y agente, en la misma transacción que la operación.

#### Scenario: Lectura auditada
- **GIVEN** un odontólogo autenticado y un paciente existente
- **WHEN** consulta la anamnesis del paciente
- **THEN** queda una fila `HC_READ` con su usuario y el `patient_id`, y ninguna otra fila clínica

#### Scenario: Alta auditada
- **WHEN** el odontólogo registra una anamnesis
- **THEN** queda una fila `HC_CREATE` cuyo `entity_id` es el identificador de la nueva entrada

#### Scenario: Corrección auditada
- **WHEN** el odontólogo corrige una anamnesis
- **THEN** queda una fila `HC_CORRECT` cuyo `entity_id` es la nueva entrada y cuyos detalles referencian solo el identificador de la entrada corregida

#### Scenario: Falla de auditoría revierte la escritura
- **GIVEN** que el registro de auditoría falla
- **WHEN** el odontólogo registra o corrige una entrada
- **THEN** no queda ninguna entrada ni historia clínica nueva y la respuesta es un error genérico

#### Scenario: Solicitud rechazada no se audita como acceso
- **WHEN** una solicitud de historia clínica es rechazada por autenticación, autorización o validación
- **THEN** no se registra `HC_READ`, `HC_CREATE` ni `HC_CORRECT`

### Requirement: Auditoría clínica sin contenido clínico
Los eventos `HC_READ`, `HC_CREATE` y `HC_CORRECT` MUST NOT incluir contenido de las entradas ni el motivo de corrección; sus detalles SHALL limitarse al tipo de entrada y a identificadores.

#### Scenario: Detalles de una corrección
- **WHEN** se corrige una anamnesis con motivo "alergia mal cargada"
- **THEN** la fila `HC_CORRECT` no contiene el texto del motivo ni el contenido de la anamnesis

### Requirement: Detalles de auditoría clínicos permitidos
El sanitizador de detalles de auditoría SHALL admitir las claves `entry_type`, `version` y `corrects_entry_id`, y MUST conservar sin redactar un UUID exacto únicamente bajo la clave `corrects_entry_id`; la lista de claves prohibidas MUST permanecer sin cambios.

#### Scenario: Referencia a la entrada corregida
- **WHEN** se audita una corrección con detalles `entry_type` y `corrects_entry_id` con un UUID exacto
- **THEN** ambos se conservan tal cual en el evento

#### Scenario: UUID bajo otra clave
- **WHEN** un detalle con una clave distinta de `corrects_entry_id` contiene un UUID exacto
- **THEN** el valor sigue el tratamiento habitual del sanitizador

#### Scenario: Clave prohibida sigue descartada
- **WHEN** los detalles incluyen una clave prohibida (por ejemplo `email` o `content`)
- **THEN** esa clave no se persiste

### Requirement: Auditoría obligatoria en accesos a pacientes
El sistema SHALL registrar un evento de auditoría con la acción `PATIENT_READ` cada vez que el odontólogo consulte los detalles de la ficha de un paciente, asociando el identificador del usuario y del paciente sin incluir datos personales sensibles en el detalle del evento.

#### Scenario: Lectura de ficha de paciente auditada
- **GIVEN** una consulta exitosa de la ficha de un paciente por el odontólogo autenticado
- **WHEN** se completa la operación
- **THEN** queda registrado un evento en `audit_log` con acción `PATIENT_READ`, `user_id` del profesional y `patient_id` correspondiente

### Requirement: Booking token use audit event
La acción `BOOKING_TOKEN_USE` SHALL ser emitida por el módulo `reservas` en cada consulta, cancelación o reprogramación con token, con `user_id` nulo y el turno como entidad referenciada. Los `details` SHALL usar únicamente claves del catálogo permitido (`outcome`, `reason_code`) y NO SHALL contener token, DNI, email, teléfono ni notas.

#### Scenario: Registro del uso del token
- **GIVEN** una consulta válida con token de gestión
- **WHEN** se resuelve el turno
- **THEN** se inserta una fila `BOOKING_TOKEN_USE` con `entity_id` igual al identificador del turno y `details` con `outcome` y `reason_code`

#### Scenario: Sin token en claro
- **GIVEN** un token con valor reconocible en claro
- **WHEN** se audita su uso
- **THEN** ni la fila ni los logs contienen ese valor, y la restricción anti-PII de la base acepta la inserción
