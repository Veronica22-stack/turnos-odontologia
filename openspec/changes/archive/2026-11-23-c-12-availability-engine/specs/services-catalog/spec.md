# Spec Delta

## ADDED Requirements

### Requirement: Consulta de prestación por identificador para disponibilidad
El sistema SHALL ofrecer un contrato de solo lectura, consultable desde `reservas`, que permita resolver un `serviceId` del consultorio a su duración en minutos y a sus marcas `active` y `publicVisible`. El contrato MUST NOT exponer descripción, marcas de auditoría ni datos de otras prestaciones. Un identificador ajeno al consultorio SHALL distinguirse de una prestación dada de baja para que la disponibilidad pueda responder `404` o `422` según corresponda.

#### Scenario: Prestación vigente
- **GIVEN** una prestación activa y visible de 45 minutos
- **WHEN** el motor la consulta por identificador
- **THEN** obtiene duración 45, `active = true` y `publicVisible = true`

#### Scenario: Identificador ajeno
- **WHEN** se consulta un identificador que no pertenece al consultorio
- **THEN** el contrato indica ausencia y la disponibilidad responde `404 SERVICE_NOT_FOUND`

#### Scenario: Prestación dada de baja
- **GIVEN** una prestación inactiva con visibilidad pública verdadera
- **WHEN** el motor la consulta por identificador
- **THEN** obtiene `active = false` y la disponibilidad responde `422 SERVICE_INACTIVE`, sin ofrecer slots
