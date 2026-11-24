# Explore: C-11 `appointments-core`

> Resumen del relevamiento previo al propose (2026-11-22). Fuente: memoria de Engram
> `opsx/c-11-appointments-core/explore` y `opsx/c-11-appointments-core/decisions`
> (`.engram/`), guardada por el agente durante la fase explore y transcripta acá para
> que quede junto al resto de los artefactos del change. Sin código ni artefactos
> creados en esta fase. Conducido por Lucas (líder).

## Qué se leyó

- `CHANGES.md` §[C-11].
- Knowledge-base: `04_modelo_de_datos.md` §appointment; `05_reglas_de_negocio.md`
  RN-AG-01/02/04/05/08/09 y RN-RS-07/09; `06_funcionalidades.md` US-012, US-021 y US-022;
  `07_flujos_principales.md` Flujo 3; `08_arquitectura_propuesta.md`;
  `09_decisiones_y_supuestos.md` DD-09.
- Código y specs de los changes de los que depende (C-07, C-08, C-09, C-10).

## Estado de las dependencias al momento del explore

- **C-07 `services-catalog`**: todavía no existía (ni tabla `service` ni change). Bloqueante
  para la FK `service_id` y el snapshot de duración.
- **C-08**: implementado (13/13) pero sin archivar; faltaba en `agenda.api` un puerto de
  lectura de horarios.
- **C-09 `schedule-blocks`**: todavía no existía. Bloqueante.
- **C-10**: archivado; ofrece `pacientes.api.PatientLookup`.
- `CHANGES.md` marcaba C-03/C-04 como pendientes aunque estaban archivados (desactualizado).

Conclusión: el propose podía hacerse contra los contratos acordados, pero el apply
debía esperar a que C-07 y C-09 estuvieran archivados.

## Hallazgos y riesgos

1. **Flyway**: existían V1, V2, V3, V5, V7 y V11, sin `outOfOrder`. Migraciones con número
   menor al último aplicado fallan en bases ya migradas (zonky, con base vacía, no lo detecta).
2. **Fronteras de módulos**: `agenda` no podía depender de ningún otro módulo; los turnos
   necesitan leer pacientes → ampliar el grafo y la spec `module-boundaries`.
3. No había eventos de aplicación; C-11 introduce el patrón (lo consume C-13).
4. Una violación de integridad se traducía a un `409 CONFLICT` genérico; C-11 debe dar un
   código propio al solapamiento sin exponer el nombre de la constraint.
5. La KB no definía las transiciones de estado ni el flag de sobreturno; diferencia de
   nombres `source` (CHANGES) vs `origin` (KB).
6. No había infraestructura para tests de concurrencia (hace falta `@SpringBootTest` sin
   `@Transactional`, con hilos, contra zonky).
7. `audit_log_action_check` no tenía acciones de turnos.
8. La zona horaria del consultorio se cacheaba de por vida aunque C-08 permite editarla.

## Recomendaciones que se llevaron a decisión

| # | Recomendación | Decisión de Lucas (2026-11-22) |
|---|---|---|
| D1 | `EXCLUDE` sobre `status <> 'CANCELLED'` (no solo `CONFIRMED`) | Aprobada: `COMPLETED`/`NO_SHOW` siguen ocupando su rango |
| D2 | Carrera turno vs. bloqueo: validación + `SELECT ... FOR UPDATE` sobre la fila `practice` | Aprobada, también al crear/editar bloqueos (toca C-09) |
| D3 | Renumerar la migración a la siguiente libre en lugar de `outOfOrder` | Aprobada: quedó `V13` |
| D4 | Turnos en `agenda`, grafo `AGENDA → PACIENTES`, eventos con `@TransactionalEventListener(AFTER_COMMIT)` | Aprobada (solo vía `PatientLookup`) |
| D5 | Columna `origin` (`PUBLIC`/`INTERNAL`) y flag `overbooking` persistido | Aprobada: sobreturno = fuera de horario, nunca superpuesto |
| D6 | Transiciones `CONFIRMED → CANCELLED/COMPLETED/NO_SHOW`; estados terminales | Aprobada (`409 INVALID_STATUS_TRANSITION`) |
| D7 | Turno manual sin anticipación mínima ni horizonte | Aprobada; sin alineación a granularidad |
| D8 | Paciente inactivo: rechazar | Ajustada en el propose: `409 PATIENT_INACTIVE` (alineado con C-16) en vez de 422 |
| D9 | Sin auditoría de turnos en C-11 | Aprobada (logs solo con IDs) |

Siguiente paso: `/opsx:propose c-11-appointments-core`.
