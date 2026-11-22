# Tasks

## 0. Puerta visual: mockups y aprobación (antes de apply)

- [x] 0.1 Crear `openspec/changes/c-19-settings-ui/mockups/` con `README.md` y HTML/CSS local para `/configuracion` y todos los estados listados en `design.md` §UI Mockups, importando `design-system/tokens.css` y `brand.js` y marcando `(TBD)` los valores provisionales; verificar que el README enumere cada archivo, las fuentes y `Approval: pending`.
- [x] 0.2 Renderizar cada mockup en 390×844 y 1440×900 con Playwright MCP (o sirviendo el directorio en localhost), guardar las capturas en `mockups/previews/`, inspeccionarlas y corregir defectos visibles; verificar que `Preview: rendered` figure en `mockups/README.md` y en `design.md`.
- [x] 0.3 Presentar al usuario las capturas y los enlaces HTML clicables de cada pantalla/estado y registrar `Approval: approved (<fecha>, by user)` en `design.md` §UI Mockups y en `mockups/README.md`; verificar que ambos indiquen la Revision 1 aprobada antes de continuar.
- [x] 0.4 NO ejecutar apply hasta aprobación explícita del mockup por el usuario; verificar que no haya cambios en `frontend/src/` ni ejecución de `/opsx:apply` mientras `Approval: pending`, y que, tras la aprobación, `design.md` sirva como criterio de aceptación.

## 1. Tipos y decodificadores del contrato

- [x] 1.1 RED: escribir `frontend/src/features/settings/settings-types.test.ts` con los casos felices `decodePracticeSettings` (payload completo → DTO readonly), `decodeWorkingHours` (`09:00:00` → `09:00`), `decodeServices` (incluye `active:false`), `decodeBlocks` (`affectedAppointments` vacío y con un turno) y verificar que `npm test -- src/features/settings/settings-types.test.ts` falle por módulo inexistente (desde `frontend/`).
- [x] 1.2 GREEN: crear `settings-types.ts` con interfaces `readonly` y decodificadores usando `isRecord`/`isString`/`isNumber`/`isBoolean`, sin `any` ni aserciones inseguras; verificar que el test del paso 1.1 pase y que `npm run typecheck` no reporte errores.
- [x] 1.3 TRIANGULATE: agregar a `settings-types.test.ts` los casos de error y borde: campo faltante, tipo incorrecto (`slotGranularityMin: '15'`), instante sin zona (`2026-03-10T09:00:00`), `weekday` fuera de 1…7 y texto hostil (`<img src=x onerror=alert(1)>`) conservado como string; verificar que el decodificador devuelva `undefined` o el texto literal según corresponda y que el test siga pasando.
- [x] 1.4 REFACTOR: extraer helpers de decodificación reutilizables dentro de `settings-types.ts` sin cambiar el comportamiento; verificar `npm test -- src/features/settings/settings-types.test.ts && npm run typecheck` en verde y revisar que no aparezca `any`.

## 2. Gateway HTTP y copy de errores

- [x] 2.1 RED: escribir `frontend/src/features/settings/settings-gateway.test.ts` con `fetch` simulado (patrón `auth-gateway.test.ts`: `createHttpClient`, `cookieCsrfTransport`, `problem()`, `json()`, `offline()`) que afirme método, URL `/api/v1/...`, cuerpo exacto y cabecera CSRF para los 11 contratos de `design.md` D2; verificar que falle por módulo inexistente.
- [x] 2.2 GREEN: implementar `settings-gateway.ts` como único lugar que conoce rutas, cuerpos y decodificadores, con resultados discriminados por sección; verificar que el test del paso 2.1 pase y que `npm run typecheck` esté limpio.
- [x] 2.3 TRIANGULATE: ampliar `settings-gateway.test.ts` con 401 → `onUnauthorized` una sola vez, `400 VALIDATION_FAILED` con `errors[{field,code}]`, `422 DURATION_NOT_MULTIPLE_OF_GRANULARITY`, `409 SCHEDULE_BLOCK_OVERLAP`, `404 SERVICE_NOT_FOUND`/`SCHEDULE_BLOCK_NOT_FOUND`, `403 CSRF_REQUIRED`, `DELETE` 204 y `network`/`timeout`/`invalid-response`; crear `settings-messages.test.ts` que verifique que el copy usa `code` y nunca `title`/`detail` crudos; verificar ambos tests en verde.
- [x] 2.4 REFACTOR: consolidar el mapeo `code` → mensaje en `settings-messages.ts` y quitar duplicación del gateway sin cambiar contratos; verificar `npm test -- src/features/settings/settings-gateway.test.ts src/features/settings/settings-messages.test.ts && npm run typecheck`.

