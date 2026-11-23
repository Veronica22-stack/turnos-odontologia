# Spec Delta

## ADDED Requirements

### Requirement: Active future appointments count by contact
El módulo `agenda` SHALL exponer en su capa `api` el conteo de turnos futuros activos (`CONFIRMED`) asociados a un DNI, email o teléfono, para consumo del módulo `reservas`. El contrato NO SHALL devolver datos personales.

#### Scenario: Conteo para el límite por contacto
- **GIVEN** tres turnos futuros activos para un mismo DNI
- **WHEN** la reserva pública consulta el conteo antes de confirmar
- **THEN** el contrato devuelve tres y la reserva adicional se rechaza según la regla anti-abuso

#### Scenario: Turnos cancelados no cuentan
- **GIVEN** un turno cancelado y otro confirmado para el mismo email
- **WHEN** se consulta el conteo
- **THEN** solo se cuenta el turno confirmado

### Requirement: Public appointment creation through the shared port
El módulo `agenda` SHALL exponer en su capa `api` una operación de alta de turno para consumidores de otros módulos. La operación SHALL delegar en el mismo servicio interno de turnos, con `origin = PUBLIC`, `overbooking = false`, el lock por consultorio, todas las validaciones concurrentes, la traducción de errores y la publicación de eventos. NO SHALL existir un segundo camino de escritura sobre `appointment`.

#### Scenario: Alta pública reutiliza el núcleo
- **GIVEN** un consumidor de otro módulo que llama a la operación pública de alta
- **WHEN** el slot está libre y la prestación es válida
- **THEN** se crea un turno `CONFIRMED` con `origin = PUBLIC` y se publica el evento de confirmación igual que en el alta interna

#### Scenario: Conflicto de slot traducido
- **GIVEN** un slot ocupado o un bloqueo que lo cubre
- **WHEN** se intenta el alta pública
- **THEN** se devuelve un error de conflicto traducido al código público `SLOT_TAKEN`, sin exponer el mensaje de la base de datos

#### Scenario: Sin sobrebooking público
- **GIVEN** una solicitud pública que intenta habilitar sobrebooking
- **WHEN** se crea el turno
- **THEN** el turno se guarda con `overbooking = false`
