# Diseño: C-01 `foundation-setup`

## Context

Ver `proposal.md` §Why. Estado actual: el repo solo contiene documentación (`knowledge-base/`, `CHANGES.md`, `design-system/` aprobado, `discovery/`), `.gitignore` y `.gitattributes`. No hay código.

Restricciones que moldean el diseño:

- Reglas duras de `CLAUDE.md`: inyección por constructor, Flyway sin `ddl-auto=update`, deny-by-default, UTC en BD, `tsconfig` strict sin `any`, sin `innerHTML`, sin dependencias de runtime en el frontend sin aprobación, sin credenciales en código ni en `application.yml`.
- KB 08 (§Patrones, §Módulos, §Estructura de directorios, §Despliegue, §Variables de entorno), KB 09 (DD-01, DD-02, DD-03, DD-08, SU-01, SU-09) y KB 04 §Preparación para multi-tenant.
- Strict TDD en apply: cada comportamiento empieza con un test que falla.
- Máquina actual del desarrollador (relevado el 2026-11-20): **JDK 17** instalado (no 21), Node 22.15, npm 10.9, Docker 29. No hay Gradle global (se usa el wrapper).

## Goals / Non-Goals

**Goals:**
- Esqueleto ejecutable y testeable de backend y frontend con las fronteras de módulos verificadas en el build.
- Estrategia de roles de BD (`migrator` / `app`) operativa en Docker Compose y en Testcontainers con el mismo script.
- Que `C-02` pueda empezar escribiendo su primer test sin tocar tooling.

**Non-Goals:**
- Tablas de negocio (`practice`, `app_user` → C-02; `audit_log` → C-03) y `practice_id` (se aplica desde C-02 en cada tabla de negocio, según KB 04).
- Autenticación, sesión, CSRF fino, cabeceras de seguridad completas (C-04).
- Manejo global de errores con `ProblemDetail` y reloj inyectable (C-02, módulo `shared`).
- Envío de emails (solo se levanta Mailpit; el starter de mail llega con el change de notificaciones).
- Servir la SPA compilada desde el backend / imagen única de producción (SU-09) y CI remoto: se difieren al change de producción (fase 8).
- Importar `design-system/tokens.css` en el frontend: se cablea en el primer change [UI].

## Decisions

Todas las decisiones marcadas como confirmables fueron aprobadas por el usuario el 2026-11-20 (Spring Boot 4.0.x, paquete `ar.turnosodontologia.consultorio`, JDK 21 vía toolchain foojay, y el resto según lo propuesto, incluida la lista de dependencias D13).

### D1 — Paquete base y groupId **(confirmado por el usuario, 2026-11-20)**
- **Decisión (Suposición)**: groupId `ar.turnosodontologia`, paquete base `ar.turnosodontologia.consultorio`, clase `ConsultorioApplication`, artefacto `consultorio`.
- **Por qué**: KB 08 fija `ar/.../consultorio` con el segmento intermedio TBD. El nombre de marca "La Muela" es provisional (`design-system/brand.md`); usarlo en el paquete obligaría a un refactor masivo si cambia. Se elige un segmento neutral alineado con el proyecto de engram.
- **Alternativas**: `ar.com.lamuela.consultorio` (acopla a marca provisional); `ar.consultorio` (demasiado genérico, riesgo de colisión).

