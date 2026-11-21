# Verify report: C-01 `foundation-setup`

Fecha: 2026-11-21 | Rama: dev-lucas | Revisor: sub-agente verificador (solo lectura sobre código)

## Veredicto: PASS WITH WARNINGS

No hay issues CRITICAL. `openspec validate c-01-foundation-setup --strict` es válido; `openspec status` con todos los artefactos presentes; tasks.md 100% `[x]`. Visual: `design.md` registra `Decision: no (no UI)`; C-01 no tiene pantallas, por lo tanto no corresponde comparación contra mockups ni verificación visual (`Visual verify: n/a`).

## Resultados de ejecución (corridos por el verificador)

| Suite | Comando | Resultado |
|-------|---------|-----------|
| Backend unitarios + ArchUnit | `./gradlew check jacocoTestReport` (tarea `test`) | 45 tests, 0 fallos, 0 omitidos |
| Backend integración (zonky PG 17) | tarea `integrationTest` | 29 tests, 0 fallos, 0 omitidos |
| Cobertura | `jacocoTestReport` | reporte XML/HTML generado en `backend/build/reports/jacoco/test/` (BUILD SUCCESSFUL, 1m45s) |
| Frontend | `npm test` | 8 tests (2 archivos) verdes |
| Frontend | `npm run typecheck` / `npm run build` | sin errores; `npm ls --omit=dev` vacío (cero dependencias de runtime) |

Sin `@Disabled` en `backend/src`. Daemon de Gradle detenido (`./gradlew --stop`). `git status` limpio tras las corridas (`dist/`, `build/` ignorados).

## 1. Completitud

- tasks.md: todas las tareas 0.1 a 9.3 en `[x]`. Spot-check por grupo: 1.x `backend/build.gradle.kts`, `settings.gradle.kts`, wrapper; 2.x `architecture/ModuleBoundaries*.java`, `archfixture/*`, 8 módulos x 4 capas con `package-info.java` (32 archivos); 3.x `TimeZoneConfigTest`, `MissingConfigurationTest`, `ConfigurationHygieneTest`, `application*.yml`; 4.x `support/RolesScript*`, `EmbeddedPostgresSupport`, `infra/postgres/init/01-roles.sql`, `V1__baseline.sql`, `MigrationsIT`, `RolePrivilegesIT`, `MigrationValidationIT`; 5.x `SecurityConfig`, `HealthEndpointIT`, `AccessPolicyIT`, `HealthDownIT`; 6.1 `LocalDatabaseCredentialsIT`; 7.x `frontend/src/main.ts`, `main.test.ts`, `vite.config.ts`, `vite.config.test.ts`; 8.x `.gitignore`, `.gitattributes` (`*.sql`, `gradlew` en LF), `README.md`. Todo existe.
- Las 7 specs tienen implementación.

## 2. Matriz de cobertura de escenarios

