# user-accounts Specification

## Purpose
Define las cuentas de usuario interno (en v1 solo el odontólogo, DENTIST): su pertenencia al consultorio, su rol, la unicidad del email y cómo se almacenan las contraseñas, como base para la autenticación de C-04 (RN-AU-01, RN-AU-03, DD-06).

## Requirements

### Requirement: Usuario pertenece al consultorio
Toda cuenta de usuario interno SHALL referenciar al consultorio de la instalación; la base de datos MUST rechazar una cuenta sin consultorio o con un consultorio inexistente.

#### Scenario: Consultorio inexistente
- **WHEN** se intenta guardar una cuenta que referencia un consultorio que no existe
- **THEN** la base de datos rechaza la operación por violación de clave foránea

### Requirement: Roles cerrados y extensibles
El rol de una cuenta SHALL ser uno de los valores admitidos; en v1 el único valor admitido es `DENTIST`. La base de datos MUST rechazar cualquier otro valor, de modo que sumar un rol (por ejemplo `SECRETARY` en v2) requiera una migración explícita.

#### Scenario: Rol DENTIST aceptado
- **WHEN** se guarda una cuenta con rol `DENTIST`
- **THEN** la cuenta queda persistida con ese rol

#### Scenario: Rol no admitido rechazado
- **WHEN** se intenta guardar una cuenta con rol `SECRETARY`
- **THEN** la base de datos rechaza la operación

### Requirement: Email único sin distinguir mayúsculas
El email de una cuenta SHALL guardarse normalizado (sin espacios al inicio o al final, en minúsculas) y MUST ser único en la instalación sin distinguir mayúsculas de minúsculas.

#### Scenario: Email normalizado
- **WHEN** se crea una cuenta con el email `  Dr.Perez@Ejemplo.COM `
- **THEN** la cuenta queda guardada con el email `dr.perez@ejemplo.com`

#### Scenario: Email duplicado con otras mayúsculas
- **GIVEN** una cuenta con el email `dr@ejemplo.com`
- **WHEN** se intenta guardar otra cuenta con el email `DR@ejemplo.com`
- **THEN** la base de datos rechaza la operación por violación de unicidad

### Requirement: Contraseñas almacenadas con Argon2id
El sistema SHALL almacenar solo un hash Argon2id de cada contraseña, con sal aleatoria por hash; la contraseña en claro MUST NOT persistirse ni registrarse en logs.

#### Scenario: Hash Argon2id con sal
- **WHEN** se calcula el hash de la misma contraseña dos veces
- **THEN** ambos resultados empiezan con `$argon2id$`, son distintos entre sí y ninguno contiene la contraseña en claro

#### Scenario: Verificación de contraseña
- **GIVEN** el hash de una contraseña
- **WHEN** se verifica la contraseña original y luego una contraseña distinta
- **THEN** la primera verificación es exitosa y la segunda falla

### Requirement: Estado de seguridad inicial de la cuenta
Una cuenta nueva SHALL tener cero intentos fallidos, ningún bloqueo vigente, fecha de creación en UTC y ningún último acceso; el indicador de cambio obligatorio de contraseña MUST ser explícito y SHALL estar activo para toda cuenta creada con credenciales de bootstrap.

#### Scenario: Cuenta recién creada
- **WHEN** se crea una cuenta con el reloj fijado en `2026-03-10T12:00:00Z`
- **THEN** la cuenta tiene `failed_attempts = 0`, `locked_until` vacío, `last_login_at` vacío, `created_at = 2026-03-10T12:00:00Z` y `must_change_password = true`

#### Scenario: Intentos fallidos negativos rechazados
- **WHEN** se intenta guardar una cuenta con intentos fallidos negativos
- **THEN** la base de datos rechaza la operación

### Requirement: Identificadores no secuenciales
Las cuentas SHALL identificarse con un UUID generado por la aplicación y ordenable por tiempo (versión 7), de modo que el identificador no revele cuántas cuentas existen.

#### Scenario: Identificador versión 7
- **WHEN** se persiste una cuenta nueva
- **THEN** su identificador es un UUID de versión 7

### Requirement: Política de contraseñas
El sistema SHALL aceptar contraseñas de entre 12 y 128 caracteres para cualquier operación que las establezca, SHALL almacenar únicamente su hash con el algoritmo ya adoptado para las cuentas y MUST NOT registrarlas en claro en la base, en respuestas ni en logs. La misma política SHALL aplicarse a la contraseña inicial del bootstrap y a los cambios posteriores.

#### Scenario: Contraseña dentro de la política
- **GIVEN** una contraseña de 12 caracteres
- **WHEN** se guarda como nueva contraseña de la cuenta
- **THEN** se persiste un hash distinto del valor enviado y la verificación con la contraseña original es exitosa

#### Scenario: Contraseña demasiado corta
- **GIVEN** una contraseña de 11 caracteres
- **WHEN** se intenta guardar
- **THEN** la operación es rechazada con un error de validación que identifica el campo sin mostrar el valor

#### Scenario: Contraseña sin valor en claro
- **GIVEN** una contraseña válida
- **WHEN** se guarda y se consulta la cuenta
- **THEN** ni la fila de la cuenta ni los mensajes de log contienen la contraseña en claro

### Requirement: Ciclo de vida de los intentos fallidos
El estado de seguridad de una cuenta SHALL evolucionar de forma determinista ante los intentos de autenticación: cada fallo incrementa `failed_attempts`; al alcanzar umbrales sucesivos se fija `locked_until` con duraciones crecientes y techo máximo; un éxito reinicia el contador, limpia `locked_until` y actualiza `last_login_at`. Ninguna cuenta SHALL quedar bloqueada sin fecha de finalización.

#### Scenario: Fallo incrementa el contador
- **GIVEN** una cuenta con cero intentos fallidos
- **WHEN** falla un intento de autenticación
- **THEN** `failed_attempts` aumenta en uno y `locked_until` permanece vacío si no se alcanzó el umbral

#### Scenario: Umbral alcanzado
- **GIVEN** una cuenta en el umbral de bloqueo
- **WHEN** falla un intento
- **THEN** `locked_until` toma un instante futuro calculado con la hora del servidor y la cuenta rechaza nuevos accesos hasta ese instante

#### Scenario: Éxito restablece el estado
- **GIVEN** una cuenta con intentos fallidos y un bloqueo ya vencido
- **WHEN** se autentica correctamente
- **THEN** `failed_attempts` es cero, `locked_until` queda vacío y `last_login_at` es el instante UTC del servidor

### Requirement: Cambio de contraseña de la propia cuenta
El sistema SHALL permitir a una cuenta autenticada cambiar su propia contraseña exigiendo la actual, actualizando el hash y limpiando el indicador de cambio obligatorio. El cambio SHALL invalidar las sesiones previamente establecidas para esa cuenta. La cuenta NO SHALL poder cambiar la contraseña de otra cuenta ni crear cuentas nuevas (RN-AU-01).

#### Scenario: Cambio correcto
- **GIVEN** una cuenta con cambio obligatorio pendiente y la contraseña actual correcta
- **WHEN** se guarda una nueva contraseña válida
- **THEN** el hash cambia, el indicador de cambio obligatorio se limpia y las sesiones anteriores de la cuenta dejan de ser válidas

#### Scenario: Sin cambio de otra cuenta
- **GIVEN** una cuenta autenticada
- **WHEN** intenta modificar la contraseña de otra cuenta
- **THEN** la operación es rechazada y no existe endpoints ni permiso para crear cuentas
