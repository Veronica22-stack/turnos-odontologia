# Skill Registry

**Delegator use only.** Any agent that launches sub-agents reads this registry to resolve compact rules, then injects them directly into sub-agent prompts. Sub-agents do NOT read this registry or individual SKILL.md files.

See `_shared/skill-resolver.md` for the full resolution protocol.

Proyecto: Sistema de Gestión Odontológica (turnos-odontologia). Refrescado: 2026-11-24. Skills indexadas: 20.

## User Skills

Rutas `~/.claude/skills/...` = `C:\Users\HP\.claude\skills\...` (varias son symlinks a `~/.agents/skills/`). Las skills `openspec-*` son de proyecto (`.claude/skills/`, prevalecen sobre la copia duplicada en `.agents/skills/`).

| Trigger | Skill | Path |
|---------|-------|------|
| Agregar features/endpoints, corregir bugs o refactorizar en Spring Boot con TDD (JUnit 5, Mockito, MockMvc, Testcontainers, JaCoCo) | springboot-tdd | ~/.claude/skills/springboot-tdd/SKILL.md |
| Revisar authn/authz, validación, CSRF, secretos, headers, rate limiting en Spring Security | springboot-security | ~/.claude/skills/springboot-security/SKILL.md |
| Agregar, refactorizar o depurar tests JUnit 5/6 + AssertJ + Mockito en Maven/Gradle | java-junit | ~/.claude/skills/java-junit/SKILL.md |
| Escribir tests, @Test, @MockBean, suites de tests de Spring Boot (slices, Testcontainers) | spring-boot-test-patterns | ~/.claude/skills/spring-boot-test-patterns/SKILL.md |
| Diseñar o revisar un esquema PostgreSQL (tipos, índices, constraints, particionado) | postgresql-table-design | ~/.claude/skills/postgresql-table-design/SKILL.md |
| Escribir tests, mocks, cobertura, filtros y fixtures con Vitest | vitest | ~/.claude/skills/vitest/SKILL.md |
| "review my UI", "check accessibility", "audit design", "review UX" | web-design-guidelines | ~/.claude/skills/web-design-guidelines/SKILL.md |
| Verificar/depurar frontend local con Playwright, capturas y logs de navegador | webapp-testing | ~/.claude/skills/webapp-testing/SKILL.md |
| Implementar tareas de un change OpenSpec ("opsx apply") | openspec-apply-change | .claude/skills/openspec-apply-change/SKILL.md |
| Archivar un change OpenSpec completado ("opsx archive") | openspec-archive-change | .claude/skills/openspec-archive-change/SKILL.md |
| Explorar ideas / aclarar requisitos en modo pensamiento ("opsx explore") | openspec-explore | .claude/skills/openspec-explore/SKILL.md |
| Proponer un change con todos sus artefactos ("opsx propose") | openspec-propose | .claude/skills/openspec-propose/SKILL.md |
| Sincronizar delta specs a main specs sin archivar ("opsx sync") | openspec-sync-specs | .claude/skills/openspec-sync-specs/SKILL.md |
| Revisar/reconciliar artefactos de planificación de un change ("opsx update") | openspec-update-change | .claude/skills/openspec-update-change/SKILL.md |
| /active-orchestrator:*, arrancar un proyecto con el flujo de fundación SDD/OpenSpec | active-orchestrator | ~/.claude/skills/active-orchestrator/SKILL.md |
| Crear/generar/actualizar AGENTS.md / CLAUDE.md del proyecto | agent-instruction | ~/.claude/skills/agent-instruction/SKILL.md |
| "find a skill for X", descubrir/instalar skills | find-skill | ~/.claude/skills/find-skill/SKILL.md |
| Crear/generar la base de conocimiento (knowledge-base/) | kb-creator | ~/.claude/skills/kb-creator/SKILL.md |
| Crear/regenerar CHANGES.md / roadmap de changes | roadmap-generator | ~/.claude/skills/roadmap-generator/SKILL.md |
| Crear, modificar o evaluar skills | skill-creator | ~/.claude/skills/skill-creator/SKILL.md |

## Compact Rules

Pre-digested rules per skill. Delegators copy matching blocks into sub-agent prompts as `## Project Standards (auto-resolved)`.