### D2 — Versiones de plataforma **(confirmado por el usuario, 2026-11-20)**
- **Decisión**: Java 21 (toolchain de Gradle), **Spring Boot 4.0.x** (último patch al momento del apply), Gradle 9.x (wrapper), PostgreSQL 17 (imagen `postgres:17-alpine`, misma etiqueta en compose y Testcontainers), Flyway 11.x (gestionado por el BOM de Boot), Testcontainers 2.x, Node 22 LTS, TypeScript 5.x, Vite 7.x, Vitest 3.x.
- **Por qué Boot 4 y no 3.x**: SU-01 dice "Spring Boot 3.x" como supuesto no confirmado. El soporte OSS de Spring Boot 3.5 (última 3.x) terminó en junio de 2026; arrancar un producto real sobre una línea sin parches públicos es deuda desde el día 1. Boot 4 requiere Java 17+ (usamos 21) y trae starters modulares (p. ej. `spring-boot-starter-flyway`, `spring-boot-starter-webmvc`).
- **Alternativa**: Spring Boot 3.5.x si el usuario prefiere mantener SU-01 literal (cambian nombres de algunos starters: `flyway-core` en lugar de `spring-boot-starter-flyway`, `spring-boot-starter-web`). El resto del diseño no cambia.
- **Nota para apply**: confirmar versiones exactas con context7 / Maven Central al implementar; no usar versiones sin publicar.

### D3 — JDK 21 en la máquina **(confirmado por el usuario, 2026-11-20)**
- **Decisión**: `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }` + plugin `org.gradle.toolchains.foojay-resolver-convention` en `settings.gradle.kts`, que descarga un JDK 21 automáticamente si no está instalado. Gradle 9 corre sobre el JDK 17 existente.
- **Alternativa**: que el usuario instale JDK 21 (Temurin) y quitar el plugin foojay. Ambas funcionan; foojay hace el entorno reproducible.

### D4 — Un solo módulo Gradle con paquetes (no multi-módulo)
- **Decisión**: `backend/` es un único proyecto Gradle; los módulos de negocio son paquetes de primer nivel bajo el paquete base, como en KB 08 §Estructura.
- **Por qué**: menor costo de build y configuración para un equipo chico; las fronteras se verifican con D5. Separar en subproyectos Gradle se puede hacer después sin cambiar paquetes.
- **Alternativa**: multi-módulo Gradle (un subproyecto por módulo) — fronteras impuestas por el compilador, pero ~8×4 configuraciones y builds más lentos.

### D5 — ArchUnit (no Spring Modulith) para verificar fronteras **(confirmado por el usuario, 2026-11-20)**
- **Decisión**: ArchUnit (`com.tngtech.archunit:archunit-junit5`, solo `testImplementation`) con reglas explícitas en `ModuleBoundariesTest`:
  1. Cada módulo de negocio solo accede a otro módulo vía `..<modulo>.api..`; `shared` es libre.
  2. Grafo permitido de KB 08 (`reservas` → `agenda`, `pacientes`, `notificaciones`; `historiaclinica` → `pacientes`, `auditoria`; `auth`/`auditoria` sin dependencias de negocio), sin ciclos (`slices().should().beFreeOfCycles()`).
  3. Capas: `domain` no depende de `api`/`application`/`infrastructure` ni de `org.springframework.web..`/`org.springframework.jdbc..`/`java.sql..`; `application` no depende de `api` ni de `infrastructure`.
  - Nota: JPA en entidades de dominio se decide en C-02. En C-01 la regla prohíbe `org.springframework.web..` y `org.springframework.jdbc..` en `domain`; si C-02 decide entidades JPA separadas del dominio, se agrega `jakarta.persistence..` a la lista.
- **Cómo se testea la regla (TDD)**: las reglas se construyen como funciones que reciben `JavaClasses`. Tests unitarios importan paquetes *fixture* bajo `src/test/java/.../archfixture/{ok,violacion_*}/` (clases de prueba que imitan la estructura de módulos) y verifican que cada regla **falla** con la violación y **pasa** con la estructura correcta. Luego un test aplica las mismas reglas al código de producción.
- **Paquetes vacíos**: cada `modulo/capa` lleva un `package-info.java` para que exista en el classpath y la regla de "estructura completa" pueda verificarlo.
- **Por qué no Spring Modulith**: Modulith trata los subpaquetes de un módulo como internos y exige `@NamedInterface` en cada `api`, no verifica capas internas (habría que sumar ArchUnit igual) y agrega dependencias al classpath principal. ArchUnit es solo de test y expresa exactamente las reglas de KB 08.
- **Alternativa**: Spring Modulith (`spring-modulith-starter-test` + `@NamedInterface("api")`), útil si más adelante se quieren eventos de módulo o documentación generada.

