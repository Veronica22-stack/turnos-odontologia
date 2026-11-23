# Design: C-13 `email-notifications-outbox`

> **Plan approval:** APROBADO por Veronica el 2026-11-22 (todas las decisiones **[Aprobar]**: D1, D2, D3, D7, D9, D10, D11, D12, D13, D14, D15). Elecciones: encolado after-commit en transacción nueva (se acepta la ventana mínima de pérdida); adaptador de log en dev solo con IDs/tipo/plantilla (sin destinatario, asunto ni cuerpo); adaptador SMTP testeado con mock, sin GreenMail; `spring-boot-starter-mail` aprobada como dependencia nueva. V14 verificada libre en master y ramas del equipo al 2026-11-22.

## Context

Ver `proposal.md` (Why). Estado actual relevante:

- `notificaciones.{api,application,domain,infrastructure}` existen solo con `package-info.java`.
- `agenda.api` publica `AppointmentConfirmed(appointmentId, practiceId, patientId, serviceId, startsAt, endsAt, origin)`, `AppointmentCancelled(..., startsAt, endsAt, cancelledBy)` y `AppointmentRescheduled(..., previousStartsAt, previousEndsAt, startsAt, endsAt)` dentro de la transacción (`SpringAppointmentEventsPublisher`); su Javadoc exige consumir con `@TransactionalEventListener(AFTER_COMMIT)`. En C-11 el `cancel` interno siempre registra `cancelledBy = DENTIST`; `PATIENT` llega con C-15.
- No hay en `agenda.api` una consulta de turno por id para otros módulos (solo `AppointmentOperations.reschedule`), ni en `pacientes.api` nada que exponga el email (`PatientLookup` solo devuelve `PatientStatus`). `patient.email` es nullable (V7).
- `practice` (en `shared.domain`, accesible desde cualquier capa) tiene `name`, `address`, `contact_email`, `contact_phone`, `timezone` y **`reminder_hours_before`** (1–168, default 24, editable desde la configuración de C-08/C-19). `InstallationPractice` resuelve id y zona; `Clock` UTC es un bean de `shared.infrastructure`.
- `ModuleBoundaries.ALLOWED_DEPENDENCIES` **no** declara dependencias salientes de `notificaciones`: hoy no podría escuchar los eventos de `agenda.api`.
- No hay `@EnableScheduling` ni `@EnableAsync` en el proyecto ni dependencia de mail. Spring Boot 4.0.10 / Java 21.
- Migraciones en disco: V1–V5, V7, V8, V11, V12, V13 (huecos V6/V9/V10 por merges paralelos), `outOfOrder=false`.
- Tests: `*Test` (`./gradlew test`) y `*IT` (`./gradlew integrationTest`) sobre `AbstractIntegrationTest` + `EmbeddedPostgresSupport` (zonky, sin perfil `dev`, propiedades vía `@DynamicPropertySource`). Sin Docker/Testcontainers (DD-10).
- Skill registry (`.atl/skill-registry.md`): las skills de código (`springboot-tdd`, `java-junit`, `spring-boot-test-patterns`, `postgresql-table-design`) no estaban instaladas al generarlo; se aplican las Reglas Duras de `CLAUDE.md`. Si el apply carga `springboot-tdd`, ignorar su parte de Testcontainers (DD-10).

## Goals / Non-Goals

**Goals:**
- Ningún fallo de notificación afecta al turno (RN-NO-05); ningún recordatorio de turno cancelado (RN-NO-04); como máximo una notificación por turno y tipo (RN-NO-03).
- Despacho seguro ante concurrencia (varios hilos o procesos) sin broker externo.
- Cero PII y cero tokens en logs y en la tabla; plantillas sin datos clínicos (RN-NO-06).
- Dominio del outbox (backoff, transiciones, cálculo del recordatorio) en Java puro con `Clock`, testeable sin Spring.

**Non-Goals:**
- Garantía exactly-once de entrega (es at-least-once, ver Riesgos).
- Emisión de tokens de gestión, UI de notificaciones, proveedor definitivo.

## Decisions

### D1 — Migración `V14__notification.sql` [Aprobar]
Siguiente versión libre sobre `dev-veronica`/`master` hoy. **C-12, C-14 y C-19 (ramas de otros integrantes) también pueden reclamar V14**: la primera tarea del apply re-chequea `backend/src/main/resources/db/migration` contra `master` actualizado y renumera a la siguiente versión libre si V14 ya está tomada (actualizando nombre de archivo, comentario de cabecera, tests de esquema y estas referencias). Con `outOfOrder=false` un número menor o repetido rompe el arranque.

