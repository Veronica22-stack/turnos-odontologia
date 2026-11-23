# Tasks: C-13 `email-notifications-outbox`

> **Strict TDD**: en cada grupo, la tarea de test (RED) precede a su implementación (GREEN). RED = el test se escribe y se **ejecuta fallando** por la razón esperada; GREEN = mínimo código para pasarlo; luego triangular (≥2 casos por comportamiento: feliz + borde/error) y refactorizar con la suite verde. Unitarios `*Test` con `./gradlew test`; integración `*IT` con `./gradlew integrationTest` (PostgreSQL embebido zonky; **sin Docker/Testcontainers**, DD-10). Specs = criterios de aceptación; decisiones = `design.md` D1–D18. **Governance ALTO: no empezar sin la aprobación del plan registrada en `design.md`.**

## 1. Safety net y número de migración

- [x] 1.1 Correr `./gradlew test integrationTest` antes de tocar código y registrar el baseline ("N unit + M IT verdes") en el resumen del apply; si algo falla, detenerse y reportarlo como falla preexistente (no corregir)
- [x] 1.2 Registrar el estado de `ModuleBoundariesRulesTest`, `ProductionArchitectureTest`, `AppointmentEventsIT`, los IT de endpoints de turnos y de pacientes, los tests de `RequiredConfiguration` y de `.env.example`, y los helpers de limpieza de tablas de `support/` (archivos que este change extiende); verificar que todos están verdes
- [x] 1.3 Traer `master` actualizado y listar `backend/src/main/resources/db/migration`: si `V14` ya existe (C-12/C-14/C-19), usar la siguiente versión libre y reemplazar "V14" en `proposal.md`, `design.md` D1, `specs/database-migrations/spec.md` y estas tareas; registrar el número elegido en el resumen

## 2. Migración de `notification` (D1)

- [x] 2.1 RED: crear `database/NotificationSchemaIT` — columnas y tipos, FKs `ON DELETE RESTRICT` a `practice` y `appointment`, CHECKs de `type`/`channel`/`status`/`attempts >= 0`/`last_error` ≤ 64 (65 rechazado, 64 aceptado), coherencia `SENT ⇔ sent_at`, defaults (`PENDING`, `EMAIL`, `attempts = 0`), índice parcial `WHERE status = 'PENDING'`; verificar que falla porque la tabla no existe
- [x] 2.2 RED: en el mismo IT, `UNIQUE (appointment_id, type)` — duplicado rechazado (SQLState `23505`), tipos distintos del mismo turno aceptados; verificar que falla
- [x] 2.3 RED: crear `database/NotificationPrivilegesIT` — `DELETE`/`TRUNCATE` denegados al rol de aplicación; `INSERT`/`UPDATE`/`SELECT ... FOR UPDATE SKIP LOCKED` permitidos; verificar que falla
- [x] 2.4 GREEN: escribir `V14__notification.sql` (comentario de cabecera: por qué V14, desvíos respecto de KB 04, sin tocar V1–V13) y verificar 2.1–2.3, `MigrationsIT` y `MigrationValidationIT` verdes; actualizar la lista de versiones esperadas en los tests de esquema que la fijan
- [x] 2.5 Ajustar los helpers de limpieza de tests que vacían `appointment` para vaciar antes `notification`; verificar con `./gradlew integrationTest` que los IT de turnos siguen verdes

## 3. Dominio puro del outbox (D4, D6, D7, D16)

