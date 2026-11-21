# Verify report: C-02 `core-models-shared`

Fecha: 2026-11-21 | Rama: dev-lucas (working tree sin commitear) | Revisor: sub-agente verificador independiente (solo lectura sobre código; solo se creó este informe)

## Veredicto: PASS WITH WARNINGS

No hay issues CRITICAL. `openspec validate c-02-core-models-shared --strict` es válido; `./gradlew check --rerun-tasks` termina en BUILD SUCCESSFUL. tasks.md está 40/42: quedan abiertas 1.3 y 9.3 (pendientes manuales conocidos, no bloqueantes). Los warnings son: cobertura real de `ApiExceptionHandler` (70 %) por debajo del 80 % declarado en 9.1, `@ExceptionHandler(Exception.class)` que convertiría 401/403 de Spring Security en 500 cuando C-04 los use, y artefactos (design D10/D12, task 8.1) que quedaron desactualizados respecto de las desviaciones.

Visual: `design.md` registra `Decision: no (no UI)`; no corresponde comparación contra mockups (`Visual verify: n/a`).

## Resultados de ejecución (corridos por el verificador)

| Suite | Comando | Resultado |
|-------|---------|-----------|
| Unitarios (`test`) | `./gradlew check --rerun-tasks` (desde `backend/`, 4 min 7 s) | 15 clases, **96 tests, 0 fallos, 0 errores, 0 skipped** |
| Integración (`integrationTest`, zonky) | idem | 13 clases, **51 tests, 0 fallos, 0 errores, 0 skipped** |
| Total | | **147 tests verdes** |
| OpenSpec | `openspec validate c-02-core-models-shared --strict` | `Change 'c-02-core-models-shared' is valid` |
| V1 intacta | `git diff --stat -- .../V1__baseline.sql` | sin cambios (V2 es archivo nuevo) |

Conteos parseados de `backend/build/test-results/{test,integrationTest}/*.xml`.

Cobertura de líneas (JaCoCo, `test.exec` + `integrationTest.exec`), clases nuevas de `shared` y `auth`: todas al 100 % salvo `shared/api/ApiExceptionHandler` con 33 cubiertas / 14 sin cubrir = **70,2 %** (líneas sin cubrir: 79-87 `handleHandlerMethodValidationException`, 107-108 `lastCode`, 121 rama por defecto de `statusOf`, 128 `406`, 131 rama por defecto de `codeFor`).

## 1. Completitud

- Tareas marcadas `[x]` (40): todas tienen código de producción y test correspondientes verificados en el árbol (ver matriz). Se inspeccionaron `V2__core_models.sql`, `AppUser`, `Practice`, `InstallationPractice`, `PracticeTime`, `ZoneConversions`, `InstantRange`, `ClockConfiguration`, `BootstrapDentist`, `BootstrapDentistRunner`, `BootstrapCredentials`, `PasswordConfiguration`, `Argon2PasswordHasher`, `ApiExceptionHandler`, `ProblemDetails`, excepciones de dominio y repositorios.
- Tareas abiertas (conocidas, ver "Pendientes manuales"): 1.3 y 9.3.
- Imprecisión en una tarea marcada: **9.1** dice "cobertura de líneas >= 80 %" para las clases nuevas y `ApiExceptionHandler` está en 70 % (WARNING W1). **8.1** habla de `@WebMvcTest` pero se implementó MockMvc standalone (ver desviaciones).
- `CHANGES.md` marca C-02 como `[~] implementado, pendiente de verify`; tras este verify corresponde pasarlo a verificado/archivar.

## 2. Matriz de cobertura de escenarios

Leyenda: OK = test que realmente asserta el comportamiento. PARCIAL = cubierto de forma indirecta o solo a nivel unitario.

