# Proposal: C-06 `login-ui`

## Why

C-05 dejó `/login` como una vista *placeholder* y decodifica `mustChangePassword` sin actuar sobre él: hoy el odontólogo no tiene forma de ingresar desde la SPA y la cuenta creada por el bootstrap (C-03, `must_change_password = true`) podría usar la agenda con la contraseña de instalación. US-001 y US-002 (Épica 1, MVP) piden ingreso seguro con errores genéricos, bloqueo temporal visible, vuelta a la ruta original y cambio obligatorio de contraseña en el primer ingreso; es la puerta de entrada a todas las pantallas privadas que siguen (agenda, pacientes, historia clínica).

Governance: **MEDIO** (lógica de negocio del frontend; el backend de auth, CRÍTICO, es C-04). Es un change **[UI]**: requiere los mockups de login, cambio de contraseña y estados de error/bloqueo aprobados (design system Revision 1, política `system-and-mockups`) antes del apply.

Dependencias: C-05 (archivado, base de router/sesión/HTTP) y **C-04 (`auth-session`), implementado y archivado (2026-11-21)**. El contrato HTTP de design D2 fue contrastado con el código real de C-04; los tests de C-06 usan un `fetch` simulado con ese contrato. PA-04 quedó resuelta (sesión de servidor + cookie + CSRF).

## What Changes

- **Pantalla `/login`** (reemplaza el *placeholder*): formulario email + contraseña dentro del app shell público, validación local mínima (campos requeridos, formato de email), estado "Ingresando…" que bloquea el doble envío, y mensajes:
  - credenciales inválidas → un único mensaje genérico que no revela si el email existe;
  - demasiados intentos (429 `RATE_LIMITED`) → mensaje genérico con el tiempo aproximado de espera cuando el servidor lo informa (el bloqueo de cuenta de C-04 llega como el mismo 401 de credenciales inválidas);
  - sin conexión / error inesperado → aviso genérico de conexión;
  - aviso "Tu sesión venció. Ingresá de nuevo." cuando se llega por vencimiento (ya existente en C-05, ahora sobre el formulario real).
- **Ingreso exitoso**: la sesión pasa a `authenticated` con los datos devueltos por el login y la SPA navega (reemplazando el historial) al `next` validado o a `/agenda`; si la cuenta tiene `mustChangePassword`, va primero al cambio obligatorio conservando el destino.
- **Pantalla `/cambiar-contrasena`**: contraseña actual, nueva y confirmación; política de RN-AU-03 (mínimo 12 caracteres, distinta de la actual, confirmación igual) validada en el cliente y en el servidor (12–128 caracteres); errores por campo accesibles. Un cambio exitoso cierra todas las sesiones en el servidor: la SPA vuelve a `/login` con un aviso de contraseña cambiada. En modo obligatorio no ofrece otra salida que "Salir"; en modo voluntario permite "Cancelar".
- **Guard de cambio obligatorio**: con `mustChangePassword = true`, toda ruta privada redirige a `/cambiar-contrasena?next=…` hasta que el cambio se complete.
- **Cliente HTTP**: un 401 de un intento de credenciales (login, contraseña actual incorrecta) no se interpreta como sesión vencida; los errores exponen `Retry-After`.
- **Logout**: se mantiene el comportamiento de C-05 y se cubre el caso "atrás" tras salir.
- **Tests (Vitest + jsdom, `fetch` simulado)**: flujo login OK / falla / bloqueo / red, redirección a `next`, guard de cambio obligatorio, cambio de contraseña (política y errores del servidor), expiración y logout.

Fuera de alcance: endpoints, hash, bloqueo, rate limit y auditoría de `LOGIN_OK/LOGIN_FAIL` (C-04); recuperación de contraseña olvidada y 2FA (PA-04, sin decidir); medidor de fortaleza de contraseña; entrada de menú para el cambio voluntario (la agrega el change de Configuración); modo oscuro.

## Capabilities

### New Capabilities
- `professional-login`: pantalla de ingreso del profesional: formulario, validación local, estados de envío, mensajes genéricos de credenciales/bloqueo/conexión y destino tras el ingreso.
- `password-change`: cambio de contraseña del profesional (obligatorio en el primer ingreso y voluntario): política mínima, errores por campo y destino tras el cambio.

### Modified Capabilities
- `spa-routing`: el guard de rutas privadas y `/login` con sesión activa consideran `mustChangePassword` y redirigen al cambio obligatorio conservando el destino; se agrega la ruta `/cambiar-contrasena`.
- `session-state`: la sesión adopta la forma real de `GET /auth/me` de C-04 (`userId`, `role`, `mustChangePassword`, `expiresAt`; sin `id` ni `fullName`), se establece a partir del ingreso y pasa a `anonymous` con aviso tras cambiar la contraseña; tras "Salir" volver atrás no muestra contenido privado.
- `api-client`: un 401 en un intento de credenciales no dispara el aviso de sesión vencida.

## Impact

- **Código** (`frontend/`): `src/features/auth/` (nuevo: vistas de login y cambio de contraseña, validación, mapa de mensajes, *gateway* de auth), `src/app/routes.ts` (rutas `/login` y `/cambiar-contrasena`), `src/router/guards.ts` (cambio obligatorio), `src/core/auth/session.ts` (login/cambio), `src/core/http/client.ts` (opción de intento de credenciales), `src/styles/` (formulario y alertas solo con tokens).
- **Dependencias**: ninguna nueva (regla dura 12).
- **Backend**: sin cambios en este change. Contrato confirmado con C-04 en design D2 (contrastado con el código): `POST /api/v1/auth/login`, `POST /api/v1/auth/change-password`, códigos de error y `Retry-After`; los pedidos pendientes a C-04 están en design §Riesgos.
- **Design system**: consume Revision 1 sin modificarla (formularios, botones, alertas y spinner de `components.html`).
- **Changes que dependen de este**: C-24 y siguientes del camino crítico (interfaces privadas asumen sesión real).
