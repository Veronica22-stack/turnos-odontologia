# clinical-odontogram Specification

## Purpose
Permite al odontólogo registrar el estado de cada pieza dental del paciente en notación FDI como parte inmutable de la historia clínica, consultar el estado actual derivado y el historial completo, y corregir errores sin perder el asiento original.

## Requirements

### Requirement: Registro de estados de piezas en notación FDI
El sistema SHALL permitir al odontólogo registrar en una sola operación entre 1 y 64 estados de piezas dentales, cada uno con código FDI, cara opcional, estado del catálogo base y notas opcionales; cada estado SHALL persistirse como una entrada clínica inmutable con autor e instante fijados por el servidor.

#### Scenario: Registro válido
- **GIVEN** un odontólogo autenticado y un paciente activo
- **WHEN** envía `POST /api/v1/patients/{patientId}/clinical-record/odontogram` con los estados `{toothCode: "16", condition: "CARIES", surface: "OCCLUSAL"}` y `{toothCode: "21", condition: "CROWN"}`
- **THEN** responde `201` con dos entradas nuevas, cada una con `id`, `authorId` de la sesión y `createdAt` del servidor

#### Scenario: Primer registro abre la historia clínica
- **GIVEN** un paciente activo sin historia clínica
- **WHEN** el odontólogo registra un estado de odontograma
- **THEN** se abre la historia clínica del paciente y la entrada queda asociada a ella

#### Scenario: Lote vacío o excedido
- **WHEN** el odontólogo envía un registro sin estados o con más de 64
- **THEN** recibe `400` con `code = VALIDATION_FAILED` y no se crea ninguna entrada

#### Scenario: Posición repetida en el mismo lote
- **WHEN** el lote incluye dos estados para la misma pieza y la misma cara
- **THEN** recibe `400` con `code = VALIDATION_FAILED` y no se crea ninguna entrada

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente dado de baja
- **WHEN** el odontólogo registra un estado de odontograma
- **THEN** recibe `409` con `code = PATIENT_INACTIVE` y no se crea ninguna entrada ni historia clínica

### Requirement: Validación de la notación FDI
El sistema SHALL aceptar únicamente códigos FDI de dos dígitos de dentición permanente (cuadrantes 1 a 4, piezas 1 a 8) o temporal (cuadrantes 5 a 8, piezas 1 a 5), y MUST rechazar cualquier otro valor tanto en la API como en la base.

#### Scenario: Piezas permanentes y temporales válidas
- **WHEN** el odontólogo registra estados para las piezas `11`, `48`, `51` y `85`
- **THEN** las cuatro entradas se crean

#### Scenario: Pieza inexistente
- **WHEN** el odontólogo registra un estado para la pieza `19`, `56`, `90`, `00`, `1` o `111`
- **THEN** recibe `400` con `code = VALIDATION_FAILED`, el error indica el campo `toothCode` sin repetir el valor y no se crea ninguna entrada del lote

#### Scenario: La base rechaza un código inválido
- **WHEN** se inserta directamente en la base una entrada con pieza `59`
- **THEN** PostgreSQL rechaza la inserción

### Requirement: Catálogo base de estados y caras
El estado de una entrada SHALL pertenecer al catálogo `HEALTHY`, `CARIES`, `FILLED`, `MISSING`, `CROWN`, `ROOT_CANAL`; la cara, si se informa, SHALL pertenecer a `OCCLUSAL`, `MESIAL`, `DISTAL`, `VESTIBULAR`, `LINGUAL` y solo MUST admitirse para `CARIES` y `FILLED`. Las notas son opcionales y de hasta 500 caracteres.

#### Scenario: Estado fuera del catálogo
- **WHEN** el odontólogo registra un estado `FRACTURED`
- **THEN** recibe `400` con `code = VALIDATION_FAILED` y no se crea ninguna entrada

