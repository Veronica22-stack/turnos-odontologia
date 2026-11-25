# Tasks: `frontend-ui-fixes`

> Change documentado después de implementado: todas las tareas están hechas y verificadas (tests Vitest, `npm run typecheck` OK y verificación en navegador). No toca `backend/`. Sin dependencias nuevas.

## 1. Horarios de atención (spec `settings-ui`)

- [x] 1.1 Test en `working-hours-section.test.ts`: el estado vacío muestra los siete días, cada uno con su botón «Agregar tramo el <día>»; verificado con Vitest
- [x] 1.2 Test: agregar un tramo al martes guarda `weekday: 2`; agregar al viernes junto a tramos existentes guarda la lista ordenada por día; agregar un tramo solapado al lunes marca el error en el campo correcto; verificado con Vitest
- [x] 1.3 `working-hours-section.ts`: botón por día con `aria-label`, filas ordenadas por día, ids de inputs únicos, sin el botón global que agregaba siempre al lunes; tests en verde
- [x] 1.4 `settings.css`: grilla de días y «Guardar horarios» a todo el ancho; verificado en navegador

## 2. Ventanas emergentes y odontograma (solo CSS)

- [x] 2.1 `agenda.css`, `settings.css`, `clinical-record.css`: diálogos con padding, borde, radio, sombra, backdrop semitransparente, tamaño máximo responsive con margen y acciones alineadas; verificado en navegador en desktop y celular
- [x] 2.2 `clinical-record.css`: marca de estado dentro de la pieza y etiqueta de cuadrante en fila propia; verificado en navegador

## 3. Sin marcadores provisionales (spec `app-layout`)

- [x] 3.1 Tests en `login-view`, `booking-messages`, `booking-view`, `details-step`, `manage-messages` y `public-layout`: ningún texto contiene `TBD` ni existe `.tbd`; verificado con Vitest
- [x] 3.2 Quitar los campos `tbd` de los mensajes y los `span.tbd` de `login-view.ts`, `booking-ui.ts`, `booking-messages.ts`, `manage-messages.ts`, `manage-view.ts`, `practice-section.ts` y `public-layout.ts`, con textos finales; tests en verde

## 4. Aviso de privacidad (specs `public-booking-ui`, `spa-routing`, `app-layout`)

- [x] 4.1 Tests en `privacy-notice.test.ts` y `details-step.test.ts`: el paso 3 tiene un `details.privacy-details` cerrado con «Ver aviso de privacidad», con el texto de la Ley 25.326, fuera del `label` de la casilla, sin `a[href="#"]`, y la casilla sigue obligatoria; verificado con Vitest
- [x] 4.2 `privacy-notice.ts` (nodos con `h()`, sin `innerHTML`) y `details-step.ts` con el desplegable; estilo en `booking.css`; tests en verde
- [x] 4.3 Test en `routes.test.ts`: `/privacidad` es pública, layout público, título «Aviso de privacidad» y contiene «25.326»; ruta agregada en `routes.ts`; verificado con Vitest
- [x] 4.4 Test en `public-layout.test.ts`: el pie tiene solo «Política de privacidad» con `href="/privacidad"`; `public-layout.ts` actualizado; verificado con Vitest

## 5. Verificación

- [x] 5.1 Tests de frontend y `npm run typecheck` en verde; recorrido en navegador de `/configuracion`, `/agenda`, historia clínica, `/reservar` (paso 3) y `/privacidad`

## Workflow follow-up

- Archivar el change con `openspec archive frontend-ui-fixes` antes del commit.
