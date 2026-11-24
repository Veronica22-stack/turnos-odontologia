# Reglas de Negocio

Cada regla tiene un código único `RN-{DOMINIO}-{NN}`. Las reglas marcadas **(decidida)** provienen de decisiones confirmadas; las marcadas **(Suposición)** son inferidas y deben validarse.

## Dominio: Autenticación y acceso (RN-AU)

- **RN-AU-01** (decidida): En v1 existe un único usuario interno (DENTIST) con acceso total. No hay registro público de usuarios.
- **RN-AU-02** (decidida): El paciente no tiene cuenta ni verificación; solo accede a su turno mediante el link con token.
- **RN-AU-03** (Suposición): Contraseñas con hash Argon2id/bcrypt; mínimo 12 caracteres; bloqueo temporal tras N intentos fallidos. El método final de autenticación es pregunta abierta.
- **RN-AU-04** (Suposición): Sesión con expiración por inactividad (ej. 30 min) por tratarse de acceso a datos de salud.

## Dominio: Agenda (RN-AG)

- **RN-AG-01** (decidida): El profesional no puede tener dos turnos `CONFIRMED` superpuestos. Se garantiza con restricción de exclusión de PostgreSQL (`tstzrange` con `&&`) **y** validación de aplicación previa para dar un error amigable.
- **RN-AG-02** (decidida): Los horarios dentro de un bloqueo (`schedule_block`) no son reservables, ni por el paciente ni por el profesional sin quitar el bloqueo.
- **RN-AG-03** (decidida): Los horarios fuera de los horarios de atención no son reservables por el paciente.
- **RN-AG-04** (Suposición): El profesional puede cargar un turno interno fuera de horario de atención de forma explícita (sobreturno) pero nunca superpuesto con otro turno ni con un bloqueo.
- **RN-AG-05** (decidida): Cada prestación tiene duración variable; el turno ocupa `starts_at` a `starts_at + duración de la prestación` (snapshot de la duración al reservar).
- **RN-AG-06** (Suposición): Los slots ofrecidos se generan con granularidad configurable (default 15 min) y solo si el rango completo entra en un tramo de atención libre.
- **RN-AG-07** (Suposición): Todas las fechas se guardan en UTC y se presentan en `America/Argentina/Buenos_Aires`; los cálculos de horarios usan la zona del consultorio.
- **RN-AG-08** (Suposición): Un bloqueo creado sobre turnos ya confirmados no los cancela automáticamente: el sistema avisa al profesional de los turnos afectados y este decide.
- **RN-AG-09** (Suposición): Desactivar una prestación no afecta turnos existentes; solo impide nuevas reservas.
- **RN-AG-10** (decidida): En v1 el consultorio tiene **un único profesional y un único sillón/box**: la agenda del profesional es la agenda del sillón, por lo que la prevención de solapamientos de RN-AG-01 rige a la vez por profesional y por sillón. Agenda multi-profesional y multi-sillón (con un recurso `chair` y la restricción de exclusión por sillón) es de etapas posteriores (ver `06_funcionalidades.md` §Funcionalidades posteriores (no MVP) y NE-02 en `10_preguntas_abiertas.md`).

## Dominio: Reservas públicas (RN-RS)

- **RN-RS-01** (decidida): La reserva pública se confirma de inmediato (`CONFIRMED`), sin aprobación del profesional.
- **RN-RS-02** (decidida): El paciente informa nombre, DNI, teléfono y email; no hay verificación de identidad ni de email.
- **RN-RS-03** (decidida): Se envía un email de confirmación con link de gestión que contiene un token único.
- **RN-RS-04** (Suposición): El token es aleatorio (CSPRNG, ≥128 bits), se almacena solo su hash, y está ligado a un único turno.
- **RN-RS-05** (Suposición): El token permite únicamente ver, cancelar o reprogramar ese turno; no da acceso a otros datos ni a la HC.
- **RN-RS-06** (Suposición): El token expira al pasar el turno y se revoca al cancelar; tras una reprogramación se mantiene el mismo token o se rota (decidir en implementación).
- **RN-RS-07** (decidida): Sin política de cancelación ni depósito en v1: el paciente puede cancelar o reprogramar en cualquier momento.
- **RN-RS-08** (Suposición, riesgo abierto): Mitigaciones de reservas falsas: rate limit por IP, honeypot, límite de turnos futuros activos por DNI/email/teléfono, anticipación mínima. Las medidas definitivas son pregunta abierta.
- **RN-RS-09** (Suposición): Reprogramar es atómico: se libera el slot anterior y se toma el nuevo en una sola transacción; si el nuevo slot ya no está libre, el turno original se conserva.
- **RN-RS-10** (Suposición): Anticipación mínima (`min_notice_min`) y horizonte máximo (`max_advance_days`) configurables para reservas públicas.

