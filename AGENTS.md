# Sistema de Gestión Odontológica (nombre a definir) — Instrucciones para Agentes

> Este archivo (y su copia `CLAUDE.md`) es lo PRIMERO que todo agente lee al entrar al repo.
> Generado a partir de `knowledge-base/` y `CHANGES.md`. No editar a mano sin re-sincronizar ambos archivos.

Aplicación web para un odontólogo independiente en Argentina: agenda, reserva online 24/7 sin cuenta (cancelación/reprogramación por link con token) e historia clínica inmutable. Monolito modular, single-tenant modelado para evolucionar a multi-tenant.

---

## Stack Tecnológico

| Capa | Tecnología | Versión mínima |
|------|------------|----------------|
| Frontend | SPA en **TypeScript vanilla (sin framework)**, responsive / mobile-first | TypeScript 5.x (Suposición) |
| Bundler frontend | Vite (Suposición: no impuesto; alternativa esbuild/tsc) | Vite 5+ |
| Backend | **Spring Boot** + **Gradle**, API REST | Java 21 LTS y Spring Boot 3.x (Suposición) |
| Base de datos | **PostgreSQL** (con `btree_gist` para la restricción de exclusión) | 15+ (Suposición) |
| Migraciones | Flyway (Suposición) | 10+ |
| Email | Proveedor transaccional (TBD; ver KB 10) | — |
| Tests | JUnit 5 + PostgreSQL embebido (zonky) (backend); Vitest (frontend) (Suposición) | — |

Detalle completo: [knowledge-base/02_descripcion_general.md](knowledge-base/02_descripcion_general.md)

---

## Base de Conocimiento

La fuente de verdad del dominio vive en `knowledge-base/`. **Leé el archivo relevante ANTES de implementar.**

| Archivo | Cuándo leerlo |
|---------|---------------|
| [01_vision_y_objetivos.md](knowledge-base/01_vision_y_objetivos.md) | Entender propósito y alcance |
| [03_actores_y_roles.md](knowledge-base/03_actores_y_roles.md) | Auth, permisos, rutas públicas |
| [04_modelo_de_datos.md](knowledge-base/04_modelo_de_datos.md) | Entidades, ERD, migraciones |
| [05_reglas_de_negocio.md](knowledge-base/05_reglas_de_negocio.md) | Reglas codificadas (RN-XX) |
| [06_funcionalidades.md](knowledge-base/06_funcionalidades.md) | Historias de usuario por épica |
| [07_flujos_principales.md](knowledge-base/07_flujos_principales.md) | Flujos E2E |
| [08_arquitectura_propuesta.md](knowledge-base/08_arquitectura_propuesta.md) | Patrones, estructura, seguridad, env vars |
| [09_decisiones_y_supuestos.md](knowledge-base/09_decisiones_y_supuestos.md) | Decisiones (DD) y supuestos (SU) |
| [10_preguntas_abiertas.md](knowledge-base/10_preguntas_abiertas.md) | Inconsistencias a resolver ANTES de codear |
| [11_cumplimiento_normativo.md](knowledge-base/11_cumplimiento_normativo.md) | Leyes 25.326, 26.529, 27.706 |

> Resolver las preguntas de prioridad **Alta** de `10_preguntas_abiertas.md` antes de arrancar el change que bloquean:
> - **PA-01** Ley 27.706 (HC electrónica): bloquea el diseño final del módulo de historia clínica.
> - **PA-02** Fecha límite del MVP: bloquea planificación y recorte de alcance.
> - **PA-03** Hosting y ubicación de los datos (Ley 25.326): bloquea el despliegue.
> - **PA-04** Método de autenticación del odontólogo (sesión vs JWT, 2FA, recuperación): bloquea el change de auth.
> - **PA-05** Reservas falsas/abuso sin cuenta: bloquea el change de reservas públicas.
> - **PA-06** Backups y recuperación: bloquea el despliegue.
> - **PA-07** Precio y modelo de cobro en ARS: bloquea el modelo de negocio.
>
> No bloquean `C-01`, pero hay que resolverlas antes de los changes indicados.

---

## Skills Disponibles

Fuente de verdad: `.atl/skill-registry.md`.

| Agente | Rol | Skills que carga |
|--------|-----|------------------|
| **Backend** | Spring Boot / JPA / Flyway / seguridad / PostgreSQL | `springboot-tdd`, `springboot-security`, `java-junit`, `spring-boot-test-patterns`, `postgresql-table-design` |
| **Frontend** | TypeScript vanilla / Vite / UI | `vitest`, `web-design-guidelines`, `emil-design-eng`, `design-taste-frontend-v1` |
| **E2E / QA** | Pruebas de navegador, verificación visual | `webapp-testing` |
| **Fundación / Orquestación** | KB, roadmap, design system, skills, instrucciones | `active-orchestrator`, `discovery-research`, `kb-creator`, `design-system`, `roadmap-generator`, `find-skill`, `skill-registry`, `agent-instruction`, `web-scraper`, `skill-creator` |

