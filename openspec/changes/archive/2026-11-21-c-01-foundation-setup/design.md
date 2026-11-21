# Diseño: C-01 `foundation-setup`

## Context

Ver `proposal.md` §Why. Estado actual: el repo solo contiene documentación (`knowledge-base/`, `CHANGES.md`, `design-system/` aprobado, `discovery/`), `.gitignore` y `.gitattributes`. No hay código aprobado (hay borradores de un apply interrumpido; ver tarea 0.1).

Restricciones que moldean el diseño:

- Reglas duras de `CLAUDE.md`: inyección por constructor, Flyway sin `ddl-auto=update`, deny-by-default, UTC en BD, `tsconfig` strict sin `any`, sin `innerHTML`, sin dependencias de runtime en el frontend sin aprobación, sin credenciales en código ni en `application.yml`.
- KB 08 (§Patrones, §Módulos, §Estructura de directorios, §Despliegue, §Variables de entorno), KB 09 (DD-01, DD-02, DD-03, DD-08, DD-10, SU-01, SU-09) y KB 04 §Preparación para multi-tenant.
- **DD-10**: el proyecto se ejecuta localmente con PostgreSQL instalado de forma nativa en cada equipo; los tests usan PostgreSQL embebido (zonky). Ninguna etapa requiere herramientas de virtualización.
- Strict TDD en apply: cada comportamiento empieza con un test que falla.
- Máquina actual del desarrollador (relevado el 2026-11-20): **JDK 17** instalado (no 21), Node 22.15, npm 10.9. No hay Gradle global (se usa el wrapper). Varios integrantes del equipo solo cuentan con JDK, Node y PostgreSQL nativo.

## Goals / Non-Goals

**Goals:**
- Esqueleto ejecutable y testeable de backend y frontend con las fronteras de módulos verificadas en el build.
- Estrategia de roles de BD (`migrator` / `app`) operativa en el PostgreSQL instalado localmente y en el PostgreSQL embebido de los tests con el **mismo** script SQL.
- `./gradlew test` y `./gradlew integrationTest` ejecutables en cualquier máquina y en CI sin instalar nada más que el JDK (o dejar que la toolchain lo descargue).
- Que `C-02` pueda empezar escribiendo su primer test sin tocar tooling.

**Non-Goals:**
- Tablas de negocio (`practice`, `app_user` → C-02; `audit_log` → C-03) y `practice_id` (se aplica desde C-02 en cada tabla de negocio, según KB 04).
- Autenticación, sesión, CSRF fino, cabeceras de seguridad completas (C-04).
- Manejo global de errores con `ProblemDetail` y reloj inyectable (C-02, módulo `shared`).
- Envío de emails: C-01 no envía ni configura email. El modo "email al log" de desarrollo se define en C-13 (DD-10).
- Estrategia de despliegue, servir la SPA compilada desde el backend (SU-09) y CI remoto: se difieren al change de producción (C-27, TBD; DD-10).
- Importar `design-system/tokens.css` en el frontend: se cablea en el primer change [UI].

## Decisions

Todas las decisiones marcadas como confirmables fueron aprobadas por el usuario el 2026-11-20 (Spring Boot 4.0.x, paquete `ar.turnosodontologia.consultorio`, JDK 21 vía toolchain foojay, y el resto según lo propuesto, incluida la lista de dependencias D13). El 2026-11-21 el usuario revisó D2, D6, D7, D8, D10, D11, D12 y D13 (ver §Historial de cambios).

### D1 — Paquete base y groupId **(confirmado por el usuario, 2026-11-20)**
- **Decisión (Suposición)**: groupId `ar.turnosodontologia`, paquete base `ar.turnosodontologia.consultorio`, clase `ConsultorioApplication`, artefacto `consultorio`.
- **Por qué**: KB 08 fija `ar/.../consultorio` con el segmento intermedio TBD. El nombre de marca "La Muela" es provisional (`design-system/brand.md`); usarlo en el paquete obligaría a un refactor masivo si cambia. Se elige un segmento neutral alineado con el proyecto de engram.
- **Alternativas**: `ar.com.lamuela.consultorio` (acopla a marca provisional); `ar.consultorio` (demasiado genérico, riesgo de colisión).

