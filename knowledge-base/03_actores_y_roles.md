# Actores y Roles

## Actores del sistema

| Actor | Descripción | Cómo interactúa |
|-------|-------------|-----------------|
| **Odontólogo (DENTIST)** | Profesional independiente; único usuario interno en v1, con acceso total | Login en la SPA privada; configura agenda, atiende, registra HC |
| **Paciente (anónimo)** | Persona que reserva turno; sin cuenta ni verificación | Páginas públicas de reserva; link con token por email para cancelar/reprogramar |
| **Sistema (scheduler)** | Procesos internos (recordatorios, limpieza de tokens vencidos) | Jobs programados; envía emails |
| **Secretaria/asistente** | **Diferida a v2.** Gestionaría turnos sin acceso a la HC | No existe en v1; el modelo de roles debe permitir agregarla (ver DD-06) |

El rol de secretaria estaba "a confirmar" en el discovery; la decisión vigente es diferirla a v2 (ver inconsistencia IN-01 en 10).

## RBAC — Matriz de permisos (v1)

CRUD: C = crear, R = leer, U = actualizar, D = eliminar/anular. "—" = sin acceso.

| Recurso | DENTIST | Anónimo (público) | Anónimo con token de turno |
|---------|---------|-------------------|----------------------------|
| Configuración del consultorio / horarios | CRUD | — | — |
| Bloqueos de agenda | CRUD | — | — |
| Prestaciones (servicios) | CRUD (baja lógica) | R (solo activas, campos públicos) | — |
| Disponibilidad (slots) | R | R | R (para reprogramar) |
| Turnos (agenda interna) | CRUD | — | — |
| Reserva pública (crear) | — | C (con rate limit) | — |
| Turno propio vía token | — | — | R, U (reprogramar), cancelar (D lógico) |
| Pacientes (datos personales) | CRUD (baja lógica) | — | — |
| Historia clínica (anamnesis, odontograma, evoluciones) | C, R; corrección = nueva entrada; **sin D ni U destructivo** | — | — |
| Log de auditoría | R (solo lectura) | — | — |
| Usuarios / credenciales | U (cambiar contraseña propia) | — | — |

Notas:
- "D" sobre turnos significa transición a estado `CANCELLED`, no borrado físico.
- "D" sobre HC no existe a nivel de aplicación ni de permisos de base (ver RN-HC-01).
- El token de turno solo da acceso al turno que lo emitió, nunca a datos de otros pacientes ni a la HC (RN-RS-05).

## Rutas públicas

Accesibles sin autenticación (SPA y API):

| Ruta SPA | Propósito |
|----------|-----------|
| `/reservar` | Selección de prestación, fecha/hora y carga de datos |
| `/reservar/confirmado` | Confirmación de la reserva |
| `/turno/{token}` | Ver, cancelar o reprogramar el turno propio |
| `/login` | Acceso del odontólogo |

| Endpoint API | Protección |
|--------------|------------|
| `GET /api/v1/public/services` | Solo prestaciones activas; cache corto |
| `GET /api/v1/public/availability` | Rate limit por IP |
| `POST /api/v1/public/bookings` | Rate limit, honeypot, validaciones (ver RN-RS-08) |
| `GET/DELETE/POST /api/v1/public/bookings/{token}...` | Token de alta entropía, rate limit |
| `POST /api/v1/auth/login` | Rate limit y bloqueo progresivo |

Todo lo demás (`/agenda`, `/pacientes`, `/historia-clinica`, `/configuracion`, `/api/v1/**` salvo lo anterior) requiere sesión de DENTIST.