## 3. Validación pura y solapes

- [x] 3.1 RED: escribir `frontend/src/features/settings/range-overlap.test.ts` y `validation.test.ts` con los casos felices: tramos adyacentes aceptados, duración 45 múltiplo de 15, parámetros 15/120/30/24 válidos y bloqueo con motivo y rango válidos; verificar que fallen por módulos inexistentes.
- [x] 3.2 GREEN: implementar `range-overlap.ts` (intervalos semiabiertos `[inicio, fin)`) y `validation.ts` con funciones puras (sin DOM ni HTTP) para consultorio, horarios, prestaciones y bloqueos; verificar que los tests del paso 3.1 pasen.
- [x] 3.3 TRIANGULATE: agregar casos de error de negocio y borde: tramo invertido, solape dentro del mismo día, duración 20 con granularidad 15, granularidad 25, horizonte 0, recordatorio 169, instante sin zona, motivo vacío y longitudes 120/500; verificar mensajes vinculados por campo y que ninguna función envíe nada.
- [x] 3.4 REFACTOR: simplificar las reglas compartidas sin tocar las firmas públicas; verificar `npm test -- src/features/settings/range-overlap.test.ts src/features/settings/validation.test.ts && npm run typecheck`.

## 4. Datos del consultorio, parámetros y link público

- [x] 4.1 RED: escribir `frontend/src/features/settings/practice-section.test.ts` con DOM jsdom que verifique la carga de valores de `GET /practice/settings`, el guardado válido de `PUT` y, para el link, `new URL('/reservar', origin)` copiado con `navigator.clipboard.writeText` y anunciado con `role="status"`; verificar que falle por módulo inexistente.
- [x] 4.2 GREEN: implementar `practice-section.ts` con `h()`/`field()`, `novalidate`, la validación del paso 3 y `timezone` de solo lectura reenviado sin cambios (D7); verificar que el test del paso 4.1 pase.
- [x] 4.3 TRIANGULATE: agregar casos de `400 VALIDATION_FAILED` vinculado por campo, granularidad 25 rechazada sin `fetch`, `403 CSRF_REQUIRED` con copy de recarga, portapapeles rechazado o ausente con `<input readonly>` visible y nombre con `<img ...>` como texto literal; verificar que no se use `innerHTML` y que el test pase.
- [x] 4.4 REFACTOR: extraer el render del aviso del link y el enlace de errores por campo sin cambiar la salida; verificar `npm test -- src/features/settings/practice-section.test.ts && npm run typecheck`.

## 5. Horarios de atención

- [x] 5.1 RED: escribir `frontend/src/features/settings/working-hours-section.test.ts` con la grilla de 7 días, dos tramos separados para el lunes y un guardado que envía la lista completa a `PUT /working-hours`; verificar que falle por módulo inexistente.
- [x] 5.2 GREEN: implementar `working-hours-section.ts` con filas editables `HH:mm` enviadas como `HH:mm:ss`, estado vacío «Agregar tramo» y error por sección con «Reintentar»; verificar que el test del paso 5.1 pase.
- [x] 5.3 TRIANGULATE: agregar tramo invertido y solapado (no envía, marca ambos campos), tramos adyacentes (sí envían), advertencia visible antes de guardar «los turnos confirmados no se cancelan automáticamente» y payload hostil en un horario como texto; verificar que el test pase y que las otras secciones sigan utilizables en el mock de vista.
- [x] 5.4 REFACTOR: mover la construcción de filas a una función pura local sin DOM y reutilizar `field()`; verificar `npm test -- src/features/settings/working-hours-section.test.ts && npm run typecheck`.

## 6. Prestaciones

- [x] 6.1 RED: escribir `frontend/src/features/settings/services-section.test.ts` con alta de prestación de 45 min visible (POST), lista que incluye inactivas y edición con `active` en el cuerpo; verificar que falle por módulo inexistente.
- [x] 6.2 GREEN: implementar `services-section.ts` con baja lógica vía `DELETE` (sin borrado físico), reactivación vía `PUT` que conserva `publicVisible`, estados vacío/error y confirmación nativa; verificar que el test del paso 6.1 pase.
- [x] 6.3 TRIANGULATE: agregar duración 20 con granularidad 15 rechazada localmente, `422 DURATION_NOT_MULTIPLE_OF_GRANULARITY` tras cambio de granularidad con el formulario conservado, `404 SERVICE_NOT_FOUND` con recarga, baja cancelada sin `fetch` y descripción con `<script>` como texto; verificar que el test pase.
- [x] 6.4 REFACTOR: unificar el formulario de alta/edición en un solo componente interno; verificar `npm test -- src/features/settings/services-section.test.ts && npm run typecheck`.

