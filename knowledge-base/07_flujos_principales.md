# Flujos Principales

Cada flujo se documenta extremo a extremo. Los códigos RN-XX remiten a `05_reglas_de_negocio.md`.

## Flujo 1: Login del odontólogo

**Disparador**: el profesional abre `/login`.
**Actor**: Odontólogo.

**Pasos**:
1. SPA envía `POST /api/v1/auth/login` con email y contraseña.
2. API aplica rate limit y verifica `locked_until`.
3. API valida el hash de la contraseña; en caso OK resetea `failed_attempts`, emite la sesión y registra `LOGIN_OK` en `audit_log`.
4. SPA redirige a `/agenda` (o a cambio obligatorio de contraseña si es el primer login).

```
Odontólogo -> SPA -> POST /auth/login -> API -> DB (app_user, audit_log)
                                          <- sesión (cookie HttpOnly o JWT; ver 10)
```

**Casos de error**:
- Credenciales inválidas → mensaje genérico; incrementa `failed_attempts`; registra `LOGIN_FAIL`.
- Cuenta bloqueada → mensaje genérico con indicación de reintentar luego.
- Sesión expirada en uso → redirección a login conservando la ruta.

**Nota**: el método de autenticación (sesión con cookie vs JWT) es pregunta abierta; el flujo es indiferente al detalle.

## Flujo 2: Configuración inicial de la agenda

**Disparador**: primer ingreso del profesional tras instalar el sistema.
**Actor**: Odontólogo.

**Pasos**:
1. Cambia la contraseña inicial (US-002).
2. Carga datos del consultorio (nombre, zona horaria, contacto).
3. Define horarios de atención por día (US-010).
4. Crea/ajusta prestaciones con duración (US-011).
5. Define parámetros de reserva: granularidad, anticipación mínima, horizonte (US-013).
6. Agrega bloqueos conocidos (US-012).
7. Copia el link público de reserva para compartirlo (WhatsApp, redes, Google Business).

**Casos de error**:
- Tramos horarios superpuestos o inválidos → validación en formulario y API.
- Sin prestaciones activas → la página pública muestra mensaje de "reservas no disponibles".

## Flujo 3: Reserva online del paciente

**Disparador**: el paciente abre el link público.
**Actor**: Paciente anónimo.

**Pasos**:
1. SPA consulta `GET /public/services` y el paciente elige prestación.
2. SPA consulta `GET /public/availability?serviceId=&date=`; el backend calcula slots = horarios de atención − bloqueos − turnos `CONFIRMED`, con duración suficiente (RN-AG-02/03/05/06, RN-RS-10).
3. El paciente elige un slot y completa nombre, DNI, teléfono, email (campo honeypot oculto).
4. SPA envía `POST /public/bookings`.
5. API: rate limit → validaciones de formato → transacción: buscar/crear `patient` por DNI (RN-PA-01..03) → insertar `appointment CONFIRMED` (la constraint de exclusión resuelve la carrera) → crear `booking_token` (hash) → encolar `notification CONFIRMATION` y `REMINDER`.
6. API responde con la confirmación; el servicio de email envía el mensaje con el link `/turno/{token}`.

```
Paciente -> SPA -> GET availability -> API -> DB
Paciente -> SPA -> POST bookings -> API -> [TX: patient, appointment, token, notification]
                                      |-> Email provider (async) -> Paciente
```

**Casos de error**:
- Slot tomado en paralelo (violación de exclusión) → HTTP 409; la SPA recarga disponibilidad y sugiere otros horarios.
- Honeypot completo / rate limit excedido → respuesta genérica sin reservar.
- Falla del proveedor de email → la reserva se mantiene; la notificación queda `FAILED/PENDING` para reintento (RN-NO-05).
- Límite de turnos futuros activos por DNI/email/teléfono → rechazo con mensaje (RN-RS-08, a definir).

## Flujo 4: Cancelar o reprogramar con el link (token)

**Disparador**: el paciente abre `/turno/{token}` desde el email.
**Actor**: Paciente con token.

**Pasos (cancelar)**:
1. SPA llama `GET /public/bookings/{token}`; la API busca por hash del token, valida no expirado ni revocado.
2. Se muestran fecha, prestación y acciones.
3. El paciente confirma cancelación → `DELETE /public/bookings/{token}`.
4. API: estado `CANCELLED`, `cancelled_by = PATIENT`, revoca token, anula el recordatorio pendiente, envía email de cancelación y notifica al profesional (RN-NO-04). Registra `BOOKING_TOKEN_USE`.

**Pasos (reprogramar)**:
1. Desde la misma pantalla, el paciente elige "reprogramar"; SPA consulta disponibilidad para la misma prestación.
2. `POST /public/bookings/{token}/reschedule` con el nuevo slot.
3. API en una transacción: toma el nuevo slot y libera el anterior (RN-RS-09); recalcula recordatorio; envía nueva confirmación.

**Casos de error**:
- Token inválido/expirado/revocado → mensaje genérico (no revela si existió).
- Nuevo slot ocupado → 409; el turno original se conserva.
- Turno ya pasado → solo lectura.
- Intentos masivos de adivinar tokens → rate limit y log.

## Flujo 5: Job de recordatorios

**Disparador**: scheduler (ej. cada 5-15 min).
**Actor**: Sistema.

**Pasos**:
1. Selecciona notificaciones `REMINDER` en estado `PENDING` con `scheduled_for <= now()` cuyo turno siga `CONFIRMED`; lo hace con bloqueo (`FOR UPDATE SKIP LOCKED`) para evitar duplicados.
2. Para cada una arma el email (sin datos clínicos, con link de gestión) y lo envía.
3. Marca `SENT` con `sent_at`; ante error incrementa `attempts` y reprograma con backoff hasta un máximo, luego `FAILED`.

**Casos de error**:
- Proveedor de email caído → reintentos con backoff; alerta en logs.
- Reinicio de la app durante el envío → idempotencia por estado y `UNIQUE (appointment_id, type)`.
- Turno reprogramado/cancelado entre medio → se recalcula o se descarta (RN-NO-04).

**Suposición:** si la app corre solo en local y se apaga, los recordatorios no salen; esto condiciona el hosting (ver 10).

## Flujo 6: Registro y corrección en la historia clínica

**Disparador**: el profesional atiende al paciente.
**Actor**: Odontólogo.

**Pasos (registro)**:
1. Abre la ficha del paciente; la API registra `PATIENT_READ`/`HC_READ` en `audit_log`.
2. Si no existe HC, se abre (`clinical_record`) al primer registro.
3. Carga anamnesis, estados del odontograma y/o evolución (`POST .../anamnesis|odontogram|evolutions`); el servidor completa `author_id` y `created_at`; registra `HC_CREATE`.

**Pasos (corrección)**:
1. El profesional elige "corregir" sobre una entrada; indica el motivo.
2. `POST .../corrections` crea una nueva entrada con `corrects_entry_id` apuntando a la original; la API registra `HC_CORRECT`.
3. La vista muestra la entrada vigente y, en el historial, la original marcada como corregida.

```
Odontólogo -> SPA -> API -> [TX: INSERT entry (nunca UPDATE/DELETE)] -> audit_log
```

**Casos de error**:
- Intento de UPDATE/DELETE sobre entradas → no existe endpoint; permisos de BD lo impiden además.
- Corrección sin motivo → validación rechaza.
- Sesión expirada durante la carga → se conserva borrador local en el navegador (**Suposición**, sin datos persistidos en claro; evaluar riesgo).