#### Scenario: Cara en un estado de pieza completa
- **WHEN** el odontólogo registra `MISSING` con cara `MESIAL`
- **THEN** recibe `400` con `code = VALIDATION_FAILED` y no se crea ninguna entrada

#### Scenario: Notas demasiado largas
- **WHEN** el odontólogo registra un estado con notas de 501 caracteres
- **THEN** recibe `400` con `code = VALIDATION_FAILED` sin eco del texto

### Requirement: Estado actual derivado de la última entrada vigente
El estado actual del odontograma SHALL derivarse, para cada posición (pieza y cara, o pieza completa sin cara), de la entrada más reciente de esa posición que no haya sido corregida; las entradas anteriores MUST conservarse y no alteran el estado actual.

#### Scenario: Nuevo estado reemplaza al anterior en el estado actual
- **GIVEN** la pieza `36` registrada como `CARIES` en cara `OCCLUSAL`
- **WHEN** luego se registra la pieza `36` como `FILLED` en cara `OCCLUSAL`
- **THEN** el estado actual muestra `36/OCCLUSAL = FILLED` y el historial conserva ambas entradas

#### Scenario: Posiciones independientes
- **GIVEN** la pieza `36` con `CARIES` en `OCCLUSAL` y luego `CARIES` en `MESIAL`
- **WHEN** el odontólogo consulta el odontograma
- **THEN** el estado actual muestra ambas caras de la pieza `36`

#### Scenario: Pieza sin registros
- **WHEN** el odontólogo consulta el odontograma de un paciente sin entradas para la pieza `11`
- **THEN** el estado actual no incluye la pieza `11` (el sistema no asume un estado por defecto)

### Requirement: Consulta del odontograma con historial
El sistema SHALL exponer `GET /api/v1/patients/{patientId}/clinical-record/odontogram` con el estado actual y el historial completo ordenado del más reciente al más antiguo, indicando en cada entrada la entrada que corrige y la entrada que la corrigió, si las hay.

#### Scenario: Historial con corrección
- **GIVEN** una entrada `A` corregida por una entrada `B`
- **WHEN** el odontólogo consulta el odontograma
- **THEN** el historial incluye `A` con `correctedByEntryId = B` y `B` con `correctsEntryId = A` y su motivo, y el estado actual incluye `B` y no `A`

#### Scenario: Paciente sin historia clínica
- **GIVEN** un paciente existente sin historia clínica
- **WHEN** el odontólogo consulta el odontograma
- **THEN** recibe `200` con estado actual e historial vacíos y no se abre la historia clínica

#### Scenario: Paciente dado de baja
- **GIVEN** un paciente dado de baja con odontograma
- **WHEN** el odontólogo lo consulta
- **THEN** recibe `200` con el estado actual y el historial completos

### Requirement: Corrección de una entrada del odontograma
El sistema SHALL corregir una entrada mediante `POST .../clinical-record/odontogram/{entryId}/corrections` con `reason`, `condition` y `notes`, creando una entrada nueva en la misma pieza y cara que referencia la original; solo MUST admitirse la corrección de la entrada vigente de su posición.

#### Scenario: Corrección de la entrada vigente
- **GIVEN** la pieza `46` registrada como `CARIES` por error
- **WHEN** el odontólogo envía la corrección con motivo "pieza sana, error de carga" y estado `HEALTHY`
- **THEN** responde `201` con una entrada nueva de la pieza `46` que informa la entrada corregida y el motivo, el estado actual de `46` pasa a `HEALTHY` y la original sigue en el historial

#### Scenario: Corrección de una entrada ya superada
- **GIVEN** la pieza `46` con una entrada `A` y una entrada posterior `B` en la misma posición
- **WHEN** el odontólogo intenta corregir `A`
- **THEN** recibe `409` con `code = CLINICAL_ENTRY_NOT_CURRENT` y no se crea ninguna entrada

#### Scenario: Corrección sin motivo
- **WHEN** el odontólogo envía una corrección sin `reason` o con un motivo vacío
- **THEN** recibe `400` con `code = VALIDATION_FAILED` y no se crea ninguna entrada