### D2 — Versiones de plataforma **(confirmado por el usuario, 2026-11-20; revisado 2026-11-21 por decisión del usuario: sin Docker)**
- **Decisión**: Java 21 (toolchain de Gradle), **Spring Boot 4.0.x** (último patch al momento del apply), Gradle 9.x (wrapper), **PostgreSQL 17** (instalado de forma nativa en desarrollo; binarios embebidos zonky `17.12.0` en los tests, misma versión mayor), Flyway 11.x (gestionado por el BOM de Boot), zonky `embedded-postgres` 2.2.2, Node 22 LTS, TypeScript 5.x, Vite 7.x, Vitest 3.x.
- **Por qué Boot 4 y no 3.x**: SU-01 dice "Spring Boot 3.x" como supuesto no confirmado. El soporte OSS de Spring Boot 3.5 (última 3.x) terminó en junio de 2026; arrancar un producto real sobre una línea sin parches públicos es deuda desde el día 1. Boot 4 requiere Java 17+ (usamos 21) y trae starters modulares (p. ej. `spring-boot-starter-flyway`, `spring-boot-starter-webmvc`).
- **Por qué PostgreSQL 17 y no 18/19**: zonky publica binarios 17.x, 18.x y 19.0 (ver D13), pero 17 es la mayor más madura con instalador oficial de Windows estable; se fija la misma mayor en el instalador local y en los tests para que lo probado coincida con lo ejecutado. Subir de mayor es cambiar la versión del BOM y reinstalar localmente.
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
- **Decisión**: ArchUnit (`com.tngtech.archunit:archunit-junit5`, solo `testImplementation`) con reglas explícitas en `ModuleBoundaries` (reglas reutilizables), verificadas contra fixtures en `ModuleBoundariesRulesTest` y aplicadas al código de producción en `ProductionArchitectureTest` (nombres finales de la implementación; el plan original los agrupaba como `ModuleBoundariesTest`):
  1. Cada módulo de negocio solo accede a otro módulo vía `..<modulo>.api..`; `shared` es libre.
  2. Grafo permitido de KB 08 (`reservas` → `agenda`, `pacientes`, `notificaciones`; `historiaclinica` → `pacientes`, `auditoria`; `auth`/`auditoria` sin dependencias de negocio), sin ciclos (`slices().should().beFreeOfCycles()`).
  3. Capas: `domain` no depende de `api`/`application`/`infrastructure` ni de `org.springframework.web..`/`org.springframework.jdbc..`/`java.sql..`; `application` no depende de `api` ni de `infrastructure`.
  - Nota: JPA en entidades de dominio se decide en C-02. En C-01 la regla prohíbe `org.springframework.web..` y `org.springframework.jdbc..` en `domain`; si C-02 decide entidades JPA separadas del dominio, se agrega `jakarta.persistence..` a la lista.
- **Cómo se testea la regla (TDD)**: las reglas se construyen como funciones que reciben `JavaClasses`. Tests unitarios importan paquetes *fixture* bajo `src/test/java/.../archfixture/{ok,violacion_*}/` (clases de prueba que imitan la estructura de módulos) y verifican que cada regla **falla** con la violación y **pasa** con la estructura correcta. Luego un test aplica las mismas reglas al código de producción.
- **Paquetes vacíos**: cada `modulo/capa` lleva un `package-info.java` para que exista en el classpath y la regla de "estructura completa" pueda verificarlo. Para que `javac` genere la clase de esos `package-info.java` sin anotaciones, `build.gradle.kts` agrega el flag de compilador `-Xpkginfo:always` (no es una dependencia; complementa D13).
- **Por qué no Spring Modulith**: Modulith trata los subpaquetes de un módulo como internos y exige `@NamedInterface` en cada `api`, no verifica capas internas (habría que sumar ArchUnit igual) y agrega dependencias al classpath principal. ArchUnit es solo de test y expresa exactamente las reglas de KB 08.
- **Alternativa**: Spring Modulith (`spring-modulith-starter-test` + `@NamedInterface("api")`), útil si más adelante se quieren eventos de módulo o documentación generada.

