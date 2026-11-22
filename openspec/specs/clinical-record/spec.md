# clinical-record Specification

## Purpose
La historia clínica es el registro cronológico, inalterable y auditado de la atención de cada paciente: un contenedor por paciente cuyas entradas solo se agregan y se corrigen mediante nuevos asientos que referencian al original, con autor y fecha del servidor (Leyes 26.529 y 25.326).

## Requirements

### Requirement: Una historia clínica por paciente abierta al primer registro
El sistema SHALL mantener a lo sumo una historia clínica por paciente. La historia clínica SHALL abrirse automáticamente al registrar la primera entrada clínica del paciente, con instante de apertura del servidor y el profesional autenticado como responsable; una lectura MUST NOT abrirla.

#### Scenario: Primer registro abre la historia clínica
- **GIVEN** un paciente activo sin historia clínica
- **WHEN** el odontólogo registra su primera entrada clínica
- **THEN** se crea una única historia clínica para ese paciente y la entrada queda dentro de ella

#### Scenario: Lectura de un paciente sin historia clínica
- **GIVEN** un paciente sin historia clínica
- **WHEN** el odontólogo consulta su historia clínica
- **THEN** recibe `200` indicando que la historia clínica no está abierta y no se crea ninguna historia clínica

#### Scenario: Registros concurrentes de la primera entrada
- **GIVEN** un paciente sin historia clínica
- **WHEN** se registran dos primeras entradas en paralelo
- **THEN** existe una sola historia clínica para el paciente y ninguna entrada queda huérfana

### Requirement: Entradas clínicas inmutables
Toda entrada clínica SHALL ser append-only: una vez persistida, su contenido, autor, instante y referencias MUST NOT poder modificarse ni eliminarse por la aplicación, ni por la API ni por el rol de base de datos de la aplicación.

#### Scenario: Sin métodos de edición ni borrado
- **GIVEN** una entrada clínica existente
- **WHEN** el odontólogo autenticado envía `PUT`, `PATCH` o `DELETE` a cualquier ruta de la historia clínica
- **THEN** la API no ofrece la operación (responde `405` o `404` en formato de error único) y la entrada no cambia

#### Scenario: La base rechaza la mutación
- **GIVEN** una entrada clínica y una historia clínica persistidas
- **WHEN** el rol de base de datos de la aplicación ejecuta `UPDATE`, `DELETE` o `TRUNCATE` sobre ellas
- **THEN** PostgreSQL rechaza la sentencia por falta de privilegios y los datos quedan intactos

### Requirement: Autor e instante fijados por el servidor
Cada entrada clínica SHALL registrar como autor al usuario de la sesión autenticada y como instante de creación el reloj del servidor en UTC. Los valores de autor, instante, identificador, versión o historia clínica enviados por el cliente MUST ser ignorados.

#### Scenario: Cliente intenta fijar autor y fecha
- **GIVEN** un odontólogo autenticado
- **WHEN** registra una entrada enviando en el cuerpo un autor distinto y una fecha de creación pasada
- **THEN** la entrada persiste con su propio usuario como autor y el instante del servidor como fecha de creación

### Requirement: Corrección como nueva entrada que referencia la original
Corregir una entrada clínica SHALL crear una nueva entrada del mismo tipo, dentro de la misma historia clínica, que referencia a la original, con motivo obligatorio, autor e instante del servidor. La original MUST permanecer sin cambios y visible en el historial marcada como corregida.

#### Scenario: Corrección válida
- **GIVEN** una entrada clínica vigente
- **WHEN** el odontólogo la corrige indicando un motivo y el contenido corregido
- **THEN** se crea una nueva entrada que referencia a la original con el motivo, y la original queda intacta y marcada como corregida

#### Scenario: Corrección sin motivo
- **GIVEN** una entrada clínica vigente
- **WHEN** el odontólogo envía una corrección con motivo ausente, vacío o solo espacios
- **THEN** recibe `400` con error de validación sobre el campo `reason` y no se crea ninguna entrada

#### Scenario: Motivo demasiado largo
- **GIVEN** una entrada clínica vigente
- **WHEN** el odontólogo envía una corrección con un motivo de más de 500 caracteres
- **THEN** recibe `400` con error de validación sobre el campo `reason`

