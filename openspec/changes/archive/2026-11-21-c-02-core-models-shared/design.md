Approval: approved (2026-11-21, by Lucas)

# Diseño: C-02 `core-models-shared`

## Context

Motivación y alcance: ver `proposal.md`. Requisitos: ver `specs/`.

Estado actual relevado en `backend/` (C-01 archivado, `openspec/changes/archive/2026-11-21-c-01-foundation-setup/design.md`):

- Spring Boot **4.0.10**, Java 21, Gradle Kotlin DSL, paquete `ar.turnosodontologia.consultorio`, 8 módulos × 4 capas con `package-info.java`; reglas ArchUnit en `src/test/.../architecture/ModuleBoundaries.java` (hoy `domain` solo tiene prohibidos `org.springframework.web.`, `org.springframework.jdbc.` y `java.sql.`).
- `V1__baseline.sql`: `btree_gist` + `ALTER DEFAULT PRIVILEGES` que dan `SELECT, INSERT, UPDATE, DELETE` al rol `${app_role}` sobre toda tabla que cree `migrator`; patrón documentado de `REVOKE` por tabla.
- `application.yml`: `ddl-auto=validate`, `open-in-view=false`, `hibernate.jdbc.time_zone=UTC`, `server.error.include-stacktrace=never`; se excluye `UserDetailsServiceAutoConfiguration`.
- `shared.infrastructure.RequiredConfiguration`: falla el arranque si falta una propiedad obligatoria de BD/Flyway.
- `auth.infrastructure.SecurityConfig`: deny-by-default; solo `GET /actuator/health` es público; 401 vacío por `HttpStatusEntryPoint`.
- **No existe** ningún `@ControllerAdvice`, `Clock`, entidad ni repositorio: C-02 crea el manejo de errores desde cero (no hay duplicado que reemplazar).
- Tests: `*Test` (unitarios, tarea `test`) y `*IT` (zonky, tarea `integrationTest`); `AbstractIntegrationTest` (`@SpringBootTest`) + `EmbeddedPostgresSupport` singleton por JVM que publica propiedades vía `@DynamicPropertySource`.
- `.env.example`: según C-01 D10 ya lista `BOOTSTRAP_ADMIN_EMAIL/PASSWORD` con marcador y un comentario del change que las usa. **No se pudo leer en esta propuesta** (el archivo está bloqueado por los permisos del agente); la tarea 1.3 lo verifica.

Restricciones: reglas duras 1–8 y 13–15 de `CLAUDE.md`; KB 04 §practice/§app_user/§Seed, KB 05 RN-AU/RN-AG-07/RN-GL, KB 08 §Módulos; PA-04 abierta (C-02 **no** decide sesión vs JWT ni política final de contraseñas); DD-10 (sin Docker); Strict TDD.

## Goals / Non-Goals

**Goals:**
- Tablas y modelos core estables que C-03/C-04/C-05 puedan referenciar sin migraciones correctivas.
- Piezas de `shared` (reloj, zona, contexto de consultorio, errores) con API mínima y testeable sin Spring.
- Bootstrap seguro del primer DENTIST, sin secretos en el repo ni en logs.
- Cada tarea lo bastante chica para un ciclo RED→GREEN→TRIANGULATE→REFACTOR (implementan Lucas y Elian).

**Non-Goals:**
- Login, sesión/JWT, CSRF, bloqueo progresivo, cambio de contraseña, política final de contraseñas (C-04, PA-04). C-02 solo crea las columnas `failed_attempts`, `locked_until`, `must_change_password`, `last_login_at` y el puerto de hash.
- Respuestas 401/403 de la cadena de seguridad en formato `ProblemDetail` (las genera un filtro, no el `@ControllerAdvice`): C-04 cablea `AuthenticationEntryPoint`/`AccessDeniedHandler` reutilizando la fábrica de problemas de C-02.
- Endpoints REST (C-02 no expone ninguno).
- Parámetros de agenda en `practice` (C-05/V5), `audit_log` (C-03), `practice_id` en otras tablas.

