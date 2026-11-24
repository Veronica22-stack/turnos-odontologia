# Spec Delta: spa-routing

## ADDED Requirements

### Requirement: Vista privada de historia clínica
La ruta privada `/historia-clinica` SHALL mostrar la vista de historia clínica por paciente dentro del layout privado, con los mismos guards de sesión y de cambio de contraseña pendiente que las demás rutas privadas. SHALL actualizar el título con el nombre de la vista y la marca, marcar «Historia clínica» como sección actual y mover el foco al encabezado principal.

#### Scenario: Historia clínica con sesión autenticada
- **GIVEN** una sesión autenticada sin cambio de contraseña pendiente
- **WHEN** se navega a `/historia-clinica`
- **THEN** se muestra la vista de historia clínica dentro del layout privado, «Historia clínica» aparece como sección actual, el título contiene «Historia clínica» y la marca, y el foco está en el encabezado de la vista

#### Scenario: Historia clínica sin sesión
- **GIVEN** la sesión en estado anónimo
- **WHEN** se navega a `/historia-clinica`
- **THEN** la URL pasa a ser `/login?next=%2Fhistoria-clinica` y no se muestra contenido clínico

#### Scenario: Historia clínica con cambio de contraseña pendiente
- **GIVEN** una sesión autenticada con `mustChangePassword = true`
- **WHEN** se navega a `/historia-clinica`
- **THEN** la URL pasa a ser `/cambiar-contrasena?next=%2Fhistoria-clinica` y no se muestra contenido clínico

#### Scenario: Fallo de carga de la historia clínica
- **GIVEN** una sesión autenticada y una respuesta fallida de los endpoints de historia clínica
- **WHEN** se navega a `/historia-clinica`
- **THEN** se muestra el layout privado con el aviso de error y la opción de reintento de la vista, sin volver a un marcador de posición

### Requirement: Ruta de historia clínica sin datos clínicos
La ruta `/historia-clinica` SHALL permanecer sin parámetros: la selección del paciente, su identificador y cualquier dato clínico MUST NOT agregarse a la URL, al título del documento, a la consola ni al destino `next`.

#### Scenario: Navegación sin identificadores ni datos clínicos
- **GIVEN** un paciente seleccionado con anamnesis, odontograma y evoluciones cargadas
- **WHEN** se inspeccionan la URL, el título del documento, la consola y el parámetro `next` tras navegar
- **THEN** la ruta sigue siendo `/historia-clinica` y ninguno contiene el identificador del paciente, su DNI, su nombre ni contenido clínico

#### Scenario: Destino de reingreso sin datos
- **GIVEN** una sesión vencida durante la carga de la historia clínica
- **WHEN** se redirige a `/login` conservando el destino
- **THEN** el destino es exactamente `/historia-clinica`, sin identificador de paciente ni contenido clínico