### D6 — Roles de BD: creación por script SQL versionado, privilegios dentro de Flyway **(revisado 2026-11-21 por decisión del usuario: sin Docker)**
- **Creación de base y roles**: `infra/postgres/init/01-roles.sql`, un script SQL **versionado y sin credenciales**, que cada integrante ejecuta **una vez** como superusuario (`postgres`) con `psql` (o con la "PSQL Tool" de pgAdmin, que admite metacomandos; no la "Query Tool"). Recibe los valores por variables de psql (`-v`): `db_name`, `migrator_user`, `migrator_password`, `app_user`, `app_password`, que salen de las variables documentadas en `.env.example` (`DB_NAME`, `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD`, `DB_USER`, `DB_PASSWORD`). El README muestra cómo pasarlas desde PowerShell (`-v "migrator_password=$env:DB_MIGRATION_PASSWORD"`) y desde bash, siempre con `-v ON_ERROR_STOP=1`.
  - Primera sentencia: un `SELECT` que referencia todas las variables. Si falta alguna, psql deja la referencia sin sustituir, la sentencia falla por sintaxis y, con `ON_ERROR_STOP=1`, el script se detiene **antes** de crear nada.
  - Crea `migrator` (`LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE`) y `app` (`LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE`); si ya existen, actualiza su contraseña con `ALTER ROLE ... PASSWORD` (permite rotar credenciales sin borrar la base).
  - Crea la base `consultorio` con `OWNER migrator` solo si no existe; `REVOKE ALL ON DATABASE ... FROM PUBLIC`; `GRANT CONNECT` a `app`.
  - `\connect :"db_name"`: `REVOKE CREATE ON SCHEMA public FROM PUBLIC`; `ALTER SCHEMA public OWNER TO migrator`; `GRANT USAGE ON SCHEMA public TO app`.
  - **Idempotente**: cada sentencia condicional se arma con `SELECT format(...) WHERE NOT EXISTS (...)` + `\gexec` (necesario porque `CREATE DATABASE` no puede ir dentro de un bloque `DO` y psql no interpola variables dentro de `$$`). Re-ejecutarlo no falla ni duplica nada.
  - **Dialecto acotado** (para poder ejecutarlo también desde los tests, D7): comentarios `--`, sentencias terminadas en `;`, sentencias `SELECT ...` terminadas en `\gexec`, el metacomando `\connect :"db_name"` y referencias `:'var'` / `:"var"`. Ningún otro metacomando.
  - `CREATE EXTENSION btree_gist` **no** se hace aquí (ver V1).
- **`V1__baseline.sql`** (ejecutada por `migrator`), con el nombre del rol de aplicación por placeholder Flyway `${app_role}` (configurado desde `DB_USER`):
  - `CREATE EXTENSION IF NOT EXISTS btree_gist;` — `btree_gist` es extensión *trusted* desde PG13, la puede crear el dueño de la base sin superusuario.
  - `ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_role};`
  - `ALTER DEFAULT PRIVILEGES ... GRANT USAGE, SELECT ON SEQUENCES TO ${app_role};`
  - Comentario documentando el patrón para tablas inmutables: `REVOKE UPDATE, DELETE ON <tabla> FROM ${app_role};` en la migración que crea la tabla (C-03 `audit_log`, changes de HC).
- **Spring**: `spring.datasource.*` usa `DB_USER/DB_PASSWORD`; `spring.flyway.user/password` usan `DB_MIGRATION_USER/DB_MIGRATION_PASSWORD` (Flyway abre su propia conexión con esas credenciales). `spring.flyway.placeholders.app_role=${DB_USER}`.
- **Por qué**: las contraseñas nunca quedan en un archivo versionado; los privilegios sí quedan versionados y se aplican igual en cualquier entorno donde existan los roles. Un script SQL (no bash) corre igual en Windows, macOS y Linux, desde psql o pgAdmin.
- **Alternativas**: (a) script con contraseñas fijas de desarrollo — más simple, pero deja credenciales versionadas (regla dura 15); (b) que cada integrante cree roles a mano en pgAdmin — no reproducible ni testeable.
- **Riesgo**: en el hosting definitivo (PA-03) el script se corre igual, una vez, con las credenciales de ese entorno.

