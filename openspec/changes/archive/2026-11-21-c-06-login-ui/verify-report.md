# Verify report: C-06 `login-ui`

Fecha: 2026-11-21 | Rama: dev-mayra (commit eba2f58, árbol limpio) | Revisor: sub-agente verificador independiente (solo lectura sobre código, specs, tasks y design; solo se creó este informe)

## Veredicto: PASS WITH WARNINGS

No hay issues CRITICAL: el contrato HTTP del frontend coincide con el backend real de C-04 (endpoints, campos, estados, códigos `code`, `Retry-After`, CSRF y cierre de sesión por change-password). `openspec validate --strict`, `npm test`, `npm run typecheck` y `npm run build` terminan en verde. tasks.md está 38/38. Los warnings son una contradicción entre dos specs (un escenario de `spa-routing` dice `/agenda` tras el cambio, pero el comportamiento correcto y el resto de los artefactos dicen `/login`), el registro de desviaciones en `design.md`, un escenario sin test a nivel de guard y la ausencia de evidencia RED en dos grupos de tareas.

Conteo: CRITICAL 0 | WARNING 4 | SUGGESTION 6.

## Resultados de ejecución (corridos por el verificador)

| Suite | Comando | Resultado |
|-------|---------|-----------|
| OpenSpec | `openspec validate c-06-login-ui --strict` | `Change 'c-06-login-ui' is valid` |
| Estado | `openspec status --change c-06-login-ui --json` | schema `spec-driven`; proposal, design, tasks y 5 specs (`api-client`, `password-change`, `professional-login`, `session-state`, `spa-routing`) existentes |
| Tests | `npm test` (desde `frontend/`, ~25 s) | 26 archivos, **378 tests, 0 fallos** |
| Typecheck | `npm run typecheck` | sin errores |
| Build | `npm run build` | OK, 32 módulos; `index.js` 30,24 kB (gzip 10,78 kB), `css` 16,85 kB, logo con hash |
| Dependencias | `git diff 797f17c -- frontend/package.json frontend/package-lock.json` | **sin cambios**: cero dependencias nuevas (regla 12) |
| Backend | `git show --stat eba2f58 -- backend` | el commit de C-06 no toca `backend/`, `knowledge-base/` ni `design-system/` |
| Cobertura | no ejecutada | mismo motivo que C-05 (sin `@vitest/coverage-*`); se juzga por escenarios |

## 1. Completitud

- tasks.md: 38 `[x]`, 0 `[ ]`. Cada grupo tiene código y test: `validation`, `retry-after`, `auth-messages`, `auth-gateway`, `session` (`signIn`, `endAfterPasswordChange`), `client` (`credentialAttempt`, `Retry-After`), `guards` (`requireSession`, `requireAuthenticated`, `redirectIfAuthenticated`), `core/dom/form`, `login-view`, `change-password-view`, `routes`, `main` (integración E2E con `fetch` simulado), `public-layout` (`markProLink`) y `styles.test.ts`.
- Los comandos de strict TDD se declaran en tasks.md; la evidencia RED de los grupos 1.6 y 8.x no consta (ver W4).

## 2. Matriz de cobertura de escenarios

Leyenda: OK = test que asserta el comportamiento. PARCIAL = indirecto. FALTA = sin test.

