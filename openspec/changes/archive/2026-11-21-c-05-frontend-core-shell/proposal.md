# Proposal: C-05 `frontend-core-shell`

## Why

C-01 dejó la SPA reducida a montar un `[data-app-root]` vacío. Todos los changes de interfaz que siguen (login C-06, agenda, pacientes, historia clínica, configuración y la reserva pública) necesitan la misma base: navegación con rutas públicas y privadas, un cliente HTTP que hable el formato de errores de C-02 y la protección CSRF de C-04, el estado de la sesión, un render que nunca interprete datos como HTML (regla dura 10, Ley 25.326) y fechas mostradas siempre en `America/Argentina/Buenos_Aires` (regla dura 8, RN-AG-07). Sin C-05 cada pantalla inventaría su propia versión de estas piezas, con riesgo de XSS, de mezclar UTC con hora local y de filtrar el token de gestión del paciente.

Governance: **BAJO**. Es un change **[UI]**: requiere el mockup del app shell privado y público aprobado (design system Revision 1, política `system-and-mockups`) antes del apply.

## What Changes

- **Router propio (History API)** sin dependencias: tabla de rutas con parámetros (`/turno/{token}`), guards por ruta (privada → requiere sesión; `/login` con sesión → agenda), redirección a `/login` conservando solo rutas internas como destino, vista "no encontrada", interceptación de enlaces internos y botones atrás/adelante.
- **`core/http`**: cliente `fetch` tipado (decodificación con *type guards*, nunca `any`), mismo origen con cookies, cabecera CSRF en métodos que modifican estado, timeout, y traducción de `application/problem+json` (C-02) a un error tipado; un 401 marca la sesión como vencida.
- **`core/auth`**: estado de sesión (`unknown` / `anonymous` / `authenticated`) contra un contrato de sesión (`GET /api/v1/auth/me`, `POST /api/v1/auth/logout`) que implementará C-04. El transporte de credenciales (cookie + CSRF hoy) queda detrás de una interfaz para poder cambiarlo si PA-04 decide otra cosa.
- **`core/state`**: store mínimo pub/sub tipado.
- **`core/dom`**: creación de nodos con texto siempre escapado, rechazo de atributos de evento (`on*`) y de URLs `javascript:`, y una verificación automática de que el código no usa `innerHTML`/`outerHTML`/`insertAdjacentHTML`.
- **Layouts**: app shell privado mobile-first (marca, navegación Agenda / Pacientes / Configuración como barra inferior en mobile y barra lateral en desktop, usuario y "Salir") y app shell público (marca, contenido orientado a la reserva, pie con enlaces legales). Integración de `design-system/tokens.css`, `brand.js` y `assets/logo.svg`; estilos solo con tokens.
- **Utilidades de fecha** (`shared/utils`): formateo de instantes UTC en hora del consultorio, clave de día local y límites UTC de un día local, con reloj reemplazable en tests.
- **`index.html`**: política de referrer `no-referrer` (el token de `/turno/{token}` no se filtra a terceros).
- **Tests (Vitest)**: router y guards, escape/render seguro, cliente HTTP (CSRF, errores, 401, timeout), store, sesión, utilidades de fecha, layouts y verificación de tokens en CSS.

Fuera de alcance: pantallas reales de login y cambio de contraseña (C-06), endpoints de auth y emisión de la cookie CSRF (C-04), contenido de agenda/pacientes/configuración/reserva (changes posteriores; aquí solo vistas *placeholder*), modo oscuro (TBD del design system), CSP como cabecera del servidor (C-04/C-27).

## Capabilities

### New Capabilities
- `spa-routing`: navegación de la SPA por URL, rutas públicas y privadas, guards de sesión, redirecciones seguras y vista no encontrada.
- `api-client`: cómo la SPA llama a la API: mismo origen, CSRF, decodificación tipada de respuestas y errores normalizados (`problem+json`, red, timeout, 401).
- `session-state`: estado de la sesión del profesional en la SPA, su carga inicial, cierre de sesión y reacción a la sesión vencida.
- `safe-rendering`: reglas de render del DOM que impiden interpretar datos como HTML o ejecutar URLs/atributos peligrosos.
- `app-layout`: app shell privado y público, navegación principal responsive, marca y uso exclusivo de tokens del design system.
- `local-time-display`: presentación de fechas y horas en la zona del consultorio y cálculo de días locales en la SPA.

### Modified Capabilities
- `spa-shell`: se agrega que el arranque resuelve la ruta inicial y renderiza su layout, y que el documento declara la política de referrer `no-referrer`.

## Impact

- **Código** (`frontend/`): `src/main.ts` (bootstrap), `src/router/`, `src/core/{http,auth,state,dom}/`, `src/layouts/`, `src/shared/utils/dates.ts`, `src/styles/`, `index.html`, `vite.config.ts` (`server.fs.allow` para leer `../design-system`).
- **Dependencias**: ninguna nueva (regla dura 12). Se usan `fetch`, History API, `Intl.DateTimeFormat` y las devDependencies existentes (Vitest, jsdom).
- **Backend**: sin cambios. C-05 fija el contrato que C-04 debe cumplir (`/api/v1/auth/me`, `/api/v1/auth/logout`, cookie `XSRF-TOKEN` + cabecera `X-XSRF-TOKEN`, 401 sin sesión); si C-04 elige otro método (PA-04) cambia solo el adaptador de transporte.
- **Design system**: consume Revision 1 sin modificarla.
- **Changes que dependen de este**: C-06 (login usa router, guards, sesión y cliente HTTP) y todos los changes **[UI]** posteriores.
