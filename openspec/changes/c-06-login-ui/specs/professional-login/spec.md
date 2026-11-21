# Spec Delta: professional-login

## Purpose

Permite al profesional ingresar a la SPA con email y contraseña desde `/login`, con mensajes que no revelan si una cuenta existe, aviso de bloqueo temporal y vuelta a la ruta privada que intentaba abrir (US-001, RN-AU-03).

## ADDED Requirements

### Requirement: Formulario de ingreso
La vista `/login` SHALL mostrar, dentro del app shell público, un formulario con un título de nivel 1, un campo de email y un campo de contraseña con etiquetas visibles y autocompletado de credenciales, y un botón "Ingresar".

#### Scenario: Vista inicial
- **GIVEN** una sesión anónima sin aviso
- **WHEN** se navega a `/login`
- **THEN** se ve el título "Ingresar", un campo "Email" con `autocomplete="username"`, un campo "Contraseña" de tipo `password` con `autocomplete="current-password"`, el botón "Ingresar" habilitado y ningún mensaje de error

#### Scenario: Mostrar contraseña
- **GIVEN** el formulario de ingreso con una contraseña escrita
- **WHEN** el usuario activa "Mostrar contraseña"
- **THEN** el campo pasa a tipo `text`, el control indica `aria-pressed="true"` y al desactivarlo vuelve a tipo `password`

### Requirement: Validación local antes de enviar
El formulario SHALL validar en el cliente que el email no esté vacío y tenga formato de email y que la contraseña no esté vacía; si algo falla MUST NOT llamar a la API y SHALL marcar cada campo inválido con su mensaje asociado y mover el foco al primero.

#### Scenario: Campos vacíos
- **WHEN** se envía el formulario sin email ni contraseña
- **THEN** no se realiza ninguna petición, ambos campos tienen `aria-invalid="true"` con un mensaje vinculado por `aria-describedby` y el foco queda en el campo email

#### Scenario: Email con formato inválido
- **GIVEN** el email `romero@` y una contraseña
- **WHEN** se envía el formulario
- **THEN** no se realiza ninguna petición y el campo email muestra "Ingresá un email válido."

### Requirement: Envío único y estado de carga
Mientras el intento de ingreso está en curso, el formulario SHALL indicar el estado de carga en el botón ("Ingresando…", `aria-busy="true"`) y MUST ignorar nuevos envíos.

#### Scenario: Doble envío
- **GIVEN** un intento de ingreso con la respuesta todavía pendiente
- **WHEN** el usuario envía el formulario otra vez
- **THEN** se realizó una sola petición `POST /api/v1/auth/login` y el botón muestra "Ingresando…"

### Requirement: Credenciales inválidas con mensaje genérico
Si el servidor rechaza las credenciales, la vista SHALL mostrar el mismo mensaje genérico "Email o contraseña incorrectos." exista o no el email, vaciar el campo contraseña conservando el email y mover el foco a la contraseña; MUST NOT mostrar textos del servidor.

#### Scenario: Contraseña incorrecta
- **GIVEN** `POST /api/v1/auth/login` responde 401 con código `AUTH_INVALID_CREDENTIALS`
- **WHEN** se envía el formulario
- **THEN** se ve un mensaje con `role="alert"` "Email o contraseña incorrectos.", la contraseña está vacía, el email se conserva y la sesión sigue anónima sin aviso de sesión vencida

#### Scenario: Email inexistente
- **GIVEN** `POST /api/v1/auth/login` responde 401 con código `AUTH_INVALID_CREDENTIALS` para un email que no existe
- **WHEN** se envía el formulario
- **THEN** el mensaje visible es idéntico al del caso de contraseña incorrecta

### Requirement: Bloqueo temporal con mensaje genérico
Si el servidor informa cuenta bloqueada o demasiados intentos, la vista SHALL mostrar un mensaje genérico que indique reintentar más tarde, con los minutos aproximados de espera cuando el servidor los informa, sin confirmar que la cuenta existe.

#### Scenario: Bloqueo con tiempo informado
- **GIVEN** `POST /api/v1/auth/login` responde 423 con código `AUTH_ACCOUNT_LOCKED` y `Retry-After: 840`
- **WHEN** se envía el formulario
- **THEN** se ve un mensaje con `role="alert"` que dice que el ingreso está bloqueado temporalmente por seguridad y que se puede reintentar en unos 14 minutos

#### Scenario: Demasiados intentos sin tiempo informado
- **GIVEN** `POST /api/v1/auth/login` responde 429 sin cabecera `Retry-After`
- **WHEN** se envía el formulario
- **THEN** se ve el mismo mensaje de bloqueo temporal indicando reintentar en unos minutos

### Requirement: Error de conexión o inesperado
Ante falla de red, timeout o una respuesta de error no prevista, la vista SHALL mostrar un mensaje genérico de conexión, conservar el email y permitir reintentar.

#### Scenario: Backend caído
- **GIVEN** `POST /api/v1/auth/login` falla por red
- **WHEN** se envía el formulario
- **THEN** se ve "No pudimos conectar con el servidor. Revisá tu conexión e intentá de nuevo." y el botón "Ingresar" vuelve a estar habilitado

### Requirement: Destino tras el ingreso
Con un ingreso exitoso, la SPA SHALL establecer la sesión autenticada con los datos devueltos y navegar, reemplazando la entrada del historial, al parámetro `next` si es una ruta privada declarada o a `/agenda` en otro caso.

#### Scenario: Ingreso con destino original
- **GIVEN** la URL `/login?next=%2Fpacientes` y `POST /api/v1/auth/login` respondiendo 200 con `mustChangePassword: false`
- **WHEN** se envía el formulario con credenciales válidas
- **THEN** la URL es `/pacientes`, la sesión es `authenticated` y volver atrás no muestra el formulario de ingreso

#### Scenario: Destino externo ignorado
- **GIVEN** la URL `/login?next=https%3A%2F%2Fevil.example` y un ingreso exitoso
- **WHEN** se envía el formulario
- **THEN** la URL es `/agenda`

#### Scenario: Primer ingreso con cambio obligatorio
- **GIVEN** la URL `/login?next=%2Fpacientes` y `POST /api/v1/auth/login` respondiendo 200 con `mustChangePassword: true`
- **WHEN** se envía el formulario con credenciales válidas
- **THEN** la URL es `/cambiar-contrasena?next=%2Fpacientes`

### Requirement: Aviso de sesión vencida o sin conexión en el ingreso
Si la sesión llegó a `/login` por vencimiento o por falta de conexión, la vista SHALL mostrar el aviso correspondiente encima del formulario; el aviso MUST NOT reemplazar al formulario.

#### Scenario: Llegada por vencimiento
- **GIVEN** la sesión anónima con aviso de sesión vencida
- **WHEN** se muestra `/login?next=%2Fagenda`
- **THEN** se ve "Tu sesión venció. Ingresá de nuevo." con `role="status"` y el formulario de ingreso habilitado

### Requirement: Credenciales fuera de registros y almacenamiento
La vista de ingreso MUST NOT escribir el email ni la contraseña en la consola, en la URL ni en `localStorage`/`sessionStorage`, y MUST enviarlos solo en el cuerpo de `POST /api/v1/auth/login`.

#### Scenario: Intento fallido sin rastros
- **GIVEN** un intento de ingreso con `romero@ejemplo.com` que falla con 401
- **WHEN** termina el intento
- **THEN** ni la consola, ni la URL, ni `localStorage`, ni `sessionStorage` contienen `romero@ejemplo.com` ni la contraseña
