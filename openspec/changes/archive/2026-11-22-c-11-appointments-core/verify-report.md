# Verify report: C-11 `appointments-core`

Fecha: 2026-11-22 | Rama: dev-lucas (working tree sin commitear) | Revisor: sub-agente verificador independiente (solo lectura sobre código; solo se creó este informe) | Governance: CRÍTICO

## Veredicto: PASS WITH WARNINGS

Sin issues CRITICAL. `openspec validate c-11-appointments-core --strict` es válido, tasks.md está 57/57 `[x]` y `./gradlew test integrationTest jacocoTestReport --rerun-tasks` termina en BUILD SUCCESSFUL (559 unit + 362 IT, 0 fallos). Las 7 delta specs tienen cobertura de tests en todos sus escenarios (3 PARCIALES aceptables). El diseño D1–D22, incluidos los desvíos aprobados (fix de `ScheduleBlockSchemaIT`, lock en toda mutación D3/D18), está implementado como se describe. Queda **1 WARNING**: el listado `GET /api/v1/appointments` no limita la amplitud del rango. El resto son sugerencias de robustez de tests y casos borde.

Visual: `design.md` registra `Decision: no (no UI)`; no corresponde comparar contra mockups (`Visual verify: n/a`).

## Resultados de ejecución (corridos por el verificador)

| Suite | Comando | Resultado |
|-------|---------|-----------|
| Unitarios (`test`) | `./gradlew test integrationTest jacocoTestReport --rerun-tasks --continue` (desde `backend/`, 9 min 28 s) | 61 clases, **559 tests, 0 fallos, 0 errores, 0 skipped** |
| Integración (`integrationTest`, zonky, sin Docker) | idem | 56 clases, **362 tests, 0 fallos, 0 errores, 0 skipped** |
| Total | | **921 tests verdes** (coincide con lo esperado: ~559 / ~362) |
| Flaky conocido | `OdontogramEntryTest.toStringNoExponeNotasMotivoPiezaNiEstado` | pasó en esta corrida (preexistente, no es de C-11) |
| OpenSpec | `openspec validate c-11-appointments-core --strict` | `Change 'c-11-appointments-core' is valid` |
| Tareas | `grep` sobre tasks.md | 57 `[x]`, 0 `[ ]` |
| V1–V12 intactas | `git diff --stat HEAD -- backend/src/main/resources/db/migration` | sin cambios; `V13__appointment.sql` es archivo nuevo |
| Tests deshabilitados / sleeps | `grep -rn "@Disabled\|Thread.sleep" backend/src/test/java` | ninguno |

Nota: la primera corrida (`./gradlew test integrationTest`) devolvió `UP-TO-DATE` sin ejecutar; por eso se re-ejecutó con `--rerun-tasks`. Los conteos se parsearon de `backend/build/test-results/{test,integrationTest}/*.xml`.

### Cobertura JaCoCo (`test.exec` + `integrationTest.exec`)

| Elemento | Líneas | Ramas |
|----------|--------|-------|
| paquete `agenda.application` | 221/221 (100 %) | — |
| paquete `agenda.domain` | 272/275 (98,9 %) | — |
| paquete `agenda.infrastructure` | 160/162 (98,8 %) | — |
| paquete `agenda.api` | 31/31 (100 %) | — |
| `AppointmentService` | 95/95 (100 %) | 22/22 |
| `Appointment` | 77/78 (98,7 %) | 19/24 (79 %) |
| `WorkingHoursFit` | 22/22 (100 %) | 15/16 |
| `AppointmentsController` | 29/31 (93,5 %): sin cubrir el `catch (DateTimeException)` de `parseInstant` | 2/2 |
| `ScheduleBlockService` | 51/51 (100 %) | 16/16 |
| `JpaPracticeLock` | 7/7 (100 %) | — |
| `ConstraintViolations` | 7/7 (100 %) | 10/12 |
| `ApiExceptionHandler` | 45/69 (65,2 %) | preexistente (W1 del verify de C-02); las líneas nuevas de C-11 están cubiertas |

Objetivo de la task 11.1 (`agenda` >= 80 %): **cumplido**.

## 1. Completitud

- 57/57 tareas `[x]`. Para cada grupo (V13, dominio, `WorkingHoursFit`, servicio, lock, controller, eventos, adaptadores C-09, fronteras, handler, concurrencia, grupo 12 del desvío D3) hay código de producción y tests en el árbol.
- Desvío aprobado 1.1 (fix preexistente `ScheduleBlockSchemaIT`): el diff reemplaza la lista fija `("1","2","3","5","7","8")` por `containsSubsequence("1","7","8").doesNotHaveDuplicates().last()=="8"`. Corrige el problema del merge C-07/C-09 sin debilitar lo que el test verifica (que V8 sea la última del checkpoint y no haya duplicados).
- Desvío aprobado D3/D18 (lock en toda mutación): implementado en `AppointmentService.lockAndLoad` y probado (ver §4 y §6).
- No verificable: tasks 1.1/11.2 dicen que el baseline y la trazabilidad quedan anotados "en el resumen del apply". Ese resumen no está en el repo, así que este informe aporta la matriz de trazabilidad.