### springboot-tdd
- Ciclo: test primero (falla) -> código mínimo -> refactor en verde -> cobertura JaCoCo (objetivo 80%+).
- Unit: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`; AAA; sin partial mocks; `@ParameterizedTest` para variantes.
- Web: `@WebMvcTest(Controller.class)` + MockMvc + `jsonPath`; integración: `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`.
- Persistencia: `@DataJpaTest` con `Replace.NONE` contra Postgres real (aquí: zonky embebido, no Docker/Testcontainers).
- Aserciones con AssertJ (`assertThat`, `assertThatThrownBy`); builders de datos de test.
- Tests rápidos, aislados, deterministas: probar comportamiento, no implementación.
- Gradle: `./gradlew test jacocoTestReport`.

### springboot-security
- Deny by default: `@EnableMethodSecurity`, `@PreAuthorize`; exponer solo lo necesario.
- Validar toda entrada con Bean Validation (`@Valid` + DTO record con `@NotBlank/@Size/@Email`).
- Consultas parametrizadas (`:param`, derived queries); nunca concatenar SQL.
- Passwords con `PasswordEncoder` (BCrypt/Argon2); nunca plaintext.
- Sesión: cookies `httpOnly`, `Secure`, `SameSite`; con sesión de navegador mantener CSRF; CSRF off solo para API stateless con Bearer.
- Secretos fuera del código: `${ENV_VAR}` en `application.yml`.
- Headers de seguridad (CSP, frame options, referrer policy); CORS en el filtro de seguridad, sin `*` en producción.
- Rate limiting / protección de fuerza bruta en endpoints públicos; escanear dependencias por CVEs.

### java-junit
- Detectar, no imponer: copiar convenciones de `src/test/java`; no sumar AssertJ/Mockito/JaCoCo ni subir versión de JUnit sin preguntar.
- Gradle 9: requiere `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` y `useJUnitPlatform()`.
- Mockear solo colaboradores en el borde (repo, HTTP, clock); no mockear value objects/records/colecciones ni la clase bajo test; preferir fakes si hay estado.
- `MockitoExtension` usa strict stubs: arreglar el test ante `UnnecessaryStubbingException`; evitar `mockStatic`.
- Determinismo: inyectar `Clock` (`Clock.fixed`) y `Random` con semilla; no depender de locale/zona/red/sleeps.
- Unit `*Test` (Surefire / `test`), integración `*IT` (Failsafe / tarea `integrationTest`).
- NUNCA borrar, comentar, `@Disabled`, debilitar ni saltear un test que falla; ni `-x test`/`-DskipTests`. Si el fix cambia comportamiento de producción, parar y preguntar.
- Correr primero el test más angosto (`./gradlew test --tests 'pkg.XTest'`), luego `./gradlew check`; preferir el wrapper.
- Test flaky: eliminar la no-determinación, no agregar reintentos.

### spring-boot-test-patterns
- Pirámide: unit sin Spring (<50ms) -> slices (`@DataJpaTest`, `@WebMvcTest`, `@JsonTest`, <100ms) -> `@SpringBootTest` (usar poco).
- Unit: `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`; verificar resultado + `verify` puntual.
- Controllers: `@WebMvcTest` + `MockMvc` + `@MockBean`; cubre también `@ControllerAdvice`.
- Repos: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` contra Postgres real (este proyecto: PostgreSQL embebido zonky, soporta `btree_gist`).
- `@ServiceConnection` / `@DynamicPropertySource` para cablear contenedores si se usan Testcontainers.
- Reutilizar contexto entre tests (misma config) para acelerar la suite.

### postgresql-table-design
- PK `BIGINT GENERATED ALWAYS AS IDENTITY` (UUID solo si hace falta opacidad/unicidad global); normalizar a 3NF; `NOT NULL` + `DEFAULT` donde corresponda.
- Tipos: `TIMESTAMPTZ` (nunca `timestamp`), `NUMERIC` para dinero, `TEXT` (+`CHECK` de largo) en vez de `varchar/char`, nunca `serial`/`money`/`timetz`.
- Identificadores `snake_case` sin comillas; enums de negocio como `TEXT` + `CHECK` o tabla lookup.
- PostgreSQL NO indexa FKs: indexarlas a mano; indexar solo caminos de acceso reales (B-tree, parcial, expresión, GIN para JSONB, GiST para rangos).
- `UNIQUE` permite múltiples NULL: usar `NULLS NOT DISTINCT` (PG15+) si no se quieren.
- `EXCLUDE USING gist (col WITH =, rango WITH &&)` previene solapamientos (requiere `btree_gist`): base de la agenda sin doble reserva.
- FKs con `ON DELETE/UPDATE` explícito; `CHECK` combinado con `NOT NULL`.
- Los huecos en identity son normales; no "arreglarlos".