Esquema (una sola tabla, sin columnas de contenido):

```
notification(
  id uuid PK, practice_id uuid NOT NULL FK practice RESTRICT,
  appointment_id uuid NOT NULL FK appointment RESTRICT,
  type text NOT NULL CHECK IN (CONFIRMATION, REMINDER, RESCHEDULE, CANCELLATION, DENTIST_CANCELLATION),
  channel text NOT NULL DEFAULT 'EMAIL' CHECK (channel = 'EMAIL'),
  status text NOT NULL DEFAULT 'PENDING' CHECK IN (PENDING, SENT, FAILED, DISCARDED),
  scheduled_for timestamptz NOT NULL, sent_at timestamptz NULL,
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  last_error text NULL CHECK (char_length(last_error) <= 64),
  created_at, updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (appointment_id, type),
  CHECK ((status = 'SENT') = (sent_at IS NOT NULL)))
INDEX notification_pending_due_idx ON (scheduled_for) WHERE status = 'PENDING'
-- sin índice extra por appointment_id: lo cubre el índice del UNIQUE (columna líder)
REVOKE DELETE, TRUNCATE ON notification FROM ${app_role}
```

Desvíos respecto de KB 04 (documentar en el archive): se agrega `practice_id` (DD-02), el tipo `DENTIST_CANCELLATION` (D12), el estado `DISCARDED` (D6) y `created_at/updated_at`. `RESCHEDULE` ya estaba en KB 04 aunque no en `CHANGES.md`. **No** se guarda destinatario, asunto, cuerpo ni link: todo se resuelve al enviar (D8), así la tabla no acumula PII ni tokens.

### D2 — `notificaciones` depende de `agenda.api` y `pacientes.api` [Aprobar]
Se agrega `NOTIFICACIONES → {AGENDA, PACIENTES}` a `ModuleBoundaries.ALLOWED_DEPENDENCIES` y a KB 08 §Reglas de dependencia. Sin ciclos: `agenda → pacientes`, `reservas → notificaciones`, y ni `agenda` ni `pacientes` usan `notificaciones`.
- Alternativa descartada: que `reservas` escuche los eventos y llame a `notificaciones.api`. Respeta el grafo actual pero pone en el módulo de reserva pública la lógica de reaccionar a turnos internos (alta telefónica, cancelación del odontólogo), que no son de `reservas`.
- Alternativa descartada: que `agenda` llame a `notificaciones.api` (invertiría la dependencia y acoplaría el núcleo a los emails).
- **Email del odontólogo**: `notificaciones` no puede depender de `auth` (donde vive `app_user.email`); se usa `practice.contact_email` (D12).

### D3 — Listeners `AFTER_COMMIT` + transacción nueva, con fallos tragados [Aprobar]
`AppointmentEventsListener` (en `notificaciones.infrastructure`) con `@TransactionalEventListener(phase = AFTER_COMMIT)` delega en `NotificationScheduler` (`application`) anotado `@Transactional(propagation = REQUIRES_NEW)`. Todo el cuerpo va en `try/catch (RuntimeException)` que loguea `WARN` con `appointmentId` y tipo de evento y **no relanza**: en `AFTER_COMMIT` una excepción se propaga a quien hizo el commit y el controller respondería 500 con el turno ya guardado.
- **Trade-off aceptado**: el encolado no es atómico con el turno. Si el proceso cae entre el commit del turno y el commit del encolado (milisegundos), esa notificación se pierde. Se acepta porque el brief y el contrato de C-11 piden `AFTER_COMMIT` y porque cualquier error de encolado jamás debe revertir la reserva.
- Alternativa (transactional outbox puro): `BEFORE_COMMIT` en la misma transacción. Atómico, pero un error de encolado (p. ej. bug o constraint) revierte el turno, violando RN-NO-05 en espíritu y el contrato de C-11. **Pregunta para el humano**: ¿aceptan la ventana de pérdida o prefieren atomicidad?

### D4 — Reglas de encolado (upsert por `(appointment_id, type)`)
Todas usan `INSERT ... ON CONFLICT (appointment_id, type) DO ...` nativo (atómico frente a duplicados concurrentes, sin excepción que capturar):

