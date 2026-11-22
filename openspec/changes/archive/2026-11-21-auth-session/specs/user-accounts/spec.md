# Spec Delta

## MODIFIED Requirements

### Requirement: Estado de seguridad inicial de la cuenta
Una cuenta nueva SHALL tener cero intentos fallidos, ningún bloqueo vigente, fecha de creación en UTC y ningún último acceso; el indicador de cambio obligatorio de contraseña MUST ser explícito y SHALL estar activo para toda cuenta creada con credenciales de bootstrap.

#### Scenario: Cuenta recién creada
- **WHEN** se crea una cuenta con el reloj fijado en `2026-03-10T12:00:00Z`
- **THEN** la cuenta tiene `failed_attempts = 0`, `locked_until` vacío, `last_login_at` vacío, `created_at = 2026-03-10T12:00:00Z` y `must_change_password = true`

#### Scenario: Intentos fallidos negativos rechazados
- **WHEN** se intenta guardar una cuenta con intentos fallidos negativos
- **THEN** la base de datos rechaza la operación

## ADDED Requirements

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
