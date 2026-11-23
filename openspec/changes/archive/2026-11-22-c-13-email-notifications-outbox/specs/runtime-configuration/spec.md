# Spec Delta

## ADDED Requirements

### Requirement: Proveedor de email obligatorio y validado al arrancar
La aplicación SHALL exigir `MAIL_PROVIDER` con valor `log` o `smtp` y negarse a arrancar si falta o es otro valor, nombrando solo la variable. El perfil `dev` SHALL aportar `log` por defecto. Con `smtp`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM`, `MAIL_USER` y `MAIL_PASSWORD` SHALL ser obligatorias. Con `log` fuera del perfil `dev`, el arranque SHALL registrar una advertencia de que los emails no se envían.

#### Scenario: Proveedor ausente fuera de dev
- **GIVEN** `MAIL_PROVIDER` no definida y el perfil `dev` inactivo
- **WHEN** se inicia la aplicación
- **THEN** el arranque falla y el error menciona `MAIL_PROVIDER`

#### Scenario: Proveedor por defecto en dev
- **GIVEN** el perfil `dev` activo y `MAIL_PROVIDER` no definida
- **WHEN** se inicia la aplicación
- **THEN** el arranque es exitoso y los emails se escriben en el log sin enviarse

#### Scenario: Valor inválido
- **GIVEN** `MAIL_PROVIDER = sendgrid`
- **WHEN** se inicia la aplicación
- **THEN** el arranque falla y el error menciona `MAIL_PROVIDER` sin mostrar otros valores

#### Scenario: SMTP sin contraseña
- **GIVEN** `MAIL_PROVIDER = smtp` y `MAIL_PASSWORD` no definida
- **WHEN** se inicia la aplicación
- **THEN** el arranque falla, el error menciona `MAIL_PASSWORD` y no muestra el valor de `MAIL_USER`

#### Scenario: Envío simulado fuera de dev
- **GIVEN** `MAIL_PROVIDER = log` y el perfil `dev` inactivo
- **WHEN** se inicia la aplicación
- **THEN** el arranque es exitoso y el log registra una advertencia de que los emails no se envían

### Requirement: Parámetros del despacho de notificaciones configurables
La aplicación SHALL leer de variables de entorno, con valores por defecto, el intervalo del despacho periódico (`NOTIFICATION_POLL_INTERVAL`, 5 minutos), el máximo de intentos (`NOTIFICATION_MAX_ATTEMPTS`, 5) y la activación del despacho (`NOTIFICATION_DISPATCH_ENABLED`, activo), y SHALL negarse a arrancar ante un intervalo no positivo o un máximo de intentos menor que 1. La anticipación del recordatorio MUST tomarse del consultorio, no de una variable de entorno.

#### Scenario: Valores por defecto
- **GIVEN** ninguna de las variables del despacho definida
- **WHEN** se inicia la aplicación
- **THEN** el despacho corre cada 5 minutos con un máximo de 5 intentos

#### Scenario: Máximo de intentos inválido
- **GIVEN** `NOTIFICATION_MAX_ATTEMPTS = 0`
- **WHEN** se inicia la aplicación
- **THEN** el arranque falla y el error menciona `NOTIFICATION_MAX_ATTEMPTS`

#### Scenario: Despacho desactivado
- **GIVEN** `NOTIFICATION_DISPATCH_ENABLED = false`
- **WHEN** se confirma un turno
- **THEN** la notificación se encola pero no se envía hasta que el despacho se active
