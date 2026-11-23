# CHANGES — Secuencia de Implementación

> Índice canónico de todos los changes del proyecto **La Muela** (web app para odontólogo independiente en Argentina: agenda, reserva pública de turnos, historia clínica).
> Cada change es atómico: un agente puede implementarlo en una sesión (~4-6 horas).
> **Leer este archivo antes de ejecutar cualquier `/opsx:propose`.**

**Stack**: TypeScript vanilla (Vite) + Spring Boot (Gradle) + PostgreSQL (Flyway) · monolito modular · single-tenant v1 con `practice_id` en toda tabla de negocio.
**Modo de apply**: Strict TDD habilitado (RED → GREEN → TRIANGULATE → REFACTOR; PostgreSQL embebido (zonky) para integración).
**Política visual**: `system-and-mockups` con design system **Revision 1 aprobado** (`design-system/README.md`). Todo change marcado **[UI]** requiere un mockup HTML aprobado en `openspec/changes/<name>/mockups/` ANTES del apply (ver `design-system/`).

---

## Cómo usar este documento

1. Identificar el change a implementar (verificar que sus dependencias están en `openspec/changes/archive/`).
2. Leer los docs de la knowledge-base indicados en "Leer antes" (y `design-system/` si el change es **[UI]**).
3. Ejecutar `/opsx:propose <nombre-del-change>` (en changes **[UI]**: crear y aprobar el mockup antes de `/opsx:apply`).
4. Al terminar el change, archivarlo con `/opsx:archive <nombre-del-change>`.
5. Marcar el checkbox `[x]` en este archivo.

---

## Árbol de dependencias

```
C-01 foundation-setup
  ├── C-05 frontend-core-shell [UI]            ← paralelo con C-02
  └── C-02 core-models-shared
        └── C-03 audit-log
              └── C-04 auth-session            ← desbloquea TODO lo protegido
                    │
                    ├── C-06 login-ui [UI]     (+ C-05)
                    ├── C-07 services-catalog
                    ├── C-08 working-hours-booking-settings
                    │     └── C-09 schedule-blocks
                    └── C-10 patients-management
                          │
        C-07 + C-08 + C-09 + C-10
                          └── C-11 appointments-core
                                ├── C-12 availability-engine
                                └── C-13 email-notifications-outbox
                                      │
              C-10 + C-12 + C-13
                                      └── C-14 public-booking-api
                                            ├── C-15 booking-token-management
                                            └── C-24 public-booking-ui [UI]
                                                  └── C-25 booking-management-ui [UI]  (+ C-15)

C-03 + C-04 + C-10
  └── C-16 clinical-record-core               ← PA-01 (Ley 27.706) abierta
        ├── C-17 odontogram
        └── C-18 evolutions                   (+ C-11)

Interfaces del profesional [UI] (todas requieren C-06):
  C-19 settings-ui          ← C-07, C-08, C-09
  C-20 agenda-ui            ← C-10, C-11, C-12
  C-21 patients-ui          ← C-10
  C-22 clinical-record-ui   ← C-16, C-17, C-18, C-21
  C-23 audit-log-viewer     ← C-03 (API + UI)

Producción:
  C-26 security-hardening   ← C-15, C-22, C-23, C-25
    └── C-27 deployment-backups-ops
          └── C-28 compliance-launch-readiness
```

### Paralelismo por fase

> Cada "gate" es un punto de sincronización. Los changes dentro de un grupo pueden ejecutarse en paralelo.

```
GATE 0: ninguna
  → C-01 foundation-setup                       [Agente A]

GATE 1: C-01 ✓                                  ← FORK
  → C-02 core-models-shared                     [Agente A]
  → C-05 frontend-core-shell [UI]               [Agente C]

GATE 2: C-02 ✓
  → C-03 audit-log                              [Agente A]

GATE 3: C-03 ✓
  → C-04 auth-session                           [Agente A]

GATE 4: C-04 ✓ (+ C-05 ✓)                       ← FORK (3 paralelos)
  → C-07 services-catalog                       [Agente A]
  → C-08 working-hours-booking-settings         [Agente B]
  → C-06 login-ui [UI]                          [Agente C — si C-05 ✓]

GATE 5: C-07 ✓, C-08 ✓                          ← FORK (3 paralelos)
  → C-09 schedule-blocks                        [Agente A]
  → C-10 patients-management                    [Agente B]
  → C-23 audit-log-viewer [UI]                  [Agente C — si C-06 ✓]

GATE 6: C-09 ✓, C-10 ✓                          ← FORK (3 paralelos)
  → C-11 appointments-core                      [Agente A]
  → C-16 clinical-record-core                   [Agente B]
  → C-19 settings-ui [UI]                       [Agente C — si C-06 ✓]

GATE 7: C-11 ✓                                  ← FORK (3 paralelos)
  → C-12 availability-engine                    [Agente A]
  → C-13 email-notifications-outbox             [Agente B]
  → C-21 patients-ui [UI]                       [Agente C — si C-10 ✓ y C-06 ✓]

GATE 8: C-12 ✓, C-13 ✓, C-16 ✓                  ← FORK (3 paralelos)
  → C-14 public-booking-api                     [Agente A]
  → C-17 odontogram → C-18 evolutions           [Agente B — C-18 requiere además C-11 ✓]
  → C-20 agenda-ui [UI]                         [Agente C — requiere C-12 ✓]

GATE 9: C-14 ✓                                  ← FORK
  → C-15 booking-token-management               [Agente A]
  → C-22 clinical-record-ui [UI]                [Agente B — si C-17, C-18, C-21 ✓]
  → C-24 public-booking-ui [UI]                 [Agente C]

GATE 10: C-15 ✓, C-24 ✓
  → C-25 booking-management-ui [UI]             [Agente C]

GATE 11: C-15, C-22, C-23, C-25 ✓
  → C-26 security-hardening                     [Agente A]

GATE 12: C-26 ✓
  → C-27 deployment-backups-ops                 [Agente A/B]

GATE 13: C-26 ✓, C-27 ✓
  → C-28 compliance-launch-readiness            [Agente A]
```

### Camino crítico (14 changes — mínimo irreducible)

```
C-01 → C-02 → C-03 → C-04 → C-08 → C-09 → C-11 → C-12 → C-14 → C-24* → C-25* → C-26 → C-27 → C-28
```

