# patients-ui Specification

## Purpose

Define el comportamiento observable de la pantalla de gestión de fichas de pacientes del consultorio: búsqueda, consulta, edición, baja lógica y señalización de datos pendientes de revisión, con estados vacíos y de error accesibles.

## Requirements

### Requirement: Búsqueda y listado de pacientes activos
La vista `/pacientes` SHALL consultar `GET /api/v1/patients` y mostrar los pacientes activos devueltos, con búsqueda por coincidencia parcial en nombre, apellido, DNI o teléfono mediante el parámetro `q`. La lista SHALL mostrar como mínimo nombre completo, DNI y teléfono de cada paciente, y SHALL excluir a los dados de baja porque el servidor ya filtra por `active = true`.

#### Scenario: Carga inicial del listado
- **GIVEN** pacientes activos registrados en el consultorio y una sesión autenticada de odontólogo
- **WHEN** se navega a `/pacientes`
- **THEN** se muestra el listado ordenado con nombre, DNI y teléfono de esos pacientes, sin incluir ninguno dado de baja

#### Scenario: Búsqueda por nombre, DNI o teléfono
- **GIVEN** el listado cargado con varios pacientes
- **WHEN** se ingresa un término de búsqueda y se envía
- **THEN** se realiza una consulta con `q` codificado y se muestran solo los pacientes que coinciden parcialmente con el término en nombre, apellido, DNI o teléfono

#### Scenario: Búsqueda sin resultados
- **GIVEN** un término que no coincide con ningún paciente activo
- **WHEN** se envía la búsqueda
- **THEN** se muestra un estado vacío con un mensaje propio que sugiere buscar por DNI o teléfono, sin una tabla vacía ni un error

#### Scenario: Sin pacientes registrados
- **GIVEN** una respuesta de listado vacía sin término de búsqueda
- **WHEN** se carga la vista
- **THEN** se muestra un estado vacío propio para un consultorio sin pacientes, distinto del de búsqueda sin coincidencias

### Requirement: Estados de carga y error del listado
La vista SHALL mostrar un estado de carga accesible mientras se consulta el listado y SHALL permitir reintentar ante un fallo de conexión, timeout o respuesta inesperada, sin mostrar datos parciales como si fueran definitivos. La vista MUST NOT presentar `title` ni `detail` de un error del servidor.

#### Scenario: Carga del listado
- **GIVEN** una consulta de pacientes en curso
- **WHEN** se monta la vista
- **THEN** se muestra un indicador de carga con `role="status"` y no se muestran filas ni resultados falsos

#### Scenario: Error de conexión con reintento
- **GIVEN** una respuesta de red fallida al consultar el listado
- **WHEN** se muestra el estado de error y se activa «Reintentar»
- **THEN** se vuelve a consultar el listado y, con una respuesta válida, se muestran los resultados; el mensaje no contiene texto del error original

### Requirement: Ficha individual de un paciente
La vista SHALL permitir abrir la ficha de un paciente desde el listado y mostrar, con `GET /api/v1/patients/{id}`, nombre, apellido, DNI, teléfono, email, fecha de nacimiento, dirección, origen, estado activo y fechas de creación y actualización. La lectura SHALL limitarse a datos de ficha: la vista MUST NOT consultar ni mostrar datos de historia clínica.

#### Scenario: Consulta de ficha existente
- **GIVEN** un paciente activo en el listado
- **WHEN** se selecciona para ver su ficha
- **THEN** se solicita su detalle por identificador y se muestran sus datos de filiación y contacto con el origen y las fechas en formato local de Buenos Aires

#### Scenario: Ficha inexistente o ajena al consultorio
- **GIVEN** una respuesta 404 con código `PATIENT_NOT_FOUND`
- **WHEN** se intenta abrir la ficha
- **THEN** se muestra un estado «No encontramos este paciente» con acceso de vuelta al listado, sin repetir la consulta ni mostrar el identificador en el mensaje

#### Scenario: Error al cargar la ficha
- **GIVEN** una respuesta de red fallida, timeout o cuerpo ilegible al consultar el detalle
- **WHEN** se abre la ficha
- **THEN** se muestra un aviso de error propio con «Reintentar» y no se muestran datos parciales del paciente

### Requirement: Edición de la ficha del paciente
La vista SHALL permitir editar los campos que el contrato acepta en `PUT /api/v1/patients/{id}` —nombre, apellido, teléfono, email, fecha de nacimiento y dirección— y MUST NOT ofrecer edición del DNI ni del origen. Antes de enviar, SHALL validar los campos obligatorios y el formato ISO de la fecha de nacimiento, y SHALL mostrar los errores junto al campo correspondiente sin repetir el valor rechazado.

#### Scenario: Edición exitosa
- **GIVEN** la ficha cargada y cambios válidos en teléfono y email
- **WHEN** se confirma el guardado
- **THEN** se envía una única petición `PUT` con solo los campos editables, se muestra la ficha con los datos actualizados por el servidor y se ofrece volver al listado

#### Scenario: Validación local que bloquea el envío
- **GIVEN** el formulario de edición con nombre, apellido o teléfono vacíos, o una fecha de nacimiento con formato distinto de `yyyy-MM-dd`
- **WHEN** se intenta guardar
- **THEN** no se realiza ninguna petición y se muestra el error junto al campo inválido con un mensaje propio

#### Scenario: Rechazo del servidor por validación
- **GIVEN** una respuesta 400 con código `VALIDATION_FAILED` y errores por campo
- **WHEN** se guarda la edición
- **THEN** se marcan los campos informados por el servidor con mensajes propios, se conservan los cambios del formulario y no se muestra el `title` ni el `detail` de la respuesta

