# Tasks: C-01 `foundation-setup`

> Strict TDD: en cada grupo con comportamiento, la tarea de test (RED) va antes de la de implementación (GREEN). Ejecutar los tests y verlos fallar antes de implementar.
> Antes de empezar: confirmar las decisiones marcadas **(confirmar)** en `design.md` (D1, D2, D3, D5, D7, D12, D13). Las tareas usan los valores recomendados.
> Comandos: `./gradlew test` (unitarios + ArchUnit, sin Docker), `./gradlew integrationTest` (`*IT`, requiere Docker), `./gradlew check` (todo), `npm test` (Vitest).

## 1. Scaffolding del backend (sin comportamiento; sin tests propios)

- [ ] 1.1 Crear `backend/` con Gradle wrapper 9.x (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), `settings.gradle.kts` (nombre `consultorio`, plugin `foojay-resolver-convention`) y verificar que `./gradlew --version` corre desde `backend/`
- [ ] 1.2 Escribir `build.gradle.kts` con toolchain Java 21, plugins y dependencias exactamente como `design.md` §D13 (sin agregar otras), y verificar que `./gradlew dependencies --configuration testRuntimeClasspath` resuelve sin errores
- [ ] 1.3 Configurar en `build.gradle.kts` la tarea `test` (excluye `**/*IT.class`, `useJUnitPlatform()`, `-Duser.timezone=UTC`), la tarea `integrationTest` (solo `**/*IT.class`), `check` dependiendo de ambas y `jacocoTestReport` sobre ambos `.exec`; verificar con `./gradlew tasks --all` que existen `test`, `integrationTest` y `jacocoTestReport`

## 2. Fronteras de módulos (spec `module-boundaries`)

- [ ] 2.1 RED: crear paquetes fixture en `src/test/java/ar/turnosodontologia/consultorio/archfixture/` (estructura válida y una violación por regla: acceso a `infrastructure` de otro módulo, dependencia `agenda`→`reservas`, `auditoria`→`pacientes`, `domain`→`infrastructure`, ciclo) y `ModuleBoundariesRulesTest` que espera que cada regla falle con su violación y pase con la estructura válida; verificar que `./gradlew test` falla por reglas inexistentes
- [ ] 2.2 GREEN: implementar las reglas ArchUnit de `design.md` §D5 como funciones reutilizables en `src/test/java/.../architecture/ModuleBoundaries.java`; verificar que `ModuleBoundariesRulesTest` pasa (al menos un caso válido y uno inválido por regla)
- [ ] 2.3 RED: agregar `ProductionArchitectureTest` que aplica las reglas al paquete `ar.turnosodontologia.consultorio` (excluyendo `archfixture`) y verifica la estructura completa módulo/capa; verificar que falla porque los paquetes no existen
- [ ] 2.4 GREEN: crear `ConsultorioApplication` (con `main`) y un `package-info.java` por cada combinación de los 8 módulos × 4 capas; verificar que `./gradlew test` pasa

## 3. Configuración de ejecución y UTC (spec `runtime-configuration`)

- [ ] 3.1 RED: `TimeZoneConfigTest` (unitario) que verifica que el método de arranque fija la zona por defecto de la JVM en `UTC` partiendo de `America/Argentina/Buenos_Aires` (restaurando la zona original al final); verificar que falla
- [ ] 3.2 GREEN: extraer en `ConsultorioApplication` un método estático de configuración de zona invocado desde `main` antes de `SpringApplication.run`; verificar que `TimeZoneConfigTest` pasa
- [ ] 3.3 RED: `MissingConfigurationTest` (unitario, sin Docker) que arranca la aplicación sin `DB_PASSWORD` (con y sin perfil `dev`) y espera un fallo cuyo mensaje menciona `DB_PASSWORD`; verificar que falla porque no existe la configuración
- [ ] 3.4 GREEN: crear `application.yml` y `application-dev.yml` según `design.md` §D10 (datasource y Flyway por variables sin defaults para secretos, `ddl-auto=validate`, `open-in-view=false`, `hibernate.jdbc.time_zone=UTC`, actuator solo `health` sin detalles); verificar que `MissingConfigurationTest` pasa
- [ ] 3.5 RED: `ConfigurationHygieneTest` (unitario) que lee `../.env.example` y `src/main/resources/application*.yml` y verifica: todas las variables de KB 08 §Variables de entorno (más `DB_NAME` y `POSTGRES_PASSWORD`) presentes en `.env.example`, las sensibles con valor `change-me`, y ninguna clave `password` en los YAML con valor literal; verificar que falla porque `.env.example` no existe
- [ ] 3.6 GREEN: crear `.env.example` en la raíz con todas las variables, comentarios por grupo e indicación del change que consume cada una; verificar que `ConfigurationHygieneTest` pasa