### dentist-bootstrap
| Escenario | Test | Estado |
|-----------|------|--------|
| Primera puesta en marcha | `BootstrapDentistRunnerIT.elArranqueCreaElDentistaYElLogSoloTraeElId` (arranque real; 1 cuenta, email normalizado, `must_change_password`, hash `$argon2id$`) + `BootstrapDentistTest.sinDentistaYConVariablesValidasCrea...` | OK |
| Re-arranque con las mismas variables | `BootstrapDentistRunnerIT.volverAEjecutarElRunnerNoCreaNiModificaNada` (compara email+hash+flag antes/después) | OK |
| Re-arranque con otra contraseña | mismo test (segunda corrida con otro email y otra contraseña, lista idéntica) | OK |
| Re-arranque sin variables | `BootstrapDentistTest.conUnDentistaExistenteSeOmiteSinLeerVariablesNiGuardar` (0 lecturas de variables) | OK (unitario; no hay IT de arranque sin variables) |
| Contraseña corta | `BootstrapCredentialsTest.rechazaContrasenaCorta...` (menciona variable, no el valor) + `BootstrapDentistTest.contrasenaCortaFallaYNoGuardaNada` | OK (ver S2: el `hasMessageNotContaining("corta ")` del segundo es trivial) |
| Email inválido | `BootstrapCredentialsTest.rechazaEmailSinFormato...` y `rechazaEmailDeMasDe254Caracteres` | OK (no hay variante a nivel caso de uso) |
| Log del alta | `BootstrapDentistRunnerIT.elArranqueCreaElDentistaYElLogSoloTraeElId` (patrón `id=<uuid>`, sin email ni password en la salida capturada) | OK |

### runtime-configuration (delta)
| Escenario | Test | Estado |
|-----------|------|--------|
| Falta `DB_PASSWORD` / Configuración completa | tests de C-01 (`RequiredConfiguration*`, `LocalDatabaseCredentialsIT`) siguen verdes | OK |
| Falta variable de bootstrap en instalación nueva | `BootstrapDentistTest.sinDentistaYSinContrasenaFalla...` (null y en blanco; no muestra el email) y `...SinEmailFalla...` | PARCIAL: unitario; no se prueba que el runner aborte el contexto (`ApplicationRunner` propaga la excepción por contrato de Spring Boot) |
| Variables innecesarias en instalación existente | `conUnDentistaExistenteSeOmite...` + el runner IT | OK |

### practice-installation
| Escenario | Test | Estado |
|-----------|------|--------|
| Base recién migrada | `PracticeSchemaIT.existeExactamenteUnConsultorio...` (id literal, zona BA, contactos NULL, una sola fila) | OK |
| Segunda fila rechazada | `PracticeSchemaIT.unSegundoConsultorioEsRechazadoPorLaFilaUnica` (migrator; mensaje con `practice_single_row`) | OK |
| Identificador disponible | `PracticeRepositoryIT.elConsultorioDeLaInstalacionEsElSembradoPorV2` | OK |
| Consultorio ausente | `InstallationPracticeTest.sinFilasFallaDiciendoQueFaltaElConsultorio` | PARCIAL: unitario, no se prueba el fallo de arranque real |
| Nombre o zona vacíos | `PracticeSchemaIT.nombreOZonaVaciosSonRechazados` | OK |
| Zona desconocida | `InstallationPracticeTest.conZonaInvalidaFallaSinMostrarOtrosDatos` (`Mars/Olympus`, no lo muestra ni el id) | PARCIAL: unitario |

### user-accounts
| Escenario | Test | Estado |
|-----------|------|--------|
| Consultorio inexistente (FK) | `AppUserSchemaIT.rechazaUnConsultorioInexistente` | OK |
| Rol DENTIST aceptado | `AppUserSchemaIT.aceptaUnaFilaValida...` + `AppUserRepositoryIT.guardaYRelee` | OK |
| Rol no admitido | `AppUserSchemaIT.rechazaRolesFueraDeLaLista` | OK |
| Email normalizado | `AppUserTest.newDentistNormaliza...` + `AppUserRepositoryIT.guardaYRelee` | OK |
| Email duplicado otras mayúsculas | `AppUserRepositoryIT.emailConOtraCapitalizacionEsDuplicado` + `AppUserSchemaIT.rechazaEmailDuplicado` | OK |
| Hash Argon2id con sal | `Argon2PasswordHasherTest.generaHashesArgon2id...` | OK |
| Verificación de contraseña | `Argon2PasswordHasherTest.matchesVerdaderoConLaOriginalYFalsoConOtra` | OK |
| Cuenta recién creada | `AppUserTest.newDentistNormaliza...` (failed=0, locked/lastLogin vacíos, createdAt) | OK |
| Intentos fallidos negativos | `AppUserSchemaIT.rechazaIntentosFallidosNegativos` | OK |
| Identificador v7 | `AppUserRepositoryIT.guardaYRelee` (`id().version() == 7`) | OK |

