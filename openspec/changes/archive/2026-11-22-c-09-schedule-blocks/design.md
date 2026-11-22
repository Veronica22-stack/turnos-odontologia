# Design: C-09 `schedule-blocks`

## Context

Ver `proposal.md` para la motivación. Estado observable del repo al redactar este diseño:

- Hay migraciones `V1` a `V3`, `V5` (C-08: `working_hours` + parámetros de `practice`) y `V7` (C-10: `patient`). **No existen V4 ni V6.** `outOfOrder` no está habilitado y no se editan migraciones aplicadas (regla dura 3).
- `V1` ya habilita `btree_gist` y define los privileges por defecto: el rol de migración crea tablas y `${app_role}` recibe `SELECT, INSERT, UPDATE, DELETE` sin GRANT extra; V2/V5/V7 restringen según cada tabla.
- El módulo `agenda` ya tiene el patrón de C-08: entidad y validadores en `domain`, casos de uso en `application`, DTOs `record` en `api`, controlador y repositorio Spring Data en `infrastructure`, con inyección por constructor, `@PreAuthorize("hasRole('DENTIST')")` y manejo global de errores en `shared.api.ApiExceptionHandler` (`ProblemDetail` + `code`).
- No existe aún la tabla `appointment`: llegará con C-11. Por eso el aviso de turnos afectados (RN-AG-08) no puede resolverse hoy.

## Goals / Non-Goals

**Goals:**
- Persistir bloqueos con rango `tstzrange` en PostgreSQL usando solo dependencias ya presentes.
- Impedir solapamientos entre bloqueos con garantía de base de datos y traducir el conflicto a un `409` amigable.
- Publicar contratos estables (`BlockedRanges`, `AffectedAppointments`) para C-11 y C-12 sin acoplar módulos.
- Exponer el CRUD REST protegido de US-012 con validación y errores uniformes.

**Non-Goals:**
- Consultar turnos reales afectados por un bloqueo: lo implementa C-11.
- Calcular disponibilidad o excluir slots en el motor de reservas: lo hace C-12 consumiendo `BlockedRanges`.
- Crear la tabla `appointment` ni la restricción de exclusiones turno/turno: C-11.
- Actualizar `CHANGES.md`, la KB u otras migraciones: follow-up de documentación.
- UI de bloqueos (C-19) y notificaciones a pacientes por bloqueo (fuera de v1).

## Decisions

### D1 — Numeración de la migración: `V8__schedule_block.sql`
`CHANGES.md` §C-09 dice «Migración V6», pero `V7__patient.sql` ya está en la secuencia. Insertar `V6` ahora dejaría una versión menor que la última aplicada; Flyway con `outOfOrder=false` aborta el arranque en toda base migrada a V7. Renombrar/editar V7 está prohibido y cambiar su checksum rompería esas bases. Se elige `V8` para mantener el orden monotónico.

*Alternativas evaluadas:* (a) `V6` con `outOfOrder=true`: descartada, cambia la configuración global y deja un historial engañoso; (b) renombrar V7 a V9: descartada, modifica una migración aplicada; (c) reutilizar V5: descartada, altera una migración ya aplicada y mezcla responsabilidades.

*Consecuencia documentada:* `CHANGES.md` queda desactualizado para C-09 (dice V6) y para C-11 (anuncia V8 para `appointment`). C-11 deberá usar el siguiente número libre (`V9` o posterior según lo que exista al momento de su propose). Se registra como follow-up del orquestador; **no se edita acá**.

### D2 — Mapeo de `tstzrange` sin dependencias nuevas
La entidad expone `Instant startsAt` / `Instant endsAt`, mapeados a columnas `timestamptz` normales. La migración agrega:

```sql
period tstzrange GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
CONSTRAINT schedule_block_period_check CHECK (ends_at > starts_at),
CONSTRAINT schedule_block_no_overlap EXCLUDE USING gist (
    practice_id WITH =,
    period WITH &&)
```

La restricción de exclusiones usa la columna generada; JPA **no la mapea** (es un detalle de la base, no un campo del API). Con `ddl-auto=validate`, una columna extra en la tabla no genera error. La consulta de bloqueos solapados (`BlockedRanges`) no necesita `PGobject`: usa `starts_at < :to AND ends_at > :from` sobre un índice `(practice_id, starts_at)`, con la misma semántica semiabierta.

*Alternativas evaluadas:* (a) columna `period` escrita por la aplicación con `PGobject`: descartada, exige conversión manual, tipos JDBC en capas superiores y riesgo de desincronización entre `starts_at`/`ends_at` y `period`; (b) librería `hibernate-types`: descartada por la regla dura de no agregar dependencias de runtime sin aprobación; (c) expresión de rango directa en el `EXCLUDE`: válida en PostgreSQL, pero la columna generada nombra el concepto, facilita consultas SQL y deja la invariante en un solo lugar. Se asume que `tstzrange(timestamptz, timestamptz, text)` es inmutable en PostgreSQL 15+, condición que la migración IT debe confirmar al aplicar V8.

### D3 — Permisos de la base
Siguiendo V1/V5/V7 se aplica `REVOKE TRUNCATE ON schedule_block FROM ${app_role};`. **No** se revocan `UPDATE` ni `DELETE`: el bloqueo es configuración editable y el CRUD lo exige; no es dato clínico ni append-only como `audit_log`/`patient`. Los intentos de borrado masivo quedan impedidos por `TRUNCATE`, y el borrado normal pasa por el endpoint con su control de sesión/CSRF.

