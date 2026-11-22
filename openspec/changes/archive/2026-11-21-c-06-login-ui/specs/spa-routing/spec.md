# Spec Delta: spa-routing

## MODIFIED Requirements

### Requirement: Guard de rutas privadas
Navegar a una ruta privada sin sesión autenticada SHALL redirigir a `/login` reemplazando la entrada del historial y conservando el destino original en el parámetro `next`. Con una sesión autenticada que tiene `mustChangePassword = true`, navegar a una ruta privada SHALL redirigir a `/cambiar-contrasena` reemplazando la entrada del historial y conservando el destino en `next`.

#### Scenario: Agenda sin sesión
- **GIVEN** la sesión en estado anónimo
- **WHEN** se navega a `/agenda`
- **THEN** la URL pasa a ser `/login?next=%2Fagenda` y el historial no conserva `/agenda` como entrada previa

#### Scenario: Sesión todavía desconocida
- **GIVEN** la sesión aún no fue consultada
- **WHEN** se navega a `/agenda`
- **THEN** la SPA espera el resultado de la consulta de sesión antes de decidir y no muestra contenido privado mientras tanto

#### Scenario: Cambio de contraseña pendiente
- **GIVEN** una sesión autenticada con `mustChangePassword = true`
- **WHEN** se navega a `/pacientes`
- **THEN** la URL pasa a ser `/cambiar-contrasena?next=%2Fpacientes` y no se muestra contenido privado

#### Scenario: Cambio de contraseña ya hecho
- **GIVEN** una sesión autenticada con `mustChangePassword = false`
- **WHEN** se navega a `/pacientes`
- **THEN** se muestra la vista de pacientes

### Requirement: Login con sesión activa
Navegar a `/login` con una sesión autenticada SHALL redirigir al destino `next` si es una ruta interna válida, o a `/agenda` en otro caso; si la sesión tiene `mustChangePassword = true`, SHALL redirigir a `/cambiar-contrasena` conservando ese destino válido en `next`.

#### Scenario: Login con next válido
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/login?next=%2Fpacientes`
- **THEN** la URL pasa a ser `/pacientes`

#### Scenario: Login sin next
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/login`
- **THEN** la URL pasa a ser `/agenda`

#### Scenario: Login con cambio de contraseña pendiente
- **GIVEN** una sesión autenticada con `mustChangePassword = true`
- **WHEN** se navega a `/login?next=%2Fpacientes`
- **THEN** la URL pasa a ser `/cambiar-contrasena?next=%2Fpacientes`

## ADDED Requirements

### Requirement: Ruta de cambio de contraseña
La ruta `/cambiar-contrasena` SHALL requerir una sesión autenticada (sin sesión redirige a `/login`), mostrarse dentro del app shell público y aceptar un parámetro `next` validado con la misma regla de destinos internos que `/login`.

#### Scenario: Cambio de contraseña sin sesión
- **GIVEN** la sesión en estado anónimo
- **WHEN** se navega a `/cambiar-contrasena`
- **THEN** la URL pasa a ser `/login`

#### Scenario: next inválido en el cambio de contraseña
- **GIVEN** una sesión con `mustChangePassword = true`
- **WHEN** se navega a `/cambiar-contrasena?next=%2F%2Fevil.example`
- **THEN** se muestra el formulario de cambio el valor inválido de `next` se ignora y, tras completarlo, la URL pasa a ser `/login` con el aviso de contraseña cambiada (el servidor cierra todas las sesiones al cambiar la contraseña)
