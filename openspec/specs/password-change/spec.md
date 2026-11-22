# password-change Specification

## Purpose
Permite al profesional cambiar su contraseña desde `/cambiar-contrasena`, de forma obligatoria tras el primer ingreso con la credencial de instalación y de forma voluntaria después, cumpliendo la política mínima de RN-AU-03 (US-002).

## Requirements

### Requirement: Formulario de cambio de contraseña
La vista `/cambiar-contrasena` SHALL mostrar un título de nivel 1 y campos "Contraseña actual", "Contraseña nueva" y "Repetí la contraseña nueva" con etiquetas visibles, la política mínima visible junto al campo nuevo y el botón "Guardar contraseña".

#### Scenario: Vista en modo obligatorio
- **GIVEN** una sesión autenticada con `mustChangePassword: true`
- **WHEN** se navega a `/cambiar-contrasena`
- **THEN** se ve el título "Elegí una contraseña nueva", una explicación de que es obligatorio en el primer ingreso, los tres campos (`autocomplete="current-password"` en el actual y `autocomplete="new-password"` en los otros dos), el texto "Mínimo 12 caracteres." y un botón "Salir", sin navegación privada ni opción "Cancelar"

#### Scenario: Vista en modo voluntario
- **GIVEN** una sesión autenticada con `mustChangePassword: false`
- **WHEN** se navega a `/cambiar-contrasena`
- **THEN** se ve el título "Cambiar contraseña", los tres campos y un enlace "Cancelar" que lleva a `/agenda`

### Requirement: Política mínima validada antes de enviar
El formulario SHALL validar en el cliente que los tres campos tengan contenido, que la contraseña nueva tenga entre 12 y 128 caracteres (unidades UTF-16, el mismo criterio que el servidor), no esté compuesta solo por espacios, que sea distinta de la actual y que la confirmación coincida; si algo falla MUST NOT llamar a la API y SHALL marcar cada campo inválido con su mensaje y mover el foco al primero.

#### Scenario: Contraseña nueva corta
- **GIVEN** contraseña actual escrita y la nueva `corta123` repetida igual
- **WHEN** se envía el formulario
- **THEN** no se realiza ninguna petición y el campo nuevo tiene `aria-invalid="true"` con "Usá al menos 12 caracteres."

#### Scenario: Confirmación distinta
- **GIVEN** la nueva `una frase larga segura` y la confirmación `una frase larga segur`
- **WHEN** se envía el formulario
- **THEN** no se realiza ninguna petición y la confirmación muestra "Las contraseñas no coinciden."

#### Scenario: Nueva igual a la actual
- **GIVEN** la actual y la nueva iguales, de 14 caracteres
- **WHEN** se envía el formulario
- **THEN** no se realiza ninguna petición y el campo nuevo muestra "La contraseña nueva tiene que ser distinta de la actual."

#### Scenario: Más de 128 caracteres
- **GIVEN** la nueva y la confirmación iguales de 129 caracteres
- **WHEN** se envía el formulario
- **THEN** no se realiza ninguna petición y el campo nuevo muestra "Usá como máximo 128 caracteres."

#### Scenario: Doce caracteres exactos
- **GIVEN** la nueva y la confirmación iguales de exactamente 12 caracteres, distintas de la actual
- **WHEN** se envía el formulario
- **THEN** se realiza una petición `POST /api/v1/auth/change-password`

### Requirement: Errores informados por el servidor
Si el servidor rechaza el cambio, la vista SHALL mostrar el error en el campo correspondiente con textos propios: contraseña actual incorrecta (401 `INVALID_CREDENTIALS`) en "Contraseña actual" (vaciándolo) y rechazos de validación (400 `VALIDATION_FAILED` con `errors[{field, code}]`) en el campo indicado; MUST NOT tratar el 401 `INVALID_CREDENTIALS` como sesión vencida y SHALL tratar el 401 `UNAUTHENTICATED` como sesión vencida.

#### Scenario: Contraseña actual incorrecta
- **GIVEN** `POST /api/v1/auth/change-password` responde 401 con código `INVALID_CREDENTIALS`
- **WHEN** se envía el formulario válido
- **THEN** el campo "Contraseña actual" está vacío con `aria-invalid="true"` y "La contraseña actual no es correcta.", la URL sigue siendo `/cambiar-contrasena` y la sesión sigue `authenticated`

#### Scenario: Validación rechazada por el servidor
- **GIVEN** `POST /api/v1/auth/change-password` responde 400 con `code: "VALIDATION_FAILED"` y `errors: [{"field":"newPassword","code":"Size"}]`
- **WHEN** se envía el formulario
- **THEN** el campo "Contraseña nueva" muestra "Usá entre 12 y 128 caracteres."

#### Scenario: Sesión vencida durante el cambio
- **GIVEN** `POST /api/v1/auth/change-password` responde 401 con código `UNAUTHENTICATED`
- **WHEN** se envía el formulario
- **THEN** la sesión es `anonymous` con aviso de sesión vencida y la URL es `/login`

#### Scenario: Página desactualizada
- **GIVEN** `POST /api/v1/auth/change-password` responde 403 con código `CSRF_REQUIRED`
- **WHEN** se envía el formulario
- **THEN** se ve "La página quedó desactualizada. Recargala e intentá de nuevo." y los campos conservan su estado

### Requirement: Reingreso tras cambiar la contraseña
Con un cambio exitoso (200 sin cuerpo), el servidor invalida todas las sesiones de la cuenta, incluida la actual; la SPA SHALL pasar la sesión a `anonymous` con aviso de contraseña cambiada, vaciar los campos y navegar, reemplazando la entrada del historial, a `/login` conservando `next` solo si es una ruta privada declarada.

#### Scenario: Cambio obligatorio completado
- **GIVEN** una sesión con `mustChangePassword: true` en `/cambiar-contrasena?next=%2Fpacientes` y el servidor respondiendo 200
- **WHEN** se envía el formulario válido
- **THEN** la sesión es `anonymous` con aviso de contraseña cambiada, la URL es `/login?next=%2Fpacientes` y los campos de contraseña quedaron vacíos

#### Scenario: Cambio voluntario completado
- **GIVEN** una sesión con `mustChangePassword: false` en `/cambiar-contrasena` y el servidor respondiendo 200
- **WHEN** se envía el formulario válido
- **THEN** la URL es `/login` con el aviso de contraseña cambiada

#### Scenario: next inválido descartado
- **GIVEN** `/cambiar-contrasena?next=%2F%2Fevil.example` y el servidor respondiendo 200
- **WHEN** se envía el formulario válido
- **THEN** la URL es `/login`

### Requirement: Salida en modo obligatorio
En modo obligatorio, la única salida alternativa al cambio SHALL ser "Salir", que cierra la sesión como el "Salir" del app shell privado.

#### Scenario: Salir sin cambiar
- **GIVEN** una sesión con `mustChangePassword: true` en `/cambiar-contrasena`
- **WHEN** el usuario elige "Salir"
- **THEN** se invoca `POST /api/v1/auth/logout`, la sesión es `anonymous` y la URL es `/login`

### Requirement: Contraseñas fuera de registros y almacenamiento
La vista MUST NOT escribir ninguna de las contraseñas en la consola, la URL ni `localStorage`/`sessionStorage`, y MUST enviarlas solo en el cuerpo de `POST /api/v1/auth/change-password`.

#### Scenario: Cambio fallido sin rastros
- **GIVEN** un cambio que el servidor rechaza con 400
- **WHEN** termina el intento
- **THEN** ni la consola, ni la URL, ni `localStorage`, ni `sessionStorage` contienen la contraseña actual ni la nueva
