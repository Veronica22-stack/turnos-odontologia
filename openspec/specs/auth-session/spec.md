# auth-session Specification

## Purpose

La autenticación del odontólogo y el mantenimiento de su sesión de servidor: ingreso y salida, cookie segura, protección CSRF, expiración por inactividad, bloqueo progresivo, cambio obligatorio de contraseña y evidencia de auditoría, sin revelar si una cuenta existe.

## Requirements

### Requirement: Inicio de sesión con credenciales
El sistema SHALL aceptar `POST /api/v1/auth/login` con email y contraseña, verificar la contraseña contra el hash almacenado y, si es correcta, crear una sesión de servidor para el odontólogo con rol `DENTIST`. En caso contrario SHALL responder `401` con el mismo cuerpo de error para credenciales inválidas, cuenta bloqueada y cuenta inexistente. El email SHALL normalizarse (sin espacios y en minúsculas) antes de buscar la cuenta.

#### Scenario: Login correcto
- **GIVEN** una cuenta `DENTIST` con contraseña válida y sin bloqueo vigente
- **WHEN** se envía `POST /api/v1/auth/login` con ese email y contraseña
- **THEN** la respuesta es `200`, se crea una sesión de servidor asociada al identificador de la cuenta y se entrega la cookie de sesión con los atributos de seguridad exigidos

#### Scenario: Contraseña incorrecta
- **GIVEN** una cuenta `DENTIST` existente
- **WHEN** se envía su email con una contraseña distinta
- **THEN** la respuesta es `401` con `application/problem+json`, `code = INVALID_CREDENTIALS` y un mensaje genérico que no indica si el email existe

#### Scenario: Email inexistente
- **WHEN** se envía `POST /api/v1/auth/login` con un email que no tiene cuenta
- **THEN** la respuesta es `401` con el mismo `code`, mensaje, tiempo de respuesta y forma de error que una contraseña incorrecta

#### Scenario: Login con cuenta bloqueada
- **GIVEN** una cuenta con bloqueo temporal vigente
- **WHEN** se envían credenciales correctas
- **THEN** la respuesta es `401` con el error genérico de credenciales inválidas que sugiere reintentar más tarde, sin crear sesión ni revelar el estado de la cuenta

### Requirement: No enumeración de cuentas
El sistema MUST NOT revelar, en la respuesta, el código de estado, el tiempo de respuesta ni el rastro de auditoría, si un email enviado al login corresponde a una cuenta existente. Para un email inexistente SHALL realizarse la misma verificación criptográfica de referencia que para una cuenta real y SHALL registrarse el mismo evento de fallo con usuario nulo.

#### Scenario: Respuesta indistinguible
- **GIVEN** un email inexistente y un email existente con contraseña incorrecta
- **WHEN** se intenta login con cada uno
- **THEN** ambos devuelven `401` con el mismo `code` y mensaje, y ningún cuerpo contiene el email enviado

#### Scenario: Evento de fallo anónimo
- **GIVEN** un login fallido con un email sin cuenta
- **WHEN** se registra el evento de auditoría
- **THEN** queda un evento `LOGIN_FAIL` con identificador de usuario nulo y sin el email en claro en ningún campo ni mensaje

### Requirement: Bloqueo progresivo por intentos fallidos
El sistema SHALL incrementar el contador de intentos fallidos de la cuenta ante cada credencial inválida y SHALL aplicar bloqueos temporales de duración creciente al superar umbrales sucesivos, usando la fecha y hora del reloj del servidor. Un inicio de sesión correcto SHALL reiniciar el contador, limpiar el bloqueo y actualizar la fecha del último acceso. La cuenta MUST NOT quedar bloqueada permanentemente.

#### Scenario: Quinto intento fallido
- **GIVEN** una cuenta con cuatro intentos fallidos consecutivos y sin bloqueo
- **WHEN** falla un quinto intento
- **THEN** el contador se incrementa, la cuenta queda bloqueada durante el primer periodo temporal y la respuesta sigue siendo el error genérico

#### Scenario: Escalada del bloqueo
- **GIVEN** una cuenta que ya cumplió un primer bloqueo y vuelve a acumular intentos fallidos hasta el siguiente umbral
- **WHEN** se alcanza ese umbral
- **THEN** el nuevo bloqueo dura más que el anterior, con un techo máximo definido

#### Scenario: Login correcto reinicia el estado
- **GIVEN** una cuenta con intentos fallidos y un bloqueo ya vencido
- **WHEN** se autentica correctamente
- **THEN** el contador vuelve a cero, el bloqueo queda vacío y la fecha de último acceso es el instante UTC del servidor

#### Scenario: Intento durante el bloqueo
- **GIVEN** una cuenta con bloqueo vigente
- **WHEN** se intenta login con contraseña correcta o incorrecta
- **THEN** no se crea sesión, no se modifica el contador y la respuesta es el error genérico