| Evento | Acción |
|---|---|
| `AppointmentConfirmed` | `CONFIRMATION` `scheduled_for = now` `ON CONFLICT DO NOTHING`; `REMINDER` en `startsAt − reminderHoursBefore` solo si es `> now`, `ON CONFLICT DO NOTHING` |
| `AppointmentRescheduled` | `RESCHEDULE` upsert → `PENDING`, `now`, `attempts=0`, `sent_at/last_error = NULL`; `REMINDER`: si nuevo instante `> now` upsert → `PENDING` con el nuevo instante y `attempts=0` (aunque estuviera `SENT`/`FAILED`/`DISCARDED`); si no, `UPDATE ... SET status='DISCARDED', last_error='REMINDER_WINDOW_PASSED' WHERE status='PENDING'` |
| `AppointmentCancelled` | `UPDATE` de todas las `PENDING` del turno → `DISCARDED` (`APPOINTMENT_CANCELLED`); `CANCELLATION` `now` `ON CONFLICT DO NOTHING`; si `cancelledBy = PATIENT`, también `DENTIST_CANCELLATION` `ON CONFLICT DO NOTHING` |

`reminderHoursBefore` se lee de `practice` al encolar. `now` sale del `Clock`.
- Consecuencia: con `UNIQUE (appointment_id, type)` la segunda reprogramación **reutiliza** la fila de `RESCHEDULE` (se pierde el `sent_at` de la anterior). Aceptado: la tabla es una cola, no un historial de envíos; no hay requisito de auditoría de emails.

### D5 — Despacho: una notificación por transacción con `FOR UPDATE SKIP LOCKED`
`NotificationDispatcher.dispatchDue()` repite hasta `batchSize` (20) veces o hasta no encontrar trabajo: en una transacción (`TransactionTemplate`), `SELECT * FROM notification WHERE status = 'PENDING' AND scheduled_for <= :now ORDER BY scheduled_for LIMIT 1 FOR UPDATE SKIP LOCKED`; revalida (D6), renderiza (D8), envía (D9), actualiza estado y commitea. El lock de fila se mantiene durante el envío SMTP (acotado por timeouts de 10 s de conexión/lectura/escritura).
- Una por transacción: un fallo no arrastra a otras y el lock dura un solo envío.
- Alternativa descartada: “claim” con estado `SENDING` + lease. Evita sostener la conexión durante el SMTP pero agrega un estado, un reaper de leases vencidos y más casos de prueba; innecesario a esta escala (un consultorio).
- `:now` es el `Clock` de la aplicación (no `now()` de la base) para que los tests controlen el tiempo.

### D6 — Revalidación al enviar y estado `DISCARDED`
Con `AppointmentNotificationLookup` (D10) se relee el turno: inexistente → `DISCARDED/APPOINTMENT_NOT_FOUND`; `REMINDER`/`CONFIRMATION`/`RESCHEDULE` con turno no `CONFIRMED` → `DISCARDED/APPOINTMENT_NOT_CONFIRMED`; con `now >= startsAt` → `DISCARDED/EXPIRED`; `CANCELLATION`/`DENTIST_CANCELLATION` se envían siempre. Destinatario sin email → `DISCARDED/NO_RECIPIENT` (sin reintento: reintentar no lo arregla). `DISCARDED` distingue “no correspondía enviar” de `FAILED` (“correspondía y no se pudo”), útil para el alerta y para una futura UI.

### D7 — Backoff, máximo de intentos y clasificación de fallos [Aprobar]
`NotificationRetryPolicy` (dominio puro): tras el fallo n (`attempts` ya incrementado), `delay = min(5 min × 2^(n−1), 60 min)` → 5, 10, 20, 40 min; con `attempts >= maxAttempts` (5) → `FAILED` + `WARN` “alerta” con `notificationId`, `appointmentId`, `type`, `attempts`, `last_error`. Ventana total ≈ 75 min. El retraso efectivo es `max(delay, intervalo del poll)`.
- `EmailSendException(code, retryable)`: el adaptador SMTP mapea `MailAuthenticationException` → `SMTP_AUTH` (retryable: suele ser config/credencial rotada, se alerta igual), `MailSendException` con respuesta 5xx de destinatario → `SMTP_REJECTED` (permanente → `FAILED` directo), conexión/timeout/4xx → `SMTP_UNAVAILABLE` (retryable). Cualquier otra `RuntimeException` → `UNEXPECTED` (retryable). Base, factor y tope son constantes; solo `NOTIFICATION_MAX_ATTEMPTS` es configurable.