## 2. Matriz de trazabilidad (requisito → escenarios → tests)

Leyenda: OK = un test que realmente verifica el comportamiento. PARCIAL = cubierto de forma indirecta o solo en otra capa. Abreviaturas: `EP` = `AppointmentsEndpointsIT`, `ST` = `AppointmentServiceTest`, `AT` = `AppointmentTest`, `WF` = `WorkingHoursFitTest`, `CI` = `AppointmentConcurrencyIT`, `EV` = `AppointmentEventsIT`, `SEC` = `AppointmentsSecurityIT`, `SCH` = `AppointmentSchemaIT`, `PRV` = `AppointmentPrivilegesIT`, `SBA` = `ScheduleBlocksAffectedAppointmentsIT`, `AFF` = `AffectedAppointmentsIT`, `RES` = `AppointmentRepositoryIT`, `MBR` = `ModuleBoundariesRulesTest`, `AAC` = `AgendaApiContractsTest`, `AEH` = `ApiExceptionHandlerTest`, `OPS` = `AppointmentOperationsAdapterTest`.

### appointments

| Requisito | Escenario | Test(s) | Estado |
|-----------|-----------|---------|--------|
| Alta manual | Alta exitosa | `EP.altaExitosaDevuelve201ConLocationYValoresDelServidor`, `ST.altaFelizCreaTurnoConfirmadoInterno...` | OK |
| | Inicio en el pasado | `EP.inicioPasadoEs422AppointmentInPastYNoSeCreaNada`, `ST.inicioPasadoOIgualAlActual...SinLock` | OK |
| | Inicio igual al instante actual | `AT.elInicioDebeSerEstrictamentePosteriorAlInstanteActual`, `ST.inicioPasadoOIgualAlActual...` (reloj fijo) | OK (unit) |
| | Campos obligatorios / notas > 1000 | `EP.camposObligatoriosAusentesONotasLargasDan400SinDevolverValores`, `AT.rechazaNotasDeMasDe1000...` | OK |
| Duración snapshot | Snapshot al crear | `AT.creaTurnoConfirmadoConFinCalculado...`, `ST.altaFeliz...` | OK |
| | Prestación editada después | `EP.laDuracionSnapshotNoCambiaSiLaPrestacionSeEditaDespues` | OK |
| | Fin enviado por el cliente | `EP.elFinYLaDuracionEnviadosPorElClienteSeIgnoran` | OK |
| Prestación existente y activa | Inexistente / Inactiva / No pública | `EP.prestacionInexistenteInactivaYNoPublica`, `ST.prestacionInexistenteODeOtroConsultorioEsNotFound`, `ST.prestacionInactivaEsReglaDeNegocioYNoTomaElLock`, `ST.altaConPrestacionActivaNoPublicaEsAceptada` | OK |
| Paciente existente y activo | Activo / Inexistente / Dado de baja | `EP.pacienteInexistenteEs404InactivoEs409SinDatosPersonales`, `ST.pacienteInexistente...`, `ST.pacienteInactivoEsConflict...`; activo vía `EP.altaExitosa...` | OK |
| No solapamiento entre turnos | Adyacente | `EP.adyacenteSeAceptaYSolapadosSeRechazanAunConSobreturno`, `ST.turnoAdyacenteAOtroVigente...` | OK |
| | Parcial, contenido o idéntico | `EP.adyacenteSeAcepta...`, `EP.unTurnoMasLargoQueContieneAlExistenteEsSlotTaken`, `EP.elErrorDeSolapamientoNoRevelaDatos...` | OK |
| | Sobreturno superpuesto | `EP.adyacenteSeAceptaYSolapadosSeRechazanAunConSobreturno`, `ST.turnoSolapadoConOtroVigenteEsConflictAunComoSobreturno` | OK |
| | Cancelado libera / COMPLETED-NO_SHOW ocupan | `EP.unTurnoCanceladoLiberaElRangoYLosCompletadosONoShowSiguenOcupando`, `SCH.elSolapamientoDeTurnosNoCancelados...23P01` | OK |
| Carrera entre altas | Mismo slot / parcial / adyacentes | `CI.dosAltasDelMismoSlot...`, `CI.dosAltasParcialmenteSolapadas...`, `CI.dosAltasDeSlotsAdyacentes...` (servicio, no MockMvc: verifica códigos de dominio y estado final de la base) | OK |
| No solapamiento con bloqueos | Dentro / adyacente | `EP.turnoDentroDeUnBloqueoEs409SinRevelarElMotivoYAdyacenteSeAcepta` | OK |
| | Sobreturno dentro de un bloqueo | `EP.sobreturnoDentroDeUnBloqueoFueraDeHorarioEsRechazado`, `ST.turnoDentroDeUnBloqueoEsRechazadoAunComoSobreturno` | OK |
| Horario y sobreturno | Dentro del tramo / excede / contiguos / hueco / bordes / medianoche / día sin atención / sobreturno fuera / hora local | `EP.dentroDelTramoYBordeFinalExactoSeAcepta`, `EP.queExcedeElFinDelTramoOEmpiezaAntesEs422`, `EP.tramosContiguosSeFusionanYUnHuecoNoSeCruza`, `EP.cruceDeMedianocheDiaSinAtencionYHoraLocalDelConsultorio`, `EP.sobreturnoFueraDeHorarioSeAceptaYSeDevuelve`; `WF` (10 tests, incluidos tramos desordenados/encadenados y otro weekday) | OK |
| Parámetros públicos no aplicados | Anticipación mínima / granularidad | `EP.noAplicaAnticipacionMinimaNiGranularidad`, `ST.noAplicaAnticipacionMinimaHorizonteMaximoNiGranularidad` | OK |
| | Más allá del horizonte (90 días) | `ST.noAplicaAnticipacionMinima...` (el servicio no consulta `booking_settings`); en `EP` las fechas son de 2099, por lo que el horizonte queda excedido de hecho | PARCIAL (aceptable) |
| Consulta y listado | Existente sin PII / inexistente en todas las operaciones | `EP.consultaPorIdNoIncluyeDatosPersonalesYElInexistenteEs404` (GET, cancel, complete, no-show, reschedule) | OK |
| | Listado por rango (todos los estados, cruza `from`) | `EP.listadoPorRangoDevuelveTodosLosEstadosOrdenadosIncluidoElQueCruzaFrom`, `RES.findInRange...` | OK |
| | Rango inválido | `EP.rangoDeListadoInvalidoEs400InvalidRange` (5 variantes, sin eco de valores) | OK |
| Cancelación | Futuro / ya iniciado / terminal | `EP.cancelaUnTurnoFuturoYUnoYaIniciadoYNoUnTerminal`, `AT.cancela...`, `AT.todaAccionSobreUnEstadoTerminal...` | OK |
| Resultado | Completar/ausente / exactamente al inicio / antes / terminal | `EP.completaYMarcaAusente...SigueOcupandoSuRango`, `EP.marcarResultadoAntesDelInicioEs422YSigueConfirmado`, `AT.completaYMarcaAusente...IncluidoElInstanteExacto`, `AT.todaAccionSobreUnEstadoTerminal...` | OK (el instante exacto, solo en unit con reloj fijo) |
| Reprogramación atómica | Exitosa / ocupado / solapado con el propio / prestación cambiada / bloqueado o fuera de horario / no reprogramable / inicio pasado | `EP.reprogramaConservandoIdYLiberaElRangoAnterior`, `EP.reprogramarAUnRangoOcupadoEs409YElOriginalQuedaIntacto`, `EP.reprogramarSolapandoConElPropioRangoAnteriorEsValido`, `EP.reprogramarConservaLaDuracionSnapshot...`, `EP.reprogramarADentroDeUnBloqueoOFueraDeHorario...`, `EP.turnoNoReprogramableYInicioPasado` | OK |
| Serialización turnos/bloqueos | Turno y bloqueo simultáneos | `CI.turnoYBloqueoQueSeIntersectanNuncaDejanUnTurnoDentroSinInformarlo` (12 iteraciones) + `CI.altaDeTurnoYAltaYEdicionDeBloqueoEsperanAlLock...` (determinista) | OK |
| | Rangos disjuntos | `CI.operacionesSobreRangosDisjuntosConfirmanAmbas` | OK |
| | Cancelar vs reprogramar | `CI.reprogramarYCancelarElMismoTurnoNuncaPierdeLaCancelacion` | OK (discrimina el bug: sin el fix, la cancelación se pierde) |
| | Completar vs reprogramar | `CI.completarYReprogramarUnTurnoYaIniciadoDejaElEstadoTerminal` | PARCIAL: ver S3, no discrimina el lock |
| | Error no bloquea las siguientes | `CI.trasUnRechazoLasOperacionesSiguientesNoQuedanBloqueadas` | OK |
| Eventos | Confirmar / cancelar y reprogramar / sin evento ante rechazo / resultado sin eventos | `EV` (listener `@TransactionalEventListener(AFTER_COMMIT)` real, incluido rollback forzado), `ST.*Publica*`, `ST.unRechazoNoPublicaNingunEvento` | OK |
| Operaciones reutilizables | Desde otro módulo: éxito / conflicto / fuera de horario o bloqueada | `OPS` (3 tests, servicio mockeado) | PARCIAL: el adaptador delega en `AppointmentService` y las reglas se prueban en ese servicio, pero no hay IT del puerto contra la base |
| | Contrato sin alta ni cancelación | `AAC.appointmentOperationsDeclaraUnicamenteLaReprogramacion` | OK |
| Sin borrado y logs mínimos | Sin endpoint DELETE | `EP.noExisteEndpointDeBorradoYElTurnoNoSeElimina`, `PRV.laAplicacionNoPuedeBorrarNiTruncarTurnos` | OK |
| | Notas con datos personales | `EP.notasConDatosPersonalesNoApareceNiEnRespuestaNiEnLogsYElLogLlevaSoloIds` (ListAppender en root) | OK |