## Decisions

### D1 — Claves primarias UUIDv7 generadas en la aplicación **(decisión del usuario)**
- **Decisión**: `id uuid PRIMARY KEY` sin `DEFAULT` en la base. Las entidades usan el generador de Hibernate para UUID versión 7 (`@UuidGenerator(style = …)`). En Hibernate 7 (el de Boot 4) el estilo para v7 es `Style.VERSION_7`; `Style.TIME` en Hibernate 6 generaba un UUID basado en tiempo **no** v7. En el apply se confirma con context7 qué estilo produce v7 en la versión resuelta y el test `AppUserRepositoryIT` verifica `uuid.version() == 7`.
- La fila de `practice` sembrada por V2 usa un literal fijo con formato v7 (`01900000-0000-7000-8000-000000000001`), estable entre instalaciones.
- **Por qué**: KB 04 fija UUID; v7 es ordenable por tiempo (índices B-tree sin fragmentación, a diferencia de v4) y no revela volumen como un `bigint`. Generar en la app evita depender de `uuidv7()` (solo PG18+) o de `gen_random_uuid()` (v4).
- **Alternativas**: `bigint GENERATED ALWAYS AS IDENTITY` (recomendación por defecto del skill `postgresql-table-design`, pero filtra conteos en URLs); UUIDv4 en BD.
- `audit_log` (C-03) mantiene `bigint`: fuera de alcance.

### D2 — Argon2id con parámetros OWASP **(decisión del usuario; nueva dependencia aprobada)**
- **Decisión**: `Argon2PasswordEncoder(saltLength = 16, hashLength = 32, parallelism = 1, memory = 19456 KiB, iterations = 2)` (mínimo OWASP: m=19 MiB, t=2, p=1). No se usa `defaultsForSpringSecurity_v5_8()` porque su memoria es 16 MiB, por debajo de OWASP.
- Dependencia: `implementation("org.bouncycastle:bcprov-jdk18on")` — Spring Security la necesita para Argon2. Si el BOM de Boot 4 no gestiona su versión, se fija la última estable publicada en Maven Central (confirmar al implementar).
- Se expone en el dominio como puerto `auth.domain.PasswordHasher { String hash(CharSequence raw); boolean matches(CharSequence raw, String hash); }`, implementado por `auth.infrastructure.Argon2PasswordHasher`. Además se publica un bean `PasswordEncoder` (el mismo encoder) para que C-04 lo use en Spring Security.
- **Por qué puerto**: el caso de uso de bootstrap queda testeable con un *fake* sin coste de CPU; C-04 reutiliza `matches`.
- **Alternativas**: BCrypt cost 12 (sin dependencia nueva; KB lo admite) — descartado por decisión del usuario; `DelegatingPasswordEncoder` (prefijo `{argon2}`) — innecesario mientras haya un solo algoritmo; se puede introducir después sin migrar hashes.

### D3 — Sin Docker; integración con PostgreSQL embebido zonky **(decisión del usuario, DD-10)**
- Todos los tests de repositorio, restricciones, privilegios y bootstrap son `*IT` sobre `EmbeddedPostgresSupport` (instancia singleton, roles reales `migrator`/`app`). Se ignora cualquier consejo de Testcontainers/`@ServiceConnection` de los skills.
- `@DataJpaTest` **no** se usa: reemplazaría el `DataSource` y ocultaría la separación de roles (mismo motivo que C-01 D7). Los IT de repositorio extienden `AbstractIntegrationTest`.