### Requirement: Sesión de servidor con cookie segura
Toda sesión autenticada SHALL residir en el servidor y SHALL identificarse en el cliente mediante una cookie de sesión con `HttpOnly` y `SameSite=Strict`; el atributo `Secure` SHALL estar activo en entornos HTTPS y desactivarse solo para desarrollo local HTTP. La cookie MUST NOT contener datos personales, roles, permisos ni información de la cuenta. El identificador de sesión SHALL rotarse al autenticar para evitar fijación de sesión.

#### Scenario: Atributos de la cookie
- **GIVEN** un login correcto en un entorno HTTPS
- **WHEN** se inspecciona la cabecera `Set-Cookie`
- **THEN** la cookie de sesión incluye `HttpOnly`, `Secure`, `SameSite=Strict` y un valor opaco sin datos de la cuenta

#### Scenario: Sesión solo en el servidor
- **GIVEN** una sesión iniciada
- **WHEN** se inspecciona la cookie y el estado del cliente
- **THEN** el único dato de sesión en el cliente es el identificador opaco; el rol, el usuario y el vencimiento residen en el servidor

#### Scenario: Rotación en login
- **GIVEN** un identificador de sesión anónimo previo al login
- **WHEN** el login es correcto
- **THEN** la sesión autenticada usa un identificador distinto

### Requirement: Expiración por inactividad
Cada sesión autenticada SHALL tener un instante de vencimiento calculado a partir del reloj del servidor más un periodo configurable con `AUTH_SESSION_TIMEOUT_MIN` (valor por defecto: 30 minutos). Toda solicitud autenticada SHALL renovar ese instante. Una sesión vencida MUST NOT autorizar solicitudes y SHALL recibir `401` con el formato de error de la API.

#### Scenario: Servicio dentro del plazo
- **GIVEN** una sesión iniciada hace menos del tiempo configurado
- **WHEN** se consulta `GET /api/v1/auth/me`
- **THEN** la respuesta es `200` y el vencimiento de la sesión se extiende respecto del instante original

#### Scenario: Sesión expirada
- **GIVEN** una sesión cuyo instante de vencimiento es anterior al instante actual del servidor
- **WHEN** se usa en cualquier ruta protegida
- **THEN** la respuesta es `401` con `code = UNAUTHENTICATED`, la sesión se considera inválida y no se renueva

#### Scenario: Configuración del plazo
- **GIVEN** `AUTH_SESSION_TIMEOUT_MIN = 5`
- **WHEN** se inicia sesión
- **THEN** el vencimiento queda fijado cinco minutos después del instante del reloj del servidor

### Requirement: Protección CSRF
El sistema SHALL exigir una token CSRF válida en toda solicitud autenticada que cambie estado (`POST`, `PUT`, `PATCH`, `DELETE`), emitida junto con la sesión y verificada de forma que un sitio de otro origen no pueda reproducirla. `POST /api/v1/auth/login` y las solicitudes de solo lectura MUST NOT exigir CSRF. Una token ausente o inválida SHALL rechazarse con `403` y un código estable, sin revelar datos de la cuenta.

#### Scenario: Solicitud con token válida
- **GIVEN** una sesión autenticada y su token CSRF vigente
- **WHEN** se envía `POST /api/v1/auth/change-password` con esa token
- **THEN** la solicitud se procesa normalmente

#### Scenario: Solicitud sin token
- **GIVEN** una sesión autenticada
- **WHEN** se envía `POST /api/v1/auth/logout` sin la token CSRF
- **THEN** la respuesta es `403` con `code = CSRF_REQUIRED` y la sesión permanece activa

#### Scenario: Login sin sesión previa
- **WHEN** se envía `POST /api/v1/auth/login` sin token CSRF
- **THEN** la solicitud no se rechaza por CSRF

### Requirement: Consulta y cierre de sesión
El sistema SHALL exponer `GET /api/v1/auth/me` para informar el identificador de la cuenta, el rol, la necesidad de cambiar la contraseña y el vencimiento de la sesión, sin datos personales como email completo, DNI o teléfono. `POST /api/v1/auth/logout` SHALL invalidar la sesión actual y eliminar la cookie en el cliente; MUST NOT fallar si no hay sesión activa.

#### Scenario: Datos de la sesión actual
- **GIVEN** una sesión autenticada de `DENTIST`
- **WHEN** se llama a `GET /api/v1/auth/me`
- **THEN** la respuesta contiene el identificador de la cuenta, el rol, el indicador de cambio de contraseña y el instante de vencimiento en UTC, y no contiene DNI, teléfono ni dirección

#### Scenario: Cierre de sesión
- **GIVEN** una sesión autenticada
- **WHEN** se envía `POST /api/v1/auth/logout` con CSRF válida
- **THEN** la sesión queda invalidada en el servidor, la cookie se elimina y cualquier solicitud posterior con ese identificador recibe `401`

#### Scenario: Logout sin sesión
- **WHEN** se envía `POST /api/v1/auth/logout` sin sesión
- **THEN** la respuesta es `204` y no se crea ni modifica ninguna sesión

