# session-state Specification

## Purpose
Define el estado de la sesión del profesional dentro de la SPA: cómo se conoce al arrancar, cómo se observa desde las pantallas, cómo se cierra y cómo se reacciona cuando el servidor la da por vencida.

## Requirements

### Requirement: Estados de la sesión
La sesión SHALL estar en exactamente uno de tres estados: `unknown` (todavía no consultada), `anonymous` o `authenticated` con los datos que informa el servidor (`userId`, `role`, `expiresAt` y `fullName` opcional, que C-04 hoy no envía) y la indicación `mustChangePassword` (falsa si el servidor no la informa); `anonymous` puede llevar un aviso (`expired`, `offline` o `password-changed`); MUST NOT guardar contraseñas, tokens ni la sesión en `localStorage` o `sessionStorage`.

#### Scenario: Estado inicial
- **WHEN** arranca la SPA
- **THEN** la sesión está en `unknown` y no hay datos de sesión en `localStorage` ni `sessionStorage`

#### Scenario: Cambio de contraseña pendiente informado
- **GIVEN** `GET /api/v1/auth/me` responde 200 `{"userId":"u1","role":"DENTIST","mustChangePassword":true,"expiresAt":"2026-11-21T15:30:00Z"}`
- **WHEN** arranca la SPA
- **THEN** la sesión es `authenticated` con `mustChangePassword = true`

#### Scenario: Sin nombre del usuario
- **GIVEN** `GET /api/v1/auth/me` responde 200 sin `fullName`
- **WHEN** arranca la SPA
- **THEN** la sesión es `authenticated` (no es una respuesta inválida) y el app shell privado muestra la etiqueta de respaldo "Profesional" en lugar del nombre

#### Scenario: Indicación ausente
- **GIVEN** `GET /api/v1/auth/me` responde 200 sin el campo `mustChangePassword`
- **WHEN** arranca la SPA
- **THEN** la sesión es `authenticated` con `mustChangePassword = false`

### Requirement: Carga inicial de la sesión
Al arrancar, la SPA SHALL consultar una sola vez la sesión actual (`GET /api/v1/auth/me`): 200 con usuario válido pasa a `authenticated`, 401 pasa a `anonymous`, y cualquier otro error pasa a `anonymous` mostrando un aviso genérico de conexión.

#### Scenario: Sesión existente
- **GIVEN** `GET /api/v1/auth/me` responde 200 `{"id":"u1","fullName":"Dra. Romero","role":"DENTIST"}`
- **WHEN** arranca la SPA
- **THEN** la sesión pasa a `authenticated` con `fullName = "Dra. Romero"`

#### Scenario: Sin sesión
- **GIVEN** `GET /api/v1/auth/me` responde 401
- **WHEN** arranca la SPA
- **THEN** la sesión pasa a `anonymous` sin aviso de sesión vencida

#### Scenario: Backend caído
- **GIVEN** `GET /api/v1/auth/me` falla por red
- **WHEN** arranca la SPA
- **THEN** la sesión pasa a `anonymous` y se muestra un aviso genérico de conexión

#### Scenario: Consultas concurrentes
- **WHEN** dos guards piden la sesión mientras la consulta inicial está en curso
- **THEN** se realiza una única petición a `GET /api/v1/auth/me`

### Requirement: Observación de la sesión
Las pantallas SHALL poder suscribirse a los cambios de la sesión y recibir el nuevo estado en cada cambio, y MUST poder cancelar la suscripción.

#### Scenario: Suscriptor notificado
- **GIVEN** un suscriptor a la sesión
- **WHEN** la sesión pasa de `unknown` a `authenticated`
- **THEN** el suscriptor recibe el estado `authenticated` una vez

#### Scenario: Suscripción cancelada
- **GIVEN** un suscriptor que canceló su suscripción
- **WHEN** la sesión cambia
- **THEN** el suscriptor no es notificado

### Requirement: Cierre de sesión
Al elegir "Salir", la SPA SHALL invocar `POST /api/v1/auth/logout`, pasar la sesión a `anonymous` aunque la llamada falle y navegar a `/login`; volver atrás después de salir MUST NOT mostrar contenido privado.

#### Scenario: Logout exitoso
- **GIVEN** una sesión autenticada
- **WHEN** el usuario elige "Salir" y el servidor responde 204
- **THEN** la sesión es `anonymous` y la URL es `/login`

#### Scenario: Logout con error de red
- **GIVEN** una sesión autenticada
- **WHEN** el usuario elige "Salir" y la llamada falla por red
- **THEN** la sesión es `anonymous` y la URL es `/login`

#### Scenario: Atrás después de salir
- **GIVEN** una sesión autenticada que navegó de `/agenda` a `/pacientes` y en `/pacientes` elige "Salir"
- **WHEN** el usuario vuelve atrás en el historial
- **THEN** la URL pasa a ser `/login?next=%2Fagenda` y no se muestra contenido privado

### Requirement: Sesión vencida
Cuando una llamada a la API informa sesión vencida estando en una ruta privada, la SPA SHALL pasar la sesión a `anonymous` y redirigir a `/login` con la ruta actual en `next` y un aviso "Tu sesión venció. Ingresá de nuevo.".

#### Scenario: 401 en pacientes
- **GIVEN** una sesión autenticada en `/pacientes`
- **WHEN** una llamada a la API recibe 401
- **THEN** la sesión es `anonymous`, la URL es `/login?next=%2Fpacientes` y se muestra el aviso de sesión vencida

### Requirement: Transporte de sesión reemplazable
La forma de autenticar las llamadas (cookie de sesión con CSRF) SHALL poder reemplazarse por otra (por ejemplo, token portador) sin cambiar las pantallas, el router ni los guards.

#### Scenario: Transporte alternativo
- **GIVEN** la SPA configurada con un transporte de prueba que agrega `Authorization: Bearer t`
- **WHEN** se envía `GET /auth/me`
- **THEN** la petición lleva `Authorization: Bearer t`, no lleva `X-XSRF-TOKEN` y los guards se comportan igual

### Requirement: Sesión establecida por el ingreso
Un ingreso exitoso (`POST /api/v1/auth/login` con 200 y un usuario válido) SHALL pasar la sesión a `authenticated` con los datos devueltos, sin aviso, sin una consulta adicional a `GET /api/v1/auth/me`; un ingreso rechazado MUST dejar la sesión `anonymous`.

#### Scenario: Ingreso exitoso
- **GIVEN** la sesión `anonymous` con aviso de sesión vencida
- **WHEN** `POST /api/v1/auth/login` responde 200 `{"userId":"u1","role":"DENTIST","mustChangePassword":false,"expiresAt":"2026-11-21T15:30:00Z"}`
- **THEN** la sesión es `authenticated` con `userId = "u1"` y `mustChangePassword = false` y los suscriptores reciben ese estado

#### Scenario: Respuesta de ingreso inválida
- **WHEN** `POST /api/v1/auth/login` responde 200 con un cuerpo sin `userId`
- **THEN** la sesión sigue `anonymous` y la vista muestra el error genérico de conexión

### Requirement: Sesión cerrada por el cambio de contraseña
Un cambio de contraseña exitoso invalida en el servidor todas las sesiones de la cuenta; la SPA SHALL pasar la sesión a `anonymous` con aviso `password-changed`, sin consultar `GET /api/v1/auth/me`.

#### Scenario: Cambio obligatorio resuelto
- **GIVEN** una sesión `authenticated` con `mustChangePassword = true`
- **WHEN** `POST /api/v1/auth/change-password` responde 200
- **THEN** la sesión es `anonymous` con aviso `password-changed` y los suscriptores reciben ese estado
