# Proposal: C-12 `availability-engine`

## Why

C-11 ya persiste turnos, bloqueos, horarios y prestaciones, y dejó los contratos públicos `BlockedRanges` y `AffectedAppointments`, pero **no existe hoy ninguna forma de calcular qué horarios se pueden ofrecer**. Sin ese cálculo, la reserva online (US-030, Flujo 3 paso 2) no puede mostrar slots libres y la agenda del profesional (C-20) no puede apoyar la carga de turnos. C-12 está en el camino crítico: lo siguen C-14, C-15 y C-24. Governance: **MEDIO** (decisiones de ubicación, puertos y ruta pública surfaceadas en `design.md`).

## What Changes

- **Motor puro de disponibilidad en `reservas.domain`**: función sin Spring ni `java.sql`, con `Clock` inyectable y `ZoneId` del consultorio. Sobre intervalos semiabiertos `[inicio, fin)` en UTC calcula `working_hours − BlockedRanges − AffectedAppointments (CONFIRMED)`, exige que quepa la duración completa de la prestación (`startsAt + durationMin_snapshot`, RN-AG-05), emite pasos de `slotGranularityMin` solo si el rango completo entra en un tramo libre (RN-AG-06, default 15 min) y aplica `minNoticeMin` / `maxAdvanceDays` (RN-RS-10). Respeta límites de día local, hora local inexistente y ambigua, y el cambio de día UTC ↔ `America/Argentina/Buenos_Aires` (RN-AG-07).
- **Puertos de lectura estrictamente necesarios (a crear en el apply; hoy no existen)**: `agenda.api.WorkingHoursLookup` (tramos semanales), `agenda.api.ServiceAvailabilityLookup` (prestación publicable y existencia de activas; los DTOs actuales dependen de `agenda.application` y no son consumibles como puerto) y `shared.api.PracticeBookingSettingsLookup` (`timezone`, `slotGranularityMin`, `minNoticeMin`, `maxAdvanceDays`, reutilizando `PracticeSettingsResponse`/`PracticeSettingsData`). Firmas solo con JDK/records propios, sin entidades JPA ni tipos de infraestructura; se conservan `AgendaApiContractsTest` y `ModuleBoundariesRulesTest`.
- **Contrato público reutilizable en `reservas.api`**: `AvailabilityLookup` + records `AvailabilityQuery`, `AvailabilityResult`, `AvailableSlot` y enum `AvailabilityStatus`, sin PII. Es la fachada estable que C-14 reutilizará sin conocer el controller ni la infraestructura; la capa `application` usa el mismo caso de uso subyacente porque `applicationIndependent` le prohíbe depender de `reservas.api`.
- **Dos endpoints**: `GET /api/v1/availability` (rol `DENTIST`, `401` sin sesión; vista interna que necesita C-20) y `GET /api/v1/public/availability?serviceId=&date=` (declarada pública bajo `/api/v1/public/**`, con `RateLimiter` por IP → `429 RATE_LIMITED` + `Retry-After`). Ambos son `GET`: no exigen CSRF. Se unifica la discrepancia de ruta entre `CHANGES.md` (`/api/v1/public/availability`) y KB 07 (`/public/availability`) a favor de la primera, porque la política de acceso solo declara pública `/api/v1/public/**`.
- **Errores y caso sin prestaciones**: `ProblemDetail` de `shared.api` con `400 VALIDATION_FAILED`, `404 SERVICE_NOT_FOUND`, `422 SERVICE_INACTIVE`, `429 RATE_LIMITED`. Si el consultorio no tiene **ninguna** prestación pública activa, la respuesta es `200` con `slots: []` y `status = NO_ACTIVE_SERVICES`, para que la SPA muestre «reservas no disponibles» sin confundirlo con un `serviceId` inválido. Decisión surfaceada en D9.
- **Sin cambios de base de datos**: no hay migración nueva; se consumen tablas y contratos existentes. Sin dependencias de runtime nuevas.
- **Deltas de specs aditivos**: se agregan requirements nuevos; no se reescribe ningún requirement existente (evita conflictos de merge en `openspec/specs/`).

### Fuera de alcance

- Alta de turnos, tokens, honeypot y transacción de reserva (C-14), cancelación/reprogramación por token (C-15) y envío de emails (C-13).
- UI de agenda y de reserva (C-20/C-24); el mockup de C-24 consume esta API, no se dibuja acá.
- Sobreturnos en la disponibilidad pública, cupos por profesional múltiple, `overbooking`, bloqueos por paciente y optimización de caché.
- Modificar `CHANGES.md`, los specs principales, `backend/src/**` ni `frontend/**`: este change es de planeamiento.
- Actualizar la KB: el follow-up del archive registrará la ruta canónica `/api/v1/public/availability`.

## Capabilities

### New Capabilities
- `availability`: motor puro de cálculo de slots, puertos de lectura que lo alimentan, contrato público en `reservas.api`, endpoints interno y público, zona horaria, límites de reserva, rate limit y códigos de error.

### Modified Capabilities
- `api-access-policy`: declara `GET /api/v1/public/availability` como ruta pública con límite de tasa y la ruta interna como privada de `DENTIST`.
- `module-boundaries`: nuevos contratos en `agenda.api`, `shared.api` y `reservas.api`; el dominio de `reservas` recibe instantes/tramos ya mapeados, nunca tipos de `api`.
- `time-and-zones`: reglas de conversión aplicadas al cálculo de un día local de disponibilidad.
- `booking-settings`: exposición de los parámetros de reserva como puerto de lectura para el motor.
- `working-hours`: exposición de los tramos semanales como puerto de lectura para el motor.
- `public-services`: prestación publicable como filtro de disponibilidad y caso de catálogo vacío.
- `services-catalog`: consulta mínima de prestación por identificador para validar `serviceId`.
- `error-responses`: códigos estables del dominio de disponibilidad.

## Impact

- **Backend**: nuevos tipos en `reservas.{api,application,domain,infrastructure}`; interfaces nuevas en `agenda.api` y `shared.api` con adaptadores delgados en sus `infrastructure`; sin tocar entidades JPA ni la configuración HTTP de seguridad (`SecurityConfig` ya permite `/api/v1/public/**` y exige `DENTIST` en `/api/v1/**`).
- **Contratos para otros changes**: C-14 reutiliza `AvailabilityLookup`/`AvailabilityResult` para validar el slot antes de reservar y para recalcular tras reprogramar (C-15); C-20 consume `GET /api/v1/availability`; C-24 consume la ruta pública.
- **Arquitectura**: se mantiene `reservas → agenda, shared` (y `pacientes`/`notificaciones` según el grafo); `agenda → reservas` sigue prohibido; `reservas.domain` no depende de `agenda.api` ni de `shared.api`.
- **Base de datos**: ninguna. **Dependencias**: ninguna. **UI/mockups**: no aplica (change de backend).