### D8 — Plantillas de texto plano renderizadas al enviar, sin PII del paciente
`EmailTemplates` (dominio puro) produce `EmailMessage(subject, body)` por tipo, en español, **texto plano** (sin HTML: elimina inyección de marcado con el nombre de la prestación o del consultorio). Datos: fecha `EEEE dd/MM/yyyy` y hora `HH:mm` en la zona del consultorio (locale `es-AR`), nombre de la prestación, nombre/dirección/teléfono del consultorio, y el bloque de link (D11). **Sin saludo con nombre del paciente**, sin notas, sin DNI/teléfono del paciente. Asunto sin datos del paciente (p. ej. “Turno confirmado — {consultorio}”). `DENTIST_CANCELLATION`: “Un paciente canceló su turno del {fecha} {hora} ({prestación})”, sin nombre (el odontólogo lo ve en la agenda).
- Alternativa descartada: motor de plantillas (Thymeleaf/Mustache): dependencia nueva sin beneficio para 5 textos cortos.
- Se renderiza al enviar (no al encolar) para reflejar el estado vigente (reprogramaciones, cambio de nombre de prestación) y para no persistir contenido.

### D9 — Puerto `EmailSender` y adaptadores `log`/`smtp`; PII en el adaptador de log [Aprobar]
`notificaciones.domain.EmailSender.send(OutgoingEmail)` donde `OutgoingEmail(notificationId, appointmentId, type, templateName, to, subject, body)`. Selección por `MAIL_PROVIDER` con `@ConditionalOnProperty`:
- `log` (dev, DD-10): `INFO "email simulado notificationId=… appointmentId=… type=… template=…"`. **No** registra `to`, `subject` ni `body`.
  - **Tensión con DD-10**: DD-10 quiere que en desarrollo “los emails se escriban en el log” para verlos. Escribir el cuerpo choca con la regla dura 6 en el email (destinatario) y, desde C-14, con la regla 8 (el link lleva el token en claro). Además la prestación reservada puede considerarse dato de salud. **Decisión propuesta**: loguear solo metadatos; el contenido se verifica con tests unitarios de `EmailTemplates` (que fijan el texto exacto). **Alternativa** para el humano: en perfil `dev` únicamente, loguear a `DEBUG` asunto y cuerpo con el token enmascarado (`/turno/****`) y sin destinatario.
- `smtp`: `JavaMailSender` de `spring-boot-starter-mail` (**dependencia nueva, requiere aprobación**). `MAIL_*` → `spring.mail.*`; `mail.smtp.starttls.enable=true`, timeouts 10 s. Mapea excepciones a códigos (D7) **sin** copiar `getMessage()` (los servidores SMTP suelen incluir la dirección).
- Tests del adaptador SMTP con `JavaMailSender` mockeado (sin GreenMail, para no sumar dependencia de test). **Pregunta**: ¿se acepta sumar GreenMail (test-only) para un IT SMTP real?

### D10 — Contratos de lectura en `agenda.api` y `pacientes.api`
- `agenda.api.AppointmentNotificationLookup.find(UUID) → Optional<AppointmentNotificationView(appointmentId, patientId, serviceName, startsAt, endsAt, AppointmentStatusView status)>`, implementado en `agenda.infrastructure` (filtra por `InstallationPractice.id()`). Sin notas.
- `pacientes.api.PatientContactLookup.findEmail(UUID) → Optional<String>`, implementado en `pacientes.infrastructure`. **No registra `PATIENT_READ`**: es una lectura del sistema, sin usuario, que no muestra la ficha; la regla 7 aplica a historia clínica. Devuelve el email aunque el paciente esté inactivo (sus turnos existentes siguen notificándose).
- Datos del consultorio: `PracticeRepository` de `shared` (permitido desde cualquier capa).

### D11 — Link de gestión antes de que existan los tokens [Aprobar]
Los tokens llegan en C-14/C-15 y se guardan **solo hasheados** (regla 8), así que el token en claro no puede recuperarse para un recordatorio que sale días después. C-13 define la SPI `notificaciones.api.ManagementLinkProvider { Optional<String> tokenFor(UUID appointmentId, NotificationTypeView type); }` inyectada como `Optional`/`ObjectProvider`:
- **Sin implementación (C-13)**: el email omite el link y dice “Para cancelar o reprogramar, comunicate con el consultorio al {teléfono}”.
- **Con implementación (C-14, módulo `reservas`, que ya puede depender de `notificaciones`)**: se invoca al renderizar; se espera que emita un token nuevo por email (guardado hasheado). El link `APP_BASE_URL + "/turno/" + token` se arma en memoria, va solo en el cuerpo y nunca se persiste ni se loguea.
- `APP_BASE_URL` (ya existe en `.env.example` desde C-02) se valida como URL absoluta `http(s)` sin `/` final **solo si** hay `ManagementLinkProvider`.
- **Pregunta para C-14 (no bloquea C-13)**: ¿token nuevo por email o un token por turno re-emitido? Afecta TTL (`BOOKING_TOKEN_TTL_DAYS=1` hoy no alcanzaría para el link de la confirmación usado días después).