### professional-login
| Escenario | Test | Estado |
|-----------|------|--------|
| Vista inicial | `login-view.test.ts` "muestra titulo, campos con autocomplete correcto, boton habilitado y ningun error" | OK |
| Mostrar contraseña | "Mostrar contraseña alterna el tipo del campo" | OK |
| Campos vacíos / Email inválido | `validation.test.ts` + `login-view.test.ts` (envío vacío con foco en email, `romero@`, solo contraseña vacía) | OK |
| Doble envío | "envia email recortado y contraseña; en curso: aria-busy, ... segundo envio ignorado" | OK |
| Contraseña incorrecta / Email inexistente | `login-view.test.ts` "credenciales invalidas..." y "el texto es identico exista o no el email"; `auth-gateway.test.ts` 401 `INVALID_CREDENTIALS` | OK |
| Rechazo de validación del servidor (400 `VALIDATION_FAILED`) | `auth-gateway.test.ts` -> `invalid-credentials`, mismo copy que la vista | PARCIAL (la vista no lo prueba con el gateway real; el copy es el mismo `kind`) |
| Cuenta bloqueada (401 `INVALID_CREDENTIALS`) | `auth-gateway.test.ts` 401 + `login-view.test.ts` "identico... aun con cuenta bloqueada" | OK (ver S3: en la vista el gateway es un doble) |
| Límite con / sin tiempo | `login-view.test.ts` (Retry-After 45 -> "en 1 minuto"; sin -> "en unos minutos"); `retry-after.test.ts`; `auth-gateway.test.ts` 429 | OK |
| Backend caído | `login-view.test.ts` "red caida: mensaje de conexion, email conservado y boton habilitado" | OK |
| Ingreso con destino / Destino externo / Cambio obligatorio | `login-view.test.ts` (it.each de `next`, `mustChangePassword` con y sin `next` inválido); `main.test.ts` flujo completo | OK |
| Llegada por vencimiento / tras cambiar la contraseña | `login-view.test.ts` it.each `expired/offline/password-changed` + `main.test.ts` | OK |
| Intento fallido sin rastros | `login-view.test.ts` "sin rastros de credenciales" (espías de consola, URL y storage) | OK |

### password-change
| Escenario | Test | Estado |
|-----------|------|--------|
| Vista en modo obligatorio / voluntario | `change-password-view.test.ts` "modos" (tres) | OK |
| Nueva corta / Confirmación distinta / Igual a la actual / >128 / 12 exactos | `validation.test.ts` + `change-password-view.test.ts` (validación local y límites 12 y 129) | OK |
| Contraseña actual incorrecta | "contraseña actual incorrecta: campo vaciado y enfocado..." + `auth-gateway.test.ts` 401 `INVALID_CREDENTIALS` | OK |
| Validación rechazada por el servidor | "400 Size en newPassword", "400 NotBlank en currentPassword"; gateway 400 con y sin `errors` | OK |
| Sesión vencida durante el cambio | "401 UNAUTHENTICATED (expired): navega a /login" + gateway `onSessionExpired` una sola vez | OK |
| Página desactualizada | "403 CSRF_REQUIRED: alerta de pagina desactualizada" | OK |
| Cambio obligatorio / voluntario completado / next inválido descartado | tres tests de "exito" + "el orden es: sesion anonima con aviso primero" + `main.test.ts` | OK |
| Salir sin cambiar | "Salir en modo obligatorio invoca onLogout" + `main.test.ts` "Salir" | OK |
| Cambio fallido sin rastros | "sin rastros de contraseñas" | OK |

### session-state
| Escenario | Test | Estado |
|-----------|------|--------|
| Estado inicial / Cambio pendiente informado / Indicación ausente | `session.test.ts` (me con `mustChangePassword` true y ausente) | OK |
| Sin nombre del usuario | `session.test.ts` "forma real... sin id ni fullName" + `main.test.ts:131` etiqueta "Profesional" | OK |
| Logout exitoso / con error de red / Atrás después de salir | `session.test.ts`, `main.test.ts` "Salir" y "Salir y luego atras" | OK |
| Ingreso exitoso / Respuesta de ingreso inválida | `session.test.ts` `signIn`; `auth-gateway.test.ts` "200 sin userId -> connection" | OK |
| Cambio obligatorio resuelto | `session.test.ts` `endAfterPasswordChange` + `main.test.ts` | OK |