### D7 — PostgreSQL embebido (zonky) con los dos roles **(revisado 2026-11-21 por decisión del usuario: sin Docker)**
- **Decisión**: los `*IT` usan `io.zonky.test:embedded-postgres` (solo de test), que obtiene como dependencia Maven los binarios de PostgreSQL de la plataforma y levanta un PostgreSQL **real** temporal dentro del proceso de test (directorio de datos temporal, puerto libre aleatorio). Se importa el BOM `embedded-postgres-binaries-bom` 17.12.0 para usar PostgreSQL 17 (sin el BOM, `embedded-postgres` 2.2.2 trae por defecto binarios 14.22).
- **Soporte de test** (bajo `src/test/java/.../support/`):
  - `RolesScript`: lee `infra/postgres/init/01-roles.sql`, sustituye `:'var'` (literal) y `:"var"` (identificador) con escape correcto, falla con un error que nombra la variable si falta alguna, ejecuta `\gexec` (ejecuta cada fila devuelta como sentencia) y `\connect` (abre una conexión a la base indicada). Es un intérprete del dialecto acotado de D6, con tests unitarios propios.
  - `EmbeddedPostgresSupport`: instancia **singleton por JVM** (sin `@DirtiesContext`); al iniciar, aplica `RolesScript` como superusuario con credenciales generadas en el test (no versionadas) y expone las propiedades vía `@DynamicPropertySource`: datasource → `app`, Flyway → `migrator`. initdb con codificación `UTF8` y locale `C` para no depender del idioma del sistema operativo.
- **Por qué**: mantiene la intención del D7 original: probar que la app corre **sin** DDL y que el script real de roles funciona, con un PostgreSQL real, en cualquier máquina y en CI.
- **Por qué no H2**: la restricción de exclusión requiere PostgreSQL real (DD-10); H2 sigue prohibido.
- **Por qué no `embedded-database-spring-test` (`@AutoConfigureEmbeddedDatabase`)**: reemplaza el `DataSource` por uno de superusuario y crea bases por test; ocultaría justamente la separación de roles que queremos probar.

### D8 — Separación de tests unitarios e integración **(revisado 2026-11-21 por decisión del usuario: sin Docker)**
- **Decisión**: un solo source set `src/test`; convención de nombres `*Test` (unitarios, sin base de datos) y `*IT` (integración, PostgreSQL embebido). La tarea `test` excluye `**/*IT.class`; una tarea `integrationTest` (tipo `Test`) incluye solo `**/*IT.class`; `check` depende de ambas. JaCoCo agrega ambos reportes (`jacocoTestReport` sobre los dos `.exec`).
- **Comandos**: `./gradlew test` (rápido, incluye ArchUnit), `./gradlew integrationTest` (más lento: arranca PostgreSQL embebido; no requiere instalar nada), `./gradlew check` (todo). Ambos corren en cualquier máquina y en CI.
- **Por qué**: el skill `java-junit` pide separar `*Test`/`*IT`; los IT son más lentos (initdb + arranque), así que conviene poder correr solo los unitarios en cada ciclo TDD. Mantener un solo source set evita configuración extra de Gradle.
- **Alternativa**: que `./gradlew test` corra todo (más simple, pero más lento en cada ciclo TDD de dominio).

### D9 — Seguridad mínima deny-by-default en C-01
- **Decisión**: incluir `spring-boot-starter-security` ya en C-01 con un `SecurityFilterChain` en `auth.infrastructure` (ver nota): `GET /actuator/health` → `permitAll`; todo lo demás → `denyAll`. Para un cliente anónimo la denegación pasa por `authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED))`, que responde 401 sin `WWW-Authenticate: Basic`; `formLogin`, `httpBasic` y `logout` deshabilitados. CSRF queda con la configuración por defecto (un `POST` anónimo recibe 401 o 403; C-04 define CSRF para la SPA). Se excluye `UserDetailsServiceAutoConfiguration` para que no se genere ni se loguee una contraseña.
  - Nota: se ubica en `auth.infrastructure` porque `C-04` lo reemplaza/extiende allí.