### vitest
- API compatible con Jest (`test/it`, `describe`, `expect`, hooks) con ESM/TypeScript nativo; comparte config con Vite (`defineConfig`).
- Mocks con `vi.fn`, `vi.spyOn`, `vi.mock`, timers/fechas falsos (`vi.useFakeTimers`); `vi.hoisted` para mocks hoisted.
- Entorno DOM: `jsdom` o `happy-dom` (`environment`) para tests de UI vanilla; `node` para lógica pura.
- Cobertura V8/Istanbul (`--coverage`); filtrar con `-t` / patrones de archivo; evitar `.only` commiteado.
- Para dudas de API leer `references/core-*.md`, `features-*.md` de la skill bajo demanda.

### web-design-guidelines
- Antes de revisar leer `references/command.md` (snapshot fijado; no bajar reglas de la red) y aplicar TODAS sus reglas.
- Revisar los archivos/patrón pedidos (preguntar si no se indican) y reportar hallazgos en formato terso `file:line`.
- Cubre accesibilidad, semántica, foco, formularios, animación, tipografía, contraste y responsive.

### webapp-testing
- Scripts Python Playwright sync: `chromium.launch(headless=True)`, cerrar el browser al final.
- Esperar `page.wait_for_load_state('networkidle')` ANTES de inspeccionar el DOM en apps dinámicas.
- Gestionar servidores con `scripts/with_server.py` (correr `--help` primero; usarlo como caja negra, no leer su código).
- Reconocimiento y luego acción: screenshot/DOM -> selectores (`text=`, `role=`, CSS/ID) -> acciones.
- Capturar console logs y screenshots para verificación visual de los changes [UI].

### openspec-apply-change
- Leer los `contextFiles` del output de `openspec instructions apply`; no asumir nombres de archivo.
- Tarea ambigua o error/bloqueo: pausar y preguntar; no adivinar.
- Cambios mínimos y acotados por tarea; marcar `- [x]` apenas se completa (usar `sourcePath` y `line`) y solo si el comportamiento especificado está completo.
- Si hace falta más alcance que el spec, exponerlo y pausar; nunca recortar ni diferir en silencio.
- Si la implementación revela problemas de diseño, sugerir actualizar artefactos.

### openspec-archive-change
- Usar `openspec status --json` para verificar completitud; avisar de warnings sin bloquear y confirmar.
- Si hay delta specs, correr la evaluación de sync y mostrar el resumen ANTES de preguntar; nunca archivar con un sync en curso.
- Preservar `.openspec.yaml` al mover a `openspec/changes/archive/YYYY-MM-DD-<name>/`.
- Si el nombre del change es ambiguo, pedir selección; mostrar un resumen claro al final.

### openspec-explore
- Modo pensamiento: NUNCA implementar ni editar código/config/esquemas; derivar a `openspec-propose` cuando el usuario quiera construir.
- Antes de la primera escritura (incl. `openspec new change`): nombrar archivos, preguntar sí/no y esperar confirmación explícita en mensaje aparte.
- Nunca crear a mano directorios en `openspec/changes/`: usar `openspec new change "<name>"`.
- Explorar el código para fundamentar; visualizar con diagramas; cuestionar supuestos.

### openspec-propose
- Solo planificación: no implementar ni arrancar apply aunque el pedido lo mencione; parar tras presentar artefactos.
- Crear todos los artefactos de los que apply depende transitivamente (proposal, design, specs, tasks).
- Releer del disco los artefactos dependientes antes de crear el siguiente; verificar que cada archivo exista tras escribirlo.
- Preguntar solo ambigüedades que cambien alcance/comportamiento/aceptación; el resto, supuestos documentados.
- Si el change ya existe, preguntar si continuar o crear uno nuevo.

### openspec-sync-specs
- Leer delta y main specs antes de tocar; preservar contenido no mencionado en el delta.
- Nunca copiar el delta tal cual: fusionar con el formato de main spec, sin encabezados de operación delta.
- Usar solo `artifactPaths.specs.existingOutputPaths`; respetar el subconjunto que pase el invocador.
- Idempotente; frenar ante respuesta de instrucciones inválida o con código distinto de cero.

### openspec-update-change
- Solo artefactos de planificación; NUNCA editar código (derivar a `openspec-apply-change`).
- Editar solo `existingOutputPaths` concretos reportados por `openspec status`; no avanzar la frontera de build ni tocar artefactos `skipped`.
- Confirmar cada edición con el usuario antes de escribir.
- Si cambia la intención del change, recomendar un change nuevo (`openspec new change`).

### active-orchestrator
- Orquestador fino del flujo de fundación: `openspec init` -> [discovery-research] -> kb-creator -> design-system -> roadmap-generator -> find-skill -> skill-registry -> agent-instruction.
- Es dueño del archivo de estado `.active-orchestrator-state.json` y solo del campo `step`; es idempotente (no repite pasos hechos).
- No modificar `step`, `version` ni `owner` al correr skills sueltas (p. ej. rebuild de registry).

