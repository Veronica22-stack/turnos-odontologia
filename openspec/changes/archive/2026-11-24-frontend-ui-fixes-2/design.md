# Design: `frontend-ui-fixes-2`

## Context

Ver proposal.md (Why). Los arreglos ya están implementados en `frontend/`; este diseño registra las decisiones tomadas. Restricciones: TypeScript estricto sin `any`, sin `innerHTML`, solo tokens de `design-system/tokens.css`, ninguna dependencia nueva, sin cambios de backend.

## Goals / Non-Goals

**Goals**
- Ningún diálogo cerrado visible ni formularios huérfanos en el documento.
- Que dar de baja a un paciente deje libres sus horarios futuros en la agenda y en la reserva pública.
- Que el índice de Configuración indique dónde está la persona.

**Non-Goals**
- Mover la liberación de turnos al backend (operación atómica): queda como mejora futura.
- Rediseñar la navegación de Configuración.

## Decisions

### D1. Diálogos ocultos salvo `[open]`
La regla de padding de los diálogos sin cabecera fijaba `display: grid` también cerrados, pisando el `display: none` del navegador. Se deja `display: grid` solo en `[open]` y se agrega `dialog:not([open]) { display: none; }` como red de seguridad.
- Además, `agenda-view.ts` agregaba el formulario al `body` en cada apertura y nunca lo quitaba: ahora se elimina en el evento `close` y una nueva apertura elimina el anterior, así hay como máximo un formulario y ninguno tras cerrar.

### D2. Liberar turnos desde el frontend antes de la baja
`releasePatientAppointments()` usa solo `listAppointments` y `cancelAppointment` del gateway de agenda (inyectado en `createPatientsView` desde `routes.ts`). Consulta de ahora a 365 días en tramos de 90 días (el backend limita el rango a 93), filtra por `patientId`, `CONFIRMED` y `startsAt >= ahora`, deduplica por id y cancela uno por uno.
- Orden: primero liberar, después `DELETE`. Si el listado falla, no se hizo nada irreversible: se informa el error (`kind: 'release'`) con «Reintentar» y la ficha sigue activa.
- Si falla alguna cancelación, la baja se completa igual y el aviso de éxito pasa a advertencia con la cantidad y la fecha/hora (Buenos Aires) de los turnos que quedaron sin liberar, para cancelarlos desde la agenda. Los mensajes no incluyen datos personales.
- *Alternativa descartada*: cancelar en el backend dentro del `DELETE`. Correcto a largo plazo, pero el alcance pedido era solo frontend.
- *Trade-off*: horizonte de 365 días; turnos más lejanos (improbables por el horizonte de reserva ≤ 365) no se liberan.

### D3. Índice con clic controlado y scroll-spy
El clic en un enlace del índice hace `preventDefault`, marca `aria-current` en ese enlace, hace `scrollIntoView` y enfoca la sección; así el salto de ancla no dispara `popstate` ni el re-render del router. `startSectionSpy()` recalcula en cada scroll qué sección cruza la línea bajo el header fijo (`getBoundingClientRect`, función pura `sectionsCrossing()`; arriba de todo, la primera) y además usa `IntersectionObserver` cuando existe; el cálculo por posición no depende de que el navegador pinte (pestaña en segundo plano). Decide con la función pura `pickActiveSection()`: la última sección que cruza la banda, o la última sección al llegar al final de la página. Se desconecta solo cuando la vista sale del documento y sin `IntersectionObserver` sigue funcionando por posición.

## Risks / Trade-offs

- [Baja no atómica: cancelaciones parciales] → se informa explícitamente qué turnos quedaron sin liberar.
- [Consultas extra al dar de baja (5 listados)] → costo bajo y acotado; solo ocurre al confirmar la baja.
- [Scroll-spy depende de la geometría del header] → margen fijo documentado en `section-spy.ts`.

## Migration Plan

Solo frontend; se despliega con el build de la SPA. Rollback: revertir el commit.