### D6 — Roles de BD: creación fuera de Flyway, privilegios dentro de Flyway
- **Creación de roles y base** (no versionable con contraseñas): `infra/postgres/init/01-roles.sh`, ejecutado por la imagen oficial de PostgreSQL en `/docker-entrypoint-initdb.d/` al inicializar un volumen vacío. Lee `DB_MIGRATION_USER/PASSWORD`, `DB_USER/PASSWORD` y `DB_NAME` del entorno y:
  - crea `migrator` (`LOGIN`, sin `SUPERUSER`) y `app` (`LOGIN`, `NOSUPERUSER NOCREATEDB NOCREATEROLE`);
  - crea la base `consultorio` con `OWNER migrator`; `REVOKE ALL ON DATABASE ... FROM PUBLIC`; `GRANT CONNECT` a `app`;
  - `REVOKE CREATE ON SCHEMA public FROM PUBLIC`; el esquema `public` pasa a ser de `migrator`; `GRANT USAGE ON SCHEMA public TO app`;
  - `CREATE EXTENSION btree_gist` **no** se hace aquí (ver V1).
- **`V1__baseline.sql`** (ejecutada por `migrator`), con el nombre del rol de aplicación por placeholder Flyway `${app_role}` (configurado desde `DB_USER`):
  - `CREATE EXTENSION IF NOT EXISTS btree_gist;` — `btree_gist` es extensión *trusted* desde PG13, la puede crear el dueño de la base sin superusuario.
  - `ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_role};`
  - `ALTER DEFAULT PRIVILEGES ... GRANT USAGE, SELECT ON SEQUENCES TO ${app_role};`
  - Comentario documentando el patrón para tablas inmutables: `REVOKE UPDATE, DELETE ON <tabla> FROM ${app_role};` en la migración que crea la tabla (C-03 `audit_log`, changes de HC).
- **Spring**: `spring.datasource.*` usa `DB_USER/DB_PASSWORD`; `spring.flyway.user/password` usan `DB_MIGRATION_USER/DB_MIGRATION_PASSWORD` (Flyway abre su propia conexión con esas credenciales). `spring.flyway.placeholders.app_role=${DB_USER}`.
- **Por qué**: las contraseñas nunca quedan en una migración versionada; los privilegios sí quedan versionados y se aplican igual en cualquier entorno donde existan los roles.
- **Riesgo**: en un hosting gestionado sin `docker-entrypoint-initdb.d`, el script se corre a mano una vez (documentado en el README del backend). Se resolverá con PA-03.

### D7 — Testcontainers con los dos roles **(confirmado por el usuario, 2026-11-20)**
- **Decisión**: un `@TestConfiguration` compartido (`PostgresTestcontainersConfig`) crea `PostgreSQLContainer("postgres:17-alpine")`, monta el **mismo** `infra/postgres/init/01-roles.sh` en `/docker-entrypoint-initdb.d/` y le pasa por `withEnv` credenciales de test generadas en el test (no versionadas como reales). Las propiedades se inyectan con `@DynamicPropertySource`: datasource → `app`, Flyway → `migrator`.
- **Por qué no `@ServiceConnection`** (preferido por el skill `spring-boot-test-patterns`): `@ServiceConnection` fuerza las credenciales del superusuario del contenedor para el datasource, y justamente queremos probar que la app corre **sin** DDL. Es una excepción deliberada y documentada.
- **Alternativa**: `@ServiceConnection` + `withInitScript` SQL con contraseñas fijas de test; más simple, pero duplica el script de roles y no prueba el script real de compose.
- Reutilización de contenedor: un contenedor estático por JVM (patrón singleton) para que los IT no levanten un Postgres por clase; sin `@DirtiesContext`.