### api-access-policy

| Escenario | Test | Estado |
|-----------|------|--------|
| Consulta anónima 401 sin revelar existencia | `SEC.consultasAnonimasDevuelven401SinRevelarSiElTurnoExiste` | OK |
| Mutación anónima 401 / rol insuficiente 403 | `SEC.mutacionesAnonimasDevuelven401...`, `SEC.rolDistintoDeDentistDevuelve403...` | OK |
| Mutación sin CSRF 403 `CSRF_REQUIRED` | `SEC.cadaMutacionSinTokenCsrfDevuelve403CsrfRequiredYElTurnoNoCambia` | OK |
| Consulta sin CSRF 200 | `SEC.consultasAutenticadasNoExigenCsrf` | OK |

### database-migrations

| Escenario | Test | Estado |
|-----------|------|--------|
| Base migrada hasta V12 → V13 | `SCH.laMigracionV13SeAplicaDesdeUnaBaseMigradaHastaV12...`, `SCH.laMigracionV13QuedaRegistradaComoExitosa`, `SCH.appointmentTieneColumnasRestriccionesEIndicesDelDiseno` | OK |
| Valores de dominio / notas | `SCH.laBaseRechazaValoresDeDominioFueraDeRango`, `SCH.notasAdmiteHasta1000CaracteresYRechazaMas` | OK |
| Coherencia de cancelación | `SCH.laCancelacionExigeCancelledByYCancelledAtJuntosSoloEnCancelled` | OK |
| Rango generado `[)` | `SCH.elPeriodoGeneradoEsSemiabierto` | OK |
| Rango inválido | `SCH.laBaseRechazaRangosInvertidosOVacios` | OK |
| Solapamiento 23P01 (CONFIRMED/COMPLETED/NO_SHOW) | `SCH.elSolapamientoDeTurnosNoCanceladosSeRechazaConSqlState23P01` (también verifica que el mensaje real nombre `appointment_no_overlap`) | OK |
| Adyacencia, cancelados, otro consultorio | `SCH.adyacenciaCanceladosYOtroConsultorioSeAceptan`, `SCH.elMismoRangoEnOtroConsultorioSeAcepta` (DROP de `practice_single_row` en una transacción revertida) | OK |
| Reactivar cancelado ocupado | `SCH.reactivarUnCanceladoSobreUnRangoOcupadoSeRechaza` | OK |
| Privilegios: DELETE/TRUNCATE rechazados; INSERT/SELECT/UPDATE; FOR UPDATE | `PRV` (3 tests) | OK |