- **Actuator**: `management.endpoints.web.exposure.include=health`, `management.endpoint.health.show-details=never`, `show-components=never`.
- **Por qué**: regla dura 4 y skill `springboot-security`. Sin security, el starter web dejaría cualquier controller futuro abierto por omisión.

### D10 — Configuración y UTC **(revisado 2026-11-21 por decisión del usuario: sin Docker)**
- `application.yml`: `spring.datasource.url=${DB_URL}`, `username=${DB_USER}`, `password=${DB_PASSWORD}` (sin defaults); Flyway con `${DB_MIGRATION_USER}`/`${DB_MIGRATION_PASSWORD}`; `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`; `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`; `server.error.include-stacktrace=never`.
- `application-dev.yml`: solo valores no sensibles (logging más verboso, `DB_URL` por defecto `jdbc:postgresql://localhost:5432/consultorio`). Nunca contraseñas.
- **UTC**: `ConsultorioApplication.main` fija `TimeZone.setDefault(UTC)` antes de arrancar Spring; además `-Duser.timezone=UTC` en `bootRun` y en las tareas de test. pgJDBC toma la zona de la JVM para la sesión, por eso `SHOW TimeZone` devuelve `UTC`.
- Las variables de KB 08 que C-01 no consume (mail, rate limit, tokens, bootstrap) quedan en `.env.example` con un comentario que indica qué change las usa; no se mapean todavía en `application.yml` para no declarar configuración muerta. Se agrega `DB_NAME` (default `consultorio`, usado por el script de roles). No hay variables de superusuario de infraestructura ni de servidor de email de prueba.
- **Cómo recibe Spring las variables en local**: Spring Boot no lee `.env` por sí solo. El README documenta, para Windows: (a) cargar `.env` en la sesión de PowerShell con un one-liner (`Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }`) antes de `./gradlew bootRun`; (b) en IntelliJ IDEA, la run configuration con "Environment variables"; (c) en VS Code, `envFile` en `launch.json`. Para macOS/Linux: `set -a; . ./.env; set +a`. `.env` nunca se versiona.
- **JPA en C-01**: se incluye `spring-boot-starter-data-jpa` ya en C-01 para fijar `ddl-auto=validate` y `open-in-view=false` desde el principio (sin entidades todavía). Alternativa: `starter-jdbc` en C-01 y JPA en C-02.

### D11 — Empaquetado y despliegue **(revisado 2026-11-21 por decisión del usuario: sin Docker)**
- **Decisión**: C-01 no define empaquetado de despliegue. El artefacto es el `bootJar` estándar de Gradle (`./gradlew bootJar`), ejecutable con `java -Duser.timezone=UTC -jar`. La estrategia de despliegue (y si la SPA se sirve desde el backend, SU-09) se define en C-27 (TBD, depende de PA-03).
- **Eliminado respecto de la versión 2026-11-20**: ver §Historial de cambios.

### D12 — Frontend **(revisado 2026-11-21 por decisión del usuario: sin Docker)**
- `frontend/` con `package.json` (`"type": "module"`, `"private": true`, `engines.node >=22`), `.nvmrc` = `22`.
- Scripts: `dev` (`vite`), `build` (`tsc --noEmit && vite build`), `typecheck` (`tsc --noEmit`), `test` (`vitest run`), `test:watch` (`vitest`).
- `tsconfig.json`: `strict`, `noUncheckedIndexedAccess`, `noImplicitOverride`, `noFallthroughCasesInSwitch`, `exactOptionalPropertyTypes`, `verbatimModuleSyntax`, `isolatedModules`, `target`/`lib` ES2022 + DOM, `moduleResolution: bundler`.
- `vite.config.ts`: `server.proxy` de `/api` y `/actuator` a `http://localhost:8080` (sobrescribible con la variable de entorno de desarrollo `BACKEND_URL`); configuración de Vitest con `environment: 'jsdom'`, `restoreMocks: true`.
- `src/main.ts` exporta `mountApp(doc: Document): HTMLElement` (testeable) y lo invoca al cargar; crea `<div data-app-root>` con `document.createElement`, idempotente. Estructura de carpetas de KB 08 (`router/`, `core/`, `features/`, `shared/`, `styles/`) se crea solo cuando haya código (no carpetas vacías con `.gitkeep`) — excepto lo que use el smoke test.
- **Cómo se sirve la SPA en dev (confirmado por el usuario, 2026-11-20; revisado 2026-11-21)**: Vite en `http://localhost:5173` en la máquina local con proxy al backend, que corre con `./gradlew bootRun` (o desde el IDE) contra el PostgreSQL instalado localmente.

