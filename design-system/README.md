# Sistema de diseño — La Muela

Mode: system-and-mockups (decidido por el usuario, 2026-11-16)
Revision: 1
Approval: approved (2026-11-16, by user)

Fundación visual del proyecto: una sola fuente de nombre, logo y valores visuales que reutilizan todos los mockups (`openspec/changes/<name>/mockups/`) y el frontend (TypeScript vanilla, responsive, mobile-first, sin framework). Todo es local: sin fuentes remotas, CDN, Figma ni servicios externos.

## Propósito

- **Back-office del odontólogo** (desktop/tablet, denso): agenda diaria/semanal, pacientes, historia clínica con odontograma y evoluciones.
- **Reserva pública del paciente** (mobile): elegir prestación → elegir horario → datos (nombre, DNI, teléfono, email) → confirmación; cancelar/reprogramar por link.

## Archivos

| Archivo | Contenido |
|---------|-----------|
| `README.md` | Este documento: modo, revisión, aprobación, fuentes, pendientes, reemplazos |
| `brand.md` | Nombre, tono, color, tipografía, logo e imágenes, con fuentes y (TBD) |
| `brand.js` | Constante `BRAND_NAME` y binding de `[data-brand-name]` / `[data-brand-name-attr]` |
| `tokens.css` | Variables CSS: color (base, roles, semánticos, estados de turno, odontograma), tipografía, espaciado, radios, sombras, foco, movimiento + `prefers-reduced-motion` |
| `components.html` | Referencia viva de componentes y estados (importa `tokens.css`, `brand.js`, `assets/logo.svg`) |
| `assets/logo.svg` | Logo **PROVISIONAL** (rotulado visiblemente) |
| `previews/components-mobile-390.png` | Captura de página completa a 390×844 |
| `previews/components-desktop-1440.png` | Captura de página completa a 1440×900 |

Cómo verlo: abrir `design-system/components.html` en un navegador (funciona con `file://`), o servir la carpeta: `python -m http.server 8765 --bind 127.0.0.1` dentro de `design-system/` y abrir `http://127.0.0.1:8765/components.html`.

## Componentes incluidos

Header con logo y navegación; botones (primario, secundario, ghost, peligro, peligro outline; foco, deshabilitado, cargando; tamaños e ícono); inputs, select, textarea con ayuda y error; DNI y teléfono (+54); checkbox de consentimiento; badges de estado de turno (reservado, confirmado, atendido, ausente, cancelado, bloqueado); alertas (info, éxito, advertencia, peligro) y toasts; cards y opciones de prestación seleccionables; selector de horario público (mobile); vista diaria de agenda con turnos, bloqueo, hueco libre y línea de hora actual; tabla de pacientes (desktop) y lista (mobile); odontograma FDI de muestra con leyenda; evoluciones con entrada corregida; diálogo de confirmación (`<dialog>` nativo); estados vacíos.

## Decisiones clave

- Primario azul `#1F5BBF` (6.35:1); neutros fríos; semánticos con texto oscuro sobre fondo suave (todos ≥ 5:1). Bordes de controles `#8391A5` (3.2:1).
- Estados nunca solo por color: ícono + texto (+ tachado en cancelado; letra de código en el odontograma; rayado + candado en bloqueos).
- Fuente del sistema y cifras tabulares para datos clínicos.
- Foco: anillo de 3 px azul con separación blanca de 2 px (`--focus-ring`).
- Movimiento: 80–280 ms con `cubic-bezier(0.23, 1, 0.32, 1)`; presión de botón `scale(0.97)`; todo se anula con `prefers-reduced-motion`.
- Inputs del flujo público a 16 px y 48 px de alto (sin zoom en iOS, objetivo táctil cómodo).
- Modo oscuro: no incluido en Revision 1 (TBD).

## Fuentes

- Decisiones del usuario (2026-11-16): modo `system-and-mockups`, nombre "La Muela", tono clínico y confiable, primario azul, sin logo existente.
- `knowledge-base/01_vision_y_objetivos.md`, `03_actores_y_roles.md`, `04_modelo_de_datos.md` (estados de turno, catálogo de odontograma), `06_funcionalidades.md`, `07_flujos_principales.md`.
- `discovery/discovery.md` §4 y §9 (referentes de UX AgendaPro, NexHealth, Dentally; stack TypeScript vanilla responsive).

## Decisiones abiertas (TBD)

1. **Logo definitivo**, isotipo, favicon y variantes — hoy marcador provisional.
2. **Hex exactos** de la paleta — propuesta, a confirmar por el usuario.
3. **Estado "Reservado"** (TBD, decidido por el usuario 2026-11-16): se mantiene en el sistema como estado reservado para v2 (p. ej. pendiente de confirmación del paciente); no se usa en v1, donde los turnos nacen `CONFIRMED`.
4. **Modo oscuro**: fuera de Revision 1.
5. **Fuente de marca** propia (si se adopta, servida localmente).
6. **Librería de íconos** definitiva (local, sin framework).
7. **Catálogo del odontograma** por superficie (hoy por pieza; estados base del modelo: sano, caries, obturado, ausente, corona, endodoncia).
8. Dominio, eslogan y datos del consultorio visibles en el flujo público.

## Instrucciones de reemplazo

- **Nombre**: editar `BRAND_NAME` en `design-system/brand.js`. En HTML, marcar el texto con `data-brand-name` (se reemplaza el contenido) o usar `data-brand-name-attr="aria-label"` / `"alt"` para reemplazar un atributo; en `<title>` usar `{{brand}}`. Actualizar también `brand.md` y el texto de respaldo dentro de los elementos marcados.
- **Logo**: reemplazar `design-system/assets/logo.svg` manteniendo la misma ruta y nombre (proporción recomendada ~5:1, con `<title>` accesible). Quitar la etiqueta "PROVISIONAL" solo con el logo aprobado. Actualizar los `alt="… (logo provisional)"` de mockups.
- **Colores y demás valores**: editar las variables en `design-system/tokens.css`. Los componentes usan los roles (`--color-*`, `--status-*`, `--tooth-*`); cambiar la paleta base (`--blue-*`, `--gray-*`, …) o reasignar el rol. Verificar contraste AA (texto ≥ 4.5:1, UI ≥ 3:1) y actualizar la tabla de `brand.md`.
- **Tras cualquier cambio de token o asset compartido**: subir `Revision:` en este archivo, volver `Approval:` a `pending`, rerenderizar `components.html` y los mockups afectados (390×844 y 1440×900) y pedir nueva revisión.

## Aprobación

Aprobado explícitamente por el usuario el 2026-11-16 (Revision 1).