### time-and-zones
| Escenario | Test | Estado |
|-----------|------|--------|
| Reloj fijo en tests | `PracticeTimeTest` (now/today con `Clock.fixed`) + `ClockConfigurationTest` (UTC) | OK |
| Mañana en BA / Cambio de día | `ZoneConversionsTest.toLocal...` (dos tests) | OK |
| Hora local a UTC / inexistente / ambigua | `ZoneConversionsTest.toInstant...` (tres tests, `America/New_York`) | OK |
| Día completo en BA | `ZoneConversionsTest.dayBounds...` (+ día de 23 h en NY) | OK |
| Ida y vuelta por la base | `AppUserRepositoryIT.guardaYRelee` (`createdAt` idéntico tras releer por otra transacción) | OK |

### error-responses
| Escenario | Test (`ApiExceptionHandlerTest`) | Estado |
|-----------|------|--------|
| Recurso inexistente 404 | `notFoundDeDominioEs404ConCodigoYTituloGenerico` | OK |
| Conflicto 409 | `conflictoDeDominioEs409` | OK |
| Regla de negocio 422 | `reglaDeNegocioIncumplidaEs422` | OK |
| Violación de unicidad | `violacionDeIntegridadEs409GenericoSinMensajeDeLaBase` (sin email, restricción ni "duplicate") | OK |
| JSON mal formado | `jsonMalFormadoEs400SinElDniNiElMensajeDelParser` | OK |
| Campo obligatorio vacío | `validacionEs400ConFieldYCodigoPorCampoSinValoresEnviados` (name/NotBlank, note/Size, sin valor) | OK |
| Excepción no controlada | `errorInesperadoEs500ConErrorIdYSinFiltrarElMensajeNiAlLog` (errorId UUID, log con errorId y `RuntimeException`, sin email) | OK |
| Método no permitido | `metodoNoSoportadoEs405` | OK |
| Parámetro con tipo inválido | `parametroNumericoConTextoEs400SinElValor` | OK |
| (extra) 415, 404 ruta inexistente, validación de parámetros de método | tests homónimos | OK, pero ver W1: JaCoCo indica que `handleHandlerMethodValidationException`/`lastCode` no se ejecutan en `validacionDeParametrosDeMetodo...` |

### module-boundaries (delta)
| Escenario | Test | Estado |
|-----------|------|--------|
| Dominio que usa infraestructura | `ModuleBoundariesRulesTest` (C-01, `violacion_dominio`) | OK |
| Dominio que usa Spring | `ModuleBoundariesRulesTest.dominioQueUsaSpringStereotypeFalla...` (nombra `PacienteSpring` y `org.springframework.stereotype.Component`) | OK |
| Dominio con anotaciones de persistencia | `ModuleBoundariesRulesTest.dominioConAnotacionesJpaEHibernateEsValido` | PARCIAL: el fixture usa `@MappedSuperclass`, no `@Entity` (ver desviaciones) |
| (producción) las nuevas entidades cumplen la regla | `ProductionArchitectureTest` verde | OK |

### database-migrations (delta)
| Escenario | Test | Estado |
|-----------|------|--------|
| Consultorio sin alta ni baja | `PracticeSchemaIT.laAplicacionNoPuedeInsertarNiBorrarConsultorios` + `laAplicacionPuedeLeerYActualizarElConsultorio` | OK |
| Cuentas sin borrado | `AppUserSchemaIT.laAplicacionPuedeActualizarPeroNoBorrarUsuarios` (+ insert/select cubiertos en los demás casos) | OK |
| Esquema coincidente | `PracticeSchemaIT.lasMigracionesV1YV2QuedanRegistradasComoExitosas` + contexto con `ddl-auto=validate` en todos los IT + `MigrationValidationIT` (C-01) | OK |