- [x] 3.1 RED: `notificaciones/domain/NotificationRetryPolicyTest` (`@ParameterizedTest`) — retrasos 5/10/20/40 min para fallos 1–4, tope 60 min con base mayor, `FAILED` al llegar a `maxAttempts` (5 y otro valor, p. ej. 3), fallo permanente → `FAILED` en el intento 1; verificar que falla por clase inexistente
- [x] 3.2 GREEN: `NotificationRetryPolicy` y `NotificationErrorCode` (catálogo cerrado, códigos ≤ 64 caracteres) y verificar 3.1 verde
- [x] 3.3 RED: `ReminderScheduleTest` con `Clock.fixed` — 24 h antes de `2026-12-10T13:00Z` = `2026-12-09T13:00Z`; instante igual a `now` → sin recordatorio; turno dentro de la ventana → sin recordatorio; `reminderHoursBefore` 1 y 168; verificar que falla
- [x] 3.4 GREEN: `ReminderSchedule` y verificar 3.3 verde
- [x] 3.5 RED: `NotificationTest` — transiciones `markSent(now)` (`sent_at`), `markRetry(code, next)` (incrementa `attempts`), `markFailed(code)`, `markDiscarded(code)`; desde `SENT`/`FAILED`/`DISCARDED` las transiciones de envío fallan; `toString` sin datos de contenido; verificar que falla
- [x] 3.6 GREEN: entidad `Notification` (UUIDv7), `NotificationType`, `NotificationStatus`; verificar 3.5 verde y `ProductionArchitectureTest` verde (dominio sin Spring)

## 4. Plantillas de email (D8, D11)

- [x] 4.1 RED: `EmailTemplatesTest` — para cada tipo (`CONFIRMATION`, `REMINDER`, `RESCHEDULE`, `CANCELLATION`, `DENTIST_CANCELLATION`) asunto y cuerpo exactos en texto plano con turno `2026-12-10T13:00Z` en `America/Argentina/Buenos_Aires` → "jueves 10/12/2026" y "10:00", nombre de prestación y datos del consultorio; verificar que falla
- [x] 4.2 GREEN: `EmailTemplates`, `EmailMessage` y verificar 4.1 verde
- [x] 4.3 RED: privacidad y link — el texto nunca contiene notas, nombre, DNI ni teléfono del paciente aunque se pasen al armado de datos de prueba; sin token → sin `/turno/` y con el texto "comunicate con el consultorio" + teléfono; con token `abc123` y base `https://turnos.ejemplo.com` → `https://turnos.ejemplo.com/turno/abc123`; consultorio sin teléfono → texto alternativo sin `null`; verificar que falla
- [x] 4.4 GREEN: bloque de link/contacto y verificar 4.3 verde; refactor con la suite de dominio verde

## 5. Contratos de lectura en agenda y pacientes (D10)

- [x] 5.1 RED: `agenda/infrastructure/AppointmentNotificationLookupIT` — turno confirmado (estado, paciente, instantes, nombre de prestación, sin notas), cancelado (estado `CANCELLED`, instantes originales), id inexistente o de otro consultorio → vacío; verificar que falla por puerto inexistente
- [x] 5.2 GREEN: `agenda.api.AppointmentNotificationLookup` + `AppointmentNotificationView` y adaptador en `agenda.infrastructure`; verificar 5.1 verde
- [x] 5.3 RED: `pacientes/infrastructure/PatientContactLookupIT` — con email, sin email, inexistente, inactivo con email (devuelve email), sin fila nueva en `audit_log`, y el log capturado no contiene el email; verificar que falla
- [x] 5.4 GREEN: `pacientes.api.PatientContactLookup` y adaptador en `pacientes.infrastructure`; verificar 5.3 verde

## 6. Fronteras de módulos (D2)

- [x] 6.1 RED: en `ModuleBoundariesRulesTest` (fixtures en `archfixture`) agregar los escenarios de `module-boundaries`: `notificaciones → agenda.api` y `→ pacientes.api` pasan; `notificaciones → agenda.domain/infrastructure` y `→ pacientes.domain/infrastructure` fallan nombrando origen y destino; `agenda → notificaciones.api` y `pacientes → notificaciones.api` fallan; `notificaciones → auth.api` falla; verificar que fallan los casos permitidos
- [x] 6.2 GREEN: `ModuleBoundaries.ALLOWED_DEPENDENCIES` suma `NOTIFICACIONES → {AGENDA, PACIENTES}`; verificar 6.1 y `ProductionArchitectureTest` verdes; actualizar KB 08 §Reglas de dependencia (`notificaciones → agenda, pacientes`)

## 7. Persistencia del outbox (D4, D5)