### error-responses

| Escenario | Test | Estado |
|-----------|------|--------|
| Slot tomado sin datos del existente | `EP.elErrorDeSolapamientoNoRevelaDatosDelTurnoExistente` | OK |
| `PATIENT_INACTIVE` 409 | `EP.pacienteInexistenteEs404InactivoEs409SinDatosPersonales` | OK |
| 422 sin valores enviados | `EP.inicioPasado...`, `EP.queExcedeElFinDelTramo...`, `EP.prestacionInexistenteInactivaYNoPublica` | OK |
| Error sin datos sensibles | `EP.notasConDatosPersonales...` | OK |
| Violación de exclusión por carrera → 409 `APPOINTMENT_SLOT_TAKEN` | `AEH.exclusionDeTurnosConSqlState23P01SeTraduceASlotTakenSinTextoDePostgres`, `ST.laViolacionDeExclusionTrasElFlushSeTraduceASlotTaken` | PARCIAL: con `SQLException` sintética. El mensaje real se verifica aparte en `SCH`, pero no hay IT que lleve una 23P01 real por `saveAndTranslate` (S2) |
| Exclusión de bloqueos sin cambios | `AEH.laExclusionDeBloqueosSigueSiendoScheduleBlockOverlapAunConSqlState`, `ST.unaExclusionDeOtraTablaNoSeTraduceASlotTaken`, `SBA.unBloqueoSolapadoSigueDandoScheduleBlockOverlap` | OK |
| Otra violación → `CONFLICT` | `AEH.elNombreDeLaRestriccionSinSqlStateDeExclusion...`, `AEH` preexistente, `ST.otraViolacionDeIntegridadSePropagaSinTraducir` | OK |