Resumen: 0 escenarios sin cobertura; 6 con cobertura PARCIAL (solo unitaria o fixture distinto), todos aceptables.

## 3. Coherencia con design.md

- **D1 UUIDv7**: `@UuidGenerator(style = VERSION_7)` en `AppUser` y `Practice`; `id uuid PRIMARY KEY` sin DEFAULT; seed con literal `01900000-0000-7000-8000-000000000001`; el IT verifica `version() == 7`. Coherente.
- **D2 Argon2id**: `Argon2PasswordEncoder(16, 32, 1, 19456, 2)`; el test comprueba `m=19456,t=2,p=1`; puerto `PasswordHasher` + bean `PasswordEncoder`. Única dependencia nueva de cripto: `bcprov-jdk18on:1.87` (versión fijada en `build.gradle.kts`, ver S5).
- **D5/D4**: `domain` sin imports de `org.springframework` (verificado por `ProductionArchitectureTest` con la regla endurecida `org.springframework.` + `java.sql.`). Ubicación de piezas por módulo coincide con la tabla de D4.
- **D6/D7 `V2__core_models.sql`**: coincide con el bosquejo (restricciones nombradas, `practice_single_row ON ((true))`, `app_user_email_key` sobre `lower(email)`, índice de FK, seed, `REVOKE INSERT, DELETE ON practice` y `REVOKE DELETE ON app_user` con `${app_role}`). Cabecera con comentario. Todo probado por IT con roles reales.
- **D8**: `InstallationPractice` perezoso, caché en `volatile` con doble chequeo, exige exactamente una fila, `ZoneId.of` con mensaje sin datos; el runner lo consulta primero (vía `BootstrapDentist`, que llama `practice.id()` antes que nada), de modo que falta de consultorio o zona inválida hacen fallar el arranque. Un fallo no se cachea (reintenta), correcto.
- **D9**: `Clock.systemUTC()`; sin `Instant.now()`/`LocalDate.now()` en `src/main` (grep limpio, solo aparece en un comentario). `ZoneConversions` maneja hueco (`getValidOffsets` vacío) y ambigüedad (`ZonedDateTime.ofLocal(..., null)` = desfase más temprano); `dayBounds` por `atStartOfDay(zone)` maneja días de 23/25 h.
- **D10**: `ProblemDetail` con título genérico por estado y `code`; sin `detail` dinámico; `DomainException` solo lleva `code`; validación → `errors:[{field, code}]` sin `rejectedValue`; 500 con `errorId` y log solo con id + nombre de clase; `handleExceptionInternal` reemplaza el cuerpo del framework. Ver W2/S3/S4 por detalles.
- **D11**: runner `ApplicationRunner` que lee `Environment.getProperty` (sin mapeo en `application.yml`), `BootstrapDentist` transaccional, idempotencia por "existe un DENTIST", `BootstrapCredentials` con `toString` redactado y errores que nombran la variable. Logs: solo `id` o "Bootstrap omitido".
- **D12**: dependencias nuevas = `spring-boot-starter-validation` y `bcprov-jdk18on` (diff de `build.gradle.kts` de 2 líneas). Sin dependencias de frontend. Sin `spring-boot-starter-webmvc-test`.

### Reglas duras de CLAUDE.md contra el diff
1. Sin `@Autowired` (grep vacío); inyección por constructor en todos los beans. OK.
2. Sin entidades JPA en API: C-02 no expone endpoints; el handler solo maneja excepciones. OK.
3. Solo Flyway: `V1__baseline.sql` sin diff, `V2` nuevo; `ddl-auto=validate`. OK.
4. Deny-by-default intacto (`SecurityConfig` sin cambios; C-02 no agrega endpoints). Errores en formato único vía `@RestControllerAdvice`. OK (ver W2).
5. N/A (no hay HC).
6. Logs: grep de `LOG.`/`System.out`/`printStackTrace` en `src/main`: solo 3 sentencias (runner x2 con id/sin datos, handler 500 con `errorId` y nombre de clase). Sin PII. OK.
7. N/A.
8. Timestamps `timestamptz` + `Instant`; `AppUser.toString` y `BootstrapCredentials.toString` sin datos sensibles. OK.
9-12. Backend: N/A para 9-11; 12: solo las dependencias aprobadas. OK.
13-14. No se verificó commits (nada commiteado). 
15. Sin credenciales en código ni `application.yml` (los `BOOTSTRAP_ADMIN_*` solo se leen por nombre en `BootstrapCredentials`/runner, sin valores ni defaults). Los IT generan credenciales aleatorias por JVM. OK.
- ArchUnit de fronteras: `ProductionArchitectureTest` y `ModuleBoundariesRulesTest` verdes; `auth` solo depende de `shared`.

