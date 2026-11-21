# spa-routing Specification

## Purpose
Define cómo la SPA decide qué pantalla mostrar según la URL, qué rutas son públicas o privadas y cómo redirige según el estado de la sesión, sin exponer destinos externos ni tokens del paciente.

## Requirements

### Requirement: Rutas declaradas
La SPA SHALL reconocer las rutas públicas `/reservar`, `/reservar/confirmado`, `/turno/{token}` y `/login`, y las rutas privadas `/agenda`, `/pacientes` y `/configuracion`, resolviendo la ruta por el `pathname` de la URL (History API, sin `#`).

#### Scenario: Ruta pública con parámetro
- **WHEN** se navega a `/turno/AbC-123_x`
- **THEN** se muestra la vista de gestión de turno con el parámetro `token` igual a `AbC-123_x` dentro del layout público

#### Scenario: Ruta privada con sesión
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/pacientes`
- **THEN** se muestra la vista de pacientes dentro del layout privado con "Pacientes" marcado como actual

#### Scenario: Barra final
- **WHEN** se navega a `/agenda/` con una sesión autenticada
- **THEN** se resuelve la misma ruta que `/agenda`

### Requirement: Ruta no encontrada
Una URL que no coincide con ninguna ruta declarada SHALL mostrar una vista "Página no encontrada" con un enlace al inicio, sin redirigir y sin mostrar la URL solicitada.

#### Scenario: URL desconocida
- **WHEN** se navega a `/no-existe`
- **THEN** se muestra "Página no encontrada", la URL sigue siendo `/no-existe` y el texto de la vista no contiene `no-existe`

### Requirement: Guard de rutas privadas
Navegar a una ruta privada sin sesión autenticada SHALL redirigir a `/login` reemplazando la entrada del historial y conservando el destino original en el parámetro `next`.

#### Scenario: Agenda sin sesión
- **GIVEN** la sesión en estado anónimo
- **WHEN** se navega a `/agenda`
- **THEN** la URL pasa a ser `/login?next=%2Fagenda` y el historial no conserva `/agenda` como entrada previa

#### Scenario: Sesión todavía desconocida
- **GIVEN** la sesión aún no fue consultada
- **WHEN** se navega a `/agenda`
- **THEN** la SPA espera el resultado de la consulta de sesión antes de decidir y no muestra contenido privado mientras tanto

### Requirement: Login con sesión activa
Navegar a `/login` con una sesión autenticada SHALL redirigir al destino `next` si es una ruta interna válida, o a `/agenda` en otro caso.

#### Scenario: Login con next válido
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/login?next=%2Fpacientes`
- **THEN** la URL pasa a ser `/pacientes`

#### Scenario: Login sin next
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/login`
- **THEN** la URL pasa a ser `/agenda`

### Requirement: Destinos de redirección solo internos
El parámetro `next` SHALL aceptarse solo si es una ruta privada declarada del mismo origen; cualquier otro valor (URL absoluta, `//host`, esquema, barra invertida, ruta pública o desconocida) MUST ignorarse.

#### Scenario: Redirección abierta
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/login?next=%2F%2Fevil.example%2Fagenda`
- **THEN** la URL pasa a ser `/agenda` y no se navega a otro origen

#### Scenario: Esquema javascript
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/login?next=javascript%3Aalert(1)`
- **THEN** la URL pasa a ser `/agenda`

### Requirement: Ruta raíz
La ruta `/` SHALL redirigir a `/agenda` con sesión autenticada y a `/reservar` sin ella.

#### Scenario: Raíz anónima
- **GIVEN** la sesión en estado anónimo
- **WHEN** se navega a `/`
- **THEN** la URL pasa a ser `/reservar`

#### Scenario: Raíz autenticada
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/`
- **THEN** la URL pasa a ser `/agenda`

### Requirement: Navegación interna sin recarga
Los clics primarios sin teclas modificadoras sobre enlaces del mismo origen hacia rutas de la SPA SHALL navegar sin recargar la página; los enlaces externos, con `target`, con `download` o con teclas modificadoras MUST conservar el comportamiento del navegador.

#### Scenario: Clic en enlace interno
- **WHEN** se hace clic en un enlace a `/pacientes` dentro de la SPA
- **THEN** la URL cambia a `/pacientes`, se renderiza la vista y no hay recarga de documento

#### Scenario: Clic con Ctrl
- **WHEN** se hace clic con Ctrl en un enlace a `/pacientes`
- **THEN** la SPA no intercepta el evento

#### Scenario: Atrás del navegador
- **GIVEN** se navegó de `/agenda` a `/pacientes`
- **WHEN** el usuario vuelve atrás
- **THEN** se muestra la vista de agenda

### Requirement: Título y foco al navegar
Cada navegación SHALL actualizar el título del documento con el nombre de la vista y la marca, y mover el foco al encabezado principal de la vista para lectores de pantalla.

#### Scenario: Título de pacientes
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/pacientes`
- **THEN** el título del documento contiene "Pacientes" y el nombre de la marca, y el foco está en el `h1` de la vista

### Requirement: Token de gestión fuera de títulos y registros
El parámetro `token` de `/turno/{token}` MUST NOT aparecer en el título del documento, en el parámetro `next` ni en mensajes de consola o de error.

#### Scenario: Título de gestión de turno
- **WHEN** se navega a `/turno/AbC-123_x`
- **THEN** el título del documento no contiene `AbC-123_x` y no se escribe nada en la consola que lo contenga
