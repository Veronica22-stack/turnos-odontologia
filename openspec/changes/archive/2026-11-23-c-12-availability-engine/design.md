# Design: C-12 `availability-engine`

## Context

C-11 dejó persistidos turnos, bloqueos, horarios semanales y prestaciones, y expuso los contratos `agenda.api.BlockedRanges.findOverlapping(UUID, Instant, Instant)` y `agenda.api.AffectedAppointments.findWithin(UUID, Instant, Instant)`. No existe hoy lectura de horarios ni de parámetros de reserva como puerto, y `serviceId` solo se valida desde DTOs acoplados a `agenda.application`. `reservas` tiene los cuatro `package-info.java` pero ninguna implementación. Ver `proposal.md` para la motivación y las specs deltas para los requisitos.

Restricciones verificadas antes de redactar:

- `ModuleBoundaries.ALLOWED_DEPENDENCIES`: `RESERVAS → {AGENDA, PACIENTES, NOTIFICACIONES}`, `AGENDA → {PACIENTES}`, `shared` accesible desde todos. `agenda → reservas` está prohibido.
- `ModuleBoundaries.domainIndependent` prohíbe dependencias de `api` de **cualquier** módulo; `applicationIndependent` prohíbe a `reservas.application` depender de `reservas.api` ni de `reservas.infrastructure`.
- `AgendaApiContractsTest.appointmentOperationsDeclaraUnicamenteLaReprogramacion()` asserta `containsExactly("reschedule")`: no se extiende `AppointmentOperations`.
- `api-access-policy` declara pública solo `GET /api/v1/public/services`; `SecurityConfig` ya hace `permitAll` sobre `/api/v1/public/**` y aplica `dentistAccessManager` al resto de `/api/v1/**`. No se cambia configuración HTTP.
- Existen `RateLimiter`, `PublicReadRateLimiter`, `RateLimitedException` y `ProblemDetails.of`, reutilizables sin dependencias nuevas.

## Goals / Non-Goals

**Goals:**

- Calcular slots libres con un algoritmo determinista, puro y testeable sin Spring ni base de datos.
- Definir los puertos mínimos de lectura para alimentar el motor y la fachada estable que consumirán C-14/C-15/C-20/C-24.
- Unificar la ruta pública de disponibilidad en `/api/v1/public/availability`, coherente con la política de acceso ya implementada.
- Dejar un plan de implementación Strict TDD con evidencia verificable por comportamiento.

**Non-Goals:**

- Escribir código de producción o tests (este change es solo planeamiento).
- Crear turnos, emitir tokens, honeypot, envío de emails ni cancelar/reprogramar (C-13/C-14/C-15).
- Dibujar o aprobar mockups: el change es de backend y no está marcado `[UI]`.
- Agregar migraciones Flyway, dependencias de runtime ni cambios en `CHANGES.md` o `openspec/specs/**`.
- Resolver PA-01…PA-07: ninguna bloquea C-12 y sus respuestas no cambian este diseño.

## Decisions

### D1 — Ubicación del motor: `reservas`, no `agenda`

La KB 08 menciona «agenda … cálculo de disponibilidad», pero el contrato ejecutable (`ModuleBoundaries`, specs `module-boundaries`) y el roadmap (C-14 consume disponibilidad desde `reservas`) son fuentes de verdad posteriores: `agenda → reservas` está prohibido, así que poner el motor en `agenda` haría imposible que `reservas.application` lo use sin romper el grafo. Se coloca en `reservas.{api,application,domain,infrastructure}`.

*Alternativa descartada:* moverlo a `shared` (rompe la cohesión del dominio de reservas y ensucia un módulo que solo debe tener contratos e infraestructura común). *Alternativa descartada:* duplicar la lógica en `agenda` y en `reservas` (divergencia garantizada).

### D2 — Puertos nuevos, todos con tipos del JDK

