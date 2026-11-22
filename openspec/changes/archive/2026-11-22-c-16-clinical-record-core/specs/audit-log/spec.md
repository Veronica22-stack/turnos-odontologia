# Spec Delta

## ADDED Requirements

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