## 4. Roles de base de datos y migraciones (spec `database-migrations`)

- [ ] 4.1 Crear `infra/postgres/init/01-roles.sh` según `design.md` §D6 (lee credenciales del entorno, crea `migrator` y `app`, base con dueño `migrator`, revoca `CREATE` en `public` a `PUBLIC`) y `PostgresTestcontainersConfig` según §D7 (contenedor singleton `postgres:17-alpine`, monta el script, credenciales de test generadas, `@DynamicPropertySource`); verificar que el script queda con fin de línea LF
- [ ] 4.2 RED: `MigrationsIT` que verifica: `V1` registrada como exitosa en `flyway_schema_history`, `btree_gist` en `pg_extension`, `CREATE TABLE` rechazado con la conexión del rol `app`, y `SHOW TimeZone` = `UTC` en la conexión de la app; verificar con `./gradlew integrationTest` que falla porque `V1` no existe
- [ ] 4.3 GREEN: crear `src/main/resources/db/migration/V1__baseline.sql` (extensión `btree_gist`, `ALTER DEFAULT PRIVILEGES` para `${app_role}` en tablas y secuencias, comentario con el patrón de revocación) y el placeholder `app_role` en `application.yml`; verificar que `MigrationsIT` pasa
- [ ] 4.4 RED→GREEN: `RolePrivilegesIT` que, conectado como `migrator`, crea una tabla de prueba y otra con `REVOKE UPDATE, DELETE ... FROM app`, y verifica como `app`: `SELECT`/`INSERT` permitidos en ambas, `UPDATE`/`DELETE` rechazados solo en la inmutable; limpiar las tablas al final; verificar que pasa con `./gradlew integrationTest` (si falla, corregir `V1`/script, no el test)
- [ ] 4.5 RED→GREEN: `MigrationValidationIT` que usa Flyway con credenciales de `migrator` para (a) migrar dos veces y verificar que la segunda no aplica nada y (b) validar contra una ubicación de test con un `V1` alterado y esperar un error de validación por checksum; verificar que pasa

## 5. Salud y política de acceso (specs `platform-health`, `api-access-policy`)

- [ ] 5.1 RED: `HealthEndpointIT` (`@SpringBootTest` + MockMvc + Testcontainers) que verifica `GET /actuator/health` → 200 JSON `{"status":"UP"}` sin `components`/`details`, y `GET /actuator/env` / `GET /actuator/info` ≠ 200; verificar que falla por falta de configuración de seguridad/actuator correcta
- [ ] 5.2 RED: `AccessPolicyIT` que verifica 401 en `GET /api/v1/cualquier-cosa` y `GET /no-existe`, ausencia de `WWW-Authenticate: Basic` y de redirección a `/login`, `POST /actuator/health` → 401 o 403, y (con `OutputCaptureExtension`) que el log de arranque no contiene "Using generated security password"; verificar que falla
- [ ] 5.3 GREEN: implementar `SecurityConfig` en `auth/infrastructure` según `design.md` §D9 (inyección por constructor, `permitAll` solo para `GET /actuator/health`, `denyAll` para el resto, `HttpStatusEntryPoint(401)`, sin form login/basic/logout, exclusión de `UserDetailsServiceAutoConfiguration`); verificar que `HealthEndpointIT`, `AccessPolicyIT` y `ProductionArchitectureTest` pasan
- [ ] 5.4 RED→GREEN: `HealthDownIT` con su propio contenedor (no el singleton): arranca el contexto, detiene el contenedor y verifica que `GET /actuator/health` responde 503 con `status` `DOWN` (timeout de conexión de Hikari bajo en ese test); verificar que pasa

## 6. Entorno local con Docker Compose (spec `local-dev-environment`)