| Spec / Requirement | Scenario | Cobertura |
|---|---|---|
| module-boundaries / Módulos y capas | Estructura completa | `ProductionArchitectureTest.existeCadaCombinacionModuloCapa`; `ModuleBoundariesRulesTest.estructuraCompletaPasa...`, `estructuraIncompletaNombraLaCapaFaltante`, `paqueteDeModuloNoDeclaradoFalla` |
| module-boundaries / Acceso por api | Dependencia a api de otro módulo | `ModuleBoundariesRulesTest.estructuraValidaCumpleTodasLasReglas...` |
| | Dependencia a infraestructura ajena | `accesoAInfraestructuraDeOtroModuloFallaNombrandoOrigenYDestino`; `ProductionArchitectureTest.losModulosSoloSeAccedenPorSuApi` |
| module-boundaries / Grafo | Dependencia no declarada | `dependenciaNoDeclaradaAgendaHaciaReservasFalla`, `dependenciaNoDeclaradaPasaLaReglaDeAccesoPorApi`; `ProductionArchitectureTest.lasDependenciasEntreModulos...`, `noHayCiclosEntreModulos`; `cicloEntreModulosFalla` |
| | auditoria independiente | `auditoriaNoPuedeDependerDeModulosDeNegocio` |
| module-boundaries / Dominio independiente | Dominio usa infraestructura | `dominioQueUsaInfraestructuraFalla`, `dominioQueUsaSpringWebFalla`, `dominioQueUsaJdbcFalla`, `aplicacionQueUsaLaApiDeSuPropioModuloFalla`; `ProductionArchitectureTest.elDominioEsIndependiente...`, `laAplicacionNoDepende...` |
| module-boundaries / Verificación en el build | Violación rompe el build | `ProductionArchitectureTest` corre en `test` (excluye solo `*IT`), por lo que una violación rompe `check` (cubierto por diseño de Gradle, sin test dedicado de "build falla") |
| runtime-configuration / Credenciales por entorno | Archivos sin secretos | `ConfigurationHygieneTest.losYamlVersionadosSoloReferencianContrasenasPorVariable`, `laDeteccionDeContrasenasLiteralesFunciona` |
| | Plantilla de entorno completa | `envExampleDeclaraTodasLasVariablesDeKb08`, `lasVariablesSensiblesUsanElMarcadorChangeMe`, `envExampleNoDeclaraVariablesDeInfraestructuraDeSuperusuario` |
| runtime-configuration / Falla ante faltante | Falta DB_PASSWORD | `MissingConfigurationTest.fallaMencionandoDbPasswordSinPerfil`, `...ConPerfilDev`, `...VariableFaltanteDeMigracion` |
| | Configuración completa | `LocalDatabaseCredentialsIT.conLaContrasenaCorrectaElArranqueEsExitoso`; `HealthEndpointIT` |
| runtime-configuration / Fechas en UTC | Sesión de base en UTC | `MigrationsIT.laSesionDeLaAplicacionUsaUtc`; `TimeZoneConfigTest.fijaLaZonaPorDefectoEnUtcPartiendoDeBuenosAires` (ver W2) |
| runtime-configuration / Perfil dev | Perfil dev sin contraseñas | `MissingConfigurationTest.fallaMencionandoDbPasswordConPerfilDev` |
| database-migrations / Migraciones al arrancar | Base vacía | `MigrationsIT.laMigracionV1QuedaRegistradaComoExitosa` |
| | Base ya migrada | `MigrationValidationIT.migrarDosVecesNoAplicaNadaLaSegundaVez`, `validarContraLasMigracionesRealesPasa` (ver W3) |
| database-migrations / Checksum | Checksum distinto | `MigrationValidationIT.validarContraUnaV1AlteradaFallaPorChecksum` (ver W3) |
| database-migrations / Separación de roles | Rol app sin DDL | `MigrationsIT.elRolDeAplicacionNoPuedeEjecutarDdl`, `RolesScriptIT.elRolDeAplicacionSeConecta...` |
| | Rol app con DML en tablas nuevas | `RolePrivilegesIT.laAplicacionPuedeLeerEInsertarEnAmbasTablas`, `...ActualizarYBorrarEnLaTablaNormal` |
| database-migrations / Revocación por tabla | Tabla inmutable | `RolePrivilegesIT.laAplicacionNoPuedeActualizarLaTablaInmutable`, `...BorrarDeLaTablaInmutable` |
| database-migrations / Extensiones | btree_gist | `MigrationsIT.laExtensionBtreeGistEstaInstalada` |
| local-dev-environment / Script de roles | Primera ejecución | `RolesScriptIT.creaRolesSinPrivilegiosYUnaBaseDeLaQueEsDuenoElMigrador`, `elRolDeAplicacionSeConecta...`; en PG nativo: manual 2026-11-21 (tasks 6.2) |
| | Falta una variable | `RolesScriptIT.sinLaContrasenaDelRolDeAplicacionFallaAntesDeCrearNada`; `RolesScriptTest.fallaNombrandoLaVariableFaltante` |
| | Re-ejecución | `RolesScriptIT.laSegundaEjecucionEsIdempotente...`; manual 2026-11-21 (6.2) |
| local-dev-environment / App local | Arranque local completo | manual 2026-11-21 (tasks 6.2 y 9.2, PostgreSQL 18.7) |
| | Credenciales incorrectas | `LocalDatabaseCredentialsIT.unaContrasenaIncorrectaDetieneElArranque...` |
| local-dev-environment / Tests sin base instalada | Máquina sin PostgreSQL | implícito: `integrationTest` corre sobre zonky (29 verdes); no se probó en máquina sin PG instalado |
| | Script de roles inválido | `RolesScriptIT.unaSentenciaInvalidaFallaIndicandoElErrorSinExponerContrasenas` |
| local-dev-environment / Solo local | Servidor de desarrollo local | `vite.config.test.ts` "escucha solo en localhost"; manual 9.2 |
| local-dev-environment / Mismo origen | Salud vía servidor de desarrollo | `vite.config.test.ts` (2 tests de proxy); manual 9.2 |
| platform-health / Endpoint público | Aplicación sana | `HealthEndpointIT.healthResponde200ConEstadoUpSinDetalles` |
| platform-health / Refleja la BD | Base caída | `HealthDownIT.healthResponde503ConEstadoDownCuandoLaBaseNoEstaDisponible` |
| platform-health / Sin detalles | Respuesta sin detalles | `HealthEndpointIT.healthResponde200...SinDetalles` |
| | Otros endpoints no disponibles | `HealthEndpointIT.envNoEstaDisponible`, `infoNoEstaDisponible`, `beansNoEstaDisponible` |
| api-access-policy / Denegación por defecto | Ruta de API no declarada | `AccessPolicyIT.rutaDeApiSinAutenticacionResponde401` |
| | Ruta inexistente | `AccessPolicyIT.rutaInexistenteSinAutenticacionResponde401` |
| | Ruta pública declarada | `HealthEndpointIT` / `HealthDownIT` |
| api-access-policy / Sin login implícito | Sin desafío Basic ni redirección | `AccessPolicyIT.rutaDeApiSinAutenticacion...` (aserciones de `assertDenied`) |
| | Sin contraseña generada en log | `AccessPolicyIT.elArranqueNoGeneraNiLoguaUnaContrasenaDeSeguridad` |
| api-access-policy / Métodos no seguros | POST a salud | `AccessPolicyIT.postAnonimoAlHealthEsRechazado` |
| spa-shell / Montaje | #app presente | `main.test.ts` "monta un unico [data-app-root]..." |
| | Arranque repetido | `main.test.ts` "es idempotente..." |
| spa-shell / #app faltante | Sin #app | `main.test.ts` "falla mencionando #app y no modifica el body..." |
| spa-shell / Idioma | Idioma declarado | `main.test.ts` "declara el idioma es-AR" |

