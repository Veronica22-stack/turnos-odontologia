# Design

## Context

Ver `proposal.md` para la motivación. Estado actual observado en el repo: los cambios archivados `C-16` (`clinical-record`, `clinical-anamnesis`, `audit-log`), `C-17` (`clinical-odontogram`) y `C-18` (`clinical-evolutions`) fijan el contrato real de la API; `C-06`, `C-21` y `C-05` aportan sesión, UI de pacientes y shell. La SPA tiene rutas `/agenda`, `/pacientes`, `/configuracion` y navegación privada con esas tres secciones; no existe `/historia-clinica` ni módulo clínico en `frontend/src/features/`. El layout privado solo acepta `agenda | pacientes | configuracion` como sección actual. El design system está aprobado en Revision 1 con política `system-and-mockups` y ya incluye ejemplos de odontograma y evoluciones corregidas.

Principios de dominio declarados como guía para el apply (skills no cargadas en esta fase de propose y no registradas en `.atl/skill-registry.md`): `vitest` (ciclos TDD), `web-design-guidelines` y `emil-design-eng` (foco, estados y microinteracciones), `design-taste-frontend-v1` (tokens y densidad), `webapp-testing` (verificación visual con navegador). Se cargarán las que estén disponibles antes de escribir código; ante conflicto, prevalece la foundation aprobada.

Restricciones: datos de salud sensibles (RN-HC-01..08, RN-AD-01..03, Leyes 25.326 y 26.529); gobernanza **ALTO**; PA-01 (Ley 27.706) sigue sin relevar; TypeScript estricto sin `any`, sin frameworks nuevos, sin `innerHTML`, solo tokens de `design-system/tokens.css`, sin commit/push.

## Goals / Non-Goals

**Goals:**
- Exponer en la SPA el contrato inmutable ya existente sin inventar reglas clínicas nuevas ni cambiar el backend.
- Hacer auditable desde el cliente que cada lectura/escritura visible corresponde a una sola petición real.
- Garantizar que el texto clínico nunca se interprete como HTML ni filtre a URL, título, consola, errores o almacenamiento persistente.
- Producir mockups aprobados antes de tocar código de implementación.

**Non-Goals:**
- Modificar esquema, migraciones, endpoints, DTOs ni reglas de autoría/auditoría del servidor.
- Agregar `PUT`/`PATCH`/`DELETE` o cualquier-control de borrado físico; tampoco simular auditoría en el cliente.
- Definir decisiones clínicas definitivas sobre catálogos, dentición o campos mínimos (PA-17/PA-18 quedan como supuestos a validar).
- Implementar el visor de `audit_log` (C-23) ni la gestión de turnos (C-25).

## Decisions

1. **Ruta sin parámetros y paciente en memoria.** `/historia-clinica` no lleva `?patientId=`. La selección vive en el estado de la vista; el gateway recibe el identificador solo en la ruta de la API (`/patients/{id}/clinical-record/...`). *Alternativa descartada:* `/historia-clinica/:patientId`, más simple de enlazar pero expone un identificador vinculado a datos de salud en URL, historial y posiblemente analytics. *Consecuencia:* recargar vuelve a la selección; es el coste aceptado frente a RN-HC-08.

2. **Contrato D2: el gateway es el único que conoce rutas y cuerpos.** Se crea `features/clinical-record/clinical-record-gateway.ts`, con decodificadores estrictos (`unknown` + type guards) para las respuestas de anamnesis, odontograma, evoluciones y correcciones, siguiendo el patrón de `patients-gateway.ts`. Se envían únicamente los campos del contrato: nunca `authorId`, `createdAt`, `id`, `clinicalRecordId`, `toothCode`/`surface` en correcciones de odontograma ni `appointmentId` en correcciones de evolución. *Alternativa descartada:* reutilizar un `HttpClient` genérico desde la vista, que dispersaría rutas y facilitaría caminos fuera del contrato.