- [x] 7.1 RED: `notificaciones/infrastructure/NotificationRepositoryIT` — `enqueueIfAbsent` (`ON CONFLICT DO NOTHING`: segunda llamada no cambia la fila `SENT`), `upsertPending` (resetea `PENDING`, `attempts = 0`, `sent_at`/`last_error` nulos, nuevo `scheduled_for`), `discardPending(appointmentId, code)` y `discardPendingOfType` (solo `PENDING`), `claimNextDue(now)` (respeta `scheduled_for <= now`, orden, ignora no `PENDING`); verificar que falla
- [x] 7.2 GREEN: puerto `NotificationRepository` y `SpringDataNotificationRepository` con consultas nativas; verificar 7.1 verde
- [x] 7.3 RED: en el mismo IT, concurrencia real con dos hilos + latch: el primero toma una fila con `claimNextDue` y la retiene; el segundo obtiene la siguiente o vacío, nunca la misma (`SKIP LOCKED`); y dos `enqueueIfAbsent` concurrentes del mismo `(turno, tipo)` dejan una fila sin excepción; verificar que falla si se quita `SKIP LOCKED`
- [x] 7.4 GREEN: ajustar la consulta y verificar 7.3 verde

## 8. Encolado por eventos (D3, D4, D12, D13)

- [x] 8.1 RED: `notificaciones/application/NotificationSchedulerTest` (Mockito, `Clock.fixed`) — confirmado → `CONFIRMATION` ahora + `REMINDER` con `reminder_hours_before` del consultorio; confirmado dentro de la ventana → sin `REMINDER`; reprogramado → `RESCHEDULE` upsert + `REMINDER` upsert o descarte; cancelado por odontólogo → descarte de pendientes + `CANCELLATION`; cancelado por paciente → además `DENTIST_CANCELLATION`; verificar que falla
- [x] 8.2 GREEN: `NotificationScheduler` y verificar 8.1 verde
- [x] 8.3 RED: `notificaciones/infrastructure/AppointmentNotificationsIT` end-to-end con turnos reales vía `AppointmentService`/endpoints y despacho desactivado — escenarios de `appointment-notifications` "Turno confirmado", "Alta rechazada", "Recordatorio a 24 h", "Recordatorio recalculado", "Recordatorio ya enviado y turno movido más adelante", "Segunda reprogramación", "Reprogramado dentro de la ventana", "Cancelación anula el recordatorio", "Confirmación aún pendiente al cancelar", "Cancelación por el odontólogo", "Evento de confirmación procesado dos veces"; y "Cancelación por el paciente" publicando un `AppointmentCancelled` con `PATIENT` dentro de una transacción de test; verificar que falla
- [x] 8.4 GREEN: `AppointmentEventsListener` (`@TransactionalEventListener(AFTER_COMMIT)` → `NotificationScheduler` en `REQUIRES_NEW`) y verificar 8.3 verde
- [x] 8.5 RED: aislamiento de fallos — con el repositorio de notificaciones forzado a lanzar (bean `@Primary` de test), crear y cancelar un turno por la API responde éxito, el turno queda guardado y el log tiene `WARN` con el id del turno sin PII ("Error al encolar"); verificar que falla
- [x] 8.6 GREEN: `try/catch` en el listener que loguea solo ids y no relanza; verificar 8.5 y `AppointmentEventsIT` verdes

## 9. Despacho, revalidación y reintentos (D5, D6, D7, D14, D16)