### agent-instruction
- Genera AGENTS.md/CLAUDE.md desde KB + CHANGES.md: stack, referencia a `knowledge-base/`, skills por agente, roadmap y reglas duras.
- Solo contenido específico del proyecto (no repetir el CLAUDE.md global); AGENTS.md y CLAUDE.md deben quedar sincronizados.

### find-skill
- Buscar skills instalables ante "cómo hago X"/"hay una skill para X"; recomendar y registrar en `skills.recommended` / `installed` del estado.

### kb-creator
- Construye `knowledge-base/` con los 10 archivos canónicos (+README); modo silencioso desde `docs/` o interactivo desde cero.

### roadmap-generator
- Genera `CHANGES.md`: árbol de dependencias, camino crítico, gobernanza por change y punteros "Leer antes" a la KB; sin preguntas.

### skill-creator
- Crear/modificar skills y medir su desempeño con evals; optimizar la descripción para mejorar el disparo.

## Project Conventions

| File | Path | Notes |
|------|------|-------|
| AGENTS.md | AGENTS.md | Index. Copia sincronizada de CLAUDE.md; define stack, reglas duras y flujo de trabajo |
| CLAUDE.md | CLAUDE.md | Index. Mismas convenciones que AGENTS.md (no editar uno sin el otro) |
| CHANGES.md | CHANGES.md | Roadmap de 28 changes (C-01..C-28), referenciado por AGENTS.md/CLAUDE.md |
| KB 01 | knowledge-base/01_vision_y_objetivos.md | Propósito y alcance |
| KB 02 | knowledge-base/02_descripcion_general.md | Stack y descripción general |
| KB 03 | knowledge-base/03_actores_y_roles.md | Auth, permisos, rutas públicas |
| KB 04 | knowledge-base/04_modelo_de_datos.md | Entidades, ERD, migraciones |
| KB 05 | knowledge-base/05_reglas_de_negocio.md | Reglas RN-XX |
| KB 06 | knowledge-base/06_funcionalidades.md | Historias de usuario por épica |
| KB 07 | knowledge-base/07_flujos_principales.md | Flujos E2E |
| KB 08 | knowledge-base/08_arquitectura_propuesta.md | Patrones, estructura, seguridad, env vars |
| KB 09 | knowledge-base/09_decisiones_y_supuestos.md | Decisiones (DD) y supuestos (SU) |
| KB 10 | knowledge-base/10_preguntas_abiertas.md | Preguntas abiertas (PA-01..PA-07 de prioridad Alta bloquean changes) |
| KB 11 | knowledge-base/11_cumplimiento_normativo.md | Leyes 25.326, 26.529, 27.706 |
| Design system | design-system/README.md | Revision 1 aprobada (system-and-mockups); tokens en `design-system/tokens.css` |
| Env example | .env.example | Variables de entorno sin valores reales |

Reglas duras del proyecto (resumen de AGENTS.md/CLAUDE.md, aplicar siempre):
- Backend: inyección por constructor (no `@Autowired` en campos); DTOs record con Bean Validation, nunca entidades JPA en la API; Flyway para todo cambio de esquema (nunca `ddl-auto=update` ni editar migraciones aplicadas); seguridad deny-by-default con errores unificados vía `@ControllerAdvice`.
- Datos de salud: historia clínica append-only (correcciones = entrada nueva referenciando la original); NUNCA loguear nombre/DNI/email/teléfono/datos clínicos (solo IDs); todo acceso a HC se registra en `audit_log`; fechas en UTC y mostradas en `America/Argentina/Buenos_Aires`; tokens de gestión de turno solo hasheados.
- Frontend: `tsconfig` strict, sin `any` (usar `unknown` + type guards); sin `innerHTML` con datos de usuario/API; sin colores/espaciados/tipografías hardcodeados (usar `design-system/tokens.css`); sin frameworks ni dependencias de runtime sin aprobación explícita.
- Universales: conventional commits con ID del change (`feat(C-11): ...`); nunca commit/push sin pedido explícito; sin credenciales en código ni `application.yml` (env vars + `.env.example`); nunca datos reales de pacientes en el repo (solo ficticios: "Paciente Prueba", DNI `00000000`, `@ejemplo.com`).
- Changes [UI]: requieren mockup HTML aprobado en `openspec/changes/<name>/mockups/` antes de `/opsx:apply`.

Read the convention files listed above for project-specific patterns and rules. All referenced paths have been extracted — no need to read index files to discover more.