### D4 — Modelo de dominio y contratos
- `TimeInterval` (value object en `agenda.domain`): `Instant start`, `Instant end`, validación `end.isAfter(start)`, `intersects(other)` con lógica `a.start < b.end && b.start < a.end` (adyacentes no intersectan).
- `ScheduleBlock` (entidad en `agenda.domain`): `id` UUID v7 con `@UuidGenerator(style = VERSION_7)` igual que `WorkingHours`, `practiceId`, `startsAt`, `endsAt`, `reason`, `createdAt`, `updatedAt`. El constructor valida el rango y el motivo; `createdAt`/`updatedAt` se fijan en aplicación con el `Clock` inyectable de `shared` (no con `now()` del cliente).
- `agenda.api`: `BlockedRanges` con `List<BlockedInterval> findOverlapping(UUID practiceId, Instant from, Instant to)` y `AffectedAppointments` con `List<AffectedAppointment> findWithin(UUID practiceId, Instant from, Instant to)`; `BlockedInterval` y `AffectedAppointment` son `record` con UUID e `Instant`, sin JPA ni Spring Data.
- `agenda.application` consume `Optional<AffectedAppointments>` por constructor: en C-09 no hay bean, por lo que la lista de afectados es vacía y no se crea un bean muerto; C-11 registra la implementación real y el mismo código la utiliza sin cambiar el contrato HTTP. *Alternativa descartada:* adapter `NoOp` anotado como bean en C-09, que deja un componente temporal que habrá que quitar.

### D5 — Capas, API y errores
- Se replica la ubicación de C-08: DTOs en `agenda.api`, casos de uso en `agenda.application`, entidad/repositorio en `agenda.domain`/`infrastructure`, controlador `ScheduleBlocksController` en `agenda.infrastructure` con `@RequestMapping("/api/v1/schedule-blocks")`. Esto respeta que `application` no depende de `api`, y que otros módulos solo ven `agenda.api`.
- DTOs `record`: `ScheduleBlockRequest(startsAt, endsAt, reason)` y `ScheduleBlockResponse(id, startsAt, endsAt, reason, createdAt, updatedAt, affectedAppointments)`, con Bean Validation (`@NotNull`, `@NotBlank`, `@Size(max = 500)`). Las entidades JPA nunca salen del controlador.
- Endpoints: `GET` (filtro opcional `from`/`to`), `POST`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}` con estados `200/201/200/200/204`. `@PreAuthorize("hasRole('DENTIST')")` en todos y CSRF (`X-XSRF-TOKEN`) en mutaciones, igual que C-08.
- Errores: `ValidationException`/`IllegalArgumentException` de rango → `400` (`VALIDATION_FAILED` / `INVALID_RANGE`), `NotFoundException` → `404 SCHEDULE_BLOCK_NOT_FOUND`, solapamiento previsto o `DataIntegrityViolationException` por la exclusión → `409 SCHEDULE_BLOCK_OVERLAP`. Se reutiliza `ApiExceptionHandler`; no se exponen mensajes de PostgreSQL ni del motivo.

### D6 — Concurrencia
La validación en dominio da un mensaje claro, pero la garantía la da el `EXCLUDE USING gist`: ante dos inserciones simultáneas, una falla en la base y se traduce a `409`. No se agregan locks de aplicación ni `advisory locks`.

## Risks / Trade-offs

- **[Riesgo] El constructor de rango generado no es inmutable en la versión de PostgreSQL instalada** → la migración IT de V8 falla tempranamente; alternativa de contingencia: `EXCLUDE` sobre la expresión `tstzrange(starts_at, ends_at, '[)')`, sin columna generada, conservando el mismo contrato.
- **[Riesgo] Desfase entre V8 y el cronograma** → `CHANGES.md` sigue diciendo V6 y C-11 anuncia V8; se reporta como follow-up y C-11 deberá numerarse V9 o posterior. Ninguna migración existente se toca.
- **[Riesgo] `affectedAppointments` vacío hasta C-11** → se documenta en el contrato y en la spec; el campo ya existe para no romper el DTO cuando C-11 llegue.
- **[Riesgo] Rango en UTC vs presentación local** → la API solo acepta/devuelve `Instant`; la conversión a `America/Argentina/Buenos_Aires` es responsabilidad de `time-and-zones`/frontend, no de este change.
- **[Riesgo] Motivo con datos personales** → se valida longitud pero no contenido; se prohíbe incluirlo en logs y respuestas de error; se recomienda al usuario no cargar datos sensibles (C-19 puede mostrar una advertencia).

## Migration Plan

1. Agregar `backend/src/main/resources/db/migration/V8__schedule_block.sql` (creación de tabla, `CHECK`, columna generada, `EXCLUDE`, índice `(practice_id, starts_at)` y `REVOKE TRUNCATE`). No se tocan V1–V7.
2. Verificar con `*IT` sobre PostgreSQL embebido zonky: aplicar de clean y desde V7, constraint de rango, solapamiento/adyacencia, `period`, privilegios de `${app_role}` y rechazo de `TRUNCATE`.
3. Rollback: no hay script de down; ante reversión antes de producción se restaura backup o se aplica un script de compensación específico (fuera de este change, según convención del repo).

## Open Questions

Ninguna que bloquee el propose. Quedan como follow-up de documentación ajeno a este change: actualizar `CHANGES.md` (V6 → V8 en C-09 y reubicar la V8 de C-11) y decidir en C-11 si los estados `COMPLETED`/`NO_SHOW` siguen bloqueando su rango histórico (KB 04 lo marca como suposición).