### spa-routing
| Escenario | Test | Estado |
|-----------|------|--------|
| Agenda sin sesión / Sesión desconocida | `guards.test.ts` | OK |
| Cambio pendiente / ya hecho | `guards.test.ts` (`requireSession` con y sin pendiente) | OK |
| Login con next válido / sin next / con cambio pendiente | `guards.test.ts` (`redirectIfAuthenticated`, it.each) | OK |
| Cambio de contraseña sin sesión | `guards.test.ts` `requireAuthenticated` "anonimo va a /login" | OK |
| next inválido en el cambio de contraseña | `change-password-view.test.ts` "next //evil.example se descarta: va a /login" | PARCIAL: el escenario del spec dice `/agenda` (ver W1); no hay test de guard con `next` inválido |

### api-client
| Escenario | Test | Estado |
|-----------|------|--------|
| 401 en llamada privada (regresión C-05) | `client.test.ts` "el mismo 401 sin la opcion sigue siendo unauthorized y llama onUnauthorized" | OK |
| 401 en intento de ingreso / en cambio de contraseña | `client.test.ts` líneas 244 y 264 (`credentialAttempt`, sin `onUnauthorized`) | OK |
| Retry-After en segundos / otro formato | `client.test.ts` 429 con 45 y `it.each` (fecha HTTP, texto, negativo, decimal) | OK |

Resumen: 0 FALTA; 2 PARCIAL (rechazo de validación en la vista, `next` inválido en `/cambiar-contrasena`). No se detectaron aserciones triviales: los tests "sin rastros" usan espías de consola, URL y storage reales, y el orden "sesión anónima antes de navegar" se asserta con la secuencia de llamadas.

## 3. Fidelidad del contrato contra el backend real (solo lectura)

| Aspecto | Backend | Frontend | Resultado |
|---------|---------|----------|-----------|
| Login | `POST /api/v1/auth/login` `{email,password}`; 200 `AuthMeResponse(userId, role, mustChangePassword, expiresAt)` | `auth-gateway.ts` POST `/auth/login` con `{email,password}` (base `/api/v1`); `decodeSessionUser` exige `userId` y `role`, `mustChangePassword` por defecto `false` | OK |
| Credenciales / bloqueo | `AuthenticateUser`: usuario inexistente, contraseña mala y cuenta bloqueada responden 401 `INVALID_CREDENTIALS`; rate limit 429 `RATE_LIMITED` con `Retry-After` en segundos | 401 y 400 `VALIDATION_FAILED` -> `invalid-credentials`; 429 -> `locked` con `retryAfterSeconds` | OK (sin enumeración de cuentas) |
| 400 de validación | `ApiExceptionHandler`: `VALIDATION_FAILED` + `errors[{field,code}]` | `parseFieldErrors`; `fieldErrorMessage` por `field`+`code` (`Size`, `NotBlank`) | OK |
| Change-password | `POST /api/v1/auth/change-password` `{currentPassword,newPassword}`; 200 vacío; 401 `INVALID_CREDENTIALS` si la actual es incorrecta; 400 `VALIDATION_FAILED` (`newPassword`, `Size`); invalida todas las sesiones y borra la cookie | gateway: 401 `INVALID_CREDENTIALS` -> `current-invalid`; otro 401 -> `expired`; 400 con `errors` -> `fields`; éxito -> `endAfterPasswordChange` + `/login` | OK |
| Sesión vencida | `ProblemExpiredSessionHandler` / entry point: 401 `UNAUTHENTICATED` | `credentialAttempt: true` evita el `onUnauthorized` global y el gateway llama `onSessionExpired` una vez | OK |
| CSRF | `CookieCsrfTokenRepository.withHttpOnlyFalse()` (cookie `XSRF-TOKEN`, cabecera `X-XSRF-TOKEN`); `/auth/login` ignorado por CSRF; 403 `CSRF_REQUIRED` | `cookieCsrfTransport` por defecto lee `XSRF-TOKEN` y envía `X-XSRF-TOKEN` en métodos no seguros; 403 `CSRF_REQUIRED` -> `stale-page` | OK |
| Política de contraseña | `PasswordPolicy` 12–128 por `String.length()` y `@Size` del DTO | `PASSWORD_MIN/MAX_LENGTH` con `String.length` (UTF-16), test "cuenta unidades UTF-16 como el servidor" | OK |
| `/auth/me` y logout | `GET /auth/me` autenticado, `POST /auth/logout` `permitAll` con CSRF, 204 | `httpSessionGateway`; sin `fullName` el shell usa "Profesional" | OK |
| Cambio pendiente | `DentistAccessManager`: 403 `PASSWORD_CHANGE_REQUIRED` en `/api/v1/**` salvo `/auth/me` y `/auth/change-password` | el guard redirige a `/cambiar-contrasena` (UX); el 403 no dispara `onUnauthorized` | OK |