#### Scenario: Conflicto o sesión desactualizada al editar
- **GIVEN** una respuesta 403 con código `CSRF_REQUIRED` o una respuesta 404 con `PATIENT_NOT_FOUND` al guardar
- **WHEN** se confirma la edición
- **THEN** se muestra un aviso propio que pide recargar o vuelve al listado, según corresponda, y no se afirma que los cambios se guardaron

### Requirement: Baja lógica del paciente
La vista SHALL solicitar confirmación antes de dar de baja a un paciente y SHALL ejecutarla con `DELETE /api/v1/patients/{id}`. La baja MUST NOT ofrecer ni realizar borrado físico: tras el éxito el paciente queda inactivo en el servidor, desaparece de los resultados habituales y la interfaz MUST NOT exponer controles para eliminarlo definitivamente.

#### Scenario: Confirmación y baja correcta
- **GIVEN** la ficha de un paciente activo
- **WHEN** se elige «Dar de baja» y se confirma en el diálogo accesible
- **THEN** se envía una única petición `DELETE`, se confirma en pantalla que la ficha quedó inactiva y se ofrece volver al listado sin el paciente

#### Scenario: Cancelación de la baja
- **GIVEN** el diálogo de confirmación abierto
- **WHEN** se elige cancelar o se cierra con Escape
- **THEN** no se realiza ninguna petición y el foco vuelve al control que abrió el diálogo

#### Scenario: Error durante la baja
- **GIVEN** una respuesta de red fallida, timeout o error inesperado al eliminar
- **WHEN** se confirma la baja
- **THEN** se muestra un aviso que no afirma que la baja se completó, se conserva la ficha en pantalla y se permite reintentar

### Requirement: Indicador de datos marcados para revisión
Cuando la ficha informe explícitamente que un paciente tiene datos pendientes de revisión según RN-PA-03, la vista SHALL mostrar un indicador accesible con texto y etiqueta acorde, acompañado de una explicación de que esos datos no fueron sobreescritos. La vista MUST NOT inferir ese indicador del origen del paciente ni fabricarlo cuando el servidor no lo informa.

#### Scenario: Ficha con datos para revisar
- **GIVEN** una ficha cuyo detalle informa que hay datos pendientes de revisión
- **WHEN** se abre la ficha
- **THEN** se muestra una insignia con texto «Datos para revisar» y una explicación de que la reserva no modificó los datos registrados, legible también por tecnología de asistencia

#### Scenario: Ficha sin marcador o con marcador en falso
- **GIVEN** una ficha que no informa el marcador o lo informa en falso
- **WHEN** se abre la ficha
- **THEN** no se muestra ningún indicador de revisión, aunque el origen del paciente sea una reserva pública

#### Scenario: Datos existentes no sobreescritos desde la UI
- **GIVEN** un paciente con datos marcados para revisión
- **WHEN** se edita y guarda su ficha con cambios explícitos del odontólogo
- **THEN** la petición de edición no incluye el DNI ni el origen, y el indicador sigue reflejando exclusivamente lo que informe el servidor tras la respuesta

### Requirement: Privacidad y render seguro de datos de pacientes
La vista SHALL renderizar los datos del paciente como texto o nodos seguros, sin interpretarlos como HTML, y MUST NOT incluir nombre, DNI, teléfono, email ni dirección en la URL, el título del documento, la consola, mensajes de error ni almacenamiento del navegador. Las fechas e instantes SHALL mostrarse en `America/Argentina/Buenos_Aires`.

#### Scenario: Texto del paciente con caracteres especiales
- **GIVEN** un paciente cuyo nombre o dirección contiene `<script>` u otros caracteres especiales
- **WHEN** se muestra el listado o la ficha
- **THEN** el texto aparece literalmente y no se crea ningún elemento HTML ni se ejecuta código

#### Scenario: Sin datos personales en la navegación
- **GIVEN** una búsqueda por DNI y una ficha abierta
- **WHEN** se inspeccionan la URL, el título, la consola, `localStorage` y `sessionStorage`
- **THEN** ninguno contiene el DNI, el nombre, el teléfono, el email ni la dirección del paciente

### Requirement: Accesibilidad y diseño responsive
La vista SHALL ser operable a 390×844 y 1440×900 usando los componentes del design system: tabla en desktop y lista apilada en móvil, etiquetas y foco visibles en todos los controles, estados de carga y avisos anunciados por tecnología de asistencia, y diálogo de confirmación con foco inicial seguro y cierre con Escape.

#### Scenario: Listado en móvil
- **GIVEN** un viewport de 390×844 con resultados de búsqueda
- **WHEN** se muestra el listado
- **THEN** los pacientes aparecen como tarjetas o filas apiladas, sin desplazamiento horizontal de página, y cada enlace a la ficha tiene una etiqueta accesible

#### Scenario: Listado en desktop
- **GIVEN** un viewport de 1440×900 con resultados de búsqueda
- **WHEN** se muestra el listado
- **THEN** los pacientes aparecen en una tabla con encabezados de columna asociados y «Pacientes» queda marcado como sección actual del layout privado

#### Scenario: Confirmación de baja accesible
- **GIVEN** el diálogo de confirmación de baja abierto
- **WHEN** se tabula, se cierra con Escape o se confirma
- **THEN** el foco está inicialmente en la opción segura, Escape cancela sin pedir la baja y el foco vuelve al origen al cerrar