Escenarios UNCOVERED: ninguno. Cubiertos solo por verificación manual 2026-11-21 (aceptado): arranque local completo contra PostgreSQL nativo (6.2, 9.2).

## 3. Coherencia con design.md

- D1 paquete `ar.turnosodontologia.consultorio`, clase `ConsultorioApplication`: OK. D2 Boot `4.0.10`, Java 21 por toolchain, foojay en `settings.gradle.kts`, Gradle 9.8.2: OK. D4 módulo único: OK. D5 ArchUnit (`archunit-junit5:1.5.1`, solo test), fixtures y reglas reutilizables: OK.
- D6 script SQL sin credenciales, idempotente, `\gexec`/`\connect`; `V1__baseline.sql` con `btree_gist`, `ALTER DEFAULT PRIVILEGES` y placeholder `${app_role}`: OK. D7/D8 zonky 2.2.2 + BOM 17.12.0 + darwin-arm64v8; tareas `test`/`integrationTest`/`check`/`jacocoTestReport` separadas: OK.
- D9 `SecurityConfig` con constructor-less bean method, `permitAll` solo `GET /actuator/health`, `denyAll` resto, 401 sin Basic, formLogin/httpBasic/logout deshabilitados, `UserDetailsServiceAutoConfiguration` excluida: OK. D10 `ddl-auto=validate`, `open-in-view=false`, `jdbc.time_zone=UTC`, actuator solo `health` sin detalles, `include-stacktrace=never`, sin defaults para secretos: OK; `application-dev.yml` solo URL por defecto y logging: OK. UTC también en `bootRun` y tareas de test: OK.
- D12/D13 frontend: `devDependencies` exactamente typescript, vite, vitest, jsdom; cero `dependencies`; tsconfig strict con todas las banderas listadas; proxy `/api` y `/actuator` + `BACKEND_URL`; `server.host = 'localhost'`: OK. Backend: lista de dependencias coincide con D13 (ver S1).
- Sin Docker/Testcontainers/Mailpit/H2/`.sh`/`POSTGRES_PASSWORD`: grep sobre backend, frontend, infra, README, .gitignore, .gitattributes sin coincidencias (DD-10 cumplido; `infra/postgres/init/` contiene solo `01-roles.sql`).