- [x] 9.1 RED: `notificaciones/application/NotificationDispatcherIT` con `EmailSender` falso controlable (`@TestConfiguration`, `@Primary`) y `Clock` mutable — "Recordatorio vencido enviado", "Recordatorio todavía no vencido", orden por `scheduled_for`, tope de lote; verificar que falla por clase inexistente
- [x] 9.2 GREEN: `NotificationDispatcher.dispatchDue()` (transacción por notificación con `TransactionTemplate`) y verificar 9.1 verde
- [x] 9.3 RED: revalidación — "Recordatorio de turno cancelado entre medio", "Recordatorio tardío", "Paciente sin email", turno inexistente, `DENTIST_CANCELLATION` con `contact_email` nulo → `DISCARDED/NO_RECIPIENT`, `CANCELLATION` de turno cancelado sí se envía; verificar que falla
- [x] 9.4 GREEN: revalidación con `AppointmentNotificationLookup`, `PatientContactLookup` y `PracticeRepository`; verificar 9.3 verde
- [x] 9.5 RED: reintentos — "Primer fallo transitorio" (T+5 min), "Tercer fallo transitorio" (T+20 min), "Agotados los intentos" (`FAILED`, `WARN` con id), "Proveedor recuperado", "Destinatario rechazado" (`FAILED` en el intento 1), excepción inesperada → `UNEXPECTED` retryable, y "Proveedor caído al reservar" end-to-end (turno `CONFIRMED`, `CONFIRMATION` `PENDING` con `attempts = 1`); verificar que falla
- [x] 9.6 GREEN: aplicar `NotificationRetryPolicy` en el despacho; verificar 9.5 verde
- [x] 9.7 RED: "Dos despachadores a la vez" — diez notificaciones vencidas, dos hilos llamando `dispatchDue()` en paralelo: exactamente diez envíos (sin ids repetidos en el falso) y diez `SENT`; verificar que falla si se quita `SKIP LOCKED` o se toman en lote sin lock
- [x] 9.8 GREEN: ajustes necesarios y verificar 9.7 verde
- [x] 9.9 RED: privacidad — "Error del proveedor que incluye la dirección": el falso lanza con un mensaje que contiene el email; `last_error` es un código del catálogo y el log capturado (`OutputCaptureExtension`) no contiene el email, el nombre, el cuerpo ni el token; verificar que falla
- [x] 9.10 GREEN: logs solo con ids/tipo/estado/código y verificar 9.9 verde

## 10. Link de gestión opcional (D11)

- [x] 10.1 RED: en `NotificationDispatcherIT`, "Sin emisor de tokens" (cuerpo sin `/turno/`) y "Con emisor de tokens" (bean de test de `ManagementLinkProvider` que devuelve `abc123`: el cuerpo enviado contiene `https://turnos.ejemplo.com/turno/abc123`; la fila y el log no contienen `abc123`); verificar que falla
- [x] 10.2 GREEN: SPI `notificaciones.api.ManagementLinkProvider` + `NotificationTypeView`, inyección opcional y armado del link con `APP_BASE_URL`; verificar 10.1 verde

## 11. Adaptadores de envío (D9)

- [x] 11.1 RED: `LogEmailSenderTest` — "Envío en desarrollo": la línea contiene `notificationId`, `appointmentId`, `CONFIRMATION` y la plantilla; no contiene destinatario, asunto ni cuerpo; `OutgoingEmail.toString()` tampoco; verificar que falla
- [x] 11.2 GREEN: `LogEmailSender` (`MAIL_PROVIDER=log`) y verificar 11.1 verde
- [x] 11.3 Agregar `spring-boot-starter-mail` a `backend/build.gradle.kts` (**solo si la dependencia fue aprobada**, D9) y verificar `./gradlew dependencies` y `./gradlew test` verdes
- [x] 11.4 RED: `SmtpEmailSenderTest` con `JavaMailSender` mockeado — arma el mensaje con `MAIL_FROM`, destinatario, asunto y cuerpo de texto plano UTF-8; mapea conexión/timeout/4xx → `SMTP_UNAVAILABLE` retryable, rechazo 5xx de destinatario → `SMTP_REJECTED` permanente, autenticación → `SMTP_AUTH`; la excepción traducida no conserva el mensaje original; verificar que falla
- [x] 11.5 GREEN: `SmtpEmailSender` (`MAIL_PROVIDER=smtp`, timeouts 10 s, STARTTLS) y verificar 11.4 verde

## 12. Configuración y scheduling (D14, D15)

