# Spec Delta: clinical-evolutions

## Purpose

Permite al odontólogo registrar lo realizado en cada atención como evolución inmutable de la historia clínica, asociarla opcionalmente a un turno del mismo paciente, consultar el historial cronológico y corregir errores sin perder el asiento original.

## ADDED Requirements

### Requirement: Registro de una evolución
El sistema SHALL permitir al odontólogo registrar una evolución con una descripción de texto libre obligatoria y un turno asociado opcional; la evolución SHALL persistirse como una entrada clínica inmutable con autor e instante fijados por el servidor.

#### Scenario: Registro válido sin turno
- **GIVEN** un odontólogo autenticado y un paciente activo
- **WHEN** envía `POST /api/v1/patients/{patientId}/clinical-record/evolutions` con `description` "Control post extracción, sin complicaciones"
- **THEN** responde `201` con la evolución nueva, su `id`, `authorId` de la sesión, `createdAt` del servidor y `appointmentId` nulo

#### Scenario: Primer registro abre la historia clínica
- **GIVEN** un paciente activo sin historia clínica
- **WHEN** el odontólogo registra una evolución
- **THEN** se abre la historia clínica del paciente y la evolución queda asociada a ella

#### Scenario: Descripción ausente, vacía o excedida
- **WHEN** el odontólogo envía una evolución sin `description`, con solo espacios o con más de 5000 caracteres
- **THEN** recibe `400` con `code = VALIDATION_FAILED`, el error indica el campo `description` sin repetir el texto y no se crea ninguna entrada

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente dado de baja
- **WHEN** el odontólogo registra una evolución
- **THEN** recibe `409` con `code = PATIENT_INACTIVE` y no se crea ninguna entrada ni historia clínica

#### Scenario: Paciente inexistente
- **WHEN** el odontólogo registra una evolución para un paciente que no existe
- **THEN** recibe `404` con `code = PATIENT_NOT_FOUND` y no se crea ninguna entrada

### Requirement: Asociación opcional a un turno del mismo paciente
Una evolución SHALL poder asociarse a un turno informando `appointmentId`; el turno MUST existir en el consultorio, pertenecer al mismo paciente, no estar `CANCELLED` ni `NO_SHOW` y haber comenzado (inicio anterior o igual al instante actual). Un mismo turno SHALL admitir varias evoluciones. Registrar una evolución MUST NOT modificar el turno.

#### Scenario: Turno completado del mismo paciente
- **GIVEN** un turno `COMPLETED` del paciente
- **WHEN** el odontólogo registra una evolución con ese `appointmentId`
- **THEN** responde `201` con la evolución asociada a ese turno

#### Scenario: Turno confirmado ya comenzado
- **GIVEN** un turno `CONFIRMED` del paciente cuyo inicio ya pasó
- **WHEN** el odontólogo registra una evolución con ese `appointmentId`
- **THEN** responde `201` y el turno sigue `CONFIRMED`

#### Scenario: Varias evoluciones del mismo turno
- **GIVEN** un turno `COMPLETED` del paciente con una evolución asociada
- **WHEN** el odontólogo registra otra evolución para el mismo turno
- **THEN** responde `201` y ambas evoluciones quedan asociadas al turno

#### Scenario: Turno de otro paciente o inexistente
- **WHEN** el odontólogo registra una evolución con el `appointmentId` de un turno de otro paciente o que no existe
- **THEN** recibe `422` con `code = EVOLUTION_APPOINTMENT_NOT_FOUND` y no se crea ninguna entrada ni historia clínica

#### Scenario: Turno cancelado o ausente
- **GIVEN** un turno del paciente `CANCELLED` o `NO_SHOW`
- **WHEN** el odontólogo registra una evolución asociada a él
- **THEN** recibe `422` con `code = EVOLUTION_APPOINTMENT_NOT_ATTENDED` y no se crea ninguna entrada

#### Scenario: Turno futuro
- **GIVEN** un turno `CONFIRMED` del paciente que todavía no comenzó
- **WHEN** el odontólogo registra una evolución asociada a él
- **THEN** recibe `422` con `code = EVOLUTION_APPOINTMENT_NOT_STARTED` y no se crea ninguna entrada

#### Scenario: Identificador de turno mal formado
- **WHEN** `appointmentId` no es un UUID válido
- **THEN** recibe `400` con `code = VALIDATION_FAILED` y no se crea ninguna entrada

### Requirement: Consulta cronológica de evoluciones
El sistema SHALL exponer `GET /api/v1/patients/{patientId}/clinical-record/evolutions` con todas las evoluciones del paciente, incluidas las corregidas y sus correcciones, ordenadas de la más reciente a la más antigua por instante de registro y, ante igual instante, por identificador descendente (orden determinista), indicando en cada entrada el turno asociado, la entrada que corrige con su motivo y la entrada que la corrigió, si las hay.

#### Scenario: Más reciente primero
- **GIVEN** tres evoluciones registradas en distinto momento
- **WHEN** el odontólogo consulta las evoluciones
- **THEN** recibe `200` con las tres de la más reciente a la más antigua, cada una con autor y fecha

#### Scenario: Desempate determinista con igual instante
- **GIVEN** dos evoluciones con el mismo instante de registro
- **WHEN** el odontólogo consulta las evoluciones dos veces
- **THEN** ambas respuestas las devuelven en el mismo orden, la de mayor identificador primero

#### Scenario: Historial con corrección
- **GIVEN** una evolución `A` corregida por una evolución `B`
- **WHEN** el odontólogo consulta las evoluciones
- **THEN** la respuesta incluye `A` con `correctedByEntryId = B` y `B` con `correctsEntryId = A` y su motivo