## Issues

### CRITICAL
Ninguno.

### WARNING
- **W1** `backend/src/main/java/ar/turnosodontologia/consultorio/shared/api/ApiExceptionHandler.java:77-88,106-109` cobertura de líneas 70,2 % (task 9.1 afirma >= 80 %). Según JaCoCo, `handleHandlerMethodValidationException` y `lastCode` no se ejecutan ni siquiera en `validacionDeParametrosDeMetodoEs400ConCodigoDeRestriccionSinElValor`, que igual pasa con `errors[0].field=q`/`Size`; es decir, ese test está llegando al resultado por otro camino (o el override es inalcanzable en la configuración standalone) y el comportamiento "validación de parámetros de método en la app real" no está realmente probado. Corregir: depurar qué excepción produce `/probe/constrained` (p. ej. `@Validated` en el controller probe o assert sobre la clase de la excepción resuelta con `MvcResult.getResolvedException()`), agregar tests para las ramas `406`, `statusOf` por defecto y `codeFor` por defecto, y re-medir; o corregir el texto de 9.1.
- **W2** `ApiExceptionHandler.java:56-63` `@ExceptionHandler(Exception.class)` capturará también `AccessDeniedException`/`AuthenticationException` lanzadas desde controllers o `@PreAuthorize` y las convertirá en 500 `INTERNAL_ERROR` (error clásico: se las come antes del filtro de Spring Security). Hoy no hay endpoints, por lo que no se manifiesta, pero C-04+ lo heredará. Corregir: excluir esas excepciones (`@ExceptionHandler` específico que las relance, o `if (error instanceof AccessDeniedException || error instanceof AuthenticationException) throw error;`) con un test, o dejar anotado como tarea explícita en C-04.
- **W3** Artefactos desactualizados respecto de la implementación: `design.md` D10 (§"Cómo se testea sin endpoints") y `tasks.md` 8.1 mencionan `@WebMvcTest`/`spring-boot-webmvc-test`; D12 no registra la versión fijada `bcprov-jdk18on:1.87`; D5/spec mencionan fixture con `@Entity` y el real usa `@MappedSuperclass`; D11/tarea 7.5 no mencionan que las credenciales entran por `EmbeddedPostgresSupport.Database.properties()`. Corregir: actualizar `design.md` (sección "Deviations" o ajuste de D10/D12) antes de archivar.