- [x] 12.1 RED: tests de configuración (patrón de los tests de `RequiredConfiguration`) — "Proveedor ausente fuera de dev", "Proveedor por defecto en dev", "Valor inválido", "SMTP sin contraseña" (menciona `MAIL_PASSWORD`, no muestra `MAIL_USER`), "Envío simulado fuera de dev" (`WARN`), "Máximo de intentos inválido", intervalo no positivo; verificar que falla
- [x] 12.2 GREEN: validación en `RequiredConfiguration` (o una equivalente del módulo), `application.yml` (`MAIL_*` → `spring.mail.*` sin defaults de secretos, propiedades de despacho), `application-dev.yml` (`MAIL_PROVIDER: log`); verificar 12.1 verde
- [x] 12.3 RED: "Valores por defecto" y "Despacho desactivado" — con `NOTIFICATION_DISPATCH_ENABLED=false` el turno encola y nada se envía; con el despacho activo y poll corto, el disparo inmediato tras confirmar envía la `CONFIRMATION` sin esperar el poll (aserción con timeout acotado, sin `sleep` fijo); verificar que falla
- [x] 12.4 GREEN: `NotificationsConfiguration` (`@EnableScheduling` condicionado, `@Scheduled(fixedDelayString = NOTIFICATION_POLL_INTERVAL)`, executor de 1 hilo/cola 1/discard, disparo en `afterCommit`); `EmbeddedPostgresSupport` define `MAIL_PROVIDER=log` y `NOTIFICATION_DISPATCH_ENABLED=false`; verificar 12.3 y la suite IT completa verdes
- [x] 12.5 Actualizar `.env.example` (quitar `REMINDER_HOURS_BEFORE`; agregar `NOTIFICATION_POLL_INTERVAL`, `NOTIFICATION_MAX_ATTEMPTS`, `NOTIFICATION_DISPATCH_ENABLED`; `MAIL_PASSWORD=change-me`) y KB 08 §Variables de entorno en el mismo sentido (nota: la anticipación del recordatorio sale de `practice.reminder_hours_before`); verificar el test de "Plantilla de entorno completa" verde
- [x] 12.6 Documentar en el README (sección de desarrollo local) que en `dev` los emails no se envían y solo se loguean metadatos, y cómo configurar `smtp`; verificar que los comandos y variables citados existen

## 13. Verificación integral

- [x] 13.1 Correr `./gradlew test integrationTest jacocoTestReport` y comparar contra el baseline de 1.1: sin regresiones; cobertura de `notificaciones` reportada en el resumen
- [x] 13.2 Revisar con `grep` que en `notificaciones` no hay `@Autowired` en campos, ni `getMessage()` del proveedor en logs/`last_error`, ni `Instant.now()` sin `Clock`, ni `innerHTML`/HTML en plantillas; registrar el resultado
- [x] 13.3 Arrancar la app con perfil `dev` y `MAIL_PROVIDER=log`, crear un turno desde la API interna y verificar en el log la línea de envío simulado con solo ids/tipo/plantilla y la fila `CONFIRMATION` en `SENT`

## Workflow follow-up

- Verify (`openspec-verify`): matriz de escenarios feliz/error/borde contra las specs de `appointment-notifications`, `database-migrations`, `module-boundaries`, `appointments`, `patients-management` y `runtime-configuration`.
- Archive (`/opsx:archive`): actualizar `CHANGES.md` §[C-13] — estado, migración **V14** (o la elegida en 1.3), tipos `RESCHEDULE`/`DENTIST_CANCELLATION`, estado `DISCARDED`, eliminación de `REMINDER_HOURS_BEFORE`, decisiones D1–D18 resumidas — y KB 04 §notification con los desvíos de D1; marcar PA-14 como resuelta por Suposición (D12) en KB 10.
- Registrar en engram el cierre (`opsx/c-13-email-notifications-outbox/archive`).
- Nota para C-14: implementar `notificaciones.api.ManagementLinkProvider` en `reservas` (token emitido al enviar, guardado hasheado; revisar `BOOKING_TOKEN_TTL_DAYS` para links de confirmación usados días después).
- Nota para C-15: la cancelación por token debe registrar `cancelledBy = PATIENT` para disparar `DENTIST_CANCELLATION`.