### module-boundaries

| Escenario | Test | Estado |
|-----------|------|--------|
| Dependencia no declarada (agenda → reservas) | `MBR.agendaSigueSinPoderUsarReservas` | OK |
| agenda → `pacientes.api` permitido | `MBR.agendaPuedeUsarLaApiDePacientes` + `ProductionArchitectureTest` (producción real usa `PatientLookup`) | OK |
| agenda → application/domain/infrastructure de pacientes | `MBR.agendaNoPuedeUsarApplicationDomainNiInfrastructureDePacientes` | OK |
| Ciclo pacientes → agenda | `MBR.pacientesNoPuedeUsarLaApiDeAgenda` | OK |
| Escenarios previos (auditoria, auth) | tests preexistentes de `MBR` | OK |
| Reservas usa la reprogramación de `agenda.api` | `MBR.reservasPuedeUsarLaOperacionDeReprogramacionDeLaApiDeAgenda` | OK |
| Reservas usa `AppointmentService` | `MBR.reservasNoPuedeUsarElServicioDeAplicacionDeTurnosDeAgenda` | OK |
| Eventos con datos mínimos / sin tipos de persistencia | `AAC.laVistaYLosEventosDeTurnoSoloContienen...`, `AAC.losEventosTienenLosComponentesDelDiseno`, `AAC` preexistente (tipos prohibidos) | OK |

### schedule-blocks

| Escenario | Test | Estado |
|-----------|------|--------|
| Bloqueo sobre turnos (crear y editar) | `SBA.elBloqueoSeGuardaYListaLosTurnosConfirmadosAfectadosSinModificarlos`, `SBA.editarElBloqueoTambienInformaLosTurnosAfectados` | OK |
| Sin turnos / sin proveedor | `SBA.sinTurnosEnElRangoLaListaEsVacia...`; sin proveedor: `ScheduleBlockServiceTest` (`Optional.empty()`) | OK |
| No confirmados o adyacentes | `SBA.canceladosCompletadosAusentesAdyacentes...NoSeListan` | OK |
| Cruza el borde | `SBA.elTurnoQueCruzaElBordeDelBloqueoSeLista` | OK |
| Serialización (turno primero / bloqueo primero) | `CI.turnoYBloqueo...` (ambas ramas) + `CI.altaDeTurnoYAltaYEdicionDeBloqueoEsperanAlLock...` + `ScheduleBlockServiceTest.crearTomaElLock...`, `editarTomaElLock...`, `eliminarNoTomaElLock` | OK |
| Bloqueos sin cambios (`SCHEDULE_BLOCK_OVERLAP`) | `SBA.unBloqueoSolapadoSigueDandoScheduleBlockOverlap` | OK |

### blocked-ranges

| Escenario | Test | Estado |
|-----------|------|--------|
| Con proveedor | `AFF.conProveedorDevuelveElTurnoConfirmadoConIdEInstantesSinModificarlo`, `AFF.devuelveLosTurnosOrdenadosPorInicio` | OK |
| Sin proveedor | `ScheduleBlockServiceTest` con `Optional.empty()` | OK (unit) |
| Sin turnos en el rango | `SBA.sinTurnosEnElRango...` | OK |
| Turnos excluidos (cancelado, completado, adyacente, otro consultorio) | `AFF.excluyeCanceladosCompletadosAdyacentesYDeOtroConsultorio` | PARCIAL: "otro consultorio" se prueba consultando un `practice_id` sin filas, no con un turno real de otro consultorio dentro del rango (S5) |

Resumen: **0 escenarios sin cobertura**; 5 PARCIALES (horizonte, puerto sin IT, 23P01 real por el servicio, completar vs reprogramar, otro consultorio en el contrato), todos aceptables para single-tenant.

## 3. Cumplimiento del diseño (D1–D22)