### D4 — Ubicación por módulo
| Pieza | Paquete | Motivo |
|-------|---------|--------|
| `Practice` (entidad), `PracticeRepository` (puerto) | `shared.domain` | Todos los módulos necesitan `practice_id`; `shared` es accesible desde todos (ArchUnit). |
| `InstallationPractice` (contexto) | `shared.application` | Lógica de resolución sobre el puerto; sin web ni JDBC. |
| `ZoneConversions` (funciones puras) | `shared.domain` | Sin dependencias; testeable con unitarios. |
| `PracticeTime` (reloj + zona del consultorio) | `shared.application` | Combina `Clock` y `InstallationPractice`. |
| `ClockConfiguration` (bean `Clock.systemUTC()`), `SpringDataPracticeRepository` | `shared.infrastructure` | Wiring de Spring / Spring Data. |
| `DomainException` y subclases | `shared.domain` | Los dominios de cada módulo las lanzan sin conocer HTTP. |
| `ApiExceptionHandler` (`@RestControllerAdvice`) y `ProblemDetails` (fábrica) | `shared.api` | Capa web. |
| `AppUser`, `Role`, `AppUserRepository`, `PasswordHasher`, `BootstrapCredentials` | `auth.domain` | KB 04: consultorio y acceso → auth. |
| `BootstrapDentist` (caso de uso) | `auth.application` | Orquesta puertos; usa `InstallationPractice` y `Clock`. |
| `SpringDataAppUserRepository`, `Argon2PasswordHasher`, `PasswordConfiguration`, `BootstrapDentistRunner` | `auth.infrastructure` | Adaptadores y disparo al arranque. |

`auth` depende solo de `shared` (permitido: `auth` no depende de módulos de negocio).

### D5 — Entidades JPA en `domain` con anotaciones de mapeo; Spring prohibido en `domain`
- **Decisión**: las entidades de dominio (`Practice`, `AppUser`) llevan anotaciones `jakarta.persistence` y `org.hibernate.annotations.UuidGenerator`. No usan nada de `org.springframework`. Los repositorios de dominio son interfaces Java puras (`AppUserRepository`, `PracticeRepository`); en `infrastructure` una interfaz Spring Data las extiende (`interface SpringDataAppUserRepository extends JpaRepository<AppUser, UUID>, AppUserRepository`) y Spring Data implementa los métodos derivados.
- ArchUnit (`ModuleBoundaries`): la lista de prohibidos de `domain` pasa de `org.springframework.web.`/`.jdbc.` a **todo** `org.springframework.` + `java.sql.`. Se agregan fixtures `violacion_dominio_spring` (falla) y `ok_dominio_jpa` (pasa). **Desviación aplicada**: el fixture `ok_dominio_jpa` usa `@MappedSuperclass` (+ `@UuidGenerator`) y no `@Entity`, porque una `@Entity` bajo el paquete escaneado por la aplicación entraría al `EntityManagerFactory` de todos los IT y rompería `ddl-auto=validate` (no tiene tabla). La regla ArchUnit solo prohíbe `org.springframework.`/`java.sql.`, así que ejercita lo mismo (anotaciones de `jakarta.persistence` y `org.hibernate.annotations` permitidas en `domain`).
- **Por qué**: resuelve la decisión que C-01 D5 dejó para C-02 con el menor código posible para un equipo chico: sin duplicar cada entidad en un modelo de dominio y otro de persistencia con mapeadores. El dominio sigue siendo testeable sin contexto de Spring (las anotaciones son inertes en un unitario).
- **Alternativa (más pura)**: dominio sin anotaciones + entidades JPA en `infrastructure` + mapeadores; prohibir también `jakarta.persistence` en `domain`. Duplica clases por entidad y complica la asignación de IDs (habría que asignar el UUIDv7 en el dominio y usar `Persistable` para evitar `merge`). Queda disponible si el líder la prefiere: cambia D4/D5 y las tareas 3.x/4.x, no las specs.