#### Scenario: Entrada a corregir inexistente o de otro paciente
- **GIVEN** un identificador de entrada que no existe o pertenece a la historia clínica de otro paciente
- **WHEN** el odontólogo intenta corregirla a través del paciente indicado en la ruta
- **THEN** recibe `404` con `code = CLINICAL_ENTRY_NOT_FOUND` y no se crea ninguna entrada

#### Scenario: Entrada ya corregida
- **GIVEN** una entrada clínica que ya tiene una corrección
- **WHEN** el odontólogo intenta corregirla otra vez
- **THEN** recibe `409` con `code = CLINICAL_ENTRY_ALREADY_CORRECTED`; para seguir corrigiendo debe corregir la entrada vigente

#### Scenario: La base impide una corrección inconsistente
- **GIVEN** la base migrada
- **WHEN** se inserta una entrada con referencia a corregir pero sin motivo, o con motivo sin referencia, o que referencia una entrada de otra historia clínica
- **THEN** PostgreSQL rechaza la inserción

### Requirement: Mecanismo de corrección uniforme para todo tipo de entrada
Todo tipo de entrada clínica SHALL exponer su corrección como `POST /api/v1/patients/{patientId}/clinical-record/{tipo}/{entryId}/corrections`, con un cuerpo que incluye `reason` y el contenido corregido del tipo, y SHALL aplicar las mismas reglas de motivo, pertenencia, unicidad de la corrección, autoría y auditoría.

#### Scenario: Corrección de anamnesis por la ruta uniforme
- **GIVEN** una entrada de anamnesis vigente
- **WHEN** se envía `POST .../clinical-record/anamnesis/{entryId}/corrections` con motivo y contenido
- **THEN** responde `201` con la nueva entrada, que informa la entrada corregida y el motivo

### Requirement: Acceso exclusivo del profesional tratante
Las rutas de historia clínica SHALL exigir una sesión autenticada con rol `DENTIST` sin cambio de contraseña pendiente; cualquier otro rol MUST recibir `403` y sin sesión `401`, sin revelar si el paciente o la historia clínica existen.

#### Scenario: Sin sesión
- **WHEN** un cliente sin sesión consulta `GET /api/v1/patients/{patientId}/clinical-record`
- **THEN** recibe `401` con `application/problem+json` y no se registra auditoría de lectura clínica

#### Scenario: Paciente inexistente
- **GIVEN** un odontólogo autenticado
- **WHEN** consulta la historia clínica de un paciente que no existe
- **THEN** recibe `404` con `code = PATIENT_NOT_FOUND`

### Requirement: Escritura sobre pacientes dados de baja
El sistema SHALL permitir consultar la historia clínica de un paciente dado de baja, y MUST rechazar nuevas entradas o correcciones sobre él con `409` y `code = PATIENT_INACTIVE`.

#### Scenario: Lectura de paciente inactivo
- **GIVEN** un paciente dado de baja con historia clínica
- **WHEN** el odontólogo la consulta
- **THEN** recibe `200` con la historia clínica completa

#### Scenario: Alta sobre paciente inactivo
- **GIVEN** un paciente dado de baja
- **WHEN** el odontólogo intenta registrar una entrada
- **THEN** recibe `409` con `code = PATIENT_INACTIVE` y no se crea ninguna entrada ni historia clínica

### Requirement: Datos clínicos fuera de logs y cachés
El contenido clínico y los motivos de corrección MUST NOT aparecer en logs de aplicación, mensajes de error ni detalles de auditoría, y las respuestas con datos clínicos SHALL enviarse con `Cache-Control: no-store`.

#### Scenario: Error de validación sin eco del contenido
- **GIVEN** una solicitud de alta con un campo clínico que excede el largo permitido
- **WHEN** el sistema la rechaza
- **THEN** la respuesta indica el campo inválido pero no incluye el valor enviado, y el log no contiene el contenido clínico

#### Scenario: Respuesta clínica no cacheable
- **WHEN** el odontólogo obtiene la anamnesis de un paciente
- **THEN** la respuesta incluye `Cache-Control: no-store`