- [ ] 6.1 Crear `backend/Dockerfile` multi-stage según `design.md` §D11 (build JDK 21, runtime JRE 21, usuario no root, `-Duser.timezone=UTC`) y `backend/.dockerignore`; verificar que `docker build backend` termina con éxito
- [ ] 6.2 Crear `docker-compose.yml` en la raíz (servicios `db`, `mailpit`, `app`; `env_file: .env` obligatorio; healthcheck `pg_isready`; `depends_on` con `service_healthy`; volumen nombrado; script de roles montado; todos los puertos en `127.0.0.1`); verificar que `docker compose config` no da errores con un `.env` copiado de `.env.example` y que sin `.env` falla indicando el archivo faltante
- [ ] 6.3 Verificar el entorno de punta a punta: con `.env` local (no versionado), `docker compose up -d` → `curl http://127.0.0.1:8080/actuator/health` devuelve `UP`, `http://127.0.0.1:8025` responde, y `docker compose port db 5432` muestra `127.0.0.1`; luego `docker compose down` (registrar el resultado en el resumen del apply)

## 7. Frontend (spec `spa-shell` y proxy de `local-dev-environment`)

- [ ] 7.1 Crear `frontend/` con `package.json` (solo las `devDependencies` de `design.md` §D13, cero `dependencies`, scripts `dev`/`build`/`typecheck`/`test`/`test:watch`, `engines.node >=22`), `.nvmrc`, `tsconfig.json` estricto (§D12, `types: ["vite/client"]`) y `vite.config.ts` con entorno `jsdom` y `restoreMocks`; verificar que `npm install` termina y `npm ls --prod` no lista dependencias de runtime
- [ ] 7.2 RED: `src/main.test.ts` con los escenarios de `spa-shell`: monta un único `[data-app-root]` en `#app`, arranque repetido idempotente, error que menciona `#app` sin modificar el `body` cuando falta el contenedor, e `index.html` (importado con `?raw`) con `lang="es-AR"`; verificar que `npm test` falla
- [ ] 7.3 GREEN: implementar `src/main.ts` (`mountApp(doc)` con `createElement`, sin `innerHTML`, sin `any`) e `index.html` (`lang="es-AR"`, `<div id="app">`, script módulo a `/src/main.ts`); verificar que `npm test` pasa
- [ ] 7.4 RED→GREEN: `vite.config.test.ts` que importa la configuración y verifica que `/api` y `/actuator` se reenvían a `http://localhost:8080` por defecto y a `BACKEND_URL` cuando está definida; implementar el proxy en `vite.config.ts`; verificar que `npm test` pasa
- [ ] 7.5 Verificar que `npm run typecheck` y `npm run build` terminan sin errores (strict, sin `any`)

## 8. Repositorio y documentación

- [ ] 8.1 Extender (sin reemplazar) `.gitignore` con `*.log`, `frontend/.vite/`, `backend/bin/` y `.env` en subcarpetas; extender `.gitattributes` con `*.sh text eol=lf` y `gradlew text eol=lf`; verificar con `git check-ignore -v frontend/node_modules backend/build .env` y `git check-ignore .env.example` (este último no debe estar ignorado)
- [ ] 8.2 Crear `README.md` en la raíz con "Desarrollo local": requisitos (Docker, JDK 21 o toolchain automática, Node 22), copiar `.env.example` a `.env`, `docker compose up`, `npm run dev`, los comandos de test (`test`/`integrationTest`/`check`/`npm test`), recrear el volumen al cambiar credenciales y la nota sobre OneDrive; verificar que cada comando documentado corre tal como está escrito

## 9. Verificación integral

- [ ] 9.1 Ejecutar `./gradlew check jacocoTestReport` en `backend/` y `npm test && npm run build` en `frontend/`; todos verdes, sin tests deshabilitados ni `@Disabled`; registrar cantidad de tests por suite
- [ ] 9.2 Con `docker compose up` y `npm run dev` en ejecución, verificar que `GET http://localhost:5173/actuator/health` devuelve la respuesta del backend (`UP`) y que `GET http://localhost:5173/api/v1/x` devuelve 401
- [ ] 9.3 Revisar el diff contra las reglas duras de `CLAUDE.md` (sin `@Autowired` en campos, sin `ddl-auto=update`, sin secretos versionados, sin `any`/`innerHTML`, sin dependencias fuera de §D13); verificar con `grep` sobre `backend/src` y `frontend/src` que no hay coincidencias

## Workflow follow-up

- Ejecutar `openspec-verify` contra specs/design/tasks (sin verificación visual: el change no tiene UI).
- Archivar con `/opsx:archive c-01-foundation-setup` y actualizar el estado de `C-01` en `CHANGES.md`.
- No hacer commit ni push sin pedido explícito del usuario (regla dura 14); al hacerlo, usar `feat(C-01): ...`.