| Puerto | Módulo | Firma prevista | Adaptador |
|---|---|---|---|
| `WorkingHoursLookup` | `agenda.api` | `List<WeeklyInterval> findWeeklyIntervals(UUID practiceId)` | `agenda.infrastructure` lee `WorkingHoursService` y mapea `WorkingHoursDto` → record propio |
| `ServiceAvailabilityLookup` | `agenda.api` | `Optional<PublicService> findPublicService(UUID practiceId, UUID serviceId)`, `boolean hasPublicServices(UUID practiceId)` | `agenda.infrastructure` consulta el repositorio sin pasar por `ServiceResponse`/`PublicServiceResponse` (acopladas a `agenda.application`) |
| `PracticeBookingSettingsLookup` | `shared.api` | `BookingSettings findBookingSettings(UUID practiceId)` (`timezone`, `slotGranularityMin`, `minNoticeMin`, `maxAdvanceDays`) | `shared.infrastructure` mapea `PracticeSettingsData` → record propio |

Se conservan `AffectedAppointments`, `BlockedRanges` y `AppointmentOperations` tal cual. El adaptador de `AffectedAppointments` ya garantiza `CONFIRMED` (`AppointmentRepository.findConfirmedWithin`), por lo que el motor recibe instantes ocupados y no necesita conocer estados.

*Alternativa descartada:* reutilizar `WorkingHoursDto`, `ServiceResponse` y `PracticeSettingsResponse` como contratos: exportan dependencias de `application` y acoplan módulos. *Alternativa descartada:* inyectar repositorios JPA en `reservas.application`: viola el grafo y la regla de no exponer entidades.

### D3 — Fachada en `reservas.api` y caso de uso en `reservas.application`

`reservas.api` expone `AvailabilityLookup` con `AvailabilityQuery` (practiceId, serviceId, date, `audience` interna/pública), `AvailabilityResult` (`status`, `List<AvailableSlot>`) y `AvailableSlot` (`startsAt`, `endsAt`), más el enum `AvailabilityStatus`. Es el contrato estable para controllers y futuros consumidores.

`applicationIndependent` impide que `reservas.application` importe `reservas.api`; por lo tanto el caso de uso vive en `application` y la fachada de `api` es un adaptador delgado hacia él. C-14 reutiliza el mismo caso de uso, no el controller ni la infraestructura.

*Alternativa descartada:* poner la lógica de orquestación directamente en `reservas.api` (dependencia prohibida e imposibilidad de reutilizarla desde `application`).

### D4 — Dominio puro

`reservas.domain` depende solo de JDK: `Instant`, `LocalDate`, `Duration`, `ZoneId`, `Clock` inyectable y tipos propios (`Interval`, `WeeklyInterval`, `PublicService`, `BookingSettings`, `OccupiedRange`). Ni Spring, ni JPA, ni `java.sql`, ni tipos de `api`. La conversión de DTOs a tipos de dominio ocurre en los adaptadores de `infrastructure`.

*Alternativa descartada:* usar `WorkingHoursDto`/`BlockedInterval` dentro del dominio (rompe `domainIndependent`). *Alternativa descartada:* `Instant.now()` directo (tests no deterministas; viola el escenario de reloj fijo).

### D5 — Intervalos semiabiertos y fusión de tramos

Todo es `[inicio, fin)` en UTC: `start < end`, solapamiento solo si `a.start.isBefore(b.end) && b.start.isBefore(a.end)`. Los ocupantes (bloqueos + turnos confirmados) se ordenan y fusionan cuando son contiguos o solapados antes de restarlos de cada tramo; así un bloqueo que termina a las 10:00 no come el inicio de las 10:00. Los inicios se enumeran desde el comienzo del tramo libre en pasos de `slotGranularityMin` y se emite solo si `start + duration <= freeEnd`.

*Alternativa descartada:* comparar con `<=` en los bordes (duplica slots y descarta inicios adyacentes válidos). *Alternativa descartada:*chequear solo que el inicio no esté ocupado (permite slots que se cortan a mitad).

### D6 — Día local, DST y granularidad

La `date` se interpreta en la `timezone` del consultorio; el rango UTC cubre `[date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())`. Cada inicio candidato se valida con `LocalDateTime.ofInstant(...).atZone(zone)` y se descarta si la conversión no es round-trip (hora inexistente); en horas ambiguas se toma el desfase más temprano (`ZoneRules.getValidOffsets(...).get(0)`). Argentina no tiene DST hoy, pero el algoritmo debe ser correcto para zonas que sí lo tienen y para cambios futuros de configuración.