### D13 — Dependencias (aprobación explícita, regla dura 12) **(confirmado por el usuario, 2026-11-20; revisado 2026-11-21 por decisión del usuario: sin Docker)**

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
| testImplementation (platform) | `io.zonky.test.postgres:embedded-postgres-binaries-bom:17.12.0` | fija los binarios embebidos en PostgreSQL 17.12 (D7) |
| testImplementation | `io.zonky.test:embedded-postgres:2.2.2` | PostgreSQL real embebido en los `*IT` (D7); trae binarios de Windows x64, macOS x64 y Linux x64 |
| testRuntimeOnly | `io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8` (versión del BOM) | binarios para macOS Apple Silicon (no incluidos por defecto) |
| testImplementation | `org.springframework.security:spring-security-test` | tests de la cadena de seguridad |
| testImplementation | `com.tngtech.archunit:archunit-junit5` (versión explícita, no está en el BOM) | D5 |
| testRuntimeOnly | `org.junit.platform:junit-platform-launcher` | requerido por Gradle 9 |

**Verificación de versiones zonky** (2026-11-21, Maven Central: `maven-metadata.xml` y POM publicados):
- `io.zonky.test:embedded-postgres`: última versión **2.2.2** (`https://repo1.maven.org/maven2/io/zonky/test/embedded-postgres/maven-metadata.xml`, `lastUpdated` 2026-03-01). Compilada con target Java 8 (corre sobre Java 21). Su POM fija por defecto `embedded-postgres-binaries.version` = 14.22.0, por eso se importa el BOM.
- `io.zonky.test.postgres:embedded-postgres-binaries-bom`: versiones publicadas 17.9.0…**17.12.0**, 18.0.0…18.7.0 y 19.0.0 (`https://repo1.maven.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-bom/maven-metadata.xml`, `lastUpdated` 2026-11-19). Se elige 17.12.0 (última 17.x). El POM 17.12.0 incluye `-windows-amd64`, `-darwin-amd64`, `-darwin-arm64v8`, `-linux-amd64` y `-linux-arm64v8`, entre otros.
- Transitivas de `embedded-postgres` 2.2.2 (solo classpath de test): `commons-lang3`, `commons-compress` 1.28.0, `xz`, `commons-io`, `commons-codec`, `slf4j-api` 1.7.36 (Gradle resuelve la `slf4j-api` 2.x del BOM de Boot; API compatible).
- Nota para apply: volver a confirmar en Maven Central que no salió un patch nuevo de 2.2.x o 17.x antes de fijar.

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