3. **Corrección = alta, nunca edición.** El único flujo de mutación sobre una entrada existente es un diálogo de corrección con `reason` obligatorio (`trim`, ≤500) y contenido del mismo tipo. El botón se ofrece solo sobre la entrada vigente; se bloquea el doble envío mientras la petición está en curso. El historial se pinta tal como lo devuelve el servidor, con la entrada original marcada como corregida. *Alternativa descartada:* formulario de edición reutilizando el de alta con `PUT`, que violaría RN-HC-01 y el contrato C-16.

4. **Odontograma: notación FDI estándar y catálogo base (SUJETO A VALIDAR, PA-17).** Supuesto explícito: se renderizan cuadrantes 1–4 (permanente, piezas 1–8) y 5–8 (temporal, piezas 1–5); estados `HEALTHY`, `CARIES`, `FILLED`, `MISSING`, `CROWN`, `ROOT_CANAL`; caras `OCCLUSAL`, `MESIAL`, `DISTAL`, `VESTIBULAR`, `LINGUAL` solo para `CARIES` y `FILLED`; lotes de 1 a 64 entradas. Marcado **(TBD)** en los mockups y en este documento: el profesional debe confirmar dentición a mostrar por paciente, catálogo y si se requiere registro por superficie antes de considerarlo decision clínica definitiva. *Alternativa descartada:* catálogo libre o esquema local propio, que no coincide con el validador ya implementado en C-17 y provocaría `400`.

5. **Anamnesis mínima propuesta (SUJETO A VALIDAR, PA-18).** Supuesto: `allergies`, `medications`, `medicalConditions`, `habits`, `notes`, opcionales con al menos uno no vacío y ≤2000 caracteres, siguiendo `clinical-anamnesis`. **(TBD)** validar con el profesional si se necesitan antecedentes heredofamiliares, embarazo, cirugías o consentimiento informado; cualquier cambio de campos debe revisar este change y el contrato C-16 antes de incorporarse. *Alternativa descartada:* formulario libre sin campos, que no podría mapearse al DTO existente.

6. **Borrador solo en memoria.** Los valores en edición viven en el estado de la vista; se descartan al salir, al recargar y al recibir `401`/sesión vencida, redirigiendo a `/login?next=%2Fhistoria-clinica`. *Alternativa descartada:* `localStorage` para recuperar borradores, descartada por RN-HC-08 y por el riesgo de texto clínico en claro; se acepta perder el borrador. Los eventos de sesión se reutilizan del módulo existente (`session-state`).

7. **Mensajes propios, sin eco del contenido.** Los códigos `VALIDATION_FAILED`, `PATIENT_NOT_FOUND`, `PATIENT_INACTIVE`, `CLINICAL_ENTRY_NOT_FOUND`, `CLINICAL_ENTRY_ALREADY_CORRECTED`, `CLINICAL_ENTRY_NOT_CURRENT`, `CLINICAL_RECORD_CONCURRENT_WRITE`, `EVOLUTION_APPOINTMENT_*` y `ODONTOGRAM_SURFACE_NOT_ALLOWED` se mapean a texto fijo en `clinical-record-messages.ts`. Nunca se muestran `title`, `detail`, valores rechazados ni se escriben en `console`. *Alternativa descartada:* mostrar el cuerpo de `application/problem+json`, que en C-16/C-17/C-18 puede incluir nombres de campo y detalles sensibles.

8. **Fechas UTC → `America/Argentina/Buenos_Aires`.** Se reutiliza la utilidad de fechas compartida; el servidor sigue siendo la única fuente de autoría e instante. *Alternativa descartada:* `new Date().toISOString()` en el cliente para «armar» la entrada, que falsearía RN-HC-05.

9. **Accesibilidad del odontograma desde la foundation aprobada.** `role="group"` por cuadrante con etiqueta, un botón por pieza con `aria-label` «Pieza FDI, estado», `aria-pressed` para selección, marca textual además del color (`C`, `O`, `Co`, `E`, `✕`), foco visible con `--focus-ring`, movimiento anulado por `prefers-reduced-motion`. *Alternativa descartada:* un `<canvas>` o una tabla solo visual: no ofrecería foco ni etiquetas por pieza. Objetivo WCAG **(TBD, PA-21)**: se apunta AA práctico sin fijar nivel normativo.

