# Verify report: C-05 `frontend-core-shell`

Fecha: 2026-11-21 | Rama: dev-mayra (commit 07b1f73, árbol limpio) | Revisor: sub-agente verificador independiente (solo lectura sobre código; solo se creó este informe)

## Veredicto: PASS WITH WARNINGS

No hay issues CRITICAL. `openspec validate c-05-frontend-core-shell --strict` es válido; `npm test`, `npm run typecheck` y `npm run build` terminan en verde. tasks.md está 44/44. Los warnings son artefactos desactualizados respecto de las desviaciones (design D8, D9, D11, D12, D7 y spec `safe-rendering`), la cobertura no medible (no hay `@vitest/coverage-*` instalado) y dos escenarios que solo se verifican de forma visual/indirecta.

Visual: `design.md` registra `Visual verify: passed (2026-11-21)`. Las capturas de `mockups/verify/` coinciden con las aprobadas en `mockups/previews/` salvo el contenido ilustrativo del mockup (ver sección 6).

## Resultados de ejecución (corridos por el verificador)

| Suite | Comando | Resultado |
|-------|---------|-----------|
| Tests | `npm test` (desde `frontend/`, 7,4 s) | 19 archivos, **219 tests, 0 fallos** |
| Typecheck | `npm run typecheck` | sin errores |
| Build | `npm run build` (`tsc --noEmit && vite build`) | OK, 24 módulos; `index.js` 16,47 kB (gzip 6,51 kB), `css` 11,71 kB, logo con hash |
| OpenSpec | `openspec validate c-05-frontend-core-shell --strict` | `Change 'c-05-frontend-core-shell' is valid` |
| Estado | `openspec status --change c-05-frontend-core-shell --json` | 7 specs (`api-client`, `app-layout`, `local-time-display`, `safe-rendering`, `session-state`, `spa-routing`, `spa-shell`), proposal, design y tasks existentes |
| Cobertura | no ejecutada | `node_modules/@vitest/` no incluye `coverage-v8`/`coverage-istanbul`, y no se instaló nada (ver W3) |
| Dependencias | `diff <(git show bed2927:frontend/package.json) frontend/package.json` | **idéntico**: cero dependencias nuevas (regla 12) |

## 1. Completitud

- tasks.md: 44 `[x]`, 0 `[ ]`. Cada grupo tiene código de producción y test: `core/dom` (escape, h), `core/state/store`, `core/http` (client, transport, cookies, decode, errors), `core/auth` (session, session-gateway), `router` (match, safe-next, guards, router), `app/routes`, `shared/utils/dates`, `layouts` (brand, icons, private, public), `styles` (base.css, layout.css), `main.ts` y `architecture.test.ts`.
- Los tests nombrados en el D12 existen todos.
- Sin tareas abiertas ni pendientes manuales de código.

## 2. Matriz de cobertura de escenarios

Leyenda: OK = test que realmente asserta el comportamiento. PARCIAL = indirecto o solo visual.

### safe-rendering
| Escenario | Test | Estado |
|-----------|------|--------|
| Nombre con etiqueta | `h.test.ts` "muestra literal un texto con marcado y no crea elementos" | OK |
| Texto común | `h.test.ts` "muestra igual un texto con ampersand" | OK |
| Escape completo / Cadena vacía | `escape.test.ts` (dos primeros tests) | OK |
| Atributo onclick | `h.test.ts` "rechaza atributos on* nombrando el atributo pero no el valor" | OK |
| Enlace javascript | `h.test.ts` "rechaza href con esquema javascript aunque tenga espacios, mayusculas o control" + src/action/formaction | OK |
| Enlace relativo | `h.test.ts` "acepta href relativo, https, mailto y tel" | OK |
| Código limpio / Uso detectado | `architecture.test.ts` (escaneo real de `src/**/*.ts` + prueba de detección con cadenas) | OK |