## Dominio: Pacientes (RN-PA)

- **RN-PA-01** (Suposición): El paciente se identifica por DNI dentro de la instalación (`UNIQUE (practice_id, dni)`).
- **RN-PA-02** (Suposición): Una reserva pública crea el paciente con `source = PUBLIC_BOOKING` si el DNI no existe.
- **RN-PA-03** (Suposición): Si el DNI ya existe, la reserva se asocia al paciente existente **sin sobreescribir** sus datos; los datos distintos informados se guardan en el turno o se marcan para revisión del profesional. Esto evita que un tercero modifique datos de un paciente usando su DNI.
- **RN-PA-04** (Suposición): Los pacientes no se eliminan físicamente; la baja es lógica, dado que su HC debe conservarse (RN-HC-06).

## Dominio: Notificaciones (RN-NO)

- **RN-NO-01** (decidida): En v1 el único canal es email: confirmación (inmediata) y recordatorio.
- **RN-NO-02** (Suposición): El recordatorio se envía N horas antes del turno (default 24 h, configurable).
- **RN-NO-03** (Suposición): Envío idempotente: máximo un recordatorio por turno (`UNIQUE (appointment_id, type)`); reintentos limitados ante fallo del proveedor.
- **RN-NO-04** (Suposición): No se envía recordatorio de turnos cancelados; al reprogramar se recalcula el recordatorio.
- **RN-NO-05** (Suposición): Un fallo de envío nunca revierte la reserva; se registra y se reintenta.
- **RN-NO-06** (Suposición): Los emails no incluyen datos clínicos; solo fecha, hora, prestación, consultorio y link.

## Dominio: Historia clínica (RN-HC)

- **RN-HC-01** (decidida): La HC nunca se elimina ni se sobreescribe. Las entradas son inmutables (sin UPDATE/DELETE).
- **RN-HC-02** (decidida): Las correcciones se registran como nuevas entradas/versiones que referencian la original, con autor, fecha/hora y motivo (Ley 26.529).
- **RN-HC-03** (decidida): Solo el profesional tratante accede a la HC. En v1 es el único usuario; el rol secretaria (v2) no tendrá acceso.
- **RN-HC-04** (decidida): Todo acceso a datos clínicos (lectura y escritura) queda en el log de auditoría.
- **RN-HC-05** (Suposición): Cada entrada guarda el autor (`author_id`) y `created_at` generado por el servidor (no editable por el cliente).
- **RN-HC-06** (Suposición, a validar): La HC se conserva durante el plazo legal vigente (la Ley 26.529 establece un mínimo de custodia; el valor exacto y su interacción con la Ley 27.706 deben confirmarse; ver 11 y 10).
- **RN-HC-07** (Suposición): El historial completo (incluyendo versiones corregidas) es consultable; la vista por defecto muestra la versión vigente con indicación de que fue corregida.
- **RN-HC-08** (decidida): Los datos de salud son datos sensibles (Ley 25.326); se minimiza su exposición (no van en emails, logs ni URLs).

## Dominio: Auditoría (RN-AD)

- **RN-AD-01** (decidida): El audit log registra acceso a datos clínicos con usuario, acción, paciente, fecha/hora e IP.
- **RN-AD-02** (Suposición): El log es append-only; el rol de BD de la aplicación no tiene UPDATE/DELETE sobre `audit_log`.
- **RN-AD-03** (Suposición): El log no almacena contenido clínico en claro, solo referencias.

## Dominio: Excepciones globales (RN-GL)

- **RN-GL-01** (Suposición): Ninguna operación destructiva sobre datos clínicos o de pacientes existe en la API.
- **RN-GL-02** (Suposición): Todo dato personal en tránsito va por HTTPS; en reposo, cifrado de disco y copias de seguridad cifradas (ver 08).
- **RN-GL-03** (decidida): Single-tenant en v1; toda entidad de negocio lleva `practice_id` para no bloquear multi-tenant futuro.