| Decisión | Evidencia | Estado |
|----------|-----------|--------|
| D1 V13, V1–V12 intactas | `V13__appointment.sql` nuevo; `git diff --stat HEAD` vacío para V1–V12 | OK |
| D2 exclusión `WHERE status <> 'CANCELLED'` sobre `period` `[)` generada | `V13:22`, `V13:35-36`; `period` no mapeada en `Appointment` | OK |
| D3 lock `FOR UPDATE` en book/reschedule/cancel/complete/noShow y en create/update de bloqueos, antes de cargar | `AppointmentService.java:74` (book, después de los pasos 1–3 según D18), `:89/:99/:107/:122` vía `lockAndLoad` (`:154-157`, lock y luego `findById`); `ScheduleBlockService` create (`lock` antes de `ensureNoOverlap`) y update (`lock` antes de `requireOwned`); delete sin lock; `JpaPracticeLock` con `Propagation.MANDATORY` | OK |
| D4 `PatientLookup`; 404 / 409 | `AppointmentService.java:204-210` | OK |
| D5 sobreturno persistido y fusión de tramos contiguos | `Appointment.overbooking` mapeado; `WorkingHoursFit.mergeContiguous` (también absorbe tramos encadenados y desordenados) | OK |
| D6 máquina de estados | `Appointment.java` `cancel`/`finish`/`requireConfirmed`; `NOT_STARTED` con `startsAt > now` | OK |
| D7 inicio futuro, sin min_notice/max_advance/granularidad | `requireFuture` (`!isAfter` → `==now` rechazado); el servicio no depende de `BookingSettings` | OK |
| D8 sin audit | no hay escritura en `audit_log`; logs solo con IDs | OK |
| D9 columnas/CHECKs/índices/REVOKE | `V13:8-45`, nombres de restricciones y los 4 índices; `REVOKE DELETE, TRUNCATE` | OK |
| D10 23P01 → 409 sin filtrar | `ConstraintViolations` (SQLState + nombre), `AppointmentService.saveAndTranslate`, `ApiExceptionHandler.handleDataIntegrity` (cuerpo `ProblemDetails.of` sin detalle, sin log del mensaje) | OK |
| D11 eventos tras el flush, consumidores AFTER_COMMIT | `events.*` después de `saveAndTranslate`; cancel publica después de `save` (sin flush, pero sin riesgo de exclusión); `EV` prueba AFTER_COMMIT y rollback; complete/noShow no publican | OK |
| D12 contratos de C-09 | `AppointmentAffectedAdapter`, `AffectedAppointmentsApiAdapter` → `findConfirmedWithin` ordenado | OK |
| D13 concurrencia real | `CI` sin `@Transactional`, latch, timeout de 30 s, limpieza en `@AfterEach` | OK |
| D14 reprogramación in-place | misma fila; `excludedId` en la pre-validación | OK |
| D15 snapshot al reprogramar | `endsAtIfStartedAt` usa `durationMinSnapshot`; no relee la prestación | OK |
| D16 `AppointmentOperations` solo `reschedule` | interfaz de 1 método; adaptador delega en el servicio | OK |
| D17 endpoints + DTOs | `AppointmentsController`: 7 rutas, todas con `@PreAuthorize("hasRole('DENTIST')")`; records con `@NotNull`/`@Size(max=1000)` | OK |
| D18 orden de validaciones | book: futuro → paciente → prestación → lock → horario → bloqueos → turnos → flush → evento; reschedule: futuro → lock → carga → `validateReschedulable` → ... | OK |
| D19 códigos | todos presentes con el status del diseño | OK |
| D20 ownership | `requireOwned` filtra por `InstallationPractice.id()`; prestación de otro consultorio → 404 | OK |
| D21 paquetes | coincide con el listado; ArchUnit verde | OK |
| D22 zona y día local | `WorkingHoursFit` con `practice.zone()`; `Instant`/`timestamptz` | OK |

## 4. Reglas duras de CLAUDE.md

| # | Regla | Evidencia | Estado |
|---|-------|-----------|--------|
| 1 | Inyección por constructor | `grep @Autowired src/main src/test` vacío | OK |
| 2 | Sin entidades JPA en la API | `AppointmentResponse`/`AppointmentView` son records; `AAC` lo verifica | OK |
| 3 | Sin `ddl-auto=update`; migración nueva | `application.yml:20 ddl-auto: validate`; V13 nueva | OK |
| 4 | Deny-by-default, `@ControllerAdvice` | `/api/v1/**` → `dentistAccessManager` + `@PreAuthorize`; CSRF global; `SEC` verde | OK |
| 5 | HC inmutable | N/A | — |
| 6 | Logs solo con IDs | 3 sentencias `LOG.info` en `AppointmentService` (`:82`, `:93`, `:131`), solo UUIDs; probado con ListAppender | OK |
| 7 | Auditoría HC | N/A (D8) | — |
| 8 | UTC; tokens hasheados | `Instant`/`timestamptz`; sin tokens en C-11 | OK |
| 9–11 | Frontend | N/A | — |
| 12 | Sin dependencias nuevas | `build.gradle.kts` sin cambios | OK |
| 13–14 | Commits | nada commiteado | OK |
| 15 | Sin credenciales | ninguna en el código nuevo | OK |

## 5. Revisión de concurrencia