### D12 — Aviso al odontólogo (PA-14) a `practice.contact_email` [Aprobar]
Suposición de PA-14: avisar al odontólogo **solo** de cancelaciones hechas por el paciente (las suyas ya las conoce; las reservas nuevas se ven en la agenda). Destinatario: `practice.contact_email` (si es nulo → `DISCARDED/NO_RECIPIENT`). No se usa el email de login (`app_user`, módulo `auth`): no es accesible por el grafo y separar el email de contacto del de login es razonable. En C-13 el disparador queda latente (hoy toda cancelación es `DENTIST`); se prueba con un evento `cancelledBy = PATIENT` sintético.

### D13 — Anticipación del recordatorio desde `practice.reminder_hours_before`; se elimina `REMINDER_HOURS_BEFORE` [Aprobar]
La columna existe desde V5, se valida 1–168 y el odontólogo la edita (C-08/C-19). Una variable de entorno paralela crearía dos fuentes de verdad. Se elimina `REMINDER_HOURS_BEFORE` de `.env.example` y de KB 08 (el escenario “Plantilla de entorno completa” exige que coincidan). Default efectivo: **24 h** (default de la columna). Cambiar el valor **no** recalcula recordatorios ya encolados (fuera de alcance).

### D14 — Despacho periódico + disparo inmediato asíncrono [Aprobar]
- Poll: `@Scheduled(fixedDelayString = "${NOTIFICATION_POLL_INTERVAL:PT5M}", initialDelayString = "PT30S")` (dentro del rango 5–15 min de KB 07). `@EnableScheduling` en una `@Configuration` de `notificaciones.infrastructure` condicionada a `NOTIFICATION_DISPATCH_ENABLED=true`.
- Inmediato (“confirmación inmediata”, US-040): tras el commit del encolado (`TransactionSynchronization.afterCommit` del `REQUIRES_NEW`) se pide un `dispatchDue()` en un `ThreadPoolTaskExecutor` propio de 1 hilo, cola 1 y política *discard* (si ya hay uno corriendo o en cola, el pedido sobra: ese recorrerá lo vencido). Nunca corre en el hilo HTTP: la latencia SMTP no afecta la respuesta.
- Tests: `NOTIFICATION_DISPATCH_ENABLED=false` en `EmbeddedPostgresSupport` (ni poll ni disparo); los IT de despacho invocan `dispatchDue()` directamente con `Clock` controlado.

