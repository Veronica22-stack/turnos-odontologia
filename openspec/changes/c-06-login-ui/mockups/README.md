# Mockups — C-06 `login-ui`

Design system: Revision 1 (aprobada 2026-11-16) · Política: `system-and-mockups`
Approval: approved (2026-11-21, by Lucas)
Preview: rendered (2026-11-21, `previews/`)

Todas las pantallas usan el app shell público aprobado en C-05 (header con logo provisional y "Acceso profesionales", pie con `(TBD)`), sin cambios.

## Pantallas

| Archivo | Ruta | Estado mostrado |
|---------|------|-----------------|
| `login.html` | `/login` | Inicial: título "Ingresar", email, contraseña con "Mostrar", botón "Ingresar" |
| `login-validation.html` | `/login` | Validación local: email con formato inválido y contraseña vacía (no se llama a la API) |
| `login-error.html` | `/login` | Credenciales inválidas (401 `AUTH_INVALID_CREDENTIALS`): alerta genérica, email conservado, contraseña vaciada |
| `login-locked.html` | `/login` | Bloqueo temporal (423 `AUTH_ACCOUNT_LOCKED` o 429, `Retry-After: 840` → "unos 14 minutos"): mismo mensaje en ambos casos |
| `login-expired.html` | `/login?next=%2Fpacientes` | Llegada por sesión vencida: aviso informativo `role="status"` sobre el formulario |
| `login-submitting.html` | `/login` | Enviando: botón "Ingresando…" con spinner y `aria-busy`, campos de solo lectura |
| `login-offline.html` | `/login` | Falla de red / timeout / error inesperado: aviso genérico de conexión |
| `change-password.html` | `/cambiar-contrasena` | Cambio **obligatorio** (primer ingreso): tres campos, política visible, requisitos en vivo, "Guardar contraseña" + "Salir" |
| `change-password-errors.html` | `/cambiar-contrasena` | Validación local: nueva corta y confirmación distinta |
| `change-password-current-invalid.html` | `/cambiar-contrasena` | Cambio **voluntario** con contraseña actual rechazada (400 `AUTH_CURRENT_PASSWORD_INVALID`): campo vaciado, "Cancelar" |
| `auth.css` | — | Solo variables de `design-system/tokens.css`; reglas del shell público de C-05 + campos, botones, alertas y spinner de `components.html`; medida local `--layout-auth-width` (design D7) |

Capturas (viewport, escala CSS, página completa) en `previews/`: `<pantalla>-390.png` (390x844) y `<pantalla>-1440.png` (1440x900), una por archivo HTML (20 en total).

## Fuentes

- `design-system/tokens.css`, `brand.js`, `assets/logo.svg`, `components.html` (Formularios, Botones, Alertas) — Revision 1.
- `openspec/changes/archive/2026-11-21-c-05-frontend-core-shell/mockups/public-shell.html` (shell público aprobado).
- `knowledge-base/06_funcionalidades.md` §Épica 1 (US-001, US-002), `07_flujos_principales.md` §Flujo 1, `05_reglas_de_negocio.md` §RN-AU.
- Specs del change: `specs/professional-login`, `specs/password-change`.

## Placeholders `(TBD)`

- Recuperación de contraseña olvidada (PA-04): el login solo lo menciona como pendiente.
- Datos del consultorio y destinos legales del pie (design system TBD 8, PA-10), heredados de C-05.
- Logo definitivo (rotulado PROVISIONAL).
- Los valores de los campos de contraseña son de relleno (`x`); el email de ejemplo es ficticio.
- Códigos y status de error: contrato supuesto con C-04 (design D2), a confirmar.

## Cómo verlo

`file://` puede bloquear `tokens.css`; serví la raíz del repo:

```bash
# desde la raíz del repo (cualquiera de los dos)
npx serve -l 8765
python -m http.server 8765 --bind 127.0.0.1
# abrir, por ejemplo:
#   http://127.0.0.1:8765/openspec/changes/c-06-login-ui/mockups/login.html
#   http://127.0.0.1:8765/openspec/changes/c-06-login-ui/mockups/login-error.html
#   http://127.0.0.1:8765/openspec/changes/c-06-login-ui/mockups/login-locked.html
#   http://127.0.0.1:8765/openspec/changes/c-06-login-ui/mockups/login-expired.html
#   http://127.0.0.1:8765/openspec/changes/c-06-login-ui/mockups/change-password.html
#   http://127.0.0.1:8765/openspec/changes/c-06-login-ui/mockups/change-password-errors.html
```

(con `npx serve` la URL es `http://localhost:8765/...`; `serve` puede quitar la extensión `.html`.) Probar a 390x844 y 1440x900 (DevTools → modo dispositivo). Detener el servidor con Ctrl+C.

## Notas de revisión

- Mobile: formulario a ancho completo sin tarjeta (menos bordes, más espacio táctil). Desktop (≥ 1024 px): tarjeta centrada de 26rem sobre fondo `--color-bg-subtle`.
- Error de servidor como alerta arriba del formulario (`role="alert"`); errores locales debajo de cada campo con ícono + texto + borde de 2 px (no dependen solo del color).
- Bloqueo en tono de advertencia (no de error): no es una equivocación del usuario sino una espera.
- "Salir" en el cambio obligatorio es la única salida: no hay navegación privada hasta completar el cambio.
- La lista de requisitos de la contraseña nueva se actualiza mientras se escribe (punto = pendiente, ✓ = cumple, ✕ = no cumple tras intentar enviar).
- En `/login`, "Acceso profesionales" queda marcado como página actual (`aria-current="page"`).