### SUGGESTION
- **S1** `ApiExceptionHandler.java:50-54` toda `DataIntegrityViolationException` responde 409 `CONFLICT`, incluso violaciones NOT NULL / CHECK / longitud (que son errores del servidor o 400/422). Considerar distinguir por `SQLState` (23505 -> 409; otros -> 500/422) sin exponer el mensaje.
- **S2** `BootstrapDentistTest.java:140` `hasMessageNotContaining("corta ")` (con espacio final) no puede fallar nunca; el mensaje real no contiene "corta". Usar la contraseña completa (`corta` no es el valor probado) o eliminar. El caso real ya está bien cubierto en `BootstrapCredentialsTest`.
- **S3** Respuesta `instance`: Spring rellena `ProblemDetail.instance` con la ruta de la petición al serializar (comportamiento del framework, no verificado empíricamente aquí). Si un path variable (p. ej. el token de gestión de turno de C-09) llegara en una ruta inexistente, volvería en el cuerpo y quedaría en proxies/logs del cliente. Agregar un test de ruta con valor y, si aparece, fijar `instance` a `null` en `ProblemDetails.of`.
- **S4** `ProblemDetails.java:33-35` el título para estados no listados (p. ej. 413) es "Solicitud invalida" y su `code` "BAD_REQUEST" (`ApiExceptionHandler.java:131`); aceptable, pero conviene sumar 413/429/503 cuando existan casos reales.
- **S5** `build.gradle.kts` fija `bcprov-jdk18on:1.87` fuera del BOM; anotar en D12 y revisar al actualizar Spring Boot (el BOM podría gestionarla en el futuro) y vigilar CVEs.
- **S6** `shared/domain/Practice.java:39` `Practice.existing(...)` es una fábrica pública solo para tests; permite construir una entidad con id asignado (un `save` haría `merge`; el rol `app` no tiene INSERT, así que falla en BD). Aceptable, pero documentar "solo tests" o moverla a un helper de test si Practice no necesita otro uso.
- **S7** `archfixture/violacion_dominio_spring/.../PacienteSpring.java` está anotado `@Component` y vive bajo el paquete escaneado: los contextos completos de los IT registran un bean vacío `pacienteSpring`. Inofensivo; usar un tipo de `org.springframework` que no sea estereotipo (p. ej. `org.springframework.lang`/`Lazy`) para no contaminar contextos.
- **S8** `AppUserRepository.findByEmail(String)` no normaliza: C-04 debe llamar a `AppUser.normalize(email)` antes. Documentar el contrato en el puerto o normalizar dentro de la implementación.
- **S9** La regla ArchUnit solo prohíbe `org.springframework.` y `java.sql.` en `domain`; el spec dice "las únicas dependencias de framework permitidas son jakarta.persistence y org.hibernate.annotations". No hay allowlist (p. ej. `org.hibernate.Session` pasaría). Aceptable en C-02; evaluar allowlist si crece el dominio.
- **S10** `BootstrapDentistRunner`: si dos instancias arrancan a la vez sin DENTIST, la segunda falla con `DataIntegrityViolationException` cuyo mensaje de PostgreSQL contiene el email y puede quedar en el log de fallo de arranque (regla 6). Caso de borde con una sola instancia en v1; mitigar al pasar a más de una réplica (lock advisory o capturar y relanzar sin mensaje).

## Evaluación de desviaciones reportadas por el implementador

| Desviación | Evaluación |
|-----------|------------|
| MockMvc standalone en vez de `@WebMvcTest` (controller probe en `ar.turnosodontologia.probe`) | **Aceptable**, con actualización de design necesaria (W3): evita sumar `spring-boot-starter-webmvc-test` (dependencia no aprobada, regla 12). Riesgo: standalone no usa la configuración real de Boot (Jackson, validator, advice scan); como no hay endpoints no hay otra forma, pero conviene un IT con un endpoint real cuando C-03 exponga uno (y ver W1). Ubicar el probe fuera del paquete escaneado es correcto. |
| Fixture `ok_dominio_jpa` con `@MappedSuperclass` en vez de `@Entity` | **Aceptable** (motivo válido: `@Entity` bajo el paquete escaneado rompería `ddl-auto=validate`). Evidencia algo más débil que el escenario literal; registrar en design (W3). La regla prohíbe solo Spring, así que `@Entity` pasaría igual. |
| Credenciales de bootstrap inyectadas vía `EmbeddedPostgresSupport.Database.properties()` | **Aceptable**: aleatorias por JVM, no versionadas; todos los IT que arrancan contexto las reciben, incluso `BootstrapDentistRunnerIT` con su propia base. Registrar en D11/tarea 7.5. |
| `Practice.existing(...)` fábrica pública | **Aceptable** (S6). Solo la usan tests; sin uso en `src/main`. |
| `bcprov-jdk18on` fijado en 1.87 | **Aceptable**, ya previsto por D2/tarea 1.1 ("fijar la última estable"); falta anotar la versión en D12 (S5). |

## Pendientes manuales (no bloqueantes)