### D6 — Esquema `V2__core_models.sql`
```sql
CREATE TABLE practice (
    id             uuid        PRIMARY KEY,
    name           text        NOT NULL CHECK (btrim(name) <> '' AND char_length(name) <= 200),
    timezone       text        NOT NULL DEFAULT 'America/Argentina/Buenos_Aires' CHECK (btrim(timezone) <> ''),
    contact_email  text        NULL CHECK (char_length(contact_email) <= 254),
    contact_phone  text        NULL CHECK (char_length(contact_phone) <= 50),
    address        text        NULL CHECK (char_length(address) <= 300),
    created_at     timestamptz NOT NULL DEFAULT now()
);
-- v1 single-tenant: a lo sumo una fila. Se elimina con DROP INDEX al pasar a multi-tenant (DD-02).
CREATE UNIQUE INDEX practice_single_row ON practice ((true));

INSERT INTO practice (id, name) VALUES ('01900000-0000-7000-8000-000000000001', 'Consultorio');

CREATE TABLE app_user (
    id                    uuid        PRIMARY KEY,
    practice_id           uuid        NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    email                 text        NOT NULL CHECK (email = lower(btrim(email)) AND char_length(email) BETWEEN 3 AND 254),
    password_hash         text        NOT NULL CHECK (password_hash <> ''),
    role                  text        NOT NULL CHECK (role IN ('DENTIST')),
    full_name             text        NULL CHECK (char_length(full_name) <= 200),
    license_number        text        NULL CHECK (char_length(license_number) <= 50),
    failed_attempts       integer     NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until          timestamptz NULL,
    must_change_password  boolean     NOT NULL DEFAULT false,
    created_at            timestamptz NOT NULL DEFAULT now(),
    last_login_at         timestamptz NULL
);
CREATE UNIQUE INDEX app_user_email_key ON app_user (lower(email));
CREATE INDEX app_user_practice_id_idx ON app_user (practice_id);

REVOKE INSERT, DELETE ON practice FROM ${app_role};
REVOKE DELETE ON app_user FROM ${app_role};
```
(Bosquejo; el apply lo escribe por TDD. Nombres de restricciones explícitos a criterio del implementador.)
- **Fila única** con índice único de expresión `((true))`: sin columna artificial, se quita con un `DROP INDEX`.
- **`role` como `text` + `CHECK`** (no `ENUM` de PostgreSQL): sumar `SECRETARY` es una migración que reemplaza el `CHECK`; el skill `postgresql-table-design` desaconseja `ENUM` para conjuntos que evolucionan. En Java: `enum Role { DENTIST }` con `@Enumerated(EnumType.STRING)`.
- **Email**: se normaliza en el dominio (`trim` + `toLowerCase(Locale.ROOT)`); la base lo exige con `CHECK` y garantiza unicidad con índice único sobre `lower(email)`. Unicidad **global** (no por `practice_id`): el login (C-04) identifica al usuario solo por email, sin elegir consultorio. Tensión con KB 04 §multi-tenant ("las constraints de unicidad incluyen `practice_id`"): se elige global porque un email debe identificar a una persona para autenticarse; se puede revisar al pasar a multi-tenant.
- **Timestamps**: `timestamptz`; con `hibernate.jdbc.time_zone=UTC` y `Instant` en Java. `created_at` lo fija la aplicación desde el `Clock` inyectado (el `DEFAULT now()` solo cubre inserciones SQL como el seed de `practice`).
- **Campos de KB no incluidos**: `slot_granularity_min`, `min_notice_min`, `max_advance_days` → V5 (C-05, según `CHANGES.md`). `must_change_password` no está en KB 04 pero sí en el scope de C-02.
- **Datos de contacto**: `NULL` en el seed (no se inventan datos del consultorio); C-05 los edita.

### D7 — Privilegios más estrictos que el default de V1
- **Decisión**: además del DML por defecto, V2 revoca `INSERT, DELETE` sobre `practice` y `DELETE` sobre `app_user` al rol de aplicación (KB 03: usuarios solo "U"; el consultorio se edita, no se crea ni se borra). Sigue el patrón de `REVOKE` documentado en V1.
- **Alternativa**: dejar el DML completo de V1 (más simple; menos defensa en profundidad).

