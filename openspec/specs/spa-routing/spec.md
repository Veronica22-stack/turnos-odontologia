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
El parámetro `token` de `/turno/{token}` MUST NOT aparecer en el título del documento, en el parámetro `next` ni en mensajes de consola o de error. La vista de gestión MUST NOT mostrarlo en el texto de la página, guardarlo en `localStorage`/`sessionStorage`, incluirlo en enlaces de la página ni enviarlo en otra petición que no sea la ruta de la API de gestión por token; el título de `/turno/{token}` SHALL ser fijo ("Gestionar turno" con la marca) en todos sus estados. Las rutas `/reservar` y `/reservar/confirmado` MUST NOT llevar en la URL, el título del documento ni la consola datos personales del paciente (nombre, DNI, teléfono, email) ni el identificador de la reserva.

#### Scenario: Título de gestión de turno
- **WHEN** se navega a `/turno/AbC-123_x`
- **THEN** el título del documento no contiene `AbC-123_x` y no se escribe nada en la consola que lo contenga

#### Scenario: Título fijo en todos los estados de gestión
- **GIVEN** un token válido
- **WHEN** la vista pasa por cargando, detalle, reprogramación, "Turno cancelado" y "Enlace no válido"
- **THEN** el título del documento es siempre "Gestionar turno" con la marca y nunca contiene el token ni el nombre de la prestación

#### Scenario: Token fuera de otras peticiones
- **GIVEN** la reprogramación consulta la disponibilidad y las prestaciones públicas
- **WHEN** se inspeccionan las URL y cuerpos de esas peticiones
- **THEN** ninguna contiene el token; solo las peticiones a `/api/v1/public/bookings/{token}` lo llevan, en la ruta

#### Scenario: Confirmación de reserva sin datos en la URL
- **GIVEN** una reserva confirmada con DNI `30456789` e identificador de reserva `7b1e…`
- **WHEN** se muestra `/reservar/confirmado`
- **THEN** la URL es exactamente `/reservar/confirmado`, el título es "Reserva confirmada" con la marca y ni la URL, ni el título, ni la consola contienen el DNI, el email ni el identificador

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

### Requirement: Pasos de reserva en la URL
El flujo `/reservar` SHALL reflejar el paso en la query `paso` (sin parámetro: paso 1; `?paso=2`; `?paso=3`) y apilar una entrada de historial al avanzar (`pushState`), de modo que atrás y adelante recorren los pasos conservando en memoria lo elegido. La URL MUST NOT contener nada más que el número de paso. Sin estado en memoria (recarga), la SPA SHALL normalizar `?paso=2|3` a `/reservar` con `replaceState`; salir de `/reservar` y volver SHALL empezar sin lo elegido.

#### Scenario: Atrás vuelve al paso anterior
- **GIVEN** la persona eligió una prestación (URL `/reservar?paso=2`) y un horario (URL `/reservar?paso=3`)
- **WHEN** usa atrás del navegador
- **THEN** la URL es `/reservar?paso=2`, se muestra el paso 2 con la prestación y el horario aún marcados

#### Scenario: Adelante restaura el paso siguiente
- **GIVEN** la persona volvió del paso 3 al paso 2 con atrás del navegador
- **WHEN** usa adelante del navegador
- **THEN** la URL es `/reservar?paso=3` y se muestra el paso 3 con los datos cargados

#### Scenario: Recarga de un paso sin estado
- **WHEN** se carga directamente `/reservar?paso=3` sin estado en memoria
- **THEN** la URL pasa a `/reservar` sin agregar una entrada de historial y se muestra el paso 1

#### Scenario: Valor de paso inválido
- **WHEN** se carga `/reservar?paso=9` o `/reservar?paso=abc`
- **THEN** se muestra el paso 1 y la URL se normaliza a `/reservar`

#### Scenario: Sin datos en la URL
- **GIVEN** una persona en el paso 3 con datos cargados
- **WHEN** se inspecciona `location.href`
- **THEN** es exactamente `/reservar?paso=3`, sin prestación, horario ni datos personales

### Requirement: Acción de reprogramar en la URL
La vista `/turno/{token}` SHALL reflejar la pantalla de reprogramación con la query `accion=reprogramar`, apilando una entrada de historial al abrirla (`pushState`), de modo que atrás del navegador vuelve al detalle. La URL MUST NOT contener nada más que el token en la ruta y esa acción (ni horario ni prestación). Tras una reprogramación correcta la URL SHALL pasar a `/turno/{token}` con `replaceState`.

#### Scenario: Atrás desde la reprogramación
- **GIVEN** el detalle en `/turno/{token}` y la persona tocó "Reprogramar" (URL `/turno/{token}?accion=reprogramar`)
- **WHEN** usa atrás del navegador
- **THEN** la URL es `/turno/{token}` y se muestra el detalle sin volver a consultar el turno

### Requirement: Normalización de la acción de reprogramar
Al cargar `?accion=reprogramar` directamente, la SPA SHALL consultar el turno y mostrar la reprogramación solo si el servidor la habilita; si no, SHALL normalizar la URL a `/turno/{token}` con `replaceState` y mostrar el detalle. Cualquier otro valor de `accion` SHALL tratarse como el detalle.

#### Scenario: Recarga en la reprogramación de un turno pasado
- **GIVEN** un turno con `reschedulable: false`
- **WHEN** se carga `/turno/{token}?accion=reprogramar`
- **THEN** la URL pasa a `/turno/{token}` sin entrada nueva de historial y se muestra el turno en solo lectura

#### Scenario: Acción desconocida
- **WHEN** se carga `/turno/{token}?accion=borrar`
- **THEN** se muestra el detalle del turno y no se envía ninguna cancelación