*Alternativa descartada:* tratar la fecha como UTC directo (corre el día local y rompe el escenario 2026-03-10T03:00:00Z). *Alternativa descartada:* `24:00` como fin de tramo: C-11 no lo admite (`LocalTime`), así que un tramo que llega a medianoche no genera el último slot sin sobreturno; se documenta como limitación, no se inventa soporte.

### D7 — Endpoints y paralelismo de audiencias

| Ruta | Audiencia | Parámetros | Filtros de reserva | Respuesta |
|---|---|---|---|---|
| `GET /api/v1/availability` | `DENTIST` autenticado | `serviceId`, `date` obligatorios | No aplica minNotice/maxAdvance | 200 con slots |
| `GET /api/v1/public/availability` | anónima | `serviceId`, `date` obligatorios | Sí aplica | 200 con slots o `NO_ACTIVE_SERVICES` |

Ambos son `GET`, por lo que no exigen CSRF; comparten el mismo caso de uso con distinta `audience`. El endpoint interno necesita la vista sin filtros para permitir turnos manuales urgentes (rn-rs-10).

*Alternativa descartada:* un único endpoint con query param `public=true` (mezcla autorizaciones y facilita filtrar mal la información; además la política de acceso está por prefijo de ruta).

### D8 — Catálogo vacío: estado, no error

Antes de resolver `serviceId`, la ruta pública consulta `hasPublicServices`. Si es `false`, responde `200` con `slots: []` y `status = NO_ACTIVE_SERVICES`. Si hay prestaciones activas, un `serviceId` ajeno sigue siendo `404 SERVICE_NOT_FOUND` y uno inactivo `422 SERVICE_INACTIVE`.

*Alternativa descartada:* `404` cuando el catálogo está vacío (la SPA no puede distinguir «sin reservas» de «parámetro inválido»). *Alternativa descartada:* `serviceId` opcional (ambigüedad sobre qué prestación calcular).

### D9 — Errores estables

`400 VALIDATION_FAILED` (params ausentes/mal formados/fecha inválida), `404 SERVICE_NOT_FOUND`, `422 SERVICE_INACTIVE`, `429 RATE_LIMITED` + `Retry-After`, `401 UNAUTHENTICATED`, `403 FORBIDDEN`, todos vía `ProblemDetails.of` con `application/problem+json`. Ningún cuerpo incluye valores enviados, nombres de clase, IP ni datos de la prestación.

*Alternativa descartada:* `400` genérico para `serviceId` inexistente (impide al cliente distinguir configuración de error de tipeo).

### D10 — Seguridad y límite de tasa sin tocar `SecurityConfig`

`/api/v1/public/**` ya es `permitAll` y `/api/v1/**` ya exige `DENTIST` salvo lo público; no se agrega regla HTTP. La ruta pública reutiliza `PublicReadRateLimiter` con clave `request.getRemoteAddr()` y traduce `RateLimitedException` con `RateLimiter.retryAfterSeconds(now, retryAt)` (mínimo 1). No se lee `X-Forwarded-For` directamente: si en el futuro se confía en proxy, se configura `ForwardedHeaderFilter` en un change separado.

*Alternativa descartada:* `Spring Security` con `requestMatchers` nuevos (duplica la política y abre la puerta a olvidar un método). *Alternativa descartada:* rate limit en memoria nueva (duplica `PublicReadRateLimiter`).

### D11 — Deltas aditivos

Los nueve deltas usan solo `## ADDED Requirements` con `#### Scenario:`. Ningún requirement existente se reescribe, para evitar conflictos de merge con changes activos como `c-19-settings-ui`. La capability nueva es `availability`; las ocho restantes solo reciben requirements nuevos.

*Alternativa descartada:* `MODIFIED Requirements` en `api-access-policy`/`module-boundaries` (sobrescribe texto ajeno y obliga a revalidar escenarios existentes).

### D12 — Ruta canónica y follow-up documental

