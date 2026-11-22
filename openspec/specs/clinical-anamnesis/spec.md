# clinical-anamnesis Specification

## Purpose
La anamnesis registra los antecedentes del paciente (alergias, medicación, condiciones médicas, hábitos y notas) como versiones sucesivas e inmutables dentro de su historia clínica, mostrando la vigente sin perder las anteriores (US-060, US-063).

## Requirements

### Requirement: Registro de una nueva versión de anamnesis
El sistema SHALL permitir al odontólogo registrar la anamnesis de un paciente mediante `POST /api/v1/patients/{patientId}/clinical-record/anamnesis`. Cada registro SHALL crear una nueva versión con número correlativo por historia clínica, sin modificar las versiones anteriores, y responder `201` con la entrada creada.

#### Scenario: Primera anamnesis
- **GIVEN** un paciente activo sin historia clínica
- **WHEN** el odontólogo registra una anamnesis con alergias y medicación
- **THEN** responde `201` con la entrada en versión `1`, su autor e instante del servidor, y la historia clínica queda abierta

#### Scenario: Nueva versión no sobreescribe
- **GIVEN** un paciente con anamnesis en versión `1`
- **WHEN** el odontólogo registra una nueva anamnesis
- **THEN** se crea la versión `2`, la versión `1` permanece idéntica y la vigente pasa a ser la `2`

#### Scenario: Versiones concurrentes
- **GIVEN** un paciente con anamnesis en versión `1`
- **WHEN** se registran dos nuevas versiones en paralelo
- **THEN** a lo sumo una obtiene la versión `2`; la otra recibe `409` con `code = CLINICAL_RECORD_CONCURRENT_WRITE` y ninguna versión se duplica

### Requirement: Contenido de la anamnesis
La anamnesis SHALL componerse de los campos de texto opcionales `allergies`, `medications`, `medicalConditions`, `habits` y `notes`, de hasta 2000 caracteres cada uno, con al menos uno no vacío. Los campos desconocidos MUST NOT persistirse.

#### Scenario: Anamnesis vacía
- **WHEN** el odontólogo registra una anamnesis con todos los campos ausentes o en blanco
- **THEN** recibe `400` con `code = VALIDATION_FAILED` y no se crea ninguna entrada

#### Scenario: Campo excedido
- **WHEN** el odontólogo registra una anamnesis con `allergies` de más de 2000 caracteres
- **THEN** recibe `400` con error de validación sobre `allergies`

#### Scenario: Campo no previsto
- **WHEN** el odontólogo registra una anamnesis que incluye un campo `ssn`
- **THEN** la entrada se crea sin ese campo

### Requirement: Consulta de la anamnesis vigente y su historial
`GET /api/v1/patients/{patientId}/clinical-record/anamnesis` SHALL devolver la versión vigente (la de mayor número) y el historial completo en orden de versión descendente; cada entrada SHALL informar versión, contenido, autor, instante, la entrada que corrige y su motivo si las hay, y si fue corregida y por qué entrada.

#### Scenario: Historial con corrección
- **GIVEN** una anamnesis en versión `1` corregida por la versión `2`
- **WHEN** el odontólogo consulta la anamnesis
- **THEN** la vigente es la versión `2` con su motivo y la referencia a la `1`, y el historial incluye la `1` marcada como corregida por la `2`

#### Scenario: Sin anamnesis
- **GIVEN** un paciente sin historia clínica
- **WHEN** el odontólogo consulta la anamnesis
- **THEN** recibe `200` sin versión vigente y con historial vacío

### Requirement: Corrección de la anamnesis vigente
La corrección de anamnesis SHALL seguir el mecanismo uniforme de corrección y crear una nueva versión correlativa. Solo la versión vigente MUST poder corregirse; corregir una versión anterior SHALL responder `409` con `code = CLINICAL_ENTRY_NOT_CURRENT`.

#### Scenario: Corrección de la vigente
- **GIVEN** una anamnesis vigente en versión `3`
- **WHEN** el odontólogo la corrige con motivo y contenido
- **THEN** se crea la versión `4` que referencia a la `3`, y la `3` queda marcada como corregida

#### Scenario: Corrección de una versión superada
- **GIVEN** una anamnesis con versiones `1` y `2`, siendo la `2` la vigente
- **WHEN** el odontólogo intenta corregir la versión `1`
- **THEN** recibe `409` con `code = CLINICAL_ENTRY_NOT_CURRENT` y no se crea ninguna versión