### Reglas duras de CLAUDE.md contra el diff

1 `@Autowired` en campos: ninguno. 2 entidades JPA en API: no hay entidades ni controllers. 3 `ddl-auto`: solo `validate`; sin migraciones editadas. 4 deny-by-default y única ruta pública declarada: OK (`@ControllerAdvice` de errores se difiere a C-02 según Non-Goals). 5/7 HC inmutable y audit: fuera de alcance (solo patrón documentado en V1). 6 PII en logs: no hay llamadas a logger en `src/main`. 8 UTC: OK; tokens fuera de alcance. 9 `any`: ninguno. 10 `innerHTML`: solo en un test como lectura de `body.innerHTML` para comparar (no en código de producción ni con datos de usuario). 11 colores/estilos hardcodeados: ninguno (sin estilos). 12 dependencias de runtime/frameworks: ninguna. 13/14 commits: no verificable en este alcance (no se hizo commit). 15 credenciales: ninguna en YAML; `.env` ignorado; `.env.example` verificado vía `ConfigurationHygieneTest` (no legible directamente).

## Issues

### CRITICAL
Ninguno.

### WARNING
- **W1** — El grupo 2 de tasks.md y `design.md` D5 hablan de `ModuleBoundariesTest`, mientras el código usa `ModuleBoundariesRulesTest` + `ProductionArchitectureTest`. Es solo nomenclatura, pero el artefacto queda desalineado. Fix: ajustar el nombre en `design.md` D5 (o aclarar en el archive).
- **W2** — Escenario "host en `America/Argentina/Buenos_Aires` -> `SHOW TimeZone` = UTC": `MigrationsIT.laSesionDeLaAplicacionUsaUtc` corre con `-Duser.timezone=UTC` ya fijado por `build.gradle.kts` (tareas test/integrationTest), por lo que no demuestra que `main` fije UTC con un host distinto. La cobertura real está en `TimeZoneConfigTest` (BA -> UTC) más la verificación manual 6.2. Fix opcional: en un IT, fijar `TimeZone.setDefault(BA)` y llamar `ConsultorioApplication.configureTimeZone()` antes de abrir la conexión.
- **W3** — Escenarios "Base ya migrada" y "Checksum distinto" se verifican con Flyway directo (`MigrationValidationIT`), no arrancando el contexto de Spring con una `V1` alterada. La configuración Spring (`spring.flyway.*`) está ejercitada por los demás IT; el riesgo es bajo. Fix opcional: un IT que arranque `SpringApplication` con `spring.flyway.locations` apuntando a `migration_alterada` y espere fallo de arranque.

### SUGGESTION
- **S1** — `backend/build.gradle.kts:41` agrega el flag de compilador `-Xpkginfo:always` (necesario para `package-info.java` vacíos en ArchUnit) que no figura en D13; no es una dependencia, pero conviene documentarlo en `design.md` D5 ("Paquetes vacíos").
- **S2** — El escenario "Violación rompe el build" no tiene un test propio (se garantiza por construcción: `ProductionArchitectureTest` corre en `test`). Aceptable.

## Lo que no pude verificar

- Contenido de `.env.example` (sin permiso de lectura): solo inferido de `ConfigurationHygieneTest`, que pasó.
- Pasos de README para macOS/Linux y el cambio de `listen_addresses` (ya señalados como no ejecutados en tasks 8.2).
- Arranque en máquina sin PostgreSQL instalado (el verificador sí tiene PostgreSQL 18; los IT usan zonky de todos modos) y en macOS Apple Silicon.
- Verificación visual: no aplica (sin UI).
- Cumplimiento de la convención de commits (reglas 13/14): no hubo commit.
