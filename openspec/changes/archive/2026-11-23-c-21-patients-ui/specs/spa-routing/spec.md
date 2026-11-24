# Spec Delta: spa-routing

## ADDED Requirements

### Requirement: Vista operativa de pacientes
La ruta privada `/pacientes` SHALL mostrar la vista operativa de gestión de pacientes en lugar del contenido de marcador de posición, manteniendo el guard de sesión, el redireccionamiento por cambio de contraseña pendiente, el título y el layout privado ya definidos. La ruta SHALL permanecer como `/pacientes`: la búsqueda y la selección de una ficha MUST NOT agregar a la URL datos personales del paciente ni su identificador.

#### Scenario: Pacientes con sesión autenticada
- **GIVEN** una sesión autenticada sin cambio de contraseña pendiente
- **WHEN** se navega a `/pacientes`
- **THEN** se muestra la vista de búsqueda y listado de pacientes dentro del layout privado, «Pacientes» aparece como sección actual, el título contiene «Pacientes» y la marca, y el foco está en el encabezado de la vista

#### Scenario: Pacientes sin sesión
- **GIVEN** la sesión en estado anónimo
- **WHEN** se navega a `/pacientes`
- **THEN** la URL pasa a ser `/login?next=%2Fpacientes` y no se muestra contenido de pacientes

#### Scenario: Pacientes con cambio de contraseña pendiente
- **GIVEN** una sesión autenticada con `mustChangePassword = true`
- **WHEN** se navega a `/pacientes`
- **THEN** la URL pasa a ser `/cambiar-contrasena?next=%2Fpacientes` y no se muestra contenido de pacientes

#### Scenario: Fallo de carga de pacientes
- **GIVEN** una sesión autenticada y una respuesta fallida de `GET /api/v1/patients`
- **WHEN** se navega a `/pacientes`
- **THEN** se muestra el layout privado con el aviso de error y la opción de reintento de la vista de pacientes, sin volver al marcador de posición

#### Scenario: Búsqueda y ficha sin datos en la URL
- **GIVEN** una búsqueda por DNI y un paciente seleccionado para ver su ficha
- **WHEN** se inspecciona la URL tras buscar y abrir la ficha
- **THEN** la ruta sigue siendo `/pacientes` y no contiene el término buscado, el DNI, el nombre ni el identificador del paciente