10. **Una petición por operación observable.** Cada apertura de sección genera un `GET`; cada confirmación, un `POST`. Sin reintentos automáticos de escritura y con botón deshabilitado durante el envío, para que `HC_READ`/`HC_CREATE`/`HC_CORRECT` del servidor correspondan a una acción real del odontólogo. *Alternativa descartada:* refetch optimista tras cada mutación, que duplicaría eventos de auditoría.

11. **TDD estricto y sin dependencias nuevas.** Vitest + jsdom con ciclo RED → GREEN → TRIANGULATE (≥2 casos) → REFACTOR por cada comportamiento; se conserva la guardia `architecture.test.ts` contra APIs que interpretan HTML. *Alternativa descartada:* agregar una librería de componentes o testing-library: la regla dura prohíbe dependencias de runtime sin aprobación y el repo ya tiene helpers `h`, `form` y `confirm`.

12. **Tokens y assets compartidos, sin copias.** Los mockups y la vista importan `design-system/tokens.css`, `brand.js` y `assets/logo.svg`; los estados clínicos usan las variables `--tooth-*`, `--status-*` y `--focus-ring` de Revision 1. *Alternativa descartada:* duplicar colores en CSS del feature, que rompería la regla dura 11 y la fuente única de la marca.

## Risks / Trade-offs

- **PA-01 sin relevar (Ley 27.706)** → no se afirma cumplimiento legal; los supuestos quedan marcados `(TBD)` y el despliegue a producción depende de validación legal. El propose y el apply son work de código, no dictamen.
- **PA-17/PA-18 incorrectos** → catálogo FDI y campos de anamnesis concentrados en `clinical-record-types.ts` y en los mensajes; validar con el profesional durante la revisión de mockups y ajustar antes del apply si corresponde.
- **Pérdida del borrador por sesión vencida** → riesgo aceptado frente a persistir datos de salud; se comunica en pantalla y se descarta sin reintento.
- **XSS por notas o motivos** → render solo como texto + type guards + guardia automática de APIs prohibidas; triangulación con `<script>`, comillas y entidades.
- **Derivación de «vigente» y orden determinista** → son responsabilidad del backend ya archivado; la UI no recalcula ni reordena, solo presenta lo devuelto. Si el API devuelve datos inconsistentes, se muestra error genérico, nunca un estado inventado.
- **Verificación de auditoría** → los tests de frontend cubren el número de peticiones, no `audit_log`; la comprobación del evento requiere API/E2E y queda como verificación manual/integración.
- **Responsive y foco en 32/52 piezas** → densidad alta en móvil; se agrupa por cuadrante y se admite desplazamiento dentro del odontograma, nunca de la página.
- **Sin mockup aprobado** → el apply está bloqueado; cualquier revisión de mockup reinicia `Approval: pending`.

## Migration Plan

Sin migración de base de datos ni cambios de API. Despliegue: (1) crear el feature y sus pruebas, (2) agregar la ruta y la entrada de navegación, (3) ejecutar `npm test` y `npm run build` en `frontend/`, (4) verificar visualmente contra los mockups aprobados. Rollback: revertir el commit del change y retirar la ruta y el ítem de navegación; no hay datos que migrar ni entradas clínicas que tocar. No se realizan commit ni push como parte de este change sin pedido explícito.

## Open Questions

- **PA-17**: ¿catálogo definitivo de estados, dentición temporal/permanente a mostrar y nivel de detalle por superficie? Registrado como supuesto (notación FDI estándar + catálogo base de C-17) y marcado `(TBD)`; requiere confirmación del profesional antes de cerrar el diseño clínico.
- **PA-18**: ¿campos mínimos de anamnesis? Registrado como supuesto (los cinco campos de C-16) y marcado `(TBD)`; requiere confirmación del profesional.
- **PA-01**: requisitos exactos de la Ley 27.706 sobre firma, conservación e interoperabilidad; no se inventan y condicionan la puesta en producción, no la implementación de la UI.
- **PA-21**: nivel WCAG objetivo; se implementa AA práctico sobre la foundation Revision 1 y queda sujeto a confirmación.

