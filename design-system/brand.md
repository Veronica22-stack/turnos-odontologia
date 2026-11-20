# Marca — La Muela

Revision: 1 · Approval: pending

## Nombre

- **Nombre del producto**: La Muela — definitivo (decisión del usuario, 2026-11-16).
- Fuente única para previews HTML: `design-system/brand.js` (`BRAND_NAME`).
- Uso: siempre "La Muela" (L y M mayúsculas). No abreviar ni traducir.
- Dominio, eslogan y nombre legal del consultorio: (TBD).

## Tono visual

**Clínico y confiable** (decisión del usuario, 2026-11-16).

- Mucho blanco y superficies limpias; fondo de app gris frío muy claro (`#F5F7FA`).
- Acentos fríos (azul y cian). Los colores cálidos se reservan para estados (ausente, advertencia, error).
- La legibilidad de los datos clínicos va primero: cifras tabulares, contraste AA o mejor, nada de texto sobre imágenes.
- Sobrio: sin gradientes, sin ilustraciones decorativas, sombras mínimas, movimiento breve y funcional.
- Dos registros de densidad: **back-office** denso (cuerpo 14 px, controles 40 px; desktop/tablet) y **flujo público** amplio (cuerpo 16 px, controles 48 px, objetivos táctiles ≥ 44 px; mobile-first).
- Voz: español rioplatense con voseo ("Reservá", "Elegí"), frases cortas, sin jerga clínica para el paciente.

Fuentes: decisión del usuario; `knowledge-base/01_vision_y_objetivos.md`, `03_actores_y_roles.md`, `06_funcionalidades.md`, `07_flujos_principales.md`; `discovery/discovery.md` §4 (referentes de UX).

## Color (PROPUESTA — hex exactos a confirmar)

Primario azul elegido por el usuario; los valores exactos son propuesta de este documento. Contraste calculado con la fórmula WCAG 2.x.

| Rol | Hex | Contraste | Uso |
|-----|-----|-----------|-----|
| Primario | `#1F5BBF` | 6.35:1 s/blanco | Acción principal, links, selección, foco |
| Primario hover | `#184A9E` | 8.36:1 | Hover/presionado |
| Primario suave | `#EEF4FC` | — | Fondos de selección; texto primario encima 5.74:1 |
| Texto | `#1A2433` | 15.6:1 | Texto principal |
| Texto secundario | `#5A6578` | 5.9:1 | Ayudas, metadatos |
| Borde de control | `#8391A5` | 3.20:1 | Bordes de inputs/botones (mínimo UI 3:1) |
| Divisor | `#E4E9F0` | — | Separadores decorativos (no delimitan controles) |
| Fondo app | `#F5F7FA` | — | Fondo general; superficies `#FFFFFF` |
| Éxito | `#17734A` / fondo `#E8F5EE` | 5.22:1 sobre su fondo | Atendido, confirmaciones |
| Advertencia | `#8A5300` / fondo `#FDF4E1` | 5.79:1 | Ausente, conflictos |
| Peligro | `#B42318` / fondo `#FDECEB` | 5.75:1 | Errores, cancelar, caries |
| Info | `#0B6680` / fondo `#E5F4F8` | 5.77:1 | Avisos neutros, reservado, endodoncia |

### Estados de turno

Nunca se comunican solo por color: siempre ícono + texto (+ tachado para cancelado).

| Estado UI | Modelo | Color | Ícono |
|-----------|--------|-------|-------|
| Reservado | (TBD) — no existe en v1 | Info cian | reloj |
| Confirmado | `CONFIRMED` | Primario azul | check |
| Atendido | `COMPLETED` | Éxito verde | check en círculo |
| Ausente | `NO_SHOW` | Advertencia ámbar | persona con ✕ |
| Cancelado | `CANCELLED` | Gris + tachado | ✕ |
| Bloqueado | bloqueo de agenda | Gris rayado | candado |

## Tipografía

- Fuente del sistema (sin descargas ni CDN): `system-ui, -apple-system, "Segoe UI", Roboto, …`. Monoespaciada del sistema para DNI/códigos.
- Escala: 12 · 14 · 16 · 18 · 20 · 24 · 30 px. Pesos 400/500/600/700.
- Cifras tabulares (`font-variant-numeric: tabular-nums`) en horarios, DNI, teléfonos y números de pieza.
- Fuente de marca propia: (TBD) — si se adopta, debe servirse localmente.

## Logo

- **No existe logo aprobado.** `design-system/assets/logo.svg` es un **marcador PROVISIONAL**: ícono de muela blanco sobre cuadrado azul + nombre + etiqueta visible "PROVISIONAL".
- No usar fuera de previews/mockups ni en materiales públicos hasta reemplazarlo.
- Logo definitivo, isotipo, favicon y versiones monocromo/negativo: (TBD).
- Área de resguardo y tamaño mínimo: (TBD) con el logo definitivo. Provisional: alto mínimo 28 px.

## Imágenes e iconografía

- Iconos: trazo de 1.5–2 px, esquinas redondeadas, `currentColor`, SVG inline local. Set propio mínimo en `components.html`; librería definitiva (TBD, debe ser local y sin dependencia de framework).
- Fotografía/ilustración: ninguna en v1 (TBD). Si se agrega, nada de imágenes clínicas explícitas en el flujo público.
- Datos de ejemplo siempre ficticios.

## Referentes (solo UX, no copiar marca)

AgendaPro (reserva desde link), NexHealth (captación/reserva), Dentally (portal del paciente) — `discovery/discovery.md` §4. Hipótesis sin demo.