### Requirement: Cambio de contraseña y obligatoriedad en el primer ingreso
El sistema SHALL exigir la contraseña actual y una nueva contraseña de entre 12 y 128 caracteres en `POST /api/v1/auth/change-password`, y SHALL actualizar el hash sin almacenar la contraseña en claro. Toda cuenta creada con credenciales de bootstrap SHALL iniciar con cambio obligatorio: mientras el indicador esté activo, la sesión solo podrá usar `/api/v1/auth/me`, `/api/v1/auth/change-password` y `/api/v1/auth/logout`; cualquier otra ruta SHALL devolver `403` con `code = PASSWORD_CHANGE_REQUIRED`. Tras el cambio SHALL limpiarse el indicador e invalidarse las sesiones previas de esa cuenta.

#### Scenario: Contraseña actual incorrecta
- **GIVEN** una sesión autenticada
- **WHEN** se envía una contraseña actual incorrecta junto con una nueva válida
- **THEN** la respuesta es `401` con código genérico, la contraseña no cambia y el mensaje no revela cuál de las dos contraseñas fue rechazada

#### Scenario: Nueva contraseña por debajo del mínimo
- **GIVEN** una sesión autenticada y la contraseña actual correcta
- **WHEN** se envía una nueva contraseña de 8 caracteres
- **THEN** la respuesta es `400` con `code = VALIDATION_FAILED`, el campo se identifica sin mostrar su valor y la contraseña no cambia

#### Scenario: Primer login con credencial de bootstrap
- **GIVEN** una cuenta con `must_change_password = true`
- **WHEN** inicia sesión y solicita una ruta privada que no sea de cambio de contraseña
- **THEN** recibe `403` con `code = PASSWORD_CHANGE_REQUIRED` hasta completar el cambio

#### Scenario: Cambio exitoso
- **GIVEN** una cuenta con cambio obligatorio pendiente
- **WHEN** envía la contraseña actual y una nueva de al menos 12 caracteres
- **THEN** se guarda el nuevo hash, el indicador se limpia, las sesiones anteriores dejan de ser válidas y la sesión actual debe reautenticarse

### Requirement: Límite de tasa en el login
El sistema SHALL limitar los intentos de login por combinación de dirección IP y email normalizado, devolviendo `429` con `code = RATE_LIMITED` y una cabecera `Retry-After` cuando se exceda el límite. El mecanismo SHALL ser reutilizable por otros endpoints públicos y MUST NOT incluir el email ni la IP en el cuerpo de la respuesta ni en logs.

#### Scenario: Límite excedido
- **GIVEN** una combinación IP+email que alcanzó el límite de intentos por minuto
- **WHEN** se intenta otro login
- **THEN** la respuesta es `429` con `Retry-After`, `code = RATE_LIMITED` y sin el email ni la IP en el cuerpo

#### Scenario: Otra combinación no afectada
- **GIVEN** una combinación IP+email agotada
- **WHEN** se intenta login desde otra IP o con otro email
- **THEN** la solicitud se evalúa normalmente y no hereda el bloqueo de la primera combinación

#### Scenario: Dirección IP de confianza
- **GIVEN** una petición detrás de un proxy no configurado como de confianza
- **WHEN** se calcula la clave de limitación
- **THEN** se usa la dirección de conexión directa y no el valor de una cabecera reenviada suministrada por el cliente

### Requirement: Auditoría de autenticación sin datos sensibles
Cada intento de login SHALL registrar en el rastro de auditoría un evento `LOGIN_OK` cuando la autenticación y el cambio obligatorio (si aplica) se completan, o `LOGIN_FAIL` cuando falla, con identificador de usuario (nulo si no existe la cuenta), dirección IP y agente de usuario. El evento MUST NOT contener contraseñas, tokens de sesión o CSRF, email, DNI, teléfono, nombre completo ni contenido clínico, ni en los datos persistidos ni en los mensajes de log.

#### Scenario: Login correcto auditado
- **GIVEN** un login correcto
- **WHEN** se consulta el rastro de auditoría
- **THEN** existe una fila `LOGIN_OK` con el identificador de la cuenta, la IP y el agente de usuario, con instante UTC generado por el servidor

#### Scenario: Fallo auditado
- **GIVEN** un login fallido por contraseña incorrecta
- **WHEN** se consulta el rastro
- **THEN** existe una fila `LOGIN_FAIL` con el identificador de la cuenta y un código de motivo genérico, sin la contraseña enviada

#### Scenario: Sin filtración en auditoría ni logs
- **GIVEN** un intento de login con email, contraseña y un agente de usuario que contiene datos personales
- **WHEN** se procesa y se registra
- **THEN** ni la fila de auditoría ni los logs contienen esos valores en claro; solo se conservan identificadores y metadatos operacionales autorizados

#### Scenario: Cierre de sesión sin acción nueva
- **GIVEN** un logout correcto
- **WHEN** se consulta el rastro de auditoría
- **THEN** no se exige una acción de auditoría nueva fuera del catálogo vigente para registrarlo
