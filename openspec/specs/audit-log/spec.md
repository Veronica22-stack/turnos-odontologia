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