(`*` C-25 depende de C-15 y C-24; los caminos C-14 → C-15 → C-25 y C-14 → C-24 → C-25 tienen la misma longitud y ambos son críticos.) La rama de historia clínica (C-16 → C-22) y las UIs del profesional no extienden el camino crítico, pero C-22 y C-23 deben estar listos antes de C-26.

### Plan óptimo con 3 agentes

| Paso | Agente A (Backend Core) | Agente B (Backend Aux) | Agente C (Frontend) |
|------|-------------------------|------------------------|---------------------|
| 1 | C-01 foundation-setup | — | — |
| 2 | C-02 core-models-shared | — | C-05 frontend-core-shell |
| 3 | C-03 audit-log | — | — |
| 4 | C-04 auth-session | — | — |
| 5 | C-07 services-catalog | C-08 working-hours-booking-settings | C-06 login-ui |
| 6 | C-09 schedule-blocks | C-10 patients-management | C-23 audit-log-viewer |
| 7 | C-11 appointments-core | C-16 clinical-record-core | C-19 settings-ui |
| 8 | C-12 availability-engine | C-13 email-notifications-outbox | C-21 patients-ui |
| 9 | C-14 public-booking-api | C-17 odontogram | C-20 agenda-ui |
| 10 | C-15 booking-token-management | C-18 evolutions | C-24 public-booking-ui |
| 11 | — | C-22 clinical-record-ui | C-25 booking-management-ui |
| 12 | C-26 security-hardening | — | — |
| 13 | C-27 deployment-backups-ops | — | — |
| 14 | C-28 compliance-launch-readiness | — | — |

> C-18 requiere C-11 y C-16; C-22 requiere C-17, C-18 y C-21 archivados. Los pasos 3-4 no tienen paralelismo posible en backend (cadena C-02 → C-03 → C-04); el Agente C puede adelantar el mockup de C-06 y los de las UIs siguientes mientras tanto.

### División de tareas del equipo (trabajo práctico)

**Alcance del TP — MVP imprescindible**: 24 changes (C-01 a C-22, C-24 y C-25), según la sección D de `discovery/investigacion-mercado.md` ajustada por el Discovery confirmado (`discovery/discovery.md` §5-6): agenda de un profesional sin solapamientos, reserva online autogestionada, recordatorios por email y ficha clínica con anamnesis, odontograma y evoluciones.

**Fuera de alcance**: C-23 (visor de auditoría; el registro lo cubre C-03), C-26 (hardening de producción), C-27 (despliegue) y C-28 (checklist legal de lanzamiento). Lo decide la líder.

**Change principal: C-11 `appointments-core`** — núcleo del dominio (turnos sin solapamientos), en el camino crítico. **Completado por Lucas** de punta a punta (explore → propose → apply → verify → archive); el verify lo hizo un agente verificador independiente del que implementó (quien testea no es quien implementó).

| Integrante | Responsabilidad principal | Changes |
|---|---|---|
| **Lucas (líder)** | Fundación (Discovery, knowledge-base, design system, roadmap, reglas del agente). **C-01, C-02 y C-11 completos** (C-11: explore, propose, apply, verify y archive). Decide gates y paralelismo, controla `master` y aprueba todos los PR. Evidencia de Engram. | C-01 ✓ · C-02 ✓ · C-11 ✓ |
| **Elian** | Backend núcleo sobre el camino crítico. | C-03 · C-04 · C-09 · C-12 |
| **Gabriel** | Dueño de la matriz de tests (cada escenario de cada spec: feliz, error de negocio y borde). README para clonar y correr los tests. API de reserva pública. | C-08 · C-10 · C-14 · C-15 |
| **Veronica** | Informe de Discovery en PDF (resumen ejecutivo, A, B, C y D). Backend de historia clínica y notificaciones. | C-13 · C-16 · C-17 · C-18 |
| **Mayra** | Verificación manual de ≥5 fuentes del informe (no armó la tabla) y hallazgos en la sección F. Revisión de la knowledge-base contra el informe. Frontend base y reserva pública (con mockups). | C-05 · C-06 · C-24 · C-25 |
| **Tomas** | Revisión del roadmap contra el MVP. Frontend del profesional (con mockups). | C-07 · C-19 · C-20 · C-21 · C-22 |

**Reglas de trabajo**

- Cada integrante trabaja en su rama `dev-<nombre>` (`dev-lucas`, `dev-elian`, …) con commits propios; a `master` solo se entra por Pull Request aprobado por la líder.
- El paralelismo respeta los gates de este documento; nadie tiene dos changes en el mismo gate (gate 4: Tomas C-07, Gabriel C-08, Mayra C-06 · gate 6: Lucas C-11, Veronica C-16, Tomas C-19 · gate 8: Gabriel C-14, Veronica C-17 → C-18, Tomas C-20).
- Tests automatizados por cada escenario de los specs (feliz, error de negocio y borde) en todos los changes, en modo Strict TDD.
- **Video de defensa**: Lucas (fundación, C-01, C-02 y C-11 completo con OPSX), Elian (backend núcleo), Gabriel (matriz de tests y API pública), Veronica (Discovery), Mayra (verificación de fuentes y UI pública), Tomas (roadmap y UI del profesional). **Reflexión escrita**: cada uno su parte.

---

## FASE 1 — Fundación y acceso

> C-05 corre en paralelo con C-02..C-04 (solo depende de C-01).

### [C-01] `foundation-setup`
- **Estado**: `[x]` completado y archivado (2026-11-21) — PR #1 (implementación) y PR #2 (verify y archive) mergeados en `master`; 39/39 tareas; verify PASS WITH WARNINGS sin críticos (`openspec/changes/archive/2026-11-21-c-01-foundation-setup/verify-report.md`)
- **Scope**:
  - Monorepo `backend/` (Spring Boot, Gradle Kotlin DSL, paquete base `…consultorio`) y `frontend/` (Vite + TypeScript estricto, sin framework)
  - Estructura modular `agenda, reservas, pacientes, historiaclinica, notificaciones, auth, auditoria, shared` con capas api/application/domain/infrastructure; verificación de dependencias entre módulos (ArchUnit o Spring Modulith)
  - Script SQL (`infra/postgres/init/01-roles.sql`) que crea la base y los roles en el PostgreSQL instalado localmente; README con los pasos de instalación de PostgreSQL (sin Docker, DD-10); `.env.example` con las variables de 08 §Variables de entorno, `.gitignore` sin secretos
  - Flyway con dos roles (`migrator` DDL / `app` sin UPDATE/DELETE sobre tablas inmutables, preparado); Migración V1 base verificada con PostgreSQL embebido (zonky)
  - Tooling de tests: JUnit 5 + PostgreSQL embebido zonky (`io.zonky.test:embedded-postgres`, solo test) (backend), Vitest (frontend); `./gradlew test` y `npm test` verdes con un test smoke por lado
  - Config base `application.yml` / `application-dev.yml`, health endpoint `/actuator/health`