### D8 — Separación de tests unitarios e integración
- **Decisión**: un solo source set `src/test`; convención de nombres `*Test` (unitarios, sin Docker) y `*IT` (integración, Testcontainers). La tarea `test` excluye `**/*IT.class`; una tarea `integrationTest` (tipo `Test`) incluye solo `**/*IT.class`; `check` depende de ambas. JaCoCo agrega ambos reportes (`jacocoTestReport` sobre los dos `.exec`).
- **Comandos**: `./gradlew test` (rápido, incluye ArchUnit), `./gradlew integrationTest` (requiere Docker), `./gradlew check` (todo).
- **Por qué**: el skill `java-junit` pide separar `*Test`/`*IT`; mantener un solo source set evita configuración extra de Gradle.
- **Alternativa**: que `./gradlew test` corra todo (más simple, pero exige Docker hasta para un cambio de dominio).

### D9 — Seguridad mínima deny-by-default en C-01
- **Decisión**: incluir `spring-boot-starter-security` ya en C-01 con un `SecurityFilterChain` en `auth.infrastructure` (ver nota): `GET /actuator/health` → `permitAll`; todo lo demás → `denyAll`. Para un cliente anónimo la denegación pasa por `authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED))`, que responde 401 sin `WWW-Authenticate: Basic`; `formLogin`, `httpBasic` y `logout` deshabilitados. CSRF queda con la configuración por defecto (un `POST` anónimo recibe 401 o 403; C-04 define CSRF para la SPA). Se excluye `UserDetailsServiceAutoConfiguration` para que no se genere ni se loguee una contraseña.
  - Nota: se ubica en `auth.infrastructure` porque `C-04` lo reemplaza/extiende allí.
- **Actuator**: `management.endpoints.web.exposure.include=health`, `management.endpoint.health.show-details=never`, `show-components=never`.
- **Por qué**: regla dura 4 y skill `springboot-security`. Sin security, el starter web dejaría cualquier controller futuro abierto por omisión.

### D10 — Configuración y UTC
- `application.yml`: `spring.datasource.url=${DB_URL}`, `username=${DB_USER}`, `password=${DB_PASSWORD}` (sin defaults); Flyway con `${DB_MIGRATION_USER}`/`${DB_MIGRATION_PASSWORD}`; `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`; `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`; `server.error.include-stacktrace=never`.
- `application-dev.yml`: solo valores no sensibles (logging más verboso, `DB_URL` por defecto `jdbc:postgresql://localhost:5432/consultorio`). Nunca contraseñas.
- **UTC**: `ConsultorioApplication.main` fija `TimeZone.setDefault(UTC)` antes de arrancar Spring; además `-Duser.timezone=UTC` en `bootRun`, en las tareas de test y en el `ENTRYPOINT` del Dockerfile. pgJDBC toma la zona de la JVM para la sesión, por eso `SHOW TimeZone` devuelve `UTC`.
- Las variables de KB 08 que C-01 no consume (mail, rate limit, tokens, bootstrap) quedan en `.env.example` con un comentario que indica qué change las usa; no se mapean todavía en `application.yml` para no declarar configuración muerta.
- **JPA en C-01**: se incluye `spring-boot-starter-data-jpa` ya en C-01 para fijar `ddl-auto=validate` y `open-in-view=false` desde el principio (sin entidades todavía). Alternativa: `starter-jdbc` en C-01 y JPA en C-02.