### D15 — Configuración y validación al arrancar [Aprobar]
`MAIL_PROVIDER` obligatoria (`log|smtp`), default `log` solo en `application-dev.yml`. `log` fuera de `dev` → `WARN` al arrancar (no falla: los IT corren sin perfil `dev` y necesitan `log`; alternativa más estricta: fallar fuera de `dev` y agregar un perfil `test`). Con `smtp`: `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, `MAIL_USER`, `MAIL_PASSWORD` obligatorias (la contraseña sin default, regla 15). `NOTIFICATION_POLL_INTERVAL` (ISO-8601, > 0), `NOTIFICATION_MAX_ATTEMPTS` (≥ 1). La validación extiende el patrón de `RequiredConfiguration` (mensajes que nombran la variable, nunca valores).

### D16 — Logs y `last_error` sin PII
Catálogo cerrado `NotificationErrorCode` (`SMTP_UNAVAILABLE`, `SMTP_REJECTED`, `SMTP_AUTH`, `UNEXPECTED`, `NO_RECIPIENT`, `APPOINTMENT_NOT_FOUND`, `APPOINTMENT_NOT_CONFIRMED`, `APPOINTMENT_CANCELLED`, `EXPIRED`, `REMINDER_WINDOW_PASSED`). Logs solo con `notificationId`, `appointmentId`, `type`, `status`, `attempts` y código; nunca la excepción del proveedor con su mensaje (se loguea solo su clase). `OutgoingEmail.toString()` redefinido sin `to`/`subject`/`body`. Un test captura el log (`OutputCaptureExtension`) y verifica ausencia de email, nombre, token y cuerpo.

### D17 — Estructura de paquetes
- `domain`: `Notification` (entidad JPA, UUIDv7, transiciones `markSent/markRetry/markFailed/markDiscarded`), `NotificationType`, `NotificationStatus`, `NotificationErrorCode`, `NotificationRetryPolicy`, `ReminderSchedule` (cálculo), `EmailTemplates`, `EmailMessage`, `OutgoingEmail`, `EmailSender`, `EmailSendException`, `NotificationRepository` (puerto).
- `application`: `NotificationScheduler` (encolado; recibe comandos propios — `onConfirmed/onRescheduled/onCancelled` con ids e instantes — que el listener arma desde los eventos de `agenda.api`, para que `application` se pruebe sin Spring), `NotificationDispatcher` (usa `AppointmentNotificationLookup` y `PatientContactLookup` de las `api` ajenas, permitido por D2).
- `infrastructure`: `AppointmentEventsListener`, `SpringDataNotificationRepository` (+ consultas nativas de upsert y `SKIP LOCKED`), `LogEmailSender`, `SmtpEmailSender`, `NotificationsConfiguration` (scheduling, executor, validación).
- `api`: `ManagementLinkProvider`, `NotificationTypeView`.

### D18 — Orden de pruebas (TDD estricto)
Dominio puro primero (retry policy, recordatorio, plantillas, transiciones), luego esquema (IT), repositorio (IT de upsert y `SKIP LOCKED` con dos hilos), contratos de `agenda.api`/`pacientes.api`, encolado (IT end-to-end con turnos reales), despacho (IT con `EmailSender` falso controlable inyectado como `@Primary` en una `@TestConfiguration`), adaptadores, configuración y arquitectura.

## Risks / Trade-offs

- [Pérdida del encolado si el proceso cae entre commit y listener] → ventana de ms; aceptado (D3). Mitigación futura: job de reconciliación “turnos `CONFIRMED` futuros sin `CONFIRMATION`/`REMINDER`”.
- [Duplicado de email (at-least-once): SMTP aceptó y el commit del `SENT` falla o el proceso cae] → muy improbable; el email duplicado es inocuo (mismo contenido). Documentado.
- [Conexión de la base retenida durante el SMTP] → una sola fila por transacción, timeouts 10 s, pool por defecto (10) holgado para un consultorio.
- [Recordatorios no salen si la app está apagada (ejecución local, KB 07 Flujo 5 / PA-03)] → al volver, los vencidos de turnos ya iniciados se descartan (`EXPIRED`); los aún futuros se envían tarde. Condiciona el hosting (PA-03).
- [Colisión de número de migración con C-12/C-14/C-19] → re-chequeo obligatorio en la tarea 1.3.
- [FK `notification → appointment RESTRICT` rompe la limpieza de tests que vacían `appointment`] → ajustar helpers de limpieza (tarea 1.2/2.x) para limpiar `notification` primero (con el rol de migración).
- [Los IT existentes de turnos ahora encolan notificaciones] → con `NOTIFICATION_DISPATCH_ENABLED=false` y `MAIL_PROVIDER=log` no hay efectos externos; verificar que siguen verdes.
- [`SMTP_AUTH` tratado como retryable consume 5 intentos con credenciales malas] → aceptado: alerta en log y la credencial puede corregirse dentro de la ventana de ~75 min.
- [El nombre de la prestación viaja en el email por un proveedor externo] → permitido por RN-NO-06; la ubicación de datos del proveedor queda en PA-09.

## Migration Plan

1. Mergear `master` y re-chequear la versión libre (D1).
2. Desplegar: Flyway aplica `V14`; no hay datos a migrar (los turnos existentes no reciben confirmación ni recordatorio retroactivo — **aceptado**: solo eventos nuevos encolan).
3. Configurar `MAIL_PROVIDER` en cada entorno (`log` en dev; `smtp` con credenciales cuando se defina PA-09).
4. Rollback: desactivar con `NOTIFICATION_DISPATCH_ENABLED=false` (la cola se conserva); revertir código no exige revertir `V14` (tabla aislada).

## Open Questions

- Proveedor SMTP real, dominio remitente, SPF/DKIM (PA-09, PA-16): no cambia el diseño (adaptador `smtp` genérico).
- Política de token del link (nuevo por email vs. por turno) y su TTL: la decide C-14 sobre la SPI de D11.
- Retención/purga de `notification` (PA-11).
