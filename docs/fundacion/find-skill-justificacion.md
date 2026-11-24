# Etapa 5 — `find-skill`: skills recomendadas y decisión

`find-skill` solo recomienda; la decisión de instalar es del equipo. Recomendó 8 skills y se instalaron
las 8 (registro en `.active-orchestrator-state.json` → `skills.installed`). Una línea de justificación por
recomendación:

| # | Skill (repositorio) | Decisión | Justificación |
|---|---|---|---|
| 1 | `springboot-tdd` (affaan-m/ecc) | Instalar | El backend es Spring Boot y el roadmap fija Strict TDD (RED → GREEN → REFACTOR) para todos los changes. |
| 2 | `springboot-security` (affaan-m/ecc) | Instalar | Manejamos datos de salud: hace falta deny-by-default, sesión con cookie, CSRF y roles (C-04, reglas duras de AGENTS.md). |
| 3 | `java-junit` (github/awesome-copilot) | Instalar | Los tests de backend son JUnit 5 y cada escenario de cada spec debe tener su test. |
| 4 | `spring-boot-test-patterns` (giuseppe-trisciuoglio/developer-kit) | Instalar | Los tests de integración (`*IT`) corren contra PostgreSQL embebido y necesitan patrones de `@SpringBootTest` y de concurrencia (C-11). |
| 5 | `vitest` (antfu/skills) | Instalar | El frontend en TypeScript vanilla se prueba con Vitest. |
| 6 | `webapp-testing` (anthropics/skills) | Instalar | Los changes [UI] se verifican en el navegador contra los mockups aprobados (390x844 y 1440x900). |
| 7 | `postgresql-table-design` (wshobson/agents) | Instalar | El invariante central (sin solapamiento de turnos) se garantiza con una restricción `EXCLUDE` de PostgreSQL y las migraciones son Flyway. |
| 8 | `web-design-guidelines` (vercel-labs/agent-skills) | Instalar | La reserva pública es mobile-first y todo el estilo sale de los tokens del design system aprobado. |

Las skills que el equipo usa por rol (backend, frontend, QA, fundación) están en `AGENTS.md` §Skills Disponibles,
y el índice generado por `skill-registry` en `.atl/skill-registry.md`.