No hay discrepancias de contrato.

## 4. Reglas duras (CLAUDE.md raíz)

| Regla | Resultado |
|-------|-----------|
| 9 (strict, sin `any`) | 0 usos en producción (solo una mención en un comentario de `decode.ts`); `tsc --noEmit` verde. OK |
| 10 (sin `innerHTML`) | 0 apariciones de `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `srcdoc`, `eval`, `new Function` en código de producción; `architecture.test.ts` lo vigila. OK |
| 11 (solo tokens) | Sin colores literales. Literales dimensionales: `1px`/`2px`/`4px` de bordes en `forms.css` y `layout.css`, `--layout-hairline: 1px` local (documentado como candidato a token) y `64rem` en media queries. Ver S1. OK con observación |
| 12 (sin deps) | `package.json` y lock idénticos a 797f17c. OK |
| 6 (sin PII en logs) | 0 `console.*` en producción; tests "sin rastros" para email y contraseñas. OK |
| Sin almacenamiento | 0 usos de `localStorage`, `sessionStorage` o `indexedDB` en producción; la contraseña se oculta (`conceal`) tras cada envío y se vacía tras un cambio exitoso. OK |
| Mensajes genéricos | La vista nunca muestra `title`/`detail` del servidor (`auth-messages.ts` es la única fuente de copy); mismo texto para email inexistente, contraseña incorrecta y cuenta bloqueada. OK |
| `next` seguro | `safeNext` solo acepta rutas privadas declaradas (descarta `//`, `\`, caracteres de control, rutas públicas como `/cambiar-contrasena`); aplicado en login, guards y cambio de contraseña. OK |
| 13 / 14 | Commit `feat(C-06): ...`; el verificador no hizo commits. OK |

## 5. Desviaciones reportadas por el implementador

| Desviación | Evaluación |
|-----------|------------|
| Alerta separada en título + detalle (`AlertMessage`, `messageText`) | **Aceptable**: respeta la jerarquía visual del mockup y mantiene una sola fuente de copy. Registrar en design (W2). |
| 200 sin `userId` -> `connection` (D6) | **Aceptable y coherente** con el escenario "Respuesta de ingreso inválida"; test en `auth-gateway.test.ts`. |
| `createRoutes({session, gateway, navigate, onLogout})` | **Aceptable**: inyección explícita de dependencias que permite probar vistas sin `main.ts`. Registrar en design (W2). |
| `form.ts` exporta `alertBox` y `formIcon` | **Aceptable**: los comparten ambas vistas; ubicación coherente con `core/dom`. |
| `markProLink` en `public-layout` | **Aceptable**: necesario para que "Acceso profesionales" refleje `aria-current` al reutilizar el layout; test en `main.test.ts` ("Acceso profesionales se marca aria-current en /login..."). |
| `--layout-hairline: 1px` local | **Aceptable** y documentado en el propio CSS; ver S1. |
| Hover del input usa `--color-text-secondary` | **Aceptable**: usa un token existente; diferencia visual mínima respecto del mockup. |
| Padding de escritorio 112 vs 128 px y fondo más claro | **Aceptable (minor)**: confirmado en las capturas (ver sección 6). |
| `login-expired` no capturado | **Aceptable**: cubierto por tests (`login-view.test.ts` it.each, `main.test.ts`); ver sección 6. |
| auth-messages 1.6 y grupos 8.x no vistos en RED | **Warning W4**: el código está cubierto, pero no hay evidencia de ciclo RED que exige strict TDD. |

