# Mockups — C-22 `clinical-record-ui`

Design system: Revision 1 (aprobada 2026-11-16) · Política: `system-and-mockups` · Gobernanza: **ALTO** (datos de salud)
Decision: yes (2026-11-23)
Mockup revision: 1
Preview: rendered (2026-11-23, `previews/<estado>-390.png` y `previews/<estado>-1440.png`)
Visual verify: **passed (2026-11-24, re-verificación final del Ítem 10.3 en `verify/verify-report.md`)**: B-1, MA-1..MA-4, **R-1 y ME-2 resueltos** en 390×844 y 1440×900 con backend real (doble corrección sin recargar sobre la id nueva devuelta, 1 POST `201` y 0 GET por confirmación; `role="status"` = `aria-label` con FDI/estado/cara/selección). ME-1 se documenta como nota menor no bloqueante (autor `(TBD)` del mockup, sin PII). Capturas `verify/final-*.png`; sin inspección de píxeles (comparación DOM/ARIA/geometría/texto/red/estado), **no se afirma coincidencia pixel-perfect**.
Approval: **approved (2026-11-24, by user)** — aprobación explícita de la Revision 1; habilita el apply de C-22.

Un HTML por pantalla, directamente openable; sin frameworks, fuentes, CDNs ni assets remotos. Cada archivo importa `../../../../design-system/tokens.css` y `../../../../design-system/brand.js` y usa `../../../../design-system/assets/logo.svg` (rotulado **PROVISIONAL**, `(TBD)`). La navegación «Secciones», los paneles de estado y las notas de revisión son ayuda de aprobación: **no existen en la aplicación**. El resumen de diseño está en [design-brief.md](design-brief.md).

## Pantallas y estados

Ruta representada: `/historia-clinica` (privada, con «Historia clínica» como sección actual en el layout de C-05). Sin `?patientId`: el paciente se elige en memoria y la selección se pierde al recargar.

| Archivo | Pantalla | Qué muestra | Capturas |
|---------|----------|-------------|----------|
| [anamnesis.html](anamnesis.html) | Anamnesis | Cinco campos de C-16 y su historial con entrada corregida; autor/instante en Buenos Aires; formulario de alta con error local; `<script>` y entidades renderizados como texto literal. Campos marcados `(TBD, PA-18)`. Paneles: cargando, vacío, error de lectura con «Reintentar», sesión vencida con borrador solo en memoria, texto literal y error de validación sin eco del valor. | `previews/anamnesis-390.png`, `previews/anamnesis-1440.png` |
| [odontograma.html](odontograma.html) | Odontograma FDI | Cuadrantes 1–4 (permanente) y 5–8 (temporal), 52 piezas con marcas textuales y «sin registro» sin estado presumido; selección `36/OCCLUSAL` con `aria-pressed`; lote de 1–64 estados; caras solo para `CARIES`/`FILLED`; corrección sin `toothCode` ni `surface`. Catálogo marcado `(TBD, PA-17)`. Paneles: cargando, vacío, error, validación local, sesión vencida y layout sin scroll horizontal. | `previews/odontograma-390.png`, `previews/odontograma-1440.png` |
| [evoluciones.html](evoluciones.html) | Evoluciones | Línea de tiempo descendente con autor, instante local, turno ficticio y entrada corregida; alta con descripción obligatoria y turno opcional; contenido con etiquetas mostrado literalmente. Paneles: cargando, vacío, error con reintento, 422 propio, doble envío bloqueado y sesión vencida con descarte del borrador. | `previews/evoluciones-390.png`, `previews/evoluciones-1440.png` |
| [correcciones.html](correcciones.html) | Historial de correcciones | Correcciones de los tres tipos con motivo, autor, fecha y referencia a la original; originales marcadas como corregidas; diálogo estático con `reason` obligatorio; **ningún control de edición ni borrado** en ningún estado. Paneles: 409 propio, error de escritura con borrador retenido, sesión vencida, motivo con HTML literal, cargando y vacío. | `previews/correcciones-390.png`, `previews/correcciones-1440.png` |
| [accesibilidad-odontograma.html](accesibilidad-odontograma.html) | Accesibilidad del odontograma | Foco visible con `--focus-ring`, `role="group"` por cuadrante, botón por pieza con `aria-label` FDI + estado, `aria-pressed`, marcas textuales, recorrido con Tab/Enter/Espacio, `role="status"`, contraste con tokens y `prefers-reduced-motion`. Comprobaciones de layout a 390×844 y 1440×900. | `previews/accesibilidad-odontograma-390.png`, `previews/accesibilidad-odontograma-1440.png` |

Archivos de soporte:

| Archivo | Contenido |
|---------|-----------|
| `design-brief.md` | Brief: público, tareas, estados, responsive, tokens, restricciones y fuentes. |
| `clinical-record.css` | Reglas del mockup: shell privado, botones, campos, alertas, badges, timeline, odontograma, diálogo y estados. Solo `var(--…)` de `tokens.css`; variables locales `--hc-tooth-w` y `--hc-arch-max` derivadas de `--space-*`; `--hc-shadow-focus-label` replica `--focus-ring` para la demo de foco. |
| `previews/<estado>-390.png` | Captura renderizada a 390×844, escala CSS 1, página completa. |
| `previews/<estado>-1440.png` | Captura renderizada a 1440×900, escala CSS 1, página completa. |
| `verify/verify-report.md` | Informe de verificación posterior al apply: veredicto, diferencias por prioridad, consola/red, calidad final y capturas. |
| `verify/historia-clinica-con-datos-<390\|1440>.png` | Capturas de la aplicación real tras seleccionar un paciente ficticio, en ambos viewports. |
| `verify/ma-r2-*.png`, `verify/ma-r3-*.png` | Capturas de la re-verificación MA-1..MA-4: línea base, diálogo, motivo vacío/fallo simulado, altas, correcciones `201`, reintento error/éxito y foco; sufijo `-390`/`-1440` indica viewport. |
| `verify/final-*.png` | Capturas de la re-verificación final: `final-baseline`, `final-r1-anamnesis`, `final-r1-evoluciones`, `final-r1-odontograma`, `final-me2-superficie`, `final-retry-error` y `final-retry-ok`, con sufijo `-390`/`-1440`. |

## Cómo verlo

`file://` puede bloquear `tokens.css` en el entorno de render; servir la raíz del repo con un servidor estático local:

```bash
# desde la raíz del repo (cualquiera de los dos)
python -m http.server 8765 --bind 127.0.0.1
npx serve -l 8765
# abrir, por ejemplo:
#   http://127.0.0.1:8765/openspec/changes/c-22-clinical-record-ui/mockups/anamnesis.html
#   http://127.0.0.1:8765/openspec/changes/c-22-clinical-record-ui/mockups/odontograma.html
#   http://127.0.0.1:8765/openspec/changes/c-22-clinical-record-ui/mockups/evoluciones.html
#   http://127.0.0.1:8765/openspec/changes/c-22-clinical-record-ui/mockups/correcciones.html
#   http://127.0.0.1:8765/openspec/changes/c-22-clinical-record-ui/mockups/accesibilidad-odontograma.html
```

En un navegador normal también se pueden abrir los HTML con doble clic: las rutas relativas a `design-system/` funcionan con `file://` fuera de este harness. Probar a **390×844** y **1440×900** (DevTools → modo dispositivo). Detener el servidor con Ctrl+C al terminar.

Las capturas se generaron con Playwright MCP: navegación directa a cada HTML, viewport de 390×844 y 1440×900 y captura de página completa en `previews/`. La revisión automatizada comprobó los diez casos: HTTP 200, título con marca enlazada, tokens y logo cargados, textos y elementos esperados visibles, ausencia de desplazamiento horizontal de página, foco y atributos ARIA presentes, sin errores de consola ni recursos remotos. Los defectos detectados se corrigieron y se volvieron a renderizar; el detalle está en el informe de la puerta de diseño. La inspección visual final de los píxeles queda pendiente del usuario junto con la aprobación.

## Fuentes

- `design-system/README.md`, `tokens.css`, `brand.js`, `components.html` y `assets/logo.svg` — Revision 1, aprobada; no se modificaron.
- `openspec/changes/archive/2026-11-23-c-21-patients-ui/mockups/` — shell privado, estados, manejo de `(TBD)` y house style de revisión.
- `openspec/changes/c-22-clinical-record-ui/design.md` decisiones 1–12 y §UI Mockups; `proposal.md`; `tasks.md` §2.
- `specs/clinical-record-ui/spec.md` (15 requerimientos) y `specs/spa-routing/spec.md`.
- `knowledge-base/06_funcionalidades.md` §US-060..063; `knowledge-base/07_flujos_principales.md` §Flujo 6; `knowledge-base/05_reglas_de_negocio.md` §RN-HC-01..08; `knowledge-base/11_cumplimiento_normativo.md`; `knowledge-base/10_preguntas_abiertas.md` §PA-17/PA-18/PA-21.

## Placeholders `(TBD)` y privacidad

- Logo **PROVISIONAL** y nombre del profesional «Dra. Romero»: `(TBD)`.
- Catálogo FDI, dentición y caras: `(TBD, PA-17)`. Campos de anamnesis: `(TBD, PA-18)`. Nivel WCAG: `(TBD, PA-21)`. PA-01 (Ley 27.706) no se da por resuelto.
- Pacientes, notaciones dentales, motivos, turnos (`DEMO-0001`) y fechas son ficticios y evidentes; no hay PII real.
- Todo el contenido clínico se pinta como texto. No hay datos clínicos en `href`, `title`, query strings, título del documento, consola, mensajes de error, `localStorage` ni `sessionStorage`. El único JavaScript de los mockups alterna `aria-pressed` y escribe un estado con `textContent`.

## Aprobación

**Approved (2026-11-24, by user).** Revision 1 aprobada para la implementación de C-22 y como referencia de verificación visual; registrada también en `design.md` §UI Mockups. Cualquier revisión visual reinicia este estado a pending.