### api-client
| Escenario | Test | Estado |
|-----------|------|--------|
| GET relativo | `client.test.ts` "GET /auth/me va a /api/v1/auth/me con Accept y credenciales del mismo origen" | OK |
| Ruta con otro origen | `client.test.ts` it.each (`https://`, `//`, relativa sin `/`, `\`, `://`) | OK |
| POST con cookie CSRF / GET sin cabecera / POST sin cookie | `transport.test.ts` (it.each métodos, "no agrega la cabecera si no hay cookie") + `client.test.ts` POST | OK |
| Forma válida / inválida / sin contenido | `client.test.ts` (200 inválido, 200 no JSON, 204) | OK |
| Error de dominio / validación por campo / sin problem+json | `client.test.ts` (409, 400 `VALIDATION_FAILED`, 500 `errorId`, 502 HTML, sin `code`) | OK |
| Sin conexión / Timeout | `client.test.ts` ("fetch que rechaza", timeouts 15 s y 5 s con fake timers) | OK |
| 401 en llamada privada | `client.test.ts` (401 llama `onUnauthorized` una vez; con `sessionProbe` no) | OK |
| Error con datos personales | `client.test.ts` "un 500 con datos personales no escribe nada en la consola" | OK |

### session-state
| Escenario | Test | Estado |
|-----------|------|--------|
| Estado inicial | `session.test.ts` (arranca `unknown`, sin storage) | OK |
| Sesión existente / Sin sesión / Backend caído | `session.test.ts` (200, 401, red -> `offline`) | OK |
| Consultas concurrentes | `session.test.ts` "dos ensureLoaded concurrentes hacen una sola llamada" | OK |
| Suscriptor notificado / cancelada | `store.test.ts` + `session.test.ts` | OK |
| Logout exitoso / con error de red | `session.test.ts` + `main.test.ts` (POST con `X-XSRF-TOKEN`, termina en `/login`) | OK |
| 401 en pacientes | `main.test.ts` "un 401 posterior en una llamada privada lleva a /login con next y el aviso" + `session.test.ts` `expire` | OK |
| Transporte alternativo | `transport.test.ts` (Bearer, sin `X-XSRF-TOKEN`) | OK |

### spa-routing
| Escenario | Test | Estado |
|-----------|------|--------|
| Ruta pública con parámetro / privada / barra final | `match.test.ts`, `routes.test.ts`, `router.test.ts` | OK |
| URL desconocida | `router.test.ts` "una URL desconocida muestra no encontrada sin cambiar la URL ni mostrarla" | OK |
| Agenda sin sesión / sesión desconocida | `guards.test.ts` (redirect con `replace` a `/login?next=%2Fagenda`; `unknown` espera `ensureLoaded`) | OK |
| Login con next válido / sin next | `guards.test.ts` | OK |
| Redirección abierta / esquema javascript | `safe-next.test.ts` it.each | OK |
| Raíz anónima / autenticada | `guards.test.ts` (`/` -> `/reservar` o `/agenda`) | OK |
| Clic en enlace interno / con Ctrl / Atrás | `router.test.ts` (clic primario, it.each modificadores, popstate) | OK |
| Título de pacientes | `router.test.ts` "el titulo es <vista> · <marca> y el foco queda en el h1" | OK |
| Título de gestión de turno | `router.test.ts` "el titulo de /turno/:token no contiene el token y no escribe en consola" + `routes.test.ts` | OK |

### local-time-display
| Escenario | Test | Estado |
|-----------|------|--------|
| Instante válido / Sin zona | `dates.test.ts` | OK |
| Mañana en BA / Cambio de día | `dates.test.ts` (UTC-3, 02:00Z -> 23:00 del día anterior, Europe/Madrid) | OK |
| Hoy con reloj fijo | `dates.test.ts` `today` | OK |
| Día en BA / Fecha inválida | `dates.test.ts` `dayBoundsUtc` (incl. fin de mes y año) | OK |

### app-layout
| Escenario | Test | Estado |
|-----------|------|--------|
| Estructura privada | `private-layout.test.ts` (header, nav "Principal" con 3 enlaces, `main#main`) | OK |
| Configuración actual | `private-layout.test.ts` it.each + "sin seccion actual" | OK |
| Mobile 390x844 / Desktop 1440x900 | Solo verificación visual (`mockups/verify/`); jsdom no calcula layout | PARCIAL (ver W4) |
| Estructura pública | `public-layout.test.ts` | OK |
| Nombre de marca | `brand.test.ts` | OK |
| CSS con color literal / CSS del change | `styles.test.ts` (it.each de violaciones + escaneo real de `src/**/*.css`) | OK |
| Primer tab | `private-layout.test.ts` y `public-layout.test.ts` "el primer enfocable es Saltar al contenido" | OK |

### spa-shell
| Escenario | Test | Estado |
|-----------|------|--------|
| Arranque en ruta pública | `main.test.ts` "en /reservar con me 401 renderiza el layout publico..." | OK |
| Arranque mientras se consulta la sesión | `main.test.ts` "en /agenda con me pendiente muestra role=status..." | OK |
| Referrer declarado | `main.test.ts` "declara la politica de referrer no-referrer" | OK |

Resumen: 0 escenarios sin cobertura; 1 PARCIAL (responsive, solo visual, aceptable por diseño D12). No se detectaron aserciones triviales: los tests de "sin console.*" y "sin storage" comprueban espías/almacenamiento realmente. Menor: `main.test.ts:64` fija `doc.title` a `{{brand}}` (ver S3).

## 3. Reglas duras de CLAUDE.md

| Regla | Resultado |
|-------|-----------|
| 9 (strict, sin `any`) | `grep` en `src/**/*.ts` (sin tests): 0 usos de `any`; solo una aparición en un comentario de `decode.ts`. `tsc --noEmit` verde. OK |
| 10 (sin `innerHTML`) | 0 usos en código de producción; `architecture.test.ts` lo vigila (las apariciones en `main.test.ts` son lectura de `body.innerHTML` en un test, y no se escanean tests). OK |
| 11 (solo tokens) | `grep` de literales en `src/styles/*.css`: solo `1px` en `width`/`height` de la utilidad visually-hidden y `border: 1px solid`, más `--layout-sidebar-width: 15rem` y `--layout-reading-width: 40rem` y el `64rem` de los media queries, todos documentados en D8. OK |
| 12 (sin deps nuevas) | `package.json` idéntico al de `bed2927`. `vite.config.ts` declara `process` a mano para no sumar `@types/node`. OK |
| 6 (sin PII en logs) | 0 `console.*` en `src`; test específico en `client.test.ts` y `router.test.ts`. OK |
| 8 / token en títulos y `next` | `/turno/:token` con título fijo, `safeNext` solo acepta rutas privadas declaradas y descarta query. OK |
| 14 (commits) | No verificado salvo que el commit `07b1f73` usa `feat(C-05): ...` (conventional commits con ID de change). OK |

## 4. Coherencia con design.md y desviaciones reportadas

| Desviación | Evaluación |
|-----------|------------|
| `h()` admite `data:image/*` solo como `<img src>` (`h.ts` `isInlineImage`) | **Aceptable**: Vite inlinea el logo (1,3 kB) como data URI en dev y el diseño exigía rechazar esquemas no `http/https/mailto/tel`. Está restringida a `src` de `img` y a `png/jpeg/gif/webp/svg+xml`, con test que cubre `href`, `text/html` e `iframe`. **Actualizar** D7 y el requisito "Atributos peligrosos rechazados" del spec `safe-rendering` (W1). |
| `vite.config.ts` `test.css: { include: [/.+/] }` y `exclude` propio | **Aceptable**: necesario para que `styles.test.ts` lea el texto real de los CSS (Vitest los vacía por defecto) y para que `vite.config.test.ts` corra. Está comentado en el código. **Registrar** en D12 (W1). |
| Reutilización parcial del layout (D8) | **Aceptable con ajuste de texto**: `main.ts` (`render`) reutiliza `main` solo si el tipo de layout, la sección actual y el nombre del usuario no cambiaron; si cambia la sección reconstruye el shell (necesario para actualizar `aria-current`). D8 dice "solo reemplaza el contenido de main"; el router no conoce layouts, lo hace `main.ts`. **Actualizar** D8 (W1). No hay test explícito de que el nodo del shell se preserve al navegar dentro de la misma sección (S2). |
| `bootstrap()` sin opción `clock` (D11) | **Aceptable**: C-05 no usa `today()` en ninguna vista, así que el parámetro no tiene consumidor (YAGNI). **Actualizar** D11 quitando `clock` o marcarlo como "se agrega cuando una vista lo necesite" (W1). |
| `{{brand}}` en `<title>` reemplazado por `brand.js` | **Aceptable y coherente con `design-system/README.md`** (marcador `{{brand}}`; `brand.js` lo reemplaza al cargar). El router después pone `"<vista> · <marca>"`. Ver S3 sobre el test. |
| Favicon desde `../design-system/assets/logo.svg` | **Aceptable**: Vite lo resuelve en dev (permitido por `server.fs.allow`) y en build (`dist/assets/logo-*.svg`). Cubierto por `vite.config.test.ts` (permite servir `design-system`). Registrar en D9. |

El resto de decisiones (D1 estructura, D2 router sin `RegExp` desde texto de usuario, D3 `next`, D4 transporte reemplazable, D5 `HttpResult` sin excepciones, D6 store, D10 `Intl`) coincide con el código inspeccionado.

## Issues

### CRITICAL
Ninguno.

### WARNING
- **W1** Artefactos desactualizados respecto del código: `design.md` D7 (excepción `data:image/*` en `src` de `<img>`), D8 (reutilización de layout a nivel `main.ts` y recreación al cambiar de sección), D9 (favicon), D11 (sin `clock`), D12 (`test.css.include` y `exclude` en `vite.config.ts`), y el requisito "Atributos peligrosos rechazados" de `specs/safe-rendering/spec.md` (la excepción de `data:image` no figura). Corregir con `/opsx:update` o una sección "Deviations" antes de archivar, y correr `openspec validate --strict` de nuevo.
- **W2** La corrida de `vite build` genera `dist/` y `index.html` usa `../design-system/assets/logo.svg` como favicon (ruta fuera de la raíz de Vite). Funciona hoy (build verificado), pero depende de que `design-system/` siga en el repo como hermano de `frontend/`; si C-27 despliega el build desde un contexto aislado (p. ej. Docker con solo `frontend/`), la ruta se rompe. Anotarlo como riesgo para C-27 en D9/Risks.
- **W3** Cobertura de líneas no medible: `@vitest/coverage-v8` no está instalado y no hay script `coverage`; no se pudo verificar ningún umbral. Los 219 tests cubren todos los escenarios, pero no hay número. Decidir con el usuario si se aprueba esa devDependency (la regla 12 solo restringe dependencias de runtime) o dejar constancia de que la cobertura se mide por escenarios.
- **W4** Los escenarios responsive (barra inferior < 64rem, lateral >= 64rem, sin scroll horizontal, objetivos táctiles de 50 px) dependen solo de capturas manuales (`mockups/verify/`) porque jsdom no calcula layout; cualquier cambio de CSS futuro puede romperlos sin que falle un test. Aceptado en D12, pero conviene un test e2e (skill `webapp-testing`) en C-27 o antes.

### SUGGESTION
- **S1** `layout.css` usa `1px` literal en `width`/`height` (utilidad visually-hidden) y `border: 1px solid` fuera de las constantes documentadas; `styles.test.ts` los acepta. Documentar en D8 que `1px` de bordes/sr-only se permite (o usar un token si el design system lo define).
- **S2** Agregar un test que verifique que el nodo `main`/shell se preserva al navegar entre rutas de la misma sección (hoy `main.test.ts` verifica la sección actual pero no la identidad del nodo).
- **S3** `main.test.ts:64` asserta `doc.title` contiene `{{brand}}`, que prueba que el HTML no tiene el nombre hardcodeado pero no que `brand.js` lo reemplace en runtime; el reemplazo real solo lo cubre la verificación visual (títulos `<vista> · La Muela`).
- **S4** `h()` valida `href/src/action/formaction/xlink:href` pero no `srcset`, `poster` ni `data` de `<object>`; hoy no se usan. Considerar añadirlos al set `URL_ATTRIBUTES` cuando existan vistas con imágenes de la API.
- **S5** `vite.config.ts` declara `process` a mano para evitar `@types/node`; si C-06+ agrega más usos de Node, evaluar aprobar `@types/node` como devDependency.

## 5. Visual verify

- `design.md` línea `Visual verify: passed (2026-11-21)` con Playwright sobre `npm run dev` y capturas `mockups/verify/{private,public}-{390,1440}.png`.
- Comparé `private-390`, `private-1440` y `public-390` (verify vs previews):
  - Privado 390: header con logo + marca + badge PROVISIONAL, avatar "DR" y botón de salida, h1 "Agenda", barra inferior con Agenda (activo), Pacientes y Configuración. Coincide con el mockup aprobado.
  - Privado 1440: header completo con "Dra. Romero" y "Salir", lateral de 240 px con los tres destinos, contenido con ancho máximo. Coincide.
  - Público 390: header con "Acceso profesionales", h1 "Reservá tu turno", footer con Política de privacidad, Términos y datos del consultorio `(TBD)`. Coincide.
- Diferencias menores (ya registradas por el implementador y aceptables, porque son contenido ilustrativo del mockup que pertenece a vistas de otros changes): sin bajada de texto, sin stepper de 4 pasos ni botón "Continuar" en la reserva, sin aviso de ejemplo, sin fecha del consultorio ni nota "(TBD)" de datos de ejemplo, y el nombre del usuario sin el badge TBD.
- No re-ejecuté el navegador (no solicitado). `public-1440.png` no se inspeccionó visualmente.

## 6. Pendientes manuales (no bloqueantes)

- Ninguno del implementador. Pendiente de proceso: actualizar `CHANGES.md` (marcar C-05 verificado) y que el orquestador archive con `/opsx:archive c-05-frontend-core-shell` tras resolver W1.

## Lo que no pude verificar

- Cobertura de líneas (sin proveedor instalado).
- Comportamiento con el backend real de C-04 (el contrato `GET /api/v1/auth/me`, `POST /api/v1/auth/logout` y CSRF por cookie es un supuesto documentado en D4; C-05 usa `fetch` falso).
- `public-1440.png` y las capturas de `verify/` en tiempo de ejecución (solo se compararon imágenes ya existentes).

## Siguiente acción recomendada

1. Resolver W1 (actualizar `design.md` y el spec `safe-rendering`) y decidir W3 (cobertura) y W2 (riesgo para C-27).
2. Correr `openspec validate c-05-frontend-core-shell --strict`.
3. `/opsx:archive c-05-frontend-core-shell` (sincroniza specs y actualiza `CHANGES.md`). Commit solo cuando el usuario lo pida.

## Resolución de warnings (2026-11-21)

- **W1 resuelto**: `design.md` (D7, D8, D9, D11, D12) y el requisito "Atributos peligrosos rechazados" del spec `safe-rendering` reflejan lo implementado (excepción `data:image/*` en `img src` con dos escenarios nuevos cubiertos por `h.test.ts`, reutilización parcial del layout, `bootstrap` sin `clock`, `{{brand}}` y favicon, `test.css.include`/`exclude`). S1 también quedó documentado en D8 (`1px`).
- **W2, W3 y W4 registrados** en `design.md` (Riesgos y D12): imports fuera de la raíz de Vite (C-27 debe construir con el contexto completo), cobertura juzgada por escenarios (`@vitest/coverage-v8` requiere aprobación del equipo) y responsive verificado solo con capturas (e2e como seguimiento).