## Issues

### CRITICAL
Ninguno.

### WARNING
- **W1 (artefactos)** `specs/spa-routing/spec.md`, escenario "next inválido en el cambio de contraseña" termina en "tras completarlo, la URL pasa a ser `/agenda`". Contradice `specs/password-change` ("next inválido descartado": la URL es `/login`), el diseño (el servidor cierra todas las sesiones al cambiar la contraseña, D2/D6), los tests (`change-password-view.test.ts`, `main.test.ts`) y el backend (`ChangeOwnPassword` + cookie borrada). Corregir el THEN a `/login` (con el aviso de contraseña cambiada) antes de archivar; de lo contrario el spec principal queda con un comportamiento imposible. Además falta un test a nivel de guard o de integración con sesión `mustChangePassword = true` y `next=%2F%2Fevil.example` (hoy solo hay test de vista).
- **W2 (artefactos)** `design.md` no registra las desviaciones aprobadas en la implementación: división título/detalle de las alertas, firma de `createRoutes` con `session`/`gateway`/`navigate`/`onLogout`, helpers `alertBox`/`formIcon` en `form.ts`, `markProLink`, `--layout-hairline` local y el hover con `--color-text-secondary`. Añadir una sección de desviaciones (o actualizar D5/D6/D7) para que el design refleje lo construido, como se hizo en C-05.
- **W3 (artefactos)** `design.md` línea `Visual verify: differences (...)` debería quedar en `passed` con las diferencias minor aceptadas (padding 112 vs 128 px, fondo del shell, `login-expired` y `login-offline-de-sesión` cubiertos por tests) o pasar de nuevo por el flujo de aprobación según la política de Lucas/Mayra. Hoy el estado `differences` deja el change formalmente sin cerrar.
- **W4 (proceso TDD)** No hay evidencia de RED ejecutado para el módulo `auth-messages` (tarea 1.6) ni para los tests del grupo 8.x (integración en `main.test.ts`); los tests actuales pasan y cubren el comportamiento, pero el ciclo estricto exige ver fallar primero. Dejar constancia en el reporte de apply o aceptar la desviación explícitamente.

### SUGGESTION
- **S1** Documentar en design que `2px`/`4px` de bordes, `1px` del hairline y `64rem` de los media queries se permiten como literales (como D8 de C-05) o promoverlos a tokens en la próxima revisión del design system.
- **S2** Agregar un test de `requireAuthenticated` con `next` inválido (`//evil.example`) que verifique que se descarta (cierra la parte PARCIAL de `spa-routing`).
- **S3** `login-view.test.ts` usa un doble del gateway para "bloqueo" y "Rechazo de validación"; un test de integración en `main.test.ts` con `fetch` simulado (401 `INVALID_CREDENTIALS` y 400 `VALIDATION_FAILED` pasando por el gateway real) lo haría más fiel al contrato.
- **S4** El aviso de `offline` se muestra tanto en el mensaje de sesión como en el de error de conexión del envío con redacciones distintas ("Sin conexión con el servidor..." vs "No pudimos conectar con el servidor..."); unificar el copy si el producto lo prefiere.
- **S5** `list-style` de los requisitos en el cambio de contraseña usa íconos (círculo con borde) distintos de las viñetas del mockup; aceptable, pero conviene reflejarlo en `mockups/` si se quiere igualdad exacta.
- **S6** Sigue sin medirse cobertura de líneas (mismo W3 de C-05): se juzga por escenarios.

## 6. Visual verify

