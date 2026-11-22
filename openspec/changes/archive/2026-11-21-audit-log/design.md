Approval: pendiente (propuesta generada 2026-11-21; governance CRÍTICO — el líder aprueba antes del apply)

# Diseño: C-03 `audit-log`

## Context

Motivación y alcance: ver `proposal.md`; comportamiento observable: ver `specs/`.

Estado relevado en `backend/` (C-01 y C-02 archivados):

- Spring Boot **4.0.10**, Java 21, Gradle Kotlin DSL, paquete base `ar.turnosodontologia.consultorio`. Los ocho módulos ya tienen sus cuatro capas con `package-info.java`, incluido `auditoria.{api,application,domain,infrastructure}` (sin clases aún).
- `V1__baseline.sql` habilita `btree_gist` y da DML por defecto (`SELECT, INSERT, UPDATE, DELETE`) al rol `${app_role}` sobre tablas creadas por `migrator`; documenta el patrón `REVOKE UPDATE, DELETE ON <tabla> FROM ${app_role}` para tablas inmutables. `V2__core_models.sql` ya lo aplica a `practice`/`app_user` y crea `practice` (fila única, UUID literal `01900000-0000-7000-8000-000000000001`) y `app_user`.
- `application.yml`: `ddl-auto=validate`, `hibernate.jdbc.time_zone=UTC`, Flyway con `DB_MIGRATION_USER` y placeholder `app_role=${DB_USER}`. `Clock` (UTC) en `shared.infrastructure.ClockConfiguration`; `InstallationPractice` en `shared.application` expone `id()` y `zone()`.
- `architecture/ModuleBoundaries.java` ya contiene `allowedDependencyGraph`, que prohíbe a `auditoria` depender de módulos de negocio, y existen fixtures `archfixture/ok/auditoria/api/AuditoriaApi.java` y `archfixture/violacion_auditoria/...`. C-03 agrega una regla **dedicada y nombrada** con cobertura de las cuatro capas y del camino transitivo, no la duplica.
- Tests: `*Test` (unitarios, tarea `test`) y `*IT` (tarea `integrationTest`) sobre `EmbeddedPostgresSupport`: instancia zonky compartida, roles reales `migrator`/`app`, `Database.appConnection()` y `Database.migratorConnection()`, semillas de bootstrap generadas por JVM. Sin Docker (DD-10).
- La tabla `patient` **no existe todavía**: se crea en C-10 (`patients-management`). Por eso `audit_log.patient_id` no puede tener FK en V3.
- No hay dependencias nuevas disponibles ni aprobadas para este change; se usa lo que ya está en `build.gradle.kts`.

Restricciones: reglas duras 1–8 y 13–15 de `AGENTS.md`; KB 04 §audit_log, KB 05 RN-AD-01..03 y RN-HC-04/08, KB 08 §Módulos y §Seguridad (Auditoría, Datos personales en logs), KB 11 §Cómo el diseño las refleja; PA-01 y PA-03 abiertas (no bloquean este change; ver Open Questions). Strict TDD.

## Goals / Non-Goals

**Goals:**
- Un único punto de registro de eventos, utilizable por C-04/C-10/C-16/C-23 desde el primer día.
- Inmutabilidad impuesta por la base de datos, verificada con el rol real de la aplicación.
- Sanitización que haga imposible —por construcción y por prueba— persistir o loguear datos clínicos o identificadores personales.
- Cada tarea ejecutable como un ciclo RED→GREEN→TRIANGULATE→REFACTOR.

**Non-Goals:**
- Login, sesión y cableado de `LOGIN_OK`/`LOGIN_FAIL` (C-04); acceso a HC y su cableado (C-10/C-16); tokens de reserva y su cableado (C-23).
- API REST de consulta/exportación y política de retención (C-24).
- Cifrado a nivel columna, firma electrónica y definición legal de plazos (PA-01 / KB 11).
- Frontend, dependencias nuevas y cambios en `knowledge-base/`.

## Decisions

### D1 — Esquema `V3__audit_log.sql`

