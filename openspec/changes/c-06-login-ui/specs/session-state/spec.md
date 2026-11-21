# Spec Delta: session-state

## MODIFIED Requirements

### Requirement: Estados de la sesión
La sesión SHALL estar en exactamente uno de tres estados: `unknown` (todavía no consultada), `anonymous` o `authenticated` con los datos públicos del usuario (`id`, `fullName`, `role`) y la indicación `mustChangePassword` (falsa si el servidor no la informa); MUST NOT guardar contraseñas, tokens ni la sesión en `localStorage` o `sessionStorage`.

#### Scenario: Estado inicial
- **WHEN** arranca la SPA
- **THEN** la sesión está en `unknown` y no hay datos de sesión en `localStorage` ni `sessionStorage`

#### Scenario: Cambio de contraseña pendiente informado
- **GIVEN** `GET /api/v1/auth/me` responde 200 `{"id":"u1","fullName":"Dra. Romero","role":"DENTIST","mustChangePassword":true}`
- **WHEN** arranca la SPA
- **THEN** la sesión es `authenticated` con `mustChangePassword = true`

#### Scenario: Indicación ausente
- **GIVEN** `GET /api/v1/auth/me` responde 200 sin el campo `mustChangePassword`
- **WHEN** arranca la SPA
- **THEN** la sesión es `authenticated` con `mustChangePassword = false`

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

## ADDED Requirements

### Requirement: Sesión establecida por el ingreso
Un ingreso exitoso (`POST /api/v1/auth/login` con 200 y un usuario válido) SHALL pasar la sesión a `authenticated` con los datos devueltos, sin aviso, sin una consulta adicional a `GET /api/v1/auth/me`; un ingreso rechazado MUST dejar la sesión `anonymous`.

#### Scenario: Ingreso exitoso
- **GIVEN** la sesión `anonymous` con aviso de sesión vencida
- **WHEN** `POST /api/v1/auth/login` responde 200 `{"id":"u1","fullName":"Dra. Romero","role":"DENTIST","mustChangePassword":false}`
- **THEN** la sesión es `authenticated` con `fullName = "Dra. Romero"` y los suscriptores reciben ese estado

#### Scenario: Respuesta de ingreso inválida
- **WHEN** `POST /api/v1/auth/login` responde 200 con un cuerpo sin `id`
- **THEN** la sesión sigue `anonymous` y la vista muestra el error genérico de conexión

### Requirement: Sesión actualizada tras cambiar la contraseña
Un cambio de contraseña exitoso SHALL dejar la sesión `authenticated` con `mustChangePassword = false` y el resto de los datos del usuario sin cambios.

#### Scenario: Cambio obligatorio resuelto
- **GIVEN** una sesión `authenticated` con `mustChangePassword = true`
- **WHEN** `POST /api/v1/auth/change-password` responde 204
- **THEN** la sesión es `authenticated` con `mustChangePassword = false` y el mismo `fullName`