- `design.md` registra `Visual verify: differences (2026-11-21)` con 10 estados en 390x844 y 1440x900; `mockups/verify/results.json` marca `overflowX: false` en todos los pares.
- Comparé con la imagen del mockup aprobado (`previews/`): `login-1440`, `change-password-1440`, y abrí `verify/login-password-changed-390`.
  - `login-1440`: misma estructura (logo + marca + badge PROVISIONAL, "Acceso profesionales", tarjeta con título "Ingresar", bajada, campos Email y Contraseña con ojo, botón primario, nota "(TBD · PA-04)", footer con TBD). Diferencias: el fondo del área principal es blanco en la implementación y gris claro en el mockup; la tarjeta queda 16 px más arriba (padding superior 112 vs 128 px); sin la leyenda "Mockup C-06...". Minor, aceptable.
  - `change-password-1440`: título "Elegí una contraseña nueva", explicación de primer ingreso, tres campos con ojo, ayuda "Mínimo 12 caracteres...", lista de tres requisitos, "Guardar contraseña" y "Salir". Coincide con el mockup, con las mismas diferencias de fondo y padding; sin la nota explicativa del mockup, que es contenido del prototipo.
  - `login-password-changed-390`: aviso informativo "Contraseña actualizada. Ingresá de nuevo con la contraseña nueva." sobre el formulario, con el icono y borde izquierdo del tono info. Coincide con la pantalla nueva de la revisión 2.
- No volví a levantar el navegador ni la aplicación (solo se compararon capturas existentes). No inspeccioné una por una las otras 14 capturas.

## 7. Pendientes manuales (no bloqueantes)

- Probar a mano el flujo real contra el backend de C-04 (login, primer ingreso con cambio obligatorio, 429 con `Retry-After`, sesión vencida a mitad de un cambio); los tests usan `fetch` simulado con el contrato verificado contra el código.
- `login-expired` y el aviso `offline` de sesión no tienen captura en `verify/` (cubiertos por tests).

## Lo que no pude verificar

- Ejecución contra un backend real o contra el navegador (solo lectura de código y capturas existentes).
- Cobertura de líneas (sin proveedor instalado).
- Evidencia histórica de RED (no hay salida de tests previa a la implementación en el repo).

## Siguiente acción recomendada

1. Antes de archivar: corregir W1 (THEN de `spa-routing` a `/login`), W2 (desviaciones en `design.md`) y W3 (estado de `Visual verify`); decidir W4 con el equipo. Correr `openspec validate c-06-login-ui --strict` de nuevo.
2. Opcional: S2 y S3 (dos tests más).
3. `/opsx:archive c-06-login-ui` (sincroniza specs y actualiza `CHANGES.md`). Commit solo cuando el usuario lo pida.

## Resolución de warnings (2026-11-21)

- **W1 resuelto**: el escenario "next inválido en el cambio de contraseña" de `specs/spa-routing/spec.md` ahora termina en `/login` con el aviso de contraseña cambiada, coherente con `password-change` y con el cierre de sesión del backend.
- **W2 resuelto**: `design.md` tiene una sección "Desviaciones de implementación" (alertas título + detalle, firma de `createRoutes` y `getRoutes` opcional en los guards, `alertBox`/`formIcon` en `form.ts`, `markProLink`, `--layout-hairline` local como candidato a token de la Revisión 2, hover de input con `--color-text-secondary`, 200 sin `userId` -> `connection`).
- **W3 resuelto**: `Visual verify: passed (2026-11-21; diferencias minor aceptadas ...)` en `design.md`.
- **W4 resuelto (aceptado explícitamente)**: constancia en `design.md` de que las tareas 1.6 y los grupos 8.x no se observaron en RED.
- **S1 hecho**: literales de borde (2px/4px), hairline 1px y 64rem documentados como excepciones permitidas (precedente D8 de C-05).
- **S2 hecho**: nuevo test en `frontend/src/main.test.ts` ("next invalido en /cambiar-contrasena ...") que pasa contra el código actual; sin cambios de producción.
- **S3–S6**: quedan como seguimientos (S3 tests de integración con gateway real para 401/400, S4 unificar copy de offline, S5 íconos de requisitos vs mockup, S6 cobertura de líneas).