```sql
-- C-03 audit-log. Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion.
CREATE TABLE audit_log (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    practice_id   uuid        NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    user_id       uuid        NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    action        text        NOT NULL,
    entity_type   text        NULL,
    entity_id     text        NULL,
    patient_id    uuid        NULL,
    ip            inet        NULL,
    user_agent    text        NULL,
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    details       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT audit_log_action_check
        CHECK (action IN ('LOGIN_OK','LOGIN_FAIL','PATIENT_READ','PATIENT_UPDATE',
                          'HC_READ','HC_CREATE','HC_CORRECT','BOOKING_TOKEN_USE')),
    CONSTRAINT audit_log_entity_type_check CHECK (char_length(entity_type) <= 100),
    CONSTRAINT audit_log_entity_id_check   CHECK (char_length(entity_id) <= 200),
    CONSTRAINT audit_log_user_agent_check  CHECK (char_length(user_agent) <= 512),
    CONSTRAINT audit_log_details_object_check CHECK (jsonb_typeof(details) = 'object'),
    CONSTRAINT audit_log_details_no_pii_check
        CHECK (NOT (details ?| ARRAY['dni','document','documento','email','mail',
                                     'phone','telefono','name','nombre','apellido',
                                     'fullname','password','pass','secret','token',
                                     'authorization','header','body','content',
                                     'description','detalle','clinical','diagnostico',
                                     'alergia','medicacion','evolucion','note']))
);
CREATE INDEX audit_log_practice_occurred_idx ON audit_log (practice_id, occurred_at DESC);
CREATE INDEX audit_log_user_occurred_idx     ON audit_log (user_id, occurred_at DESC);
CREATE INDEX audit_log_patient_occurred_idx  ON audit_log (patient_id, occurred_at DESC)
    WHERE patient_id IS NOT NULL;
CREATE INDEX audit_log_action_occurred_idx   ON audit_log (action, occurred_at DESC);

REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM ${app_role};
```

- **Clave**: `bigint GENERATED ALWAYS AS IDENTITY` porque es un evento de log, no una entidad expuesta en URLs; recomendación del skill `postgresql-table-design` frente a UUID para series de eventos. Contrasta con las entidades UUIDv7 de C-02 (D1 de aquel diseño).
- **`action`**: `text` + `CHECK`, igual que `app_user.role` en V2. Sumar acciones es una migración nueva que reemplaza el `CHECK`; cuenta con el scenario "Ampliación del catálogo" de la spec.
- **`patient_id` sin FK**: la tabla `patient` aparece en C-10. Agregar la FK ahora sería imposible; el change de pacientes SHALL agregarla con `ALTER TABLE ... ADD CONSTRAINT ... REFERENCES patient (id) ON DELETE RESTRICT` en su propia migración. La columna ya existe y es nullable para no bloquear `LOGIN_*`.
- **`ip` (`inet`)** e `user_agent`: metadatos exigidos por RN-AD-01; el agente se acota a 512 caracteres y se limpia de caracteres de control. `inet` valida la dirección en la base (no `text`).
- **`occurred_at`**: `timestamptz` con `DEFAULT now()` para inserciones SQL; en la aplicación lo fija el `Clock` inyectado (regla dura 8), nunca un valor del cliente.
- **`details`**: `jsonb` con `CHECK` de objeto y una lista negra de claves de primer nivel como defensa en profundidad; la sanitización real (recursiva) ocurre en la aplicación (D3). No se crea índice GIN: no hay consultas por contenido en v1 (C-24 lo evaluará).
- **Privilegios**: V1 ya concede el DML por defecto; V3 revoca `UPDATE`, `DELETE` y `TRUNCATE` al rol de aplicación y conserva `INSERT`/`SELECT`. No se edita V1 ni V2 (regla dura 3).

### D2 — Forma del puerto y del evento

- `auditoria.api.AuditLogger`: puerto único con `void log(AuditEvent event)`; implementación en `auditoria.infrastructure`, inyectada por constructor en los casos de uso (regla dura 1).
- `auditoria.api.AuditAction`: enum con `LOGIN_OK, LOGIN_FAIL, PATIENT_READ, PATIENT_UPDATE, HC_READ, HC_CREATE, HC_CORRECT, BOOKING_TOKEN_USE`, alineado con el `CHECK` de D1.
- `auditoria.api.AuditEvent`: record inmutable con `action`, `userId` (nullable), `patientId` (nullable), `entityType`, `entityId`, `ip`, `userAgent` y `details` (mapa **no confiable**). **No expone** `practiceId` ni `occurredAt`: los fija la aplicación con `InstallationPractice.id()` y `Clock.instant()`, de modo que un módulo de negocio no pueda falsificarlos.
- `entityId` y `entityType` son texto acotado porque la referencia puede ser un UUID de HC/paciente o el identificador de un turno/token hasheado. Nunca el contenido.
- Se usa una fábrica estática `AuditEvent.of(...)` que valida longitud de `userAgent` y formato de `ip` antes de construir; un IP inválido se normaliza a `null` (metadato best-effort) sin rechazar el evento.
- Alternativa descartada: exponer una interfaz por acción (`logHcRead`, `logLoginOk`, …). Multiplica métodos y hace que cada módulo conozca más de lo necesario.