### D11 — Docker Compose y Dockerfile
- `docker-compose.yml` (raíz): servicios `db` (`postgres:17-alpine`, volumen nombrado, healthcheck `pg_isready`, init script montado), `mailpit` (`axllent/mailpit`, SMTP 1025, UI 8025), `app` (build `backend/Dockerfile`, `env_file: .env` obligatorio, `depends_on: db: condition: service_healthy`). Todos los puertos `127.0.0.1:<puerto>:<puerto>`.
- `backend/Dockerfile` multi-stage: etapa build con JDK 21 ejecutando `./gradlew bootJar -x test`; etapa runtime con JRE 21, usuario no root, `-Duser.timezone=UTC`. La SPA **no** se incluye todavía (Non-Goal).
- El usuario superusuario del contenedor de Postgres (`POSTGRES_PASSWORD`) también viene de `.env`; se agregan `POSTGRES_PASSWORD` y `DB_NAME` (default `consultorio`, usado por el script de roles) a `.env.example` como variables de infraestructura local, además de las de KB 08.

### D12 — Frontend
- `frontend/` con `package.json` (`"type": "module"`, `"private": true`, `engines.node >=22`), `.nvmrc` = `22`.
- Scripts: `dev` (`vite`), `build` (`tsc --noEmit && vite build`), `typecheck` (`tsc --noEmit`), `test` (`vitest run`), `test:watch` (`vitest`).
- `tsconfig.json`: `strict`, `noUncheckedIndexedAccess`, `noImplicitOverride`, `noFallthroughCasesInSwitch`, `exactOptionalPropertyTypes`, `verbatimModuleSyntax`, `isolatedModules`, `target`/`lib` ES2022 + DOM, `moduleResolution: bundler`.
- `vite.config.ts`: `server.proxy` de `/api` y `/actuator` a `http://localhost:8080` (sobrescribible con la variable de entorno de desarrollo `BACKEND_URL`); configuración de Vitest con `environment: 'jsdom'`, `restoreMocks: true`.
- `src/main.ts` exporta `mountApp(doc: Document): HTMLElement` (testeable) y lo invoca al cargar; crea `<div data-app-root>` con `document.createElement`, idempotente. Estructura de carpetas de KB 08 (`router/`, `core/`, `features/`, `shared/`, `styles/`) se crea solo cuando haya código (no carpetas vacías con `.gitkeep`) — excepto lo que use el smoke test.
- **Cómo se sirve la SPA en dev (confirmado por el usuario, 2026-11-20)**: Vite en `http://localhost:5173` en el host (fuera de Docker) con proxy al backend; el backend corre en compose o con `./gradlew bootRun`. Alternativa: servicio `frontend` en compose con `node:22` — más lento en Windows por el bind mount de OneDrive, por eso no se recomienda.

### D13 — Dependencias (aprobación explícita, regla dura 12) **(confirmado por el usuario, 2026-11-20)**

**Backend — plugins Gradle**
| Plugin | Uso |
|--------|-----|
| `org.springframework.boot` | build y empaquetado de Spring Boot |
| `io.spring.dependency-management` | BOM de versiones de Boot |
| `jacoco` (core Gradle) | cobertura |
| `org.gradle.toolchains.foojay-resolver-convention` (settings) | descarga de JDK 21 (D3) |

**Backend — dependencias** (versiones del BOM de Boot salvo indicación)
| Configuración | Coordenada | Para qué |
|---------------|------------|----------|
| implementation | `org.springframework.boot:spring-boot-starter-webmvc` (`-web` en Boot 3) | API REST |
| implementation | `org.springframework.boot:spring-boot-starter-actuator` | `/actuator/health` |
| implementation | `org.springframework.boot:spring-boot-starter-security` | deny-by-default (D9) |
| implementation | `org.springframework.boot:spring-boot-starter-data-jpa` | JPA (D10) |
| implementation | `org.springframework.boot:spring-boot-starter-flyway` (`org.flywaydb:flyway-core` en Boot 3) | migraciones |
| implementation | `org.flywaydb:flyway-database-postgresql` | soporte PostgreSQL de Flyway |
| runtimeOnly | `org.postgresql:postgresql` | driver JDBC |
| testImplementation | `org.springframework.boot:spring-boot-starter-test` | JUnit 5, AssertJ, Mockito, MockMvc |
| testImplementation | `org.springframework.boot:spring-boot-testcontainers` | integración Boot ↔ Testcontainers |
| testImplementation | `org.testcontainers:testcontainers-postgresql` (+ `testcontainers-junit-jupiter`) | Postgres real en IT |
| testImplementation | `org.springframework.security:spring-security-test` | tests de la cadena de seguridad |
| testImplementation | `com.tngtech.archunit:archunit-junit5` (versión explícita, no está en el BOM) | D5 |
| testRuntimeOnly | `org.junit.platform:junit-platform-launcher` | requerido por Gradle 9 |

