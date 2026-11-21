# Mockups — C-05 `frontend-core-shell`

Design system: Revision 1 (aprobada 2026-11-16) · Política: `system-and-mockups`
Approval: pending (aprobador: Lucas)
Preview: rendered (2026-11-21, `previews/`)

## Pantallas

| Archivo | Pantalla | Estados mostrados |
|---------|----------|-------------------|
| `private-shell.html` | App shell privado (ruta de ejemplo `/agenda`) | Header con logo provisional, usuario (avatar; nombre visible desde 1024 px) y "Salir"; navegación Agenda / Pacientes / Configuración (barra inferior fija < 1024 px, barra lateral ≥ 1024 px) con "Agenda" actual; aviso del shell (`role="status"`); área de contenido *placeholder* |
| `public-shell.html` | App shell público (ruta de ejemplo `/reservar`) | Header con logo y "Acceso profesionales"; contenido de ancho legible con pasos de la reserva y *placeholder*; botón principal deshabilitado; pie con datos del consultorio y enlaces legales |
| `shell.css` | Estilos compartidos | Solo variables de `design-system/tokens.css`; medidas de layout locales documentadas en `design.md` D8 |

Capturas (viewport, escala CSS):

- `previews/private-390.png` (390x844) · `previews/private-1440.png` (1440x900)
- `previews/public-390.png` (390x844) · `previews/public-1440.png` (1440x900)

## Fuentes

- `design-system/tokens.css`, `brand.js`, `assets/logo.svg`, `components.html` (header y navegación) — Revision 1.
- `knowledge-base/03_actores_y_roles.md` §Rutas públicas; `knowledge-base/08_arquitectura_propuesta.md`.
- `openspec/changes/c-05-frontend-core-shell/specs/app-layout/spec.md`.

## Placeholders `(TBD)`

- Nombre del usuario de ejemplo ("Dra. Romero").
- Datos del consultorio (nombre, dirección, teléfono) — design system TBD 8.
- Destinos de "Política de privacidad" y "Términos y condiciones" — PA-10.
- Logo definitivo (hoy rotulado PROVISIONAL) y librería de íconos (los SVG del mockup son provisionales).
- El contenido de cada vista (agenda, reserva) llega en su propio change [UI].

## Cómo verlo

Los navegadores con bloqueo de `file://` pueden no cargar `tokens.css`; lo más simple es servir la raíz del repo:

```bash
# desde la raíz del repo
python -m http.server 8765 --bind 127.0.0.1
# abrir:
#   http://127.0.0.1:8765/openspec/changes/c-05-frontend-core-shell/mockups/private-shell.html
#   http://127.0.0.1:8765/openspec/changes/c-05-frontend-core-shell/mockups/public-shell.html
```

Probar a 390x844 y 1440x900 (DevTools → modo dispositivo). Detener el servidor con Ctrl+C.

## Notas de revisión

- Sin *drawer*: con tres destinos, la barra inferior es más rápida en mobile y no oculta la navegación (design D8).
- "Salir" queda en el header (ícono en mobile, ícono + texto en desktop), separado de la navegación para evitar toques accidentales.
- El indicador de carga de sesión (`role="status"`) y la vista "Página no encontrada" no tienen mockup propio: reutilizan el área de contenido de estos shells.