### D3 — Sanitización y redacción (RN-AD-03, RN-HC-08)

- `auditoria.domain.SensitiveDataSanitizer` (funciones puras, sin Spring): recibe el mapa `details` y devuelve un mapa seguro.
- **Lista de permitidos** de claves operacionales, p. ej. `outcome`, `reason_code`, `attempt`, `entity_version`, `field_names`, `duration_ms`, `source`. Cualquier clave fuera de la lista se descarta; la comparación es insensible a mayúsculas y normaliza guiones/espacios.
- **Lista negra** adicional (D1) para claves que nunca deben aparecer ni anidadas:identificadores de persona, contacto, credenciales, tokens y contenido clínico.
- **Redacción por valor**: incluso bajo una clave permitida, se redacta (`[REDACTED]`) lo que parezca email, teléfono/DNI (secuencias de 7–9 dígitos), token Base64URL largo, hash `$argon2...`, o cabecera `Bearer `. Se recorre de forma recursiva con límite de profundidad (2), tamaño máximo de claves/valores y número máximo de entradas; estructuras anidadas no escalares se descartan.
- La sanitización se ejecuta **antes** de construir el `INSERT` y antes de cualquier log; el mensaje de log nunca incluye `details`.
- **Datos personales en logs**: solo `id` del evento (cuando se obtuvo), `action` y, en fallos, un `errorId` UUID con el nombre de la clase. Nunca `toString()` del evento con los detalles sin sanitizar; el `AuditEvent` sobrescribe `toString()` con una versión redactada.
- Alternativa descartada: confiar en la capa de logging (MDC + filtros) sin sanitizar en el dominio. No cubre lo que se persiste y depende de la configuración del proveedor.

### D4 — Adaptador de persistencia

- `auditoria.infrastructure.JdbcAuditLogger` usa `JdbcTemplate` con `INSERT INTO audit_log (...) VALUES (...) RETURNING id`. No se crea entidad JPA ni repositorio Spring Data para `audit_log`: evita fricción con `ddl-auto=validate`, mantiene la tabla como apéndice y deja explícito el `jsonb`/`inet` (skill `supabase-postgres-best-practices`: tipos nativos, cast explícito y parámetros ligados, nunca concatenación — regla `security-`/SQL injection de `springboot-security`).
- `details` se liga como `PGobject` de tipo `jsonb` con el mapa ya sanitizado serializado; `ip` como `PGobject` de tipo `inet`; `occurred_at` como `Instant` del `Clock`; `practice_id` de `InstallationPractice`.
- Transacción: el `INSERT` participa en la transacción del caso de uso. Si la operación de negocio revierte, su evento también; no queda rastro de una operación que no ocurrió.
- `log(...)` es **fail-closed**: si el `INSERT` falla, propaga una excepción de dominio (`BusinessRuleException` con código `AUDIT_WRITE_FAILED`) antes de que el caso de uso entregue datos clínicos. La excepción no lleva el mensaje de PostgreSQL (puede contener valores).
- Alternativa considerada: JPA con `@Entity` en `domain` (patrón de C-02). Descartada por el riesgo de `validate` y porque no aporta nada a una tabla de solo inserción.

### D5 — Aislamiento de `auditoria` (regla dedicada)

- `ModuleBoundaries.auditoriaIsolatedFromBusinessModules(root)`: para cada clase bajo `auditoria.{api,application,domain,infrastructure}`, recorre sus dependencias directas **y transitivas** dentro del raíz y falla si alguna apunta a `agenda`, `reservas`, `pacientes`, `historiaclinica` o `notificaciones`. Incluye el mensaje con origen y destino.
- `ModuleBoundaries.auditEventsExposedOnlyThroughApi(root)`: una clase fuera de `auditoria` solo puede referenciar `auditoria.api`.
- Se agregan fixtures de test: `archfixture/violacion_auditoria/auditoria/{api,application,domain,infrastructure}/...` para cada capa, `violacion_auditoria_transitiva/auditoria/application/...` (pasa por `shared` hacia un módulo de negocio) y `ok_auditoria/auditoria/infrastructure/...` (solo `shared`). Los fixtures existentes `violacion_auditoria/pacientes/api` y `ok/auditoria/api` se reutilizan.
- La regla genérica `allowedDependencyGraph` sigue vigente: esta es una verificación explícita y legible para un dominio CRÍTICO, no un reemplazo.

### D6 — Estrategia de pruebas (Strict TDD, skills `springboot-tdd` / `springboot-security`)