## UI Mockups

- Design system revision: 1 (aprobado el 2026-11-16; política `system-and-mockups`).
- Decision: yes (2026-11-23). Change **[UI]**: los mockups son obligatorios antes del apply.
- Mockups: `openspec/changes/c-22-clinical-record-ui/mockups/` (cinco HTML aprobados en Revision 1).
- Approval: approved (2026-11-24, by user).
- Preview: rendered (2026-11-23; `mockups/previews/anamnesis-390.png`, `mockups/previews/anamnesis-1440.png`, `mockups/previews/odontograma-390.png`, `mockups/previews/odontograma-1440.png`, `mockups/previews/evoluciones-390.png`, `mockups/previews/evoluciones-1440.png`, `mockups/previews/correcciones-390.png`, `mockups/previews/correcciones-1440.png`, `mockups/previews/accesibilidad-odontograma-390.png`, `mockups/previews/accesibilidad-odontograma-1440.png`).
- Visual verify: passed (2026-11-24, re-verificación final del Ítem 10.3 tras los fixes de R-1 y ME-2; informe en `mockups/verify/verify-report.md`, capturas `mockups/verify/final-*.png`). **B-1, MA-1..MA-4, R-1 y ME-2 confirmados resueltos** en 390×844 y 1440×900 contra backend real: dos correcciones consecutivas **sin recargar** apuntan a la nueva id devuelta por el `POST` anterior en anamnesis, evoluciones y odontograma (1 `POST` `201` por confirmación, **0 `GET`** por el estado local); `role="status"` del odontograma replica el `aria-label` con FDI + estado + cara + selección, con y sin cara. Reintentos con un solo `GET` y sin datos obsoletos; 52 piezas, 8 grupos, 3 formularios, marcas de corregida, fechas BA, HTML literal, foco/reduced-motion, sin controles destructivos ni scroll horizontal, 0 errores de consola en la navegación final. Comparación por DOM, ARIA, geometría, texto, red y estado; **no se inspeccionaron píxeles** (modelo sin entrada de imagen) y **no se afirma coincidencia pixel-perfect**. ME-1 se conserva como nota menor no bloqueante (autor UUID/«Profesional» porque `/auth/me` no expone nombre y el mockup lo marca `(TBD)`; sin PII). No se cambiaron backend, migraciones, `design-system/`, HTML/previews de mockups ni C-25; esta pasada fue `N/A (verification only)`. No se archiva, commitea ni pushea.

Viewports obligatorios: **390×844** y **1440×900**, con el mismo Revision 1 y assets locales (`tokens.css`, `brand.js`, `logo.svg`).

Estados a presentar para aprobación (criterios de aceptación de esta sección):

1. **Anamnesis**: formulario con los cinco campos propuestos, vigente e historial, estado vacío, error de validación y borrador en memoria. Marcar campos `(TBD)` por PA-18.
2. **Odontograma FDI interactivo**: cuadrantes permanentes y temporales, selección por pieza y cara, leyenda con marca textual, estado actual y lote de registro. Marcar catálogo `(TBD)` por PA-17.
3. **Evoluciones**: línea de tiempo con autor/fecha local, turno asociado, alta y estado vacío/de error.
4. **Historial de correcciones**: entrada vigente con motivo y referencia, original marcada como corregida, diálogo con `reason` obligatorio y **sin** controles de edición ni borrado en ningún estado.
5. **Accesibilidad del odontograma**: foco visible, `aria-label` por pieza, `aria-pressed`, marca textual además del color, navegación por teclado y `prefers-reduced-motion`.

Regla de bloqueo: no se inicia el apply ni se escribe código de implementación hasta que el usuario apruebe estos mockups y se registre `Approval: approved (<fecha>, by user)` en este archivo. Una revisión de mockups vuelve el estado a `pending`.