- [JDK 17 instalado y toolchain 21] → foojay descarga el JDK la primera vez (requiere red); documentado en el README. Si se rechaza D3, instalar Temurin 21.
- [Spring Boot 4 es más nuevo que lo asumido en la KB] → los skills instalados tienen ejemplos de Boot 3; los cambios relevantes (nombres de starters, Jackson 3) se listan en D2/D13. Alternativa 3.5.x disponible. zonky solo expone un `DataSource`/JDBC y no depende de Spring, así que la versión de Boot no lo afecta.
- [zonky en Windows: PostgreSQL se niega a correr con privilegios de Administrador] → `initdb`/`postgres` abortan si el proceso corre en una consola elevada. Mitigación: el README indica correr los tests desde una terminal **no** elevada.
- [zonky en Windows: antivirus y carpeta temporal] → los binarios se extraen en `%TEMP%`; Defender puede demorar el primer arranque o bloquear la extracción. Mitigación: aceptar la primera demora; si se bloquea, documentar la exclusión. La ruta del repo en OneDrive no afecta porque el directorio de datos es temporal.
- [zonky: contenido de los binarios] → se asume que los binarios 17.x incluyen la extensión contrib `btree_gist`; `MigrationsIT` lo verifica en el primer ciclo RED/GREEN. Si faltara, se detiene el apply y se reevalúa con el usuario (H2 no es alternativa).
- [zonky: macOS Apple Silicon y Linux ARM] → los binarios `darwin-arm64v8` se agregan explícitamente (D13); Linux ARM no se cubre hasta que alguien lo necesite.
- [Rendimiento de los IT] → initdb + arranque cuesta unos segundos; se mitiga con la instancia singleton (D7) y con `integrationTest` separado de `test` (D8).
- [El intérprete `RolesScript` es código propio] → acota el dialecto del script (D6) y tiene tests unitarios; si el script usa un metacomando no soportado, el test falla con un mensaje claro.
- [Instalaciones nativas distintas entre integrantes] → se fija la mayor 17 en el README; el script de roles es idempotente y se puede re-ejecutar (también para rotar contraseñas); las credenciales de cada integrante viven solo en su `.env`.
- [OneDrive sincroniza `node_modules/`, `build/` y `.gradle/`] → muchos archivos pequeños pueden ralentizar o bloquear builds. Mitigación: están en `.gitignore` pero OneDrive no lo respeta; recomendar marcar esas carpetas como "solo en línea"/excluirlas o mover el repo fuera de OneDrive (no bloqueante).
- [Fin de línea] → `gradlew` y `01-roles.sql` deben quedar en LF aunque se editen en Windows; `.gitattributes` ya fuerza `eol=lf` y se agregan reglas explícitas `*.sql text eol=lf` y `gradlew text eol=lf`.
- [`ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER`] → solo aplica a objetos creados por `migrator`; si alguien crea tablas con otro rol, `app` no tendrá permisos. Mitigación: Flyway siempre corre como `migrator` (D6) y un IT lo verifica.
- [Paquetes con solo `package-info.java`] → ruido inicial en el árbol; a cambio, la estructura de KB 08 queda verificada desde el día 1.

## Migration Plan

No hay datos ni despliegue previos. Rollback = eliminar `backend/`, `frontend/`, `infra/`, `.env.example`, `README.md` y revertir las líneas agregadas a `.gitignore`/`.gitattributes`. La base local se elimina con `DROP DATABASE consultorio; DROP ROLE app; DROP ROLE migrator;` desde psql o pgAdmin (paso manual, documentado en el README).

## Open Questions

- Proveedor de hosting (PA-03): define dónde y quién ejecuta el script de roles en producción; no cambia este change.
- Estrategia de despliegue y momento de servir la SPA compilada desde el backend (SU-09): C-27 (TBD).

## UI Mockups

- Design system revision: 1
- Decision: no (no UI) — 2026-11-20. C-01 no agrega pantallas ni estilos; la SPA solo monta un nodo raíz vacío.
- Mockups: n/a
- Approval: n/a
- Preview: n/a
- Visual verify: n/a

## Historial de cambios

- **2026-11-20** — Versión inicial, aprobada por el usuario (D1–D13).
- **2026-11-21** — Revisión por decisión del usuario: sin Docker (DD-10). En la fundación se había elegido "Local por ahora" para la infraestructura y Docker se agregó por error; varios integrantes no tienen Docker. Se eliminan `docker-compose.yml`, `backend/Dockerfile`, `.dockerignore`, Mailpit, Testcontainers (`spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`) y toda imagen de contenedor, junto con la variable `POSTGRES_PASSWORD` y las de Mailpit. Se reemplazan: el script bash `01-roles.sh` para `docker-entrypoint-initdb.d` → script SQL `01-roles.sql` ejecutado una vez con psql/pgAdmin sobre PostgreSQL instalado de forma nativa (D6); Testcontainers → PostgreSQL embebido zonky 2.2.2 + binarios 17.12.0 (D7, D13); `integrationTest` ya no requiere Docker (D8). Se mantienen sin cambios: Spring Boot 4.0.x, Java 21 vía toolchain + foojay, paquete `ar.turnosodontologia.consultorio`, módulo Gradle único, ArchUnit, Vite local con proxy, deny-by-default (solo `GET /actuator/health` público), `ddl-auto=validate`, UTC y la separación `test`/`integrationTest`.
