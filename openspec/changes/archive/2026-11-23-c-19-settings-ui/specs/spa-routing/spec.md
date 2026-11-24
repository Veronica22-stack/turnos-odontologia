# Spec Delta

## ADDED Requirements

### Requirement: Vista operativa de configuración
La ruta privada `/configuracion` SHALL mostrar la vista operativa de configuración del consultorio en lugar del contenido de marcador de posición, manteniendo el guard de sesión, el redireccionamiento por cambio de contraseña pendiente y el layout privado ya definidos para las rutas privadas.

#### Scenario: Configuración con sesión autenticada
- **GIVEN** una sesión autenticada sin cambio de contraseña pendiente
- **WHEN** se navega a `/configuracion`
- **THEN** se muestra la vista con las secciones de datos del consultorio, horarios, prestaciones y bloqueos, y «Configuración» aparece como sección actual del layout privado

#### Scenario: Configuración sin sesión
- **GIVEN** la sesión en estado anónimo
- **WHEN** se navega a `/configuracion`
- **THEN** la URL pasa a ser `/login?next=%2Fconfiguracion` y no se muestra contenido de configuración

#### Scenario: Configuración con cambio de contraseña pendiente
- **GIVEN** una sesión autenticada con `mustChangePassword = true`
- **WHEN** se navega a `/configuracion`
- **THEN** la URL pasa a ser `/cambiar-contrasena?next=%2Fconfiguracion` y no se muestra contenido de configuración

#### Scenario: Fallo de carga de datos
- **GIVEN** una sesión autenticada y respuestas fallidas de los endpoints de configuración
- **WHEN** se navega a `/configuracion`
- **THEN** se muestra el layout privado con las secciones en estado de error y opciones de reintento, sin volver al marcador de posición
