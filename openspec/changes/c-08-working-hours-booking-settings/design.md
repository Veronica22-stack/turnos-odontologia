# Design: C-08 `working-hours-booking-settings`

## Context

El consultorio dental opera bajo un esquema single-tenant v1 con `practice_id` transversal (DD-02, RN-GL-03).
La tabla `practice` fue introducida en V2 (`V2__core_models.sql`), pero contiene únicamente datos base (`name`, `timezone`, `contact_email`, `contact_phone`, `address`).
Hasta el momento, no existe persistencia de horarios semanales de atención habitual (`working_hours`), ni persistencia de parámetros operativos de reserva (`slot_granularity_min`, `min_notice_min`, `max_advance_days`, `reminder_hours_before`), requeridos por US-010 y US-013.

Ver motivación completa en `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Proveer migración Flyway `V5__working_hours_and_booking_settings.sql` que crea `working_hours` y amplía `practice`.
- Entidad `WorkingHours` en módulo `agenda.domain` y repositorios/adaptadores JPA en `agenda.infrastructure`.
- Ampliar entidad `Practice` en módulo `shared.domain` y actualizar `InstallationPractice` para dar acceso a los parámetros.
- Endpoints REST protegidos `/api/v1/working-hours` y `/api/v1/practice/settings` bajo autenticación de rol `DENTIST` y protección CSRF.
- Validación de dominio para tramos de atención: `start_time < end_time`, `weekday` entre 1 y 7, y rechazo de solapamientos entre tramos del mismo día.
- Cobertura estricta con TDD (unitarios y pruebas de integración con PostgreSQL zonky).

**Non-Goals:**
- Bloqueos extraordinarios de agenda (vacaciones, feriados): corresponde a C-09 (`schedule-blocks`).
- Algoritmo de cálculo de slots de disponibilidad: corresponde a C-12 (`availability-engine`).
- Envío real de recordatorios: corresponde a C-13 (`email-notifications-outbox`).
- Interfaz de usuario para configuración: corresponde a C-19 (`settings-ui`).

## Decisions

### D1: Representación de tramos de atención y validación de no solapamiento
- **Decisión**: Los tramos se modelan en `working_hours` con `weekday` (`smallint`, 1=Lunes a 7=Domingo según `java.time.DayOfWeek`), `start_time` (`java.time.LocalTime`) y `end_time` (`java.time.LocalTime`).
- **Validación**: La base de datos impone `CHECK (end_time > start_time)` y `CHECK (weekday BETWEEN 1 AND 7)`. El servicio de aplicación valida en memoria que ningún tramo del mismo día se solape con otro (`tramoA.start < tramoB.end && tramoB.start < tramoA.end`).
- **Alternativa descartada**: Usar una restricción `EXCLUDE USING gist` en PostgreSQL con tipos de rango time. Descartado porque PostgreSQL no ofrece rangos nativos de tipo `timerange` sin extensiones complejas o conversiones a timestamps arbitrarios; la validación en dominio es determinística y suficiente para el conjunto pequeño de tramos semanales (~10-20 tramos).

### D2: Actualización de tramos semanales vía reemplazo integral (PUT)
- **Decisión**: `PUT /api/v1/working-hours` recibe la lista completa de tramos semanales deseados. En una sola transacción, se validan todos los tramos, se eliminan los tramos anteriores del `practice_id` y se insertan los nuevos.
- **Razón**: Permite editar la semana completa en la UI sin lidiar con estados parciales inconsistentes.
- **Alternativa descartada**: Operaciones CRUD individuales por tramo (`POST /working-hours`, `DELETE /working-hours/{id}`). Descartado porque complicaría la edición atómica de la grilla semanal y la detección de solapamientos entre tramos viejos y nuevos.

### D3: Ubicación de los parámetros de reserva en `practice`
- **Decisión**: Los parámetros (`slot_granularity_min`, `min_notice_min`, `max_advance_days`, `reminder_hours_before`) se agregan como columnas a la tabla existente `practice` con valores por defecto (15, 120, 30, 24).
- **Razón**: `practice` ya representa la configuración global de la instalación. Evita una tabla 1:1 innecesaria y mantiene el acceso en un solo registro.
- **Alternativa descartada**: Tabla separada `booking_settings`. Descartado por sobreingeniería en v1; una fila de `practice` concentra toda la configuración de la instalación.

### D4: Separación modular
- **Decisión**: `WorkingHours` vive en `ar.turnosodontologia.consultorio.agenda`. La configuración de consultorio `practice/settings` vive en `ar.turnosodontologia.consultorio.shared` (o fachada en `agenda`), respetando `ModuleBoundaries`.
- **Razón**: Los horarios de atención son el corazón del calendario de agenda; los settings de practice pertenecen al núcleo del consultorio que todos los módulos consumen.

## Risks / Trade-offs

- **[Riesgo] Cambio de horarios de atención deja turnos confirmados existentes fuera de horario**
  - *Mitigación*: Según RN-AG-03 y US-010, modificar horarios no cancela turnos confirmados. El endpoint avisa o permite listar turnos fuera del nuevo horario, pero no destruye citas existentes.
- **[Riesgo] Granularidad incompatible con duraciones de servicios**
  - *Mitigación*: Se limita `slot_granularity_min` a valores estándar (10, 15, 20, 30, 60). C-07 y C-12 validan que los servicios sean múltiplos de la granularidad configurada.

## Migration Plan

1. Aplicar migración `V5__working_hours_and_booking_settings.sql` mediante Flyway.
2. La migración es retrocompatible: agrega columnas con `DEFAULT` en `practice` y crea `working_hours` vacía (o con seed si aplica).
3. Rollback: En caso de reversión antes de producción, restaurar backup o revertir migración Flyway mediante script específico de compensación.

## Open Questions

- Ninguna abierta que bloquee este change. PA-19 fue resuelta fijando defaults de 15 min, 120 min de anticipación mínima, 30 días horizonte y 24 h recordatorio, todos editables por el odontólogo.