- **Dependencias**: ninguna
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones, §Estructura de directorios, §Despliegue, §Variables de entorno
  - `knowledge-base/09_decisiones_y_supuestos.md`
  - `knowledge-base/04_modelo_de_datos.md` §Preparación para multi-tenant
  - `openspec/config.yaml`

### [C-02] `core-models-shared`
- **Estado**: `[x]` completado y archivado (2026-11-21) — 43/43 tareas; PR #3 mergeado en `master`; completado por Lucas; verify PASS WITH WARNINGS, warnings W1–W3 corregidos (`openspec/changes/archive/2026-11-21-c-02-core-models-shared/verify-report.md`)
- **Scope**:
  - Migración V2: tablas `practice` (una fila, seed) y `app_user` (email, `password_hash`, `role`, `failed_attempts`, `locked_until`, `must_change_password`)
  - Modelos: `Practice`, `AppUser`, enum `Role { DENTIST }` (diseñado para sumar `SECRETARY` en v2)
  - Módulo `shared`: reloj inyectable, utilitarios de zona `America/Argentina/Buenos_Aires` (UTC en BD), manejo global de errores (ProblemDetail, mensajes genéricos), `practice_id` de la instalación como contexto
  - Seed de bootstrap del DENTIST desde `BOOTSTRAP_ADMIN_EMAIL/PASSWORD` (no versionado), `must_change_password = true`
  - Tests: repositorios, conversiones de zona horaria, handler de errores, seed idempotente
- **Dependencias**: `C-01`
- **Governance**: CRITICO (modelos core que todo lo demás referencia)
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §practice, §app_user, §Seed data inicial
  - `knowledge-base/03_actores_y_roles.md`
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AU, §RN-AG-07, §RN-GL
  - `knowledge-base/08_arquitectura_propuesta.md` §Módulos