- Unitarios (`*Test`): `AuditEventTest` (inmutabilidad, validación, `toString` redactado), `SensitiveDataSanitizerTest` (allowlist, redacción recursiva, casos negativos), `AuditLoggerApplicationTest` (rellena `practice_id`/`occurred_at`, fail-closed con fake). Se usa `Clock.fixed` para el instante.
- Arquitectura (`ModuleBoundariesRulesTest`, `ProductionArchitectureTest`): cada regla nueva se prueba primero con su fixture que falla y luego con el que pasa.
- Integración (`*IT`, zonky): `AuditLogSchemaIT` (columnas, tipos, `CHECK`, índices, V3 en el historial), `AuditLogPrivilegesIT` (con `Database.appConnection()`: `INSERT`/`SELECT` OK; `UPDATE`/`DELETE`/`TRUNCATE` rechazados; la fila sobrevive), `AuditLogPersistenceIT` (evento sanitizado → fila leída con roles reales, `user_id` nulo para evento anónimo, IP `inet`), `AuditLogLeakageIT` (DNI/email/teléfono/texto clínico/token en `details` y en una excepción → ni en la fila ni en la salida capturada con `OutputCaptureExtension`). Cada caso en su propia transacción revertida; limpieza con `migrator` cuando haga falta.
- Cobertura: JaCoCo ≥ 80 % en las clases nuevas de `auditoria`; `./gradlew check` (unitarios + ArchUnit + IT) en verde.

### D7 — Dependencias y configuración

- Ninguna dependencia nueva (regla dura 12). `JdbcTemplate` ya está disponible vía `spring-boot-starter-data-jpa`; ArchUnit y zonky ya están en `testImplementation`.
- Sin variables de entorno nuevas. Sin cambios en `.env.example`, `application.yml` ni `frontend/`.

## Risks / Trade-offs

- [`patient_id` sin FK hasta C-10] → el riesgo es una referencia huérfana si un módulo escribe un ID inventado. Mitigación: el `AuditEvent` valida el tipo de `patientId` (UUID) y C-10 agrega la FK en su migración; se documenta en el design de ese change.
- [Fail-closed puede reducir disponibilidad] → un fallo de BD bloquea la operación auditada. Mitigación: es el comportamiento exigible para datos de salud (RN-HC-04); se reporta con `errorId` y sin datos personales. Se puede revisar con el líder si se mide impacto.
- [Lista negra de claves en la base] → no cubre claves anidadas ni valores con forma de DNI bajo una clave permitida. Mitigación: la sanitización recursiva de D3 es la defensa principal; el `CHECK` es solo defensa en profundidad.
- [IP como dato personal] → RN-AD-01 exige registrar la IP. Mitigación: se guarda como `inet`, sin combinarla con otros identificadores en `details`, y se excluye de logs; su conservación queda sujeta a la política de retención de C-24.
- [Regla ArchUnit transitiva más costosa] → recorrido de dependencias en el build. Mitigación: el grafo es chico (8 módulos); se mide en el primer `./gradlew test`.
- [Retención y Ley 27.706] → PA-01 puede exigir controles adicionales sobre el rastro. Mitigación: el esquema es append-only y extensible con migraciones nuevas; C-24 revisa la política sin reescribir este change.

## Migration Plan

- Al arrancar, Flyway aplica `V3` como `migrator` sobre una base con `V1`/`V2`; el rol de aplicación sigue sin DDL. No se edita ninguna migración aplicada (regla dura 3).
- Rollback en desarrollo: no existe borrado de `audit_log` desde la aplicación. Para volver atrás localmente se recrea la base con el script de roles de C-01. En un entorno con datos, cualquier reversión sería una migración **nueva** y necesitaría aprobación legal por tratarse de un rastro de accesos.
- Los módulos que hoy no escriben auditoría siguen funcionando: el puerto es nuevo y nadie está obligado a usarlo hasta C-04/C-10/C-16/C-23.

## Open Questions

- **Retención del rastro y plazos legales** (KB 11, PA-01): no se fija un borrado automático; C-24 define la política. No cambia las specs ni las tareas de C-03.
- **Ubicación de los datos y proveedor** (PA-03): afecta al despliegue, no al esquema ni al puerto. Se resuelve antes de producción.
- **FK de `patient_id`**: se agregará en la migración del change que cree `patient` (C-10); no es una decisión pendiente de C-03, sino una dependencia temporal documentada.

## UI Mockups

- Design system revision: 1
- Decision: no (no UI) — 2026-11-21. C-03 no agrega pantallas, componentes ni estilos.
- Mockups: n/a
- Approval: n/a
- Preview: n/a
- Visual verify: n/a