- **`AppointmentConcurrencyIT` prueba el invariante de verdad**: verifica el estado final de la base (`count(*)` de vigentes, `status`, `cancelled_by/at`), no el orden de ejecución. Los timeouts de 500 ms solo verifican que una operación *espera* al lock: un falso positivo es posible en una máquina lenta, pero nunca un falso negativo intermitente. No hay `sleep` usado como mecanismo de corrección.
- `reprogramarYCancelarElMismoTurnoNuncaPierdeLaCancelacion` **sí discrimina** el bug del lost update: con la versión anterior (carga antes del lock), la reprogramación guardaba una copia `CONFIRMED` desactualizada que pasaba el CHECK, y el test fallaba.
- `altaDeTurnoYAltaYEdicionDeBloqueoEsperanAlLock...` y `cancelarCompletarMarcarAusenteYReprogramarEsperanAlLock...` son deterministas (el test retiene el lock) y prueban la presencia del lock de punta a punta, incluido que la baja de bloqueo no lo toma.
- **Carreras remanentes analizadas**:
  - Baja de bloqueo sin lock: solo libera rango. El peor caso es un `APPOINTMENT_SLOT_BLOCKED` contra un bloqueo que se está borrando, que es benigno.
  - Turno vs bloqueo: serializados por el lock. Las lecturas de bloqueos/turnos se hacen en READ COMMITTED después del lock, así que ven lo confirmado por el poseedor anterior. Correcto.
  - Dos reprogramaciones del mismo turno o de turnos distintos al mismo rango: serializadas (`CI.dosReprogramacionesAlMismoRango...`).
  - Paciente dado de baja, prestación desactivada o horario editado de forma concurrente con un alta: estas validaciones no están bajo el lock (las de paciente y prestación van antes del lock por D18; los flujos de C-08/C-10 no toman el lock). Puede quedar un turno para un paciente desactivado un instante antes, o validado contra el horario anterior. Es un riesgo aceptado por diseño, ver S1.
- **Deadlocks con FK `KEY SHARE`**: `FOR UPDATE` sobre `practice` choca con el `KEY SHARE` que toman los INSERT de otras tablas que referencian `practice` (paciente, prestación, bloqueo, horario). Esas transacciones no piden después el lock de `practice`, y las de turno/bloqueo no tienen locks antes de pedirlo (los pasos 1–3 son solo lecturas). Por eso el grafo de espera es acíclico: hay espera breve, no deadlock. Dentro de la misma transacción, `FOR UPDATE` + el INSERT de `appointment` (KEY SHARE propio) es compatible.
- `PracticeLockIT`: el primer test es válido (timeout mientras se retiene el lock). Los otros dos son débiles, ver S3.

## 6. Seguridad

- Autorización: `/api/v1/appointments/**` no está en ningún `permitAll`; cae en `dentistAccessManager` y además tiene `@PreAuthorize` por método. CSRF en POST probado.
- Validación de entrada: `patientId`/`serviceId`/`startsAt` con `@NotNull`, `notes` con `@Size(max=1000)` + chequeo de dominio + CHECK en base (doble barrera; `String.length()` en Java es igual o más estricto que `char_length`). `overbooking` nulo → `false`. No se aceptan `endsAt`/`durationMin`.
- IDOR: `requireOwned` y `requireActiveService` filtran por el consultorio de la instalación; las consultas de repositorio filtran por `practice_id`. En single-tenant solo hay una fila (`practice_single_row`), así que el filtro se prueba con IDs inexistentes y en unit.
- Listado `from/to`: exige ambos y `to > from`, pero **no limita la amplitud** (W1).

## Issues

### CRITICAL
Ninguno.

### WARNING
- **W1 Listado sin límite de amplitud** — `backend/src/main/java/ar/turnosodontologia/consultorio/agenda/application/AppointmentService.java:141-147` y `agenda/infrastructure/AppointmentsController.java:55-63`. `GET /api/v1/appointments?from=0001-01-01T00:00:00Z&to=9999-12-31T00:00:00Z` es válido y devuelve, sin paginar, todos los turnos del consultorio con `notes`, que pueden contener datos de salud. El acceso está restringido a DENTIST, así que no hay exposición a terceros, pero es una lectura masiva sin cota (memoria, tiempo de respuesta y volumen de datos sensibles por request). La spec no fija un máximo, así que no incumple un requisito. Recomendación: validar una amplitud máxima (p. ej. 93 días → 400 `INVALID_RANGE`, que C-20 necesita como mucho para la vista mensual), con test en `EP`; o documentar en la spec la decisión de no limitar.