`CHANGES.md` C-12 indica `/api/v1/public/availability` y KB 07 Flujo 3 paso 2 indica `/public/availability`. Se adopta la primera: es la única cubierta por la política `api-access-policy` y por `SecurityConfig`; la otra caería en denegación por defecto. En el archive se registra el follow-up para actualizar KB 07 y, si corresponde, C-24. **No se editan `CHANGES.md` ni `knowledge-base/` en este propose.**

*Alternativa descartada:* publicar ambas rutas (superficie duplicada, una de ellas sin política declarada). *Alternativa descartada:* cambiar la política para `/public/**` (amplía el perímetro público sin necesidad).

### D13 — Strict TDD con gates y evidencia

`tasks.md` se organiza por grupo: **Safety Net → RED → GREEN → TRIANGULATE (≥2 casos por comportamiento) → REFACTOR**, con un gate por grupo y una tabla de evidencia `| Tarea | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |`. Cobertura mínima: dominio puro (varios tramos, bloqueos parciales, adyacencia, bordes de granularidad, cambio de día UTC/BA, duraciones 15/30/60, sin prestaciones, `minNoticeMin`/`maxAdvanceDays`), tests `*IT` de endpoints/seguridad/rate limit y tests de arquitectura/contratos. Cada grupo termina con `verify` contra las specs antes de pasar al siguiente.

*Alternativa descartada:* escribir producción primero y tests después (no cumple la regla global de TDD ni deja evidencia de RED). *Alternativa descartada:* un solo test por comportamiento (no detecta regresiones en bordes).

## Risks / Trade-offs

- **Riesgo:** `WorkingHoursDto` y `PracticeSettingsData` cambien en C-19 → el adaptador de D2 debe concentrar el mapeo en un solo lugar. *Mitigación:* tests de contrato que fallan si el puerto exporta tipos de `application`.
- **Riesgo:** `AffectedAppointments` no expone estado en su record; si otro adaptador cambia el filtro `CONFIRMED`, el motor ofrecerá huecos ocupados. *Mitigación:* test de arquitectura/contrato que verifica el uso del puerto y escenario de turno `CANCELLED` vs `CONFIRMED`.
- **Riesgo:** zonas con DST distinto de Argentina → hora inexistente/ambigua mal manejada. *Mitigación:* tests de dominio con una zona con salto, además del caso BA.
- **Riesgo:** tramos que terminan a medianoche no ofrecen el último slot (C-11 no admite `24:00`). *Mitigación:* documentado en D6; sobreturnos quedan fuera de alcance.
- **Trade-off:** dos endpoints comparten caso de uso con `audience` distinta → más parámetros internos, pero evita duplicar reglas y mantiene la política de acceso por ruta.
- **Trade-off:** deltas aditivos → requirements duplicados temporalmente en `openspec/specs/` hasta el archive; es preferible a sobrescribir specs de otros changes.

## Migration Plan

1. Sin migración de base de datos ni cambios en datos existentes: C-12 solo lee tablas ya creadas por C-11.
2. Implementar en orden el `tasks.md` (dominio → puertos → aplicación → api → infraestructura → integración), con `verify` por grupo.
3. Rollback: revertir los commits del change; no hay esquema ni datos que revertir. La ruta pública queda sin handler y vuelve a `404` controlado por la política existente.
4. Despliegue: sin variables de entorno nuevas; el rate limit y la zona horaria se leen de la configuración actual.
5. Archive: ejecutar `openspec archive` y recién ahí actualizar KB 07/CHANGES con la ruta canónica de D12 si el equipo lo aprueba.

## Open Questions

- ¿Debe C-24 mostrar el estado `NO_ACTIVE_SERVICES` con un texto distinto a «sin turnos disponibles»? Es decisión de UI y no cambia esta API ni sus tasks; se resuelve en el propose de C-24.
- ¿Se publicará la ruta detrás de un proxy que reenvíe `X-Forwarded-For`? No cambia los specs ni el algoritmo: D10 fija `getRemoteAddr()` y cualquier confianza en headers se decide en un change de infraestructura posterior.

Fuera de estas preguntas, la ubicación del motor, los puertos, la fachada, la ruta canónica, el caso de catálogo vacío, los códigos de error y el enfoque TDD quedan decididos arriba; no se deferían al apply.