## 7. Bloqueos y turnos afectados

- [x] 7.1 RED: escribir `frontend/src/features/settings/blocks-section.test.ts` con creación de bloqueo desde `datetime-local` en hora del consultorio → ISO con zona, listado con `formatDateTime` y aviso con `affectedAppointments` no vacío; verificar que falle por módulo inexistente.
- [x] 7.2 GREEN: implementar `blocks-section.ts` con edición/eliminación, confirmación antes de `DELETE`, estados vacío/error y conversión basada en `shared/utils/dates.ts`; verificar que el test del paso 7.1 pase.
- [x] 7.3 TRIANGULATE: agregar fin anterior al inicio y motivo vacío sin `fetch`, `409 SCHEDULE_BLOCK_OVERLAP` con copy de conflicto, `400 INVALID_RANGE`, `404 SCHEDULE_BLOCK_NOT_FOUND`, cancelación de borrado sin `fetch`, `affectedAppointments: []` con el texto honesto de D5 y navegador en `Europe/Madrid` mostrando 09:00 de Buenos Aires para 12:00 UTC; verificar que el test pase.
- [x] 7.4 REFACTOR: aislar la conversión local↔UTC en una función pura reutilizable dentro del módulo; verificar `npm test -- src/features/settings/blocks-section.test.ts && npm run typecheck`.

## 8. Vista `/configuracion` y cableado de la ruta

- [x] 8.1 RED: escribir `frontend/src/features/settings/settings-view.test.ts` y casos nuevos en `frontend/src/app/routes.test.ts` que esperen cuatro secciones independientes con `aria-busy`, reintento por sección y `/configuracion` con sesión autenticada; verificar que fallen frente al placeholder actual.
- [x] 8.2 GREEN: implementar `settings-view.ts` con orquestación por sección, reemplazar el `placeholderView('Configuración', …)` en `routes.ts` e inyectar el gateway desde `main.ts`; verificar que los tests del paso 8.1 pasen.
- [x] 8.3 TRIANGULATE: agregar a `routes.test.ts` los casos sin sesión → `/login?next=%2Fconfiguracion`, con `mustChangePassword` → `/cambiar-contrasena?next=%2Fconfiguracion`, fallo de carga → layout privado con errores por sección, y a `settings-view.test.ts` un payload hostil en nombre de prestación/motivo sin elementos `img`/`script`; verificar que pase el test y que la navegación siga marcando «Configuración».
- [x] 8.4 REFACTOR: quitar imports muertos y confirmar que no se agregaron dependencias de runtime ni estilos hardcodeados; verificar `npm run typecheck && npm test -- src/features/settings/settings-view.test.ts src/app/routes.test.ts`.

## 9. Integración y verificación final antes de archive

- [x] 9.1 Ejecutar `cd frontend && npm test && npm run typecheck && npm run build`; verificar que todos los tests (incluido `architecture.test.ts`) y el build pasen sin `any`, `innerHTML` ni dependencias nuevas.
- [ ] 9.2 Con el backend local corriendo, abrir `/configuracion` con sesión de profesional y recorrer los flujos felices y de error de los specs contra los mockups aprobados; verificar en 390×844 y 1440×900 que no haya scroll horizontal, que los estados vacíos/error/éxito coincidan y guardar capturas en `mockups/verify/`.
- [ ] 9.3 Confirmar que no se loguean nombres, emails, motivos ni IDs de turno, que los errores muestran copy propio y que las mutaciones fallidas no dejan la UI en estado engañoso; verificar con una pasada de `console` del navegador y con los tests de gateway.
- [ ] 9.4 Registrar en `design.md` §UI Mockups el resultado `Visual verify: passed | differences` con fecha y capturas; verificar que, si hay diferencias, se corrijan antes de dar por completado el change.

## Workflow follow-up

- Resolver los `(TBD)` de `design.md` §Open Questions antes de cambiar el comportamiento de v1: zona horaria editable, URL base alternativa del link público y detalle de turnos afectados al editar horarios. Cualquier respuesta distinta a D5/D6/D7 requiere revisar proposal/specs/design con `/opsx:update`.
- Ejecutar `/opsx:archive c-19-settings-ui` recién después de la aprobación del usuario y de la verificación visual; verificar el resultado archivado.