No se incluyen todavía: `spring-boot-starter-validation` (C-02, primeros DTOs), `spring-boot-starter-mail` (notificaciones), Lombok (no se usa: records + constructores).

**Frontend — `devDependencies` únicamente** (cero `dependencies` de runtime)
| Paquete | Para qué |
|---------|----------|
| `typescript` | compilador / chequeo de tipos |
| `vite` | dev server y bundler |
| `vitest` | runner de tests |
| `jsdom` | entorno DOM para tests |

Sin ESLint/Prettier en C-01 (se pueden sumar con aprobación en un change de tooling).

## Risks / Trade-offs

- [JDK 17 instalado y toolchain 21] → foojay descarga el JDK la primera vez (requiere red); documentado en el README del backend. Si se rechaza D3, instalar Temurin 21.
- [Spring Boot 4 es más nuevo que lo asumido en la KB] → los skills instalados tienen ejemplos de Boot 3; los cambios relevantes (nombres de starters, Jackson 3, Testcontainers 2) se listan en D2/D13. Alternativa 3.5.x disponible.
- [OneDrive sincroniza `node_modules/`, `build/` y `.gradle/`] → muchos archivos pequeños pueden ralentizar o bloquear builds. Mitigación: están en `.gitignore` pero OneDrive no lo respeta; recomendar marcar esas carpetas como "solo en línea"/excluirlas o mover el repo fuera de OneDrive (no bloqueante).
- [Testcontainers requiere Docker en ejecución] → los `*IT` quedan fuera de `./gradlew test` (D8); `check` los incluye.
- [Fin de línea en scripts] → `01-roles.sh` y `gradlew` deben quedar en LF aunque se editen en Windows; `.gitattributes` ya fuerza `eol=lf` y se agrega una regla explícita `*.sh text eol=lf`.
- [El script de roles solo corre con volumen vacío] → si se cambian credenciales hay que recrear el volumen (`docker compose down -v`); documentado.
- [`ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER`] → solo aplica a objetos creados por `migrator`; si alguien crea tablas con otro rol, `app` no tendrá permisos. Mitigación: Flyway siempre corre como `migrator` (D6) y un IT lo verifica.
- [Paquetes con solo `package-info.java`] → ruido inicial en el árbol; a cambio, la estructura de KB 08 queda verificada desde el día 1.

## Migration Plan

No hay datos ni despliegue previos. Rollback = eliminar `backend/`, `frontend/`, `infra/`, `docker-compose.yml`, `.env.example` y revertir las líneas agregadas a `.gitignore`/`.gitattributes`. El volumen local de Postgres se borra con `docker compose down -v`.

## Open Questions

- Proveedor de hosting (PA-03): define si el script de roles se ejecuta por `initdb.d` o manualmente; no cambia este change.
- Momento de incluir la SPA compilada dentro de la imagen del backend (SU-09): change de producción.

## UI Mockups

- Design system revision: 1
- Decision: no (no UI) — 2026-11-20. C-01 no agrega pantallas ni estilos; la SPA solo monta un contenedor raíz vacío.
- Mockups: n/a
- Approval: n/a
- Preview: n/a
- Visual verify: n/a