### [C-03] `audit-log`
- **Estado**: `[x]` completado y archivado (2026-11-21) — 30/30 tareas; implementado por Elian (PR #4); specs archivadas junto con C-04 en PR #6 (`openspec/changes/archive/2026-11-21-audit-log/`)
- **Scope**:
  - Migración V3: tabla `audit_log` (usuario, acción, `patient_id` nullable, referencia, IP, `created_at` servidor) append-only; REVOKE UPDATE/DELETE al rol `app`
  - Puerto `AuditLogger` (interfaz pública del módulo `auditoria`) con acciones `LOGIN_OK, LOGIN_FAIL, PATIENT_READ, HC_READ, HC_CREATE, HC_CORRECT, BOOKING_TOKEN_USE`
  - Sin contenido clínico en claro: solo referencias (RN-AD-03); sin DNI/email en logs de aplicación
  - Tests: inserción, imposibilidad de UPDATE/DELETE con el rol de la app (PostgreSQL embebido zonky), no filtración de datos sensibles
- **Dependencias**: `C-02`
- **Governance**: CRITICO (audit trail)
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §audit_log
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AD, §RN-HC-04
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad (Auditoría, Datos personales en logs)
  - `knowledge-base/11_cumplimiento_normativo.md`

### [C-04] `auth-session`
- **Estado**: `[x]` completado y archivado (2026-11-21) — 44/44 tareas; implementado por Elian (PR #6 mergeado en `master`) (`openspec/changes/archive/2026-11-21-auth-session/`)
- **Scope**:
  - `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `POST /api/v1/auth/change-password` (US-001, US-002)
  - Sesión servidor con cookie `HttpOnly; Secure; SameSite=Strict` + protección CSRF (PA-04: confirmar método en el propose)
  - Hash Argon2id/bcrypt, mínimo 12 caracteres, bloqueo progresivo (`failed_attempts`/`locked_until`), expiración por inactividad (`AUTH_SESSION_TIMEOUT_MIN`)
  - Errores genéricos sin revelar existencia de usuario; cambio obligatorio de contraseña en primer login
  - Rate limit de login por IP+email (infraestructura reutilizable por endpoints públicos); `@PreAuthorize` con rol `DENTIST`; todo `/api/v1/**` protegido salvo `/public/**` y `/auth/login`
  - Auditoría de `LOGIN_OK/LOGIN_FAIL` vía `AuditLogger`; cabeceras base de seguridad
  - Tests: login OK/fail, lockout, expiración de sesión, CSRF, cambio forzado de contraseña, endpoints protegidos devuelven 401
- **Dependencias**: `C-02, C-03`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/03_actores_y_roles.md` §RBAC, §Rutas públicas
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AU
  - `knowledge-base/07_flujos_principales.md` §Flujo 1
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad (Autenticación, Autorización)
  - `knowledge-base/10_preguntas_abiertas.md` §PA-04

### [C-05] `frontend-core-shell` **[UI]**
- **Estado**: `[x]` completado y archivado (2026-11-21) — verify PASS WITH WARNINGS sin críticos, W1 corregido y W2–W4 registrados en design.md (`openspec/changes/archive/2026-11-21-c-05-frontend-core-shell/verify-report.md`); mockups aprobados por Lucas; PR #5 mergeado en `master`
- **Scope**:
  - Bootstrap `main.ts`, router propio (History API) con guards (`/login`, rutas privadas, públicas `/reservar` y `/turno/{token}`)
  - `core/http` (cliente fetch tipado, manejo de errores, CSRF), `core/auth` (estado de sesión), `core/state` (store pub/sub), `core/dom` (render con escape de HTML, sin `innerHTML` con datos de usuario)
  - Layout privado responsive mobile-first (navegación: Agenda, Pacientes, Configuración) y layout público; integración de `design-system/tokens.css` y `brand.js`
  - Utilidades de fechas con zona `America/Argentina/Buenos_Aires`
  - Mockup requerido: app shell privado y público en 390x844 y 1440x900
  - Tests (Vitest): router/guards, escape HTML, cliente HTTP, utilidades de fecha
- **Dependencias**: `C-01`
- **Governance**: BAJO
- **Leer antes**:
  - `design-system/README.md`, `design-system/tokens.css`, `design-system/components.html` (Revision 1 aprobada; mockup HTML aprobado antes del apply)
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (frontend), §Seguridad (Validación de input)
  - `knowledge-base/03_actores_y_roles.md` §Rutas públicas

### [C-06] `login-ui` **[UI]**
- **Estado**: `[x]` completado y archivado (2026-11-21) — 38/38 tareas; PR #7 mergeado en `master`; verify PASS WITH WARNINGS sin críticos, W1–W4 resueltos (`openspec/changes/archive/2026-11-21-c-06-login-ui/verify-report.md`); mockups revisión 2 aprobados por Lucas; contrato confirmado contra C-04
- **Scope**:
  - Pantalla `/login` y cambio obligatorio de contraseña (US-001, US-002); mensajes genéricos de error y de cuenta bloqueada
  - Redirección a la ruta original tras login; sesión expirada redirige a login conservando la ruta; logout
  - Mockup requerido: login, cambio de contraseña, estados de error/bloqueo
  - Tests: flujo login/fail/lock, guard de rutas, expiración
- **Dependencias**: `C-04, C-05`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html` (mockup aprobado antes del apply)
  - `knowledge-base/06_funcionalidades.md` §Épica 1
  - `knowledge-base/07_flujos_principales.md` §Flujo 1
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AU

---

## FASE 2 — Configuración de la agenda

### [C-07] `services-catalog`
- **Estado**: `[x]` completado y archivado (2026-11-22) — 39/39 tareas; PR #12 mergeado en `master`; implementado con TDD estricto (`openspec/changes/archive/2026-11-22-c-07-services-catalog/`)
- **Scope**:
  - Migración V4: tabla `service` (nombre, duración en minutos, `active`, `public_visible`, `practice_id`)
  - CRUD `/api/v1/services` con baja lógica (nunca borrado físico); validación duración > 0 y múltiplo de la granularidad (US-011)
  - `GET /api/v1/public/services` (solo activas y públicas, campos públicos, cache corto, rate limit de C-04)
  - Desactivar no afecta turnos existentes (RN-AG-09)
  - Tests: CRUD, validaciones de duración, filtro público, baja lógica
- **Dependencias**: `C-04`
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §service
  - `knowledge-base/06_funcionalidades.md` §US-011
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-05, RN-AG-09
  - `knowledge-base/03_actores_y_roles.md` §Rutas públicas

### [C-08] `working-hours-booking-settings`
- **Estado**: `[x]` completado y archivado (2026-11-22) — 13/13 tareas; implementado por Gabriel (PR #8); archivado desde `dev-elian` en PR #10 junto con C-09 (`openspec/changes/archive/2026-11-22-c-08-working-hours-booking-settings/`)
- **Scope**:
  - Migración V5: tabla `working_hours` (día, tramos, sin solapamiento) y parámetros en `practice` (`slot_granularity_min` default 15, `min_notice_min`, `max_advance_days`, `reminder_hours_before`)
  - Endpoints `/api/v1/working-hours` y `/api/v1/practice/settings` (nombre, zona horaria, contacto) (US-010, US-013)
  - Validación de tramos superpuestos/inválidos; aviso (sin cancelar) de turnos confirmados afectados
  - Tests: múltiples tramos por día, solapamientos rechazados, defaults
- **Dependencias**: `C-04`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §working_hours, §practice
  - `knowledge-base/06_funcionalidades.md` §US-010, §US-013
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-03, RN-AG-06, RN-AG-07, RN-RS-10
  - `knowledge-base/10_preguntas_abiertas.md` §PA-19

### [C-09] `schedule-blocks`
- **Estado**: `[x]` completado y archivado (2026-11-22) — 29/29 tareas; implementado por Elian (PR #10 mergeado en `master`); migración real V8 (no V6) (`openspec/changes/archive/2026-11-22-c-09-schedule-blocks/`)
- **Scope**:
  - Migración V6: tabla `schedule_block` (rango `tstzrange`, motivo, `practice_id`)
  - CRUD `/api/v1/schedule-blocks` (US-012); validación de rango
  - Interfaz de dominio `BlockedRanges` consumible por disponibilidad y turnos manuales (RN-AG-02); puerto `AffectedAppointments` cuyo cableado se completa en C-11
  - Tests: creación, solapamiento entre bloqueos, rango inválido
- **Dependencias**: `C-08`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §schedule_block
  - `knowledge-base/06_funcionalidades.md` §US-012
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-02, RN-AG-08

---

## FASE 3 — Pacientes y turnos

### [C-10] `patients-management`
- **Estado**: `[x]` completado y archivado (2026-11-22) — 11/11 tareas; implementado por Gabriel (PR #8 mergeado en `master`); archivado desde `dev-veronica` junto con C-16
- **Scope**:
  - Migración V7: tabla `patient` (nombre, DNI, teléfono, email, `source`, baja lógica, `UNIQUE (practice_id, dni)`)
  - Endpoints `/api/v1/patients` (búsqueda por nombre/DNI/teléfono, ver, editar, baja lógica) (US-050); sin DELETE físico
  - Servicio de dominio `FindOrCreatePatientByDni` con RN-PA-03 (no sobreescribe datos existentes; datos distintos marcados para revisión)
  - Auditoría `PATIENT_READ` en cada lectura de ficha
  - Tests: unicidad por DNI, búsqueda, baja lógica, no sobreescritura, auditoría
- **Dependencias**: `C-03, C-04`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §patient
  - `knowledge-base/05_reglas_de_negocio.md` §RN-PA
  - `knowledge-base/06_funcionalidades.md` §US-050
  - `knowledge-base/10_preguntas_abiertas.md` §PA-13, PA-15

### [C-11] `appointments-core`
- **Estado**: `[x]` completado y archivado (2026-11-22) — 62/62 tareas; verify PASS WITH WARNINGS, W1 y S4 corregidos (`openspec/changes/archive/2026-11-22-c-11-appointments-core/verify-report.md`); completado por Lucas (explore, propose, apply, verify y archive); 566 unit + 364 IT verdes
- **Scope**:
  - Migración V13: tabla `appointment` (`starts_at`, `ends_at` con snapshot de duración, estado `CONFIRMED/CANCELLED/COMPLETED/NO_SHOW`, `cancelled_by`, `origin` (PUBLIC/INTERNAL) y `overbooking`) con `EXCLUDE USING gist` (`tstzrange` `&&`, `status <> 'CANCELLED'`) por `practice_id`
  - Desvíos aprobados: lock por consultorio en toda mutación (D3/D18), listado con tope de 93 días, fix de `ScheduleBlockSchemaIT` (ver design.md)
  - Endpoints `/api/v1/appointments`: crear turno manual (US-021, sobreturno explícito RN-AG-04), cancelar, reprogramar, marcar `COMPLETED`/`NO_SHOW` (US-022); validación previa de aplicación + 409 amigable ante violación de exclusión
  - Rechazo de solapamiento con turnos y bloqueos (RN-AG-01/02); consulta de turnos afectados por un bloqueo (cierra C-09)
  - Reprogramación atómica (RN-RS-09) reutilizable por C-15
  - Eventos de aplicación `AppointmentConfirmed/Cancelled/Rescheduled` (consumidos por C-13)
  - Tests: concurrencia (dos inserts simultáneos), solapamientos, bloqueos, transiciones de estado, snapshot de duración
- **Dependencias**: `C-07, C-08, C-09, C-10`
- **Governance**: CRITICO (invariante de no-solapamiento; modelo core)
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §appointment
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-01, RN-AG-02, RN-AG-04, RN-AG-05, RN-RS-09
  - `knowledge-base/06_funcionalidades.md` §US-021, US-022
  - `knowledge-base/07_flujos_principales.md` §Flujo 3 (transacción y 409)
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones (restricción en BD)

### [C-12] `availability-engine`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Algoritmo puro de slots: horarios de atención − bloqueos − turnos `CONFIRMED`, con duración suficiente, granularidad, `min_notice_min` y `max_advance_days` (RN-AG-03/05/06, RN-RS-10)
  - `GET /api/v1/availability` (interno) y `GET /api/v1/public/availability?serviceId=&date=` (rate limit por IP)
  - Manejo de zona horaria y cambios de día; sin prestaciones activas devuelve "reservas no disponibles"
  - Tests (dominio puro): múltiples tramos, bloqueos parciales, bordes de granularidad, zona horaria, distintas duraciones
- **Dependencias**: `C-11`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG-03, RN-AG-06, RN-AG-07, RN-RS-10
  - `knowledge-base/07_flujos_principales.md` §Flujo 3 paso 2
  - `knowledge-base/04_modelo_de_datos.md` §working_hours, schedule_block, appointment
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones (dominio testeable sin framework)

---

## FASE 4 — Notificaciones y reserva pública

> C-13 corre en paralelo con C-12 (ambos solo requieren C-11).

### [C-13] `email-notifications-outbox`
- **Estado**: `[x]` completado y archivado (2026-11-22) — 60/60 tareas; unit 650/650 e integración 441/441 verdes, jacocoTestReport OK; specs sincronizadas (`openspec/changes/archive/2026-11-22-c-13-email-notifications-outbox/`)
- **Scope**:
  - Migración V9: tabla `notification` (tipo `CONFIRMATION/REMINDER/CANCELLATION`, estado `PENDING/SENT/FAILED`, `scheduled_for`, `attempts`, `UNIQUE (appointment_id, type)`) (número a reasignar: debe ser mayor que la última versión aplicada, ≥ V14, al momento del apply; Flyway outOfOrder=false)
  - Outbox simple + scheduler (cada 5-15 min) con `FOR UPDATE SKIP LOCKED`, reintentos con backoff, idempotencia
  - Puerto `EmailSender` con adaptador SMTP; en dev los emails se escriben en el log, sin envío (DD-10); proveedor definitivo TBD (PA-09)
  - Plantillas sin datos clínicos: fecha, hora, prestación, consultorio y link `/turno/{token}` (RN-NO-06); `APP_BASE_URL`
  - Listeners de `AppointmentConfirmed/Cancelled/Rescheduled`: confirmación inmediata, recordatorio (`REMINDER_HOURS_BEFORE`), anular/recalcular (RN-NO-04); un fallo de envío nunca revierte la reserva (RN-NO-05)
  - Notificación al odontólogo de cancelaciones del paciente (Suposición, PA-14)
  - Tests: idempotencia, backoff, cancelación anula recordatorio, reprogramación recalcula, caída del proveedor
- **Dependencias**: `C-02, C-11`
- **Governance**: ALTO (sistema de notificaciones)
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §notification
  - `knowledge-base/05_reglas_de_negocio.md` §RN-NO
  - `knowledge-base/06_funcionalidades.md` §Épica 5 (US-040, US-041)
  - `knowledge-base/07_flujos_principales.md` §Flujo 5
  - `knowledge-base/10_preguntas_abiertas.md` §PA-09, PA-14

### [C-14] `public-booking-api`
- **Estado**: `[x]` completado y archivado (2026-11-23) — 40/40 tareas; `openspec/changes/archive/2026-11-23-c-14-public-booking-api/verify-report.md`; migración V16 (no V10; outOfOrder=false); 787 unit + 542 IT verdes (suite completa compartida con C-15); ver `docs/qa/matriz-de-tests.md`
- **Scope**:
  - `POST /api/v1/public/bookings` (US-030): rate limit, honeypot, validaciones de formato, anticipación y horizonte, límite de turnos futuros activos por DNI/email/teléfono (RN-RS-08; definitivo en PA-05)
  - Migración V10: tabla `booking_token` (hash, `appointment_id`, expiración, revocación); token CSPRNG ≥128 bits Base64URL, solo se guarda el hash (RN-RS-04) (número a reasignar: debe ser mayor que la última versión aplicada, ≥ V14, al momento del apply; Flyway outOfOrder=false)
  - Transacción única: `FindOrCreatePatientByDni` → `appointment CONFIRMED` → `booking_token` → encolar `CONFIRMATION` y `REMINDER`; 409 ante slot tomado
  - Respuestas genéricas ante honeypot/rate limit; no loguear token completo ni DNI/email
  - Registro de aceptación del aviso de privacidad en la reserva (texto TBD, PA-10)
  - Tests: reserva OK, carrera de slots (409), honeypot, rate limit, límites por contacto, no sobreescritura de paciente existente
- **Dependencias**: `C-10, C-12, C-13`
- **Governance**: CRITICO (endpoint público, tokens, datos personales)
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §RN-RS, §RN-PA
  - `knowledge-base/06_funcionalidades.md` §US-030
  - `knowledge-base/07_flujos_principales.md` §Flujo 3
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad (Links con token, Endpoints públicos)
  - `knowledge-base/10_preguntas_abiertas.md` §PA-05, PA-10

### [C-15] `booking-token-management`
- **Estado**: `[x]` completado y archivado (2026-11-23) — 26/26 tareas; `openspec/changes/archive/2026-11-23-c-15-booking-token-management/verify-report.md`; sin rotación de token al reprogramar; arista `reservas → auditoria.api`; 787 unit + 542 IT verdes (suite completa compartida con C-14). Pedido P1 de C-25 resuelto y archivado aparte: `serviceId` en `BookingView` (`openspec/changes/archive/2026-11-23-c-15-service-id-in-booking-view/`)
- **Scope**:
  - `GET /api/v1/public/bookings/{token}`, `DELETE /api/v1/public/bookings/{token}` (cancelar), `POST /api/v1/public/bookings/{token}/reschedule` (US-031, US-032)
  - Búsqueda por hash con comparación en tiempo constante; token ligado a un turno, expira al pasar el turno y se revoca al cancelar; decidir rotación tras reprogramar (RN-RS-06)
  - Reprogramación atómica reutilizando C-11 (si el nuevo slot se ocupó, se conserva el original; 409); turno pasado = solo lectura
  - Mensajes genéricos ante token inválido/expirado/revocado; rate limit anti-enumeración; `Referrer-Policy: no-referrer`
  - Emails de cancelación/nueva confirmación vía C-13; auditoría `BOOKING_TOKEN_USE`
  - Tests: cancelar, reprogramar OK/conflicto, token expirado/revocado, sin acceso a datos ajenos, rate limit
- **Dependencias**: `C-14`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/05_reglas_de_negocio.md` §RN-RS-04..09
  - `knowledge-base/06_funcionalidades.md` §US-031, US-032
  - `knowledge-base/07_flujos_principales.md` §Flujo 4
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad (Links con token)

---

## FASE 5 — Historia clínica

> Rama independiente de la reserva pública; avanza en paralelo con FASE 4. Condicionada por PA-01 (Ley 27.706 no relevada): el propose de C-16 debe explicitar qué se asume y registrar la validación legal pendiente.

### [C-16] `clinical-record-core`
- **Estado**: `[x]` completado y archivado (2026-11-22) — 18/18 tareas, suite completa verde (462 tests); desviaciones registradas en `openspec/changes/archive/2026-11-22-c-16-clinical-record-core/design.md`
- **Scope**:
  - Migración V11: tablas `clinical_record` y `anamnesis_entry` (`author_id`, `created_at` del servidor, `corrects_entry_id`, motivo); entradas inmutables
  - REVOKE UPDATE/DELETE al rol `app` sobre tablas de HC; sin endpoints de edición ni borrado (RN-HC-01, RN-GL-01)
  - Endpoints `/api/v1/patients/{id}/clinical-record`, `.../anamnesis` (US-060) y mecanismo genérico `POST .../corrections` con motivo obligatorio (US-063); versión vigente con historial
  - Auditoría `HC_READ/HC_CREATE/HC_CORRECT`; acceso exclusivo de `DENTIST` (RN-HC-03); apertura de `clinical_record` en el primer registro (PA-15)
  - Tests: inmutabilidad (UPDATE/DELETE fallan con el rol de la app), corrección referencia a la original, motivo obligatorio, auditoría, autor/fecha no manipulables por el cliente
- **Dependencias**: `C-03, C-04, C-10`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §clinical_record, §anamnesis_entry
  - `knowledge-base/05_reglas_de_negocio.md` §RN-HC, §RN-AD
  - `knowledge-base/06_funcionalidades.md` §US-060, US-063
  - `knowledge-base/07_flujos_principales.md` §Flujo 6
  - `knowledge-base/11_cumplimiento_normativo.md`, `knowledge-base/10_preguntas_abiertas.md` §PA-01, PA-11, PA-18

### [C-17] `odontogram`
- **Estado**: `[x]` completado y archivado (2026-11-22) — 17/17 tareas, suite completa verde (611 tests); Q1–Q7 aprobadas y desviaciones registradas en `openspec/changes/archive/2026-11-22-c-17-odontogram/design.md`
- **Scope**:
  - Migración V12: tabla `odontogram_entry` (pieza FDI, estado de catálogo base, `corrects_entry_id`, autor, `created_at` servidor)
  - Endpoints `.../odontogram` (US-061): estado actual derivado de la última entrada vigente por pieza; historial conservado; corrección vía C-16
  - Validación de notación FDI (dentición permanente/temporal según PA-17)
  - Tests: estado derivado, historial, piezas inválidas, corrección, inmutabilidad
- **Dependencias**: `C-16`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §odontogram_entry
  - `knowledge-base/06_funcionalidades.md` §US-061
  - `knowledge-base/05_reglas_de_negocio.md` §RN-HC-01, RN-HC-02, RN-HC-07
  - `knowledge-base/10_preguntas_abiertas.md` §PA-17

### [C-18] `evolutions`
- **Estado**: `[x]` completado y archivado (2026-11-23) — 21/21 tareas; unit 711/711 e integración 482/482 verdes, jacocoTestReport OK; specs sincronizadas (`openspec/changes/archive/2026-11-23-c-18-evolutions/`)
- **Scope**:
  - Migración V15: tabla `evolution_entry` (descripción ≤ 5000, `appointment_id` opcional, `corrects_entry_id`, autor, `created_at` servidor; sin `performed_at`) (V13 y V14 ya están ocupadas por `appointment` y `notification`; Flyway outOfOrder=false)
  - Endpoints `.../evolutions` (US-062): historial de la más reciente a la más antigua, asociación opcional a un turno (del mismo paciente, no cancelado ni ausente, ya comenzado); corrección vía C-16
  - Tests: alta, asociación a turno, orden, corrección, auditoría
- **Dependencias**: `C-11, C-16`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §evolution_entry
  - `knowledge-base/06_funcionalidades.md` §US-062
  - `knowledge-base/05_reglas_de_negocio.md` §RN-HC-01, RN-HC-05
  - `knowledge-base/07_flujos_principales.md` §Flujo 6

---

## FASE 6 — Interfaces del profesional

> Todos los changes de esta fase son **[UI]**: cada uno exige mockup HTML aprobado (design system Revision 1) antes del apply. Se ejecutan en paralelo entre sí en cuanto cumplan sus dependencias.

### [C-19] `settings-ui` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `/configuracion`: datos del consultorio, horarios de atención, prestaciones (CRUD con baja lógica), bloqueos, parámetros de reserva; botón "copiar link público de reserva"
  - Validación de tramos superpuestos en formulario; avisos de turnos afectados por bloqueos/horarios
  - Mockup requerido: todas las pantallas y estados vacíos/error en mobile y desktop
  - Tests: validaciones, render seguro, integración con API mockeada
- **Dependencias**: `C-06, C-07, C-08, C-09`
- **Governance**: BAJO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html` (mockup aprobado antes del apply)
  - `knowledge-base/06_funcionalidades.md` §Épica 2
  - `knowledge-base/07_flujos_principales.md` §Flujo 2
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (frontend `settings/`)

### [C-20] `agenda-ui` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Vistas diaria y semanal mobile-first con turnos, bloqueos, horarios de atención y estado (US-020)
  - Alta manual de turno (paciente existente o nuevo, prestación, slot desde disponibilidad interna), cancelar, reprogramar, marcar `COMPLETED/NO_SHOW` (US-021, US-022)
  - Mensajes claros ante solapamiento/409; indicación de sobreturno
  - Mockup requerido: vista día, vista semana, formulario de turno, estados de error
  - Tests: render de agenda, flujos de alta/cancelación, manejo de 409
- **Dependencias**: `C-06, C-10, C-11, C-12`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html` (mockup aprobado antes del apply)
  - `knowledge-base/06_funcionalidades.md` §Épica 3
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AG
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (frontend `agenda/`)

### [C-21] `patients-ui` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `/pacientes`: búsqueda (nombre/DNI/teléfono), ficha, edición, baja lógica (US-050); indicación de datos marcados para revisión (RN-PA-03)
  - Mockup requerido: listado, ficha, edición, estados vacíos
  - Tests: búsqueda, edición, baja lógica
- **Dependencias**: `C-06, C-10`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html` (mockup aprobado antes del apply)
  - `knowledge-base/06_funcionalidades.md` §US-050
  - `knowledge-base/05_reglas_de_negocio.md` §RN-PA

### [C-22] `clinical-record-ui` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `/historia-clinica` por paciente: anamnesis, odontograma FDI interactivo, evoluciones (US-060..063)
  - Corrección con motivo obligatorio; historial con entradas corregidas marcadas; sin controles de edición ni borrado
  - Sin datos clínicos en URLs, logs ni almacenamiento persistente en claro; sesión expirada durante la carga (borrador solo en memoria, evaluar riesgo)
  - Mockup requerido: anamnesis, odontograma, evoluciones, historial de correcciones, accesibilidad del odontograma
  - Tests: render, corrección, ausencia de controles de borrado, escape de HTML
- **Dependencias**: `C-06, C-16, C-17, C-18, C-21`
- **Governance**: ALTO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html` (mockup aprobado antes del apply)
  - `knowledge-base/06_funcionalidades.md` §Épica 7
  - `knowledge-base/07_flujos_principales.md` §Flujo 6
  - `knowledge-base/05_reglas_de_negocio.md` §RN-HC
  - `knowledge-base/10_preguntas_abiertas.md` §PA-17, PA-18

### [C-23] `audit-log-viewer` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `GET /api/v1/audit-log` (solo lectura, filtros por paciente y rango de fechas, paginado) (US-064)
  - Pantalla de consulta del registro de accesos; sin contenido clínico en claro
  - Mockup requerido: listado filtrable y estados vacíos
  - Tests: filtros, solo lectura (sin endpoints de escritura), autorización
- **Dependencias**: `C-03, C-06`
- **Governance**: ALTO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html` (mockup aprobado antes del apply)
  - `knowledge-base/06_funcionalidades.md` §US-064
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AD
  - `knowledge-base/04_modelo_de_datos.md` §audit_log

---

## FASE 7 — Reserva pública (UI)

### [C-24] `public-booking-ui` **[UI]**
- **Estado**: `[x]` completado y archivado (2026-11-23) — verify PASS WITH WARNINGS sin críticos, W1 (Volver tras 409) corregido con tests (`openspec/changes/archive/2026-11-23-c-24-public-booking-ui/verify-report.md`); mockups aprobados por Lucas; contrato verificado contra C-14/C-15; pendiente tarea 9.3 (prueba manual contra backend real) y PR a `master`
- **Scope**:
  - `/reservar`: selección de prestación, fecha/hora (slots de `GET /public/availability`), formulario nombre/DNI/teléfono/email con honeypot oculto y aviso de privacidad (texto TBD, PA-10)
  - `/reservar/confirmado`; manejo de 409 (recarga disponibilidad y sugiere otros horarios), rate limit y "reservas no disponibles"
  - Responsive mobile-first (canal principal del paciente); accesibilidad objetivo a definir (PA-21)
  - Mockup requerido: pasos 1-3, confirmación, errores 409/rate limit, estado sin prestaciones
  - Tests: flujo completo con API mockeada, honeypot no visible, validaciones
- **Dependencias**: `C-05, C-14`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html` (mockup aprobado antes del apply)
  - `knowledge-base/06_funcionalidades.md` §US-030
  - `knowledge-base/07_flujos_principales.md` §Flujo 3
  - `knowledge-base/03_actores_y_roles.md` §Rutas públicas
  - `knowledge-base/10_preguntas_abiertas.md` §PA-10, PA-21

### [C-25] `booking-management-ui` **[UI]**
- **Estado**: `[x]` completado y archivado (2026-11-23) — verify PASS WITH WARNINGS sin críticos (`openspec/changes/archive/2026-11-23-c-25-booking-management-ui/verify-report.md`); mockups aprobados por Lucas; contrato verificado contra C-15; pendiente tarea 8.3 (prueba manual contra backend real), pedido `serviceId` en `BookingView` a C-15 y PR a `master`
- **Scope**:
  - `/turno/{token}`: ver turno, cancelar con confirmación previa, reprogramar eligiendo nuevo slot (US-031, US-032)
  - Mensaje genérico ante token inválido/expirado; turno pasado en solo lectura
  - Mockup requerido: detalle, confirmación de cancelación, reprogramación, error de token
  - Tests: cancelar, reprogramar OK/conflicto, token inválido
- **Dependencias**: `C-15, C-24`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/components.html` (mockup aprobado antes del apply)
  - `knowledge-base/06_funcionalidades.md` §US-031, US-032
  - `knowledge-base/07_flujos_principales.md` §Flujo 4
  - `knowledge-base/05_reglas_de_negocio.md` §RN-RS-04..07

---

## FASE 8 — Producción y cumplimiento

### [C-26] `security-hardening`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - CSP estricta, HSTS, X-Content-Type-Options, frame-ancestors, CORS restringido a mismo origen, `Referrer-Policy`
  - Test de integración permanente de roles de BD (app sin UPDATE/DELETE en `audit_log` y tablas de HC)
  - Revisión de que ningún log/URL/email contiene DNI, email ni contenido clínico; revisión de rate limits y expiración de sesión
  - Pruebas de seguridad de endpoints públicos (enumeración de tokens, abuso de reservas) y escaneo de dependencias
  - Verificación visual contra mockups aprobados (`mockups/verify/`) de todas las pantallas **[UI]** a 390x844 y 1440x900
  - Tests: suite de seguridad, headers, permisos de BD
- **Dependencias**: `C-15, C-22, C-23, C-25`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad
  - `knowledge-base/05_reglas_de_negocio.md` §RN-GL, §RN-HC-08
  - `knowledge-base/11_cumplimiento_normativo.md` §Cómo el diseño las refleja
  - `design-system/README.md` (referencia visual para la verificación)

### [C-27] `deployment-backups-ops`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Estrategia de despliegue a definir (PA-03), sin Dockerfile, compose ni imagen (DD-10); artefacto único (backend sirviendo la SPA compilada), HTTPS/HSTS, variables de entorno de producción
  - Script de despliegue reproducible (un cliente = un despliegue, DD-02); health checks y logs
  - Backups `pg_dump` cifrados programados + procedimiento y prueba de restauración (RPO/RTO a definir, PA-06)
  - Hosting y jurisdicción de datos (PA-03) registrados como decisión; scheduler de recordatorios garantizado (app siempre activa)
  - Tests: build del artefacto, smoke test de despliegue, restore verificado
- **Dependencias**: `C-26`
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Despliegue, §Variables de entorno, §Seguridad (Backups, Cifrado)
  - `knowledge-base/10_preguntas_abiertas.md` §PA-03, PA-06, PA-12, IN-03
  - `knowledge-base/11_cumplimiento_normativo.md`

### [C-28] `compliance-launch-readiness`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Checklist de lanzamiento cerrado: validación legal de Ley 27.706 (PA-01), plazo de conservación de HC (PA-11), registro de la base ante AAIP (PA-10), aviso de privacidad y consentimiento final en la reserva pública
  - Ajustes derivados de la validación legal (backlog de changes correctivos si surgen requisitos de firma/conservación/registro de accesos)
  - Documentación operativa mínima: runbook de despliegue/backup, guía de bootstrap del odontólogo, métrica de no-show desde el día 1 (IN-05)
  - Tests: verificación de textos/consentimiento en la UI pública, trazabilidad de requisitos legales a RN-xx
- **Dependencias**: `C-26, C-27`
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/11_cumplimiento_normativo.md`
  - `knowledge-base/10_preguntas_abiertas.md` §PA-01, PA-10, PA-11, IN-05
  - `knowledge-base/05_reglas_de_negocio.md` §RN-HC-06, RN-HC-08
  - `knowledge-base/09_decisiones_y_supuestos.md`

---

## Tabla resumen

| ID | Change | Fase | UI | Governance | Depende de |
|----|--------|------|----|------------|------------|
| C-01 | foundation-setup | 1 | — | BAJO | — |
| C-02 | core-models-shared | 1 | — | CRITICO | C-01 |
| C-03 | audit-log | 1 | — | CRITICO | C-02 |
| C-04 | auth-session | 1 | — | CRITICO | C-02, C-03 |
| C-05 | frontend-core-shell | 1 | UI | BAJO | C-01 |
| C-06 | login-ui | 1 | UI | MEDIO | C-04, C-05 |
| C-07 | services-catalog | 2 | — | BAJO | C-04 |
| C-08 | working-hours-booking-settings | 2 | — | MEDIO | C-04 |
| C-09 | schedule-blocks | 2 | — | MEDIO | C-08 |
| C-10 | patients-management | 3 | — | ALTO | C-03, C-04 |
| C-11 | appointments-core | 3 | — | CRITICO | C-07, C-08, C-09, C-10 |
| C-12 | availability-engine | 3 | — | MEDIO | C-11 |
| C-13 | email-notifications-outbox | 4 | — | ALTO | C-02, C-11 |
| C-14 | public-booking-api | 4 | — | CRITICO | C-10, C-12, C-13 |
| C-15 | booking-token-management | 4 | — | CRITICO | C-14 |
| C-16 | clinical-record-core | 5 | — | CRITICO | C-03, C-04, C-10 |
| C-17 | odontogram | 5 | — | CRITICO | C-16 |
| C-18 | evolutions | 5 | — | CRITICO | C-11, C-16 |
| C-19 | settings-ui | 6 | UI | BAJO | C-06, C-07, C-08, C-09 |
| C-20 | agenda-ui | 6 | UI | MEDIO | C-06, C-10, C-11, C-12 |
| C-21 | patients-ui | 6 | UI | MEDIO | C-06, C-10 |
| C-22 | clinical-record-ui | 6 | UI | ALTO | C-06, C-16, C-17, C-18, C-21 |
| C-23 | audit-log-viewer | 6 | UI | ALTO | C-03, C-06 |
| C-24 | public-booking-ui | 7 | UI | MEDIO | C-05, C-14 |
| C-25 | booking-management-ui | 7 | UI | MEDIO | C-15, C-24 |
| C-26 | security-hardening | 8 | — | CRITICO | C-15, C-22, C-23, C-25 |
| C-27 | deployment-backups-ops | 8 | — | ALTO | C-26 |
| C-28 | compliance-launch-readiness | 8 | — | CRITICO | C-26, C-27 |

**Primer change recomendado**: `C-01` (foundation-setup) → `/opsx:propose C-01-foundation-setup`