#### Scenario: Entrada ya corregida
- **GIVEN** una entrada ya corregida
- **WHEN** el odontólogo intenta corregirla otra vez
- **THEN** recibe `409` con `code = CLINICAL_ENTRY_ALREADY_CORRECTED`

#### Scenario: Entrada de otro paciente o de otro tipo
- **WHEN** el odontólogo intenta corregir por la ruta del odontograma una entrada inexistente, de otro paciente o de anamnesis
- **THEN** recibe `404` con `code = CLINICAL_ENTRY_NOT_FOUND`

#### Scenario: Estado corregido incompatible con la cara
- **GIVEN** una entrada vigente `CARIES` en `36/OCCLUSAL`
- **WHEN** el odontólogo la corrige con estado `MISSING`
- **THEN** recibe `422` con `code = ODONTOGRAM_SURFACE_NOT_ALLOWED` y no se crea ninguna entrada ni auditoría

#### Scenario: El cliente no puede cambiar la posición
- **WHEN** el cuerpo de la corrección incluye `toothCode` o `surface`
- **THEN** esos campos se ignoran y la nueva entrada conserva la pieza y la cara de la original

### Requirement: Odontograma inmutable
Las entradas del odontograma MUST NOT modificarse ni eliminarse: no existen `PUT`, `PATCH` ni `DELETE` sobre `.../clinical-record/odontogram/**`, el rol de aplicación de la base no puede ejecutar `UPDATE`, `DELETE` ni `TRUNCATE` sobre la tabla, y el cliente no puede fijar `id`, `authorId`, `createdAt` ni `clinicalRecordId`.

#### Scenario: Métodos de edición rechazados
- **WHEN** el odontólogo envía `PUT`, `PATCH` o `DELETE` a `.../clinical-record/odontogram/{entryId}`
- **THEN** recibe `405` en formato `application/problem+json` y la entrada no cambia

#### Scenario: Cliente intenta fijar autor y fecha
- **WHEN** el cuerpo del registro incluye `authorId`, `createdAt` o `id`
- **THEN** la entrada se crea con el autor de la sesión, el instante del servidor y un identificador generado por el sistema

### Requirement: Auditoría del odontograma sin contenido clínico
Cada consulta del odontograma SHALL registrar un `HC_READ`, cada entrada creada un `HC_CREATE` y cada corrección un `HC_CORRECT`, en la misma transacción y con `entity_type = odontogram_entry`; los detalles MUST limitarse a `entry_type` y `corrects_entry_id`, sin pieza, cara, estado, notas ni motivo.

#### Scenario: Registro de un lote auditado
- **WHEN** el odontólogo registra tres estados en un lote
- **THEN** quedan tres filas `HC_CREATE`, una por entrada, con su `entity_id`, el `patient_id`, el usuario y la IP

#### Scenario: Lectura auditada
- **WHEN** el odontólogo consulta el odontograma de un paciente existente
- **THEN** queda una fila `HC_READ` con `entity_type = odontogram_entry`

#### Scenario: Corrección auditada sin contenido
- **WHEN** el odontólogo corrige una entrada con motivo y notas
- **THEN** la fila `HC_CORRECT` referencia la nueva entrada y la corregida, y no contiene el motivo, las notas, la pieza ni el estado

#### Scenario: Falla de auditoría revierte el lote
- **GIVEN** que el registro de auditoría falla
- **WHEN** el odontólogo registra un lote o una corrección
- **THEN** no queda ninguna entrada ni historia clínica nueva

#### Scenario: Notas fuera de logs y respuestas de error
- **WHEN** se registra o corrige una entrada con un texto marcador en notas y motivo
- **THEN** el marcador no aparece en los logs de aplicación, en las respuestas de error ni en `audit_log.details`, y las respuestas llevan `Cache-Control: no-store`