### D8 — Contexto del consultorio de la instalación
- `InstallationPractice` (bean de `shared.application`) expone `UUID id()` y `ZoneId zone()`. Lee `PracticeRepository.findAll()` una vez (perezoso, en caché), exige **exactamente una** fila y valida la zona con `ZoneId.of` (error claro si es inválida). El `BootstrapDentistRunner` lo consulta primero, por lo que una base sin consultorio o con zona inválida hace fallar el arranque (fail-fast).
- La caché vale para toda la vida del proceso: si C-05 permite cambiar la zona, deberá invalidarla (anotado para C-05).
- **Fuente de la zona**: la columna `practice.timezone`, no `APP_TIMEZONE` (ver Open Questions).
- **Alternativa**: `ThreadLocal`/request-scoped "tenant context" — innecesario en single-tenant; se introduce si llega multi-tenant.

### D9 — Reloj y zonas
- `shared.infrastructure.ClockConfiguration` publica `@Bean Clock clock() { return Clock.systemUTC(); }`. Regla para todos los changes: **nunca** `Instant.now()`/`LocalDate.now()` sin `Clock` (se puede agregar una regla ArchUnit más adelante; fuera de alcance).
- `shared.domain.ZoneConversions` (funciones estáticas puras): `toLocal(Instant, ZoneId) → LocalDateTime`, `toInstant(LocalDateTime, ZoneId) → Instant` (hueco de horario → `NonexistentLocalTimeException` de `shared.domain`; ambigüedad → `ZonedDateTime.ofLocal(..., null)` usa el desfase más temprano), `dayBounds(LocalDate, ZoneId) → InstantRange[start, end)`.
- `shared.application.PracticeTime`: `now()` (Instant del `Clock`), `today()` y conversiones usando `InstallationPractice.zone()`.
- Tests unitarios con `Clock.fixed` y zonas explícitas; `America/New_York` se usa solo para probar hueco/ambigüedad (Argentina no tiene horario de verano, SU-08).