- **1.3**: `.env.example` bloqueado por permisos del agente; falta que una persona actualice el comentario de `BOOTSTRAP_ADMIN_EMAIL/PASSWORD` (consumidas por C-02, solo requeridas en el primer arranque). `ConfigurationHygieneTest` pasa (verifica `change-me` y mención de C-02). El README ya documenta el primer arranque de forma coherente.
- **9.3**: arranque manual con `bootRun` contra el PostgreSQL local (primer `bootRun` crea el DENTIST, el segundo loguea "Bootstrap omitido"; `GET /actuator/health` = `UP`; sin email ni contraseña en el log). No ejecutado: requiere PostgreSQL local y `.env` reales. El equivalente con PostgreSQL embebido está cubierto por `BootstrapDentistRunnerIT`.

## Lo que no pude verificar

- Que `ProblemDetail.instance` no devuelva la ruta pedida (S3) ni qué excepción resuelve el test de validación de parámetros (W1): no se ejecutaron sondas adicionales para no modificar tests.
- Arranque real contra PostgreSQL local (9.3) y aborto del contexto por variable de bootstrap faltante a nivel de proceso (solo unitario + contrato de `ApplicationRunner`).
- Revisión de commits conventional (nada commiteado).

## Siguiente acción recomendada

1. Resolver W1 (tests/cobertura de `ApiExceptionHandler`) y W3 (actualizar design D10/D12 y notas de desviaciones); decidir W2 (corregir ahora o dejarlo como tarea explícita de C-04).
2. Que Lucas/Elian completen 1.3 y 9.3 manualmente.
3. Luego `/opsx:archive c-02-core-models-shared` (el orquestador sincroniza los deltas con `openspec/specs/` y actualiza `CHANGES.md`). Commit con `feat(C-02): ...` solo cuando el usuario lo pida.

## Correcciones posteriores (2026-11-21)

Aprobadas por Lucas (líder). Se corrigieron W1–W3; ver design.md (D5, D10, D11, D12) y tasks.md (8.1, 7.8, nueva 8.7).

- **W1 (resuelto)**: el test `validacionDeParametrosDeMetodo...` ahora asserta `getResolvedException()` de tipo `HandlerMethodValidationException`; pasó sin cambios de producción, así que el override y `lastCode` sí se ejecutan en MockMvc standalone (la lectura de 70 % del verify no reflejaba este camino). Tests agregados en `ApiExceptionHandlerTest`/`ProblemProbeController`: `DomainException` sin subtipo (400, `statusOf` por defecto), 406 `NOT_ACCEPTABLE`, 413 (`codeFor` por defecto 4xx -> `BAD_REQUEST`), 503 (`codeFor` por defecto 5xx -> `INTERNAL_ERROR`). Cobertura de líneas de `ApiExceptionHandler` medida con `./gradlew check jacocoTestReport --rerun-tasks` (test + integrationTest): **100 % (50/50 líneas)**, antes 70,2 %. No había ramas muertas.
- **W2 (resuelto)**: `handleUnexpected` relanza `AccessDeniedException` y `AuthenticationException`. RED visto: 2 tests fallaban (se convertían en 500); GREEN con 2 tests (`/probe/denied`, `/probe/unauthenticated`) que verifican que la excepción se propaga. Otras excepciones siguen en 500 con `errorId` y sin mensaje/PII (test existente).
- **W3 (resuelto)**: design.md actualizado (D5 `@MappedSuperclass`, D10 MockMvc standalone + probe + decisión W2, D11 credenciales vía `EmbeddedPostgresSupport.Database.properties()`, D12 `bcprov-jdk18on:1.87` fuera del BOM); tasks.md 8.1, nota en 7.8 y 8.7; escenario del delta spec `module-boundaries` ya no exige `@Entity` literal. `openspec validate c-02-core-models-shared --strict`: válido.
- Re-ejecución completa `./gradlew check --rerun-tasks`: BUILD SUCCESSFUL. Unitarios 15 clases, **102 tests** (antes 96), IT 13 clases, **51 tests**; 0 fallos/errores/skipped (**153 tests**).
- Pendientes sin cambios: 1.3 y 9.3 (manuales). Sin commit.