#### Scenario: Paciente sin historia clínica
- **GIVEN** un paciente existente sin historia clínica
- **WHEN** el odontólogo consulta las evoluciones
- **THEN** recibe `200` con una lista vacía y no se abre la historia clínica

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente dado de baja con evoluciones
- **WHEN** el odontólogo las consulta
- **THEN** recibe `200` con todas sus evoluciones

#### Scenario: Paciente inexistente
- **WHEN** el odontólogo consulta las evoluciones de un paciente que no existe
- **THEN** recibe `404` con `code = PATIENT_NOT_FOUND` y no se registra auditoría

### Requirement: Corrección de una evolución
El sistema SHALL corregir una evolución mediante `POST .../clinical-record/evolutions/{entryId}/corrections` con `reason` y `description`, creando una entrada nueva que referencia la original y conserva su turno asociado; MUST admitirse solo la corrección de una evolución que todavía no fue corregida (una corrección puede, a su vez, corregirse).

#### Scenario: Corrección válida
- **GIVEN** una evolución asociada a un turno con un error de redacción
- **WHEN** el odontólogo envía la corrección con motivo "error de tipeo en la pieza tratada" y la descripción corregida
- **THEN** responde `201` con una entrada nueva que informa la entrada corregida, el motivo y el mismo `appointmentId`, y la original sigue en el historial

#### Scenario: Corrección de una corrección
- **GIVEN** una evolución `A` corregida por `B`
- **WHEN** el odontólogo corrige `B`
- **THEN** responde `201` con una entrada `C` que corrige `B`

#### Scenario: Corrección sin motivo o sin descripción
- **WHEN** el odontólogo envía una corrección sin `reason`, con motivo vacío, con motivo de más de 500 caracteres o sin `description` válida
- **THEN** recibe `400` con `code = VALIDATION_FAILED` y no se crea ninguna entrada

#### Scenario: Entrada ya corregida
- **GIVEN** una evolución ya corregida
- **WHEN** el odontólogo intenta corregirla otra vez
- **THEN** recibe `409` con `code = CLINICAL_ENTRY_ALREADY_CORRECTED`

#### Scenario: Entrada de otro paciente o de otro tipo
- **WHEN** el odontólogo intenta corregir por la ruta de evoluciones una entrada inexistente, de otro paciente, de anamnesis o de odontograma
- **THEN** recibe `404` con `code = CLINICAL_ENTRY_NOT_FOUND`

#### Scenario: El cliente no puede cambiar el turno en la corrección
- **WHEN** el cuerpo de la corrección incluye `appointmentId`
- **THEN** ese campo se ignora y la nueva entrada conserva el turno de la original

#### Scenario: Corrección sobre paciente dado de baja
- **GIVEN** un paciente dado de baja con una evolución
- **WHEN** el odontólogo intenta corregirla
- **THEN** recibe `409` con `code = PATIENT_INACTIVE` y no se crea ninguna entrada

### Requirement: Evoluciones inmutables
Las evoluciones MUST NOT modificarse ni eliminarse: no existen `PUT`, `PATCH` ni `DELETE` sobre `.../clinical-record/evolutions/**`, el rol de aplicación de la base no puede ejecutar `UPDATE`, `DELETE` ni `TRUNCATE` sobre la tabla, y el cliente no puede fijar `id`, `authorId`, `createdAt` ni `clinicalRecordId`.

#### Scenario: Métodos de edición rechazados
- **WHEN** el odontólogo envía `PUT`, `PATCH` o `DELETE` a `.../clinical-record/evolutions/{entryId}`
- **THEN** recibe `405` en formato `application/problem+json` y la entrada no cambia

#### Scenario: Cliente intenta fijar autor y fecha
- **WHEN** el cuerpo del registro incluye `authorId`, `createdAt` o `id`
- **THEN** la evolución se crea con el autor de la sesión, el instante del servidor y un identificador generado por el sistema

### Requirement: Auditoría de evoluciones sin contenido clínico
Cada consulta de evoluciones SHALL registrar un `HC_READ`, cada alta un `HC_CREATE` y cada corrección un `HC_CORRECT`, en la misma transacción y con `entity_type = evolution_entry`; los detalles MUST limitarse a `entry_type` y `corrects_entry_id`, sin descripción, motivo ni turno asociado. Una operación rechazada por validación o por reglas de negocio MUST NOT registrar auditoría.

#### Scenario: Alta auditada
- **WHEN** el odontólogo registra una evolución
- **THEN** queda una fila `HC_CREATE` con el `entity_id` de la evolución, el `patient_id`, el usuario y la IP

#### Scenario: Lectura auditada
- **WHEN** el odontólogo consulta las evoluciones de un paciente existente
- **THEN** queda una fila `HC_READ` con `entity_type = evolution_entry`

#### Scenario: Corrección auditada sin contenido
- **WHEN** el odontólogo corrige una evolución
- **THEN** la fila `HC_CORRECT` referencia la nueva entrada y la corregida, y no contiene el motivo, la descripción ni el turno

#### Scenario: Turno inválido no audita
- **WHEN** el registro es rechazado por un `appointmentId` inválido
- **THEN** no queda ninguna fila de auditoría de escritura

#### Scenario: Falla de auditoría revierte la operación
- **GIVEN** que el registro de auditoría falla
- **WHEN** el odontólogo registra o corrige una evolución
- **THEN** no queda ninguna entrada ni historia clínica nueva

#### Scenario: Descripción fuera de logs y respuestas de error
- **WHEN** se registra o corrige una evolución con un texto marcador en descripción y motivo
- **THEN** el marcador no aparece en los logs de aplicación, en las respuestas de error ni en `audit_log.details`, y las respuestas llevan `Cache-Control: no-store`
