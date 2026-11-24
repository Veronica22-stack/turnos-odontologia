# Design: `frontend-ui-fixes`

## Context

Ver proposal.md (Why). Los arreglos ya están implementados en `frontend/`; este diseño registra las decisiones tomadas. Restricciones: TypeScript estricto sin `any`, sin `innerHTML`, solo tokens de `design-system/tokens.css`, ninguna dependencia nueva.

## Goals / Non-Goals

**Goals**
- Que el profesional pueda cargar tramos para cualquier día y que la reserva pública ofrezca esos días.
- Diálogos y odontograma legibles en desktop y celular.
- Ningún texto provisional visible y ningún enlace muerto en las pantallas públicas.

**Non-Goals**
- Texto legal definitivo del aviso (PA-10) ni versionado del aviso en backend.
- Rediseño del design system o de los mockups aprobados.

## Decisions

### D1. Botón «Agregar tramo» por día
Cada bloque de día (lunes a domingo, siempre visibles, también en el estado vacío) incluye su botón con `aria-label` «Agregar tramo el <día>», que agrega un tramo 09:00–13:00 a ese día.
- *Alternativa descartada*: un único botón con un selector de día. Más pasos y más estado en el formulario.
- Las filas se reordenan por día tras agregar (orden estable) porque la recolección de valores y el mapeo de errores de validación por índice usan ese orden; los ids de inputs usan un contador incremental en lugar de la posición, que cambia al agregar o quitar.

### D2. Aviso de privacidad en `<details>` dentro del formulario
El aviso se renderiza con `renderPrivacyNotice()` (nodos creados con `h()`, sin `innerHTML`) en un `<details class="privacy-details">` fuera del `<label>` de la casilla, para que abrirlo no marque la casilla ni saque a la persona del formulario (no se pierden datos).
- *Alternativa descartada*: enlace a página aparte en pestaña nueva. En celular rompe el flujo y era el enlace muerto original.
- La misma función alimenta la ruta pública `/privacidad`, enlazada desde el pie: una sola fuente del texto.

### D3. Texto final en lugar de marcadores
Se eliminan los campos `tbd` de `BookingNotice`/`EmptyMessage` y los `span.tbd`; los textos terminan con punto y remiten a «comunicate con el consultorio». «Términos y condiciones» se elimina porque no existe documento que enlazar.

### D4. Diálogos y odontograma solo con CSS
Los estilos de `dialog` (padding, borde, radio, sombra, `::backdrop` semitransparente, `max-width`/`max-height` con margen y scroll interno, acciones con `gap`) y la grilla del odontograma (marca dentro de la pieza, etiqueta de cuadrante en fila propia) se ajustan en `agenda.css`, `settings.css` y `clinical-record.css` con tokens. No cambian comportamiento observable de los requisitos existentes, por eso no llevan delta de spec.

## Risks / Trade-offs

- [El aviso es un texto genérico, no revisado legalmente] → PA-10 sigue abierta; el texto vive en un solo módulo y se reemplaza sin tocar las vistas.
- [Reordenar filas al agregar puede desorientar] → el tramo nuevo aparece dentro del bloque del día donde se hizo clic.

## Migration Plan

Solo frontend; se despliega con el build de la SPA. Rollback: revertir el commit.