### D10 — Manejo global de errores con `ProblemDetail`
- `shared.api.ApiExceptionHandler extends ResponseEntityExceptionHandler`, anotado `@RestControllerAdvice`. La clase base cubre las excepciones de Spring MVC (405, 415, 400 por tipo/JSON, `NoResourceFoundException` 404); se sobreescribe `handleExceptionInternal` para **reemplazar** el `detail` del framework (puede contener valores enviados o texto del parser) por uno genérico y agregar `code`.
- Cuerpo: `type` = `about:blank`, `status`, `title` genérico en español por estado (p. ej. "Solicitud inválida", "Recurso no encontrado", "Conflicto", "Regla de negocio incumplida", "Error interno"), propiedad `code` estable (`VALIDATION_FAILED`, `MALFORMED_REQUEST`, `METHOD_NOT_ALLOWED`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`, o el código del dominio). Sin `detail` dinámico.
- Excepciones de dominio (`shared.domain`): `abstract DomainException(String code)` con subclases `NotFoundException` → 404, `ConflictException` → 409, `BusinessRuleException` → 422. **Solo llevan un código**, nunca texto libre ni argumentos, para que sea imposible filtrar datos personales por error.
- Validación (`MethodArgumentNotValidException`, `HandlerMethodValidationException`): 400 con `errors: [{field, code}]`, `code` = nombre de la restricción (`NotBlank`, `Size`, …); nunca `rejectedValue`.
- `DataIntegrityViolationException` → 409 `CONFLICT` genérico (el mensaje de PostgreSQL incluye el valor duplicado, p. ej. un email).
- `AccessDeniedException` / `AuthenticationException` (Spring Security) **no se convierten en 500**: `handleUnexpected` las relanza para que `ExceptionTranslationFilter` y el entry point sigan respondiendo 401/403 (decisión posterior al verify, W2; cubierto por tests que comprueban que la excepción se propaga).
- Cualquier otra `Exception` → 500 `INTERNAL_ERROR` + `errorId` (UUID aleatorio). Log `ERROR` con `errorId` y **solo el nombre de la clase** de la excepción (sin mensaje ni causa, que pueden contener datos personales). Trade-off aceptado: menos información de depuración en el log; el `errorId` permite reproducir.
- Dependencia: `spring-boot-starter-validation` (prevista en C-01 D13 para C-02) para probar el caso de validación.
- **Cómo se testea sin endpoints** (desviación aplicada): `ApiExceptionHandlerTest` usa **MockMvc standalone** (`MockMvcBuilders.standaloneSetup(...).setControllerAdvice(new ApiExceptionHandler()).setValidator(...)`), sin contexto de Spring ni BD, y no `@WebMvcTest`: en Boot 4 `@WebMvcTest` vive en `spring-boot-webmvc-test`, dependencia no aprobada (regla dura 12). El controller de prueba `ProblemProbeController` está en `src/test` bajo el paquete `ar.turnosodontologia.probe`, **fuera** del paquete escaneado por la aplicación (`ar.turnosodontologia.consultorio`), para que no aparezca en los contextos completos de los IT (en Spring 7 un handler necesita `@Controller`). Limitación: standalone no usa la configuración real de Boot (Jackson, validator, escaneo de advice); conviene un IT con un endpoint real cuando C-03 exponga uno. El test de validación de parámetros asserta `getResolvedException()` para garantizar que realmente se ejerce `HandlerMethodValidationException`.

### D11 — Bootstrap del DENTIST
- **Disparo**: `auth.infrastructure.BootstrapDentistRunner implements ApplicationRunner` (corre después de Flyway y de crear el contexto). Una excepción aborta el arranque.
- **Lectura de variables**: con `Environment.getProperty("BOOTSTRAP_ADMIN_EMAIL")` / `("BOOTSTRAP_ADMIN_PASSWORD")`, **sin** mapearlas en `application.yml` (un `${VAR:}` sería un valor por defecto versionado para un secreto, contra la spec `runtime-configuration`). No se agregan a `RequiredConfiguration` porque son obligatorias solo de forma condicional.
- **Caso de uso** `auth.application.BootstrapDentist` (transaccional):
  1. `practiceId = installationPractice.id()`.
  2. Si `appUserRepository.existsByPracticeIdAndRole(practiceId, DENTIST)` → resultado `SKIPPED` (no lee ni valida variables).
  3. Si no: obtiene las credenciales por un `Supplier` (el runner las lee del `Environment`); variable ausente o en blanco → `IllegalStateException` que nombra la variable.
  4. `BootstrapCredentials.of(email, password)` valida: email con forma `local@dominio` y ≤ 254; contraseña ≥ 12 caracteres (RN-AU-03, Suposición; la política final es de C-04). El error nombra la variable, nunca el valor. `toString()` redactado (`BootstrapCredentials[email=***, password=***]`).
  5. `AppUser.newDentist(practiceId, email, passwordHasher.hash(password), clock)` con `mustChangePassword = true`; `save`. Resultado `CREATED(id)`.
- **Idempotencia**: el criterio es "existe al menos un DENTIST en el consultorio", no "existe ese email". Re-arrancar con otro email u otra contraseña no crea ni modifica nada (la contraseña se cambia por C-04, no por variables). Se recomienda en el README quitar las variables tras el primer arranque.
- **Logs**: `INFO` "Bootstrap: cuenta DENTIST creada (id=…)" o "Bootstrap omitido: ya existe una cuenta DENTIST". Nunca email ni contraseña (regla dura 6). Test con `OutputCaptureExtension`.
- **Tests IT** (implementado así): `EmbeddedPostgresSupport.Database.properties()` inyecta `BOOTSTRAP_ADMIN_EMAIL`/`PASSWORD` aleatorios por JVM en las propiedades de todo contexto que arranque sobre una base embebida (incluida la base propia de `BootstrapDentistRunnerIT`); no se versionan. `EmbeddedPostgresSupport`/`AbstractIntegrationTest` agrega `BOOTSTRAP_ADMIN_EMAIL`/`PASSWORD` generados en el test (no versionados) a las propiedades dinámicas, porque ahora toda base recién migrada exige bootstrap. Como la instancia embebida es compartida, la idempotencia se prueba invocando el runner otra vez sobre el contexto ya arrancado; los casos de variables faltantes/ inválidas se prueban como unitarios del caso de uso con *fakes*.
- **Alternativas**: comando CLI separado o script SQL manual (más pasos para el odontólogo y el hash Argon2 no se puede generar en SQL); seed en migración Flyway (dejaría el hash versionado — prohibido).

### D12 — Dependencias nuevas (regla dura 12, solo backend)
| Configuración | Coordenada | Motivo |
|---------------|------------|--------|
| implementation | `org.bouncycastle:bcprov-jdk18on:1.87` | Argon2 (D2) — **aprobada por el usuario**. Versión fijada en `build.gradle.kts` porque está fuera del BOM de Spring Boot: revisar al actualizar Boot (el BOM podría gestionarla) y vigilar CVEs |
| implementation | `org.springframework.boot:spring-boot-starter-validation` | Bean Validation (D10; prevista en C-01 D13) |
Sin dependencias de frontend.

## Risks / Trade-offs

- [`ddl-auto=validate` y columnas `text`] → Hibernate puede esperar `varchar(255)` para `String`. Mitigación: el IT de arranque lo detecta en el primer ciclo; usar `@Column(columnDefinition = "text")` o `@JdbcTypeCode(SqlTypes.VARCHAR)` sin longitud según lo que acepte la versión resuelta.
- [Estilo del generador UUIDv7 depende de la versión de Hibernate] → test explícito de `version() == 7`; confirmar con context7.
- [Argon2 con 19 MiB por hash] → el bootstrap tarda ~decenas de ms; los unitarios usan un *fake* de `PasswordHasher`; solo `Argon2PasswordHasherTest` usa el real.
- [Toda base nueva exige variables de bootstrap] → los IT existentes de C-01 necesitan las propiedades nuevas (tarea 6.1). Si se olvidan, fallan con un mensaje claro que nombra la variable.
- [Log de 500 sin mensaje de excepción] → depuración más difícil; mitigado con `errorId`. Se puede revisar cuando exista un sanitizador de logs.
- [Entidades JPA en `domain`] → acopla el dominio al modelo relacional; aceptado por simplicidad (D5), con alternativa documentada.
- [Caché de `InstallationPractice`] → un cambio de zona en caliente no se vería hasta reiniciar; C-05 debe invalidarla.
- [Unicidad global del email vs KB 04 multi-tenant] → aceptado (D6); revisar al pasar a multi-tenant.
- [El literal UUID de `practice` es igual en todas las instalaciones] → no es secreto ni se expone; en multi-tenant las filas nuevas usan UUIDv7 generados.

## Migration Plan

- Despliegue: al arrancar, Flyway aplica `V2` (como `migrator`); en la primera ejecución el bootstrap crea el DENTIST. Pasos para cada integrante: definir `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` en su `.env` local antes del primer `bootRun` (README).
- Rollback en desarrollo: no se editan migraciones aplicadas (regla dura 3). Para volver atrás localmente: `DROP DATABASE` y re-ejecutar el script de roles (C-01). En un entorno con datos, un rollback sería una migración nueva `V3__…` que revierta (no aplica todavía: no hay producción).

## Open Questions

- **`APP_TIMEZONE` vs `practice.timezone`**: KB 08 documenta `APP_TIMEZONE` y KB 04 una columna `timezone`. C-02 usa la columna como fuente única y no consume `APP_TIMEZONE`. Pendiente: quitar la variable de KB 08/`.env.example` o definir que solo inicializa la columna. No cambia specs ni tareas de C-02.
- **Nombre provisional del consultorio** (`'Consultorio'`): lo reemplaza el odontólogo en C-05 (`/api/v1/practice/settings`).
- **Largo mínimo de contraseña** del bootstrap (12, RN-AU-03 Suposición): C-04 (PA-04) puede cambiar la política; el bootstrap deberá reutilizarla.
- **Formato de 401/403** como `ProblemDetail`: lo resuelve C-04.

## UI Mockups

- Design system revision: 1
- Decision: no (no UI) — 2026-11-21. C-02 no agrega pantallas, componentes ni estilos.
- Mockups: n/a
- Approval: n/a
- Preview: n/a
- Visual verify: n/a