Cargá la skill correspondiente al contexto ANTES de escribir código.

> Los compact rules de cada skill los resuelve el orquestador desde `.atl/skill-registry.md` (generado por `skill-registry` y versionado en el repo). Esta tabla solo mapea skill→rol.

---

## Roadmap de Changes

El plan completo está en [CHANGES.md](CHANGES.md). Resumen:

- **Total**: 28 changes (C-01 a C-28) en 8 fases: 1 Fundación y acceso, 2 Configuración de la agenda, 3 Pacientes y turnos, 4 Notificaciones y reserva pública, 5 Historia clínica, 6 Interfaces del profesional, 7 Reserva pública (UI), 8 Producción y cumplimiento.
- **Camino crítico** (14): `C-01 → C-02 → C-03 → C-04 → C-08 → C-09 → C-11 → C-12 → C-14 → C-24 → C-25 → C-26 → C-27 → C-28` (C-25 también depende de C-15).
- **Primer change**: `C-01` (`foundation-setup`): monorepo `backend/` + `frontend/`, script SQL de base/roles para PostgreSQL local instalado, Flyway, tests con PostgreSQL embebido (zonky), README con pasos de instalación.
- **Changes [UI]**: requieren un mockup HTML aprobado (design system Revision 1, política `system-and-mockups`) en `openspec/changes/<name>/mockups/` ANTES del `/opsx:apply`.

**Antes de cualquier `/opsx:propose`**: leé [CHANGES.md](CHANGES.md), identificá las dependencias del change y los archivos de "Leer antes".

---

## Reglas Duras (específicas del proyecto)

> Reglas globales ya definidas en `~/.claude/CLAUDE.md` (orquestador, governance, TDD, engram): el proyecto las hereda. Acá viven solo las reglas **específicas de este proyecto** + las universales que el global no cubra.

Son contrato; romperlas es un defecto. Formato `NUNCA X → hacer Y`.

### Backend (Spring Boot)

1. NUNCA `@Autowired` en campos → inyección por constructor.
2. NUNCA exponer entidades JPA en la API → DTOs (records) con Bean Validation.
3. NUNCA `spring.jpa.hibernate.ddl-auto=update` ni editar una migración ya aplicada → cada cambio de esquema es una migración Flyway nueva.
4. Seguridad deny-by-default: todo endpoint requiere autenticación salvo los públicos de reserva/gestión por token declarados explícitamente; errores con formato único vía `@ControllerAdvice`.

### Datos de salud (Leyes 25.326 y 26.529)

5. NUNCA `UPDATE`/`DELETE` sobre entradas de historia clínica → una corrección es una entrada nueva que referencia la original, con autor y timestamp.
6. NUNCA loguear nombre, DNI, email, teléfono ni datos clínicos → loguear solo IDs.
7. Toda lectura o escritura de historia clínica registra un evento en `audit_log`.
8. Fechas guardadas en UTC y mostradas en `America/Argentina/Buenos_Aires`; NUNCA guardar tokens de gestión de turno en claro → solo hasheados.

### Frontend (TypeScript vanilla)

9. `tsconfig` strict; NUNCA `any` → `unknown` + type guards.
10. NUNCA `innerHTML` con datos de usuario o de la API → `textContent` o creación de nodos.
11. NUNCA colores, espaciados o tipografías hardcodeados → variables de `design-system/tokens.css`.
12. NUNCA agregar un framework o una dependencia de runtime sin aprobación explícita del usuario.

### Universales (no cubiertas por el global)

13. Conventional commits con el ID del change (ej. `feat(C-11): ...`).
14. NUNCA commit ni push sin pedido explícito del usuario.
15. NUNCA credenciales en el código ni en `application.yml` → variables de entorno + `.env.example` sin valores reales.

### Dominio (agregadas a mano por el equipo)

16. NUNCA almacenar datos reales de pacientes en el repositorio (código, tests, fixtures, mockups, capturas, ejemplos de la documentación ni Engram) → solo datos ficticios evidentes (ej. "Paciente Prueba", DNI `00000000`, emails `@ejemplo.com`).

---

## Flujo de Trabajo

```
1. Leer la KB relevante (knowledge-base/)          → entender el dominio
2. Identificar el change en CHANGES.md             → respetar dependencias
3. /opsx:propose <change>                          → proposal + design + specs + tasks
4. Mockup aprobado si el change es [UI]            → antes de implementar
5. /opsx:apply <change>                            → implementar tasks (cargando skills)
6. verify                                          → validar contra specs/design/tasks (y mockups si [UI])
7. /opsx:archive <change>                          → cerrar el change
```

Primer change: `/opsx:propose C-01-foundation-setup`.

Aplicar TODAS las reglas duras en cada paso. Ante conflicto entre la KB y este archivo, las reglas duras prevalecen.