### SUGGESTION
- **S1 Validaciones fuera del lock** — `AppointmentService.java:72-73` (paciente/prestación antes del lock, según D18) y `:169-170` (horario leído bajo el lock, pero C-08 no lo toma). Una baja de paciente o un cambio de horario concurrentes con un alta pueden no verse. Es aceptable en single-tenant. Si se quisiera cerrarlo, C-10/C-08 podrían tomar `PracticeLock` en la desactivación o edición. Conviene anotarlo en Risks de design.md.
- **S2 Camino residual 23P01 sin IT de punta a punta** — `AppointmentService.java:187-196`. Como el lock serializa, la pre-validación siempre gana y ninguna IT ejerce `saveAndTranslate` con una `PSQLException` real. Hoy queda cubierto por partes: `ST` con `SQLException` sintética + `SCH` verificando que el mensaje real nombra la restricción. Sugerencia: un IT que inserte por JDBC un turno solapado sin pasar por la pre-validación (p. ej. mockeando `findOverlappingActive`, o insertando en otra transacción entre la pre-validación y el flush) para probar la traducción real, también desde `AppointmentOperations`.
- **S3 Tests de concurrencia débiles** — `AppointmentConcurrencyIT.java:383-393`: `completarYReprogramar...` pasa con o sin lock, porque un turno de 2020 siempre falla `APPOINTMENT_ALREADY_STARTED`. La garantía real la dan los tests deterministas de lock. `PracticeLockIT.despuesDelCommitElLockQuedaLibreParaOtraTransaccion` ejecuta dos transacciones secuenciales en el mismo hilo, nunca contienden y el test no tiene aserciones (tautológico). `PracticeLockIT.sinTransaccionActivaFalla` acepta cualquier `RuntimeException`; conviene `IllegalTransactionStateException`. Además, `firstCommitted` se marca antes del commit real, lo que debilita la aserción final del primer test.
- **S4 `AppointmentOperations.reschedule` con `newStartsAt` nulo** — `AppointmentService.java:198-199`: `startsAt.isAfter` lanza NPE → 500 para un consumidor interno (C-15). Sugerencia: `Objects.requireNonNull` o `ValidationException` explícita en el adaptador o el servicio.
- **S5 "Otro consultorio" en el contrato de afectados** — `AffectedAppointmentsIT.java:76-84` consulta un `practice_id` sin filas en lugar de insertar un turno de otro consultorio dentro del rango. Se podría reutilizar el patrón de `SCH.elMismoRangoEnOtroConsultorioSeAcepta` (DROP del índice en una transacción revertida) si se quiere cubrir el filtro con datos reales.
- **S6 `parseInstant` con instante mal formado** — `AppointmentsController.java:96-100` sin cubrir (JaCoCo). Agregar `?from=no-es-fecha&to=...` al test `rangoDeListadoInvalido...`.
- **S7 Preexistente, no de C-11** — `ApiExceptionHandler` sigue en 65 % de líneas (W1 de C-02), y cualquier `IllegalArgumentException` de la app se mapea globalmente a 400 `INVALID_RANGE`.

## No verificado

- Comportamiento con un navegador o cliente real (C-11 es solo backend; las respuestas HTTP se verificaron vía MockMvc en los IT).
- Carga y throughput del lock (non-goal del diseño).
- Estabilidad de los tests de concurrencia en muchas corridas: se ejecutó una sola corrida completa (todo verde, sin flakes).
- El "resumen del apply" con baseline y trazabilidad (tasks 1.1/11.2) no está versionado.
- Multi-consultorio real en endpoints (imposible por `practice_single_row`; solo unit y SQL).

## Próxima acción recomendada

Resolver W1 (o documentar la decisión en la spec) y, opcionalmente, S2–S4; luego `/opsx:archive c-11-appointments-core` (actualizar `CHANGES.md` §C-11 con V13 y anotar el corrimiento de versión de C-18).

## Addendum post-verify (2026-11-22): W1 y S4 corregidos

Ajuste aprobado por Lucas; implementado con TDD (grupo 13 de tasks.md, 62/62 `[x]`).

| Hallazgo | Estado | Evidencia |
|----------|--------|-----------|
| W1 — listado sin tope de rango | Resuelto | `AppointmentService.MAX_LIST_RANGE = 93 días` (l.44); `list` lanza `InvalidRangeException` (400 `INVALID_RANGE`) si `to - from > 93 días` (l.160-161). Escenarios nuevos en `specs/appointments` y `specs/error-responses`; tests de borde exacto 93 días / +1 ns en `AppointmentServiceTest` y 400 en `AppointmentsEndpointsIT`. |
| S4 — reprogramar con `newStartsAt` nulo daba 500 | Resuelto | `ValidationException("newStartsAt"/"appointmentId", "REQUIRED")` en `AppointmentService` (l.126-129) y en `AppointmentOperationsAdapter` (l.26-29) → 400 `VALIDATION_FAILED`; tests en `AppointmentOperationsAdapterNullsTest` y `AppointmentsEndpointsIT`. |
| S1 — validaciones previas al lock | Documentado | Agregado a Risks de `design.md` como aceptable en single-tenant. |

Validación: `openspec validate c-11-appointments-core --strict` válido (re-ejecutado por el orquestador). Suite completa `./gradlew test integrationTest --rerun-tasks`: 566 unit + 364 IT verdes **según el implementador**; esta re-corrida no fue repetida por el verificador independiente.

Veredicto actualizado: **PASS WITH WARNINGS → sin warnings abiertos**; quedan las sugerencias S2, S3, S5, S6 y S7 (no bloquean el archive).
