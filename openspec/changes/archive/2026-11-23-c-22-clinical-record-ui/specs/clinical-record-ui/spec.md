# Spec Delta: clinical-record-ui

## Purpose

Define el comportamiento observable de la interfaz de historia clínica del odontólogo: consultar y registrar anamnesis, odontograma y evoluciones sobre el contrato inmutable existente, corregir con motivo sin borrar asientos y proteger los datos de salud frente a exposición, interpretación como HTML y errores de accesibilidad.

## ADDED Requirements

### Requirement: Selección de paciente sin datos en la navegación
La vista de historia clínica SHALL permitir elegir un paciente desde el listado ya disponible de la aplicación y mantener esa selección solo en memoria de la vista. La URL SHALL permanecer exactamente `/historia-clinica`: MUST NOT incluir identificador, nombre, DNI, teléfono ni correo, y la selección MUST NOT viajar en `next`, el título del documento ni mensajes de consola.

#### Scenario: Elección de un paciente
- **GIVEN** un odontólogo con sesión autenticada y pacientes disponibles
- **WHEN** elige un paciente en la vista
- **THEN** se cargan sus secciones clínicas y la URL sigue siendo `/historia-clinica` sin ningún dato del paciente

#### Scenario: Recarga sin selección persistida
- **GIVEN** un paciente seleccionado y su historia clínica visible
- **WHEN** se recarga la página
- **THEN** la vista vuelve a pedir la selección y no recupera el paciente anterior desde almacenamiento del navegador

#### Scenario: Sin identificador en título ni consola
- **GIVEN** un paciente con DNI y nombre conocidos
- **WHEN** se muestra su historia clínica
- **THEN** el título del documento y la consola no contienen su DNI, nombre ni identificador

### Requirement: Consulta de anamnesis vigente e historial
La vista SHALL consultar `GET /api/v1/patients/{patientId}/clinical-record/anamnesis` y mostrar la versión vigente con `allergies`, `medications`, `medicalConditions`, `habits` y `notes`, autor e instante, además del historial completo en orden descendente. Cada entrada corregida SHALL aparecer marcada con texto y referencia a la entrada que la corrigió; una lectura MUST NOT abrir una historia clínica inexistente.

#### Scenario: Anamnesis con corrección
- **GIVEN** una anamnesis vigente en versión `2` que corrige la versión `1`
- **WHEN** se abre la sección de anamnesis
- **THEN** se muestra la versión `2` con su motivo y referencia, y la versión `1` en el historial con la marca «Corregida»

#### Scenario: Paciente sin historia clínica
- **GIVEN** un paciente existente sin entradas clínicas
- **WHEN** se abre la sección de anamnesis
- **THEN** se muestra un estado vacío propio y no se envía ninguna escritura

### Requirement: Alta de anamnesis con el contrato del servidor
La vista SHALL registrar anamnesis con `POST /api/v1/patients/{patientId}/clinical-record/anamnesis` enviando solo los campos del contrato, con al menos uno no vacío y hasta 2000 caracteres por campo. El cliente MUST NOT enviar autor, instante, identificador, versión ni historia clínica; la respuesta del servidor SHALL actualizar la vista sin confiar en valores calculados localmente.

#### Scenario: Anamnesis válida
- **GIVEN** el formulario con alergias y medicación
- **WHEN** se confirma el registro
- **THEN** se envía una única petición `POST` con esos campos y se muestra la entrada devuelta por el servidor

#### Scenario: Formulario vacío o campo excedido
- **GIVEN** todos los campos vacíos o un campo de más de 2000 caracteres
- **WHEN** se intenta registrar
- **THEN** no se envía ninguna petición y se muestra un error junto al campo, sin repetir el valor rechazado

### Requirement: Odontograma FDI con estado actual derivado
La vista SHALL ofrecer piezas permanentes y temporales en notación FDI y mostrar, para cada posición, el estado de la entrada vigente derivado del historial. Una pieza sin registros MUST NOT mostrar un estado presumido, y una corrección SHALL reemplazar el estado visible de su posición sin alterar las entradas anteriores.

#### Scenario: Pieza sin registros
- **GIVEN** un paciente sin entradas para la pieza `11`
- **WHEN** se consulta el odontograma
- **THEN** la pieza `11` se muestra sin estado asignado y sin marcar como sana

#### Scenario: Nuevo estado y corrección
- **GIVEN** la pieza `36` con caries en cara oclusal
- **WHEN** se registra como obturado y luego se corrige esa última entrada
- **THEN** el estado visible pasa a ser el de la corrección y el historial conserva todas las entradas

### Requirement: Registro y corrección de estados del odontograma
La vista SHALL enviar entre 1 y 64 estados por lote con `toothCode`, `condition`, `surface` opcional y `notes` opcional, usando solo el catálogo `HEALTHY`, `CARIES`, `FILLED`, `MISSING`, `CROWN` y `ROOT_CANAL`; las caras SHALL limitarse a `CARIES` y `FILLED`. La corrección SHALL usar `POST .../odontogram/{entryId}/corrections` con motivo y contenido, sin permitir que el cliente cambie pieza ni cara.

#### Scenario: Lote válido con cara
- **GIVEN** las piezas `16` y `21` seleccionadas con caries oclusal y corona respectivamente
- **WHEN** se confirma el registro
- **THEN** se envía una única petición con dos estados y se muestran las entradas devueltas

#### Scenario: Estado o cara inválidos
- **GIVEN** un estado fuera del catálogo o una cara en `MISSING`
- **WHEN** se intenta registrar
- **THEN** no se envía ninguna petición y se muestra un mensaje propio sobre el campo inválido

#### Scenario: Corrección sin cambiar la posición
- **GIVEN** una entrada vigente en `36/OCCLUSAL`
- **WHEN** se corrige con motivo y otro estado
- **THEN** la petición no incluye `toothCode` ni `surface` y la nueva entrada conserva la posición original

### Requirement: Accesibilidad del odontograma
Cada pieza SHALL ser alcanzable por teclado, tener una etiqueta accesible que indique código FDI y estado en texto, y señalar la selección mediante un estado de pulsación además del color. El odontograma SHALL agrupar los cuadrantes con etiquetas, conservar un anillo de foco visible, respetar `prefers-reduced-motion` y ser operable a 390×844 y 1440×900 sin desplazamiento horizontal de página.

#### Scenario: Recorrido por teclado
- **GIVEN** el odontograma cargado
- **WHEN** se tabula hasta una pieza y se activa con Enter o Espacio
- **THEN** la pieza queda seleccionada, el foco sigue visible y un lector de pantalla anuncia su código FDI y su estado

#### Scenario: Estado no dependiente del color
- **GIVEN** una pieza con caries y otra ausente
- **WHEN** se consulta el odontograma
- **THEN** cada estado se identifica por texto o marca, además del color, y la leyenda explica las marcas

### Requirement: Consulta y alta de evoluciones
La vista SHALL consultar `GET /api/v1/patients/{patientId}/clinical-record/evolutions` y mostrar las entradas de la más reciente a la más antigua con autor, instante, turno asociado y marcas de corrección. El alta SHALL exigir `description` no vacía de hasta 5000 caracteres y permitir `appointmentId` opcional sin modificar el turno.

#### Scenario: Historial cronológico
- **GIVEN** evoluciones en distintos instantes y una corrección entre ellas
- **WHEN** se abre la sección de evoluciones
- **THEN** se muestran todas en orden descendente y la corregida conserva su marca

#### Scenario: Descripción inválida
- **GIVEN** una descripción vacía o de más de 5000 caracteres
- **WHEN** se intenta registrar
- **THEN** no se envía ninguna petición y se muestra un error junto al campo sin repetir el texto

#### Scenario: Turno rechazado por el servidor
- **GIVEN** una respuesta `422` con código `EVOLUTION_APPOINTMENT_NOT_STARTED`
- **WHEN** se confirma el registro
- **THEN** se muestra un mensaje propio, no se afirma que se guardó y no se reintenta automáticamente

### Requirement: Corrección con motivo obligatorio para toda entrada
La vista SHALL ofrecer «Corregir» solo sobre una entrada vigente y SHALL exigir un `reason` no vacío de hasta 500 caracteres antes de enviar `POST .../clinical-record/{tipo}/{entryId}/corrections`. El cuerpo SHALL contener el contenido corregido del tipo correspondiente, sin `authorId`, `createdAt`, `id` ni `clinicalRecordId`; una corrección rechazada MUST NOT dar lugar a un reintento automático.

#### Scenario: Corrección sin motivo
- **GIVEN** el formulario de corrección con contenido válido y el motivo vacío o solo espacios
- **WHEN** se confirma
- **THEN** no se envía ninguna petición y el error se muestra junto al motivo

#### Scenario: Motivo demasiado largo
- **GIVEN** un motivo de más de 500 caracteres
- **WHEN** se confirma la corrección
- **THEN** no se envía ninguna petición y se muestra el error sin repetir el motivo

#### Scenario: Corrección aceptada
- **GIVEN** una entrada vigente con motivo y contenido corregido
- **WHEN** se confirma la corrección
- **THEN** se envía una única petición al tipo correcto y la nueva entrada aparece como vigente con la original marcada

#### Scenario: Conflicto del servidor
- **GIVEN** una respuesta `409` con `CLINICAL_ENTRY_ALREADY_CORRECTED` o `CLINICAL_ENTRY_NOT_CURRENT`
- **WHEN** se intenta corregir
- **THEN** se muestra un mensaje propio que invita a recargar la vista y no se modifica lo mostrado como vigente

### Requirement: Historial inmutable sin controles de edición ni borrado
La vista MUST NOT ofrecer botones, enlaces, menús, atajos ni formularios para editar o borrar entradas clínicas, y MUST NOT enviar `PUT`, `PATCH` ni `DELETE` a rutas de historia clínica. Toda corrección SHALL presentarse como una nueva entrada; el contenido y la autoría de las entradas existentes MUST NOT modificarse en la interfaz.

#### Scenario: Ausencia de controles destructivos
- **GIVEN** una historia clínica con entradas de los tres tipos
- **WHEN** se inspeccionan las secciones, el historial y los diálogos en ambos viewports
- **THEN** no existe ningún control rotulado como editar, borrar, eliminar o sobrescribir ni acción que lo sugiera

#### Scenario: Sin métodos de mutación directa
- **GIVEN** la vista cargada y una corrección confirmada
- **WHEN** se inspeccionan todas las peticiones emitidas
- **THEN** solo hay lecturas `GET` y escrituras `POST`, sin `PUT`, `PATCH` ni `DELETE` sobre la historia clínica

### Requirement: Datos clínicos fuera de URLs, títulos, consola y almacenamiento
El contenido clínico, las notas y los motivos de corrección MUST NOT aparecer en la URL, el título del documento, mensajes de consola, mensajes de error visibles, `localStorage`, `sessionStorage` ni `history.state`. Los errores del servidor SHALL traducirse a mensajes propios sin mostrar `title`, `detail` ni el valor rechazado.

#### Scenario: Texto marcador en contenido clínico
- **GIVEN** una nota, descripción o motivo con un texto marcador reconocible
- **WHEN** se muestra, se corrige o se rechaza esa entrada
- **THEN** el marcador no aparece en la URL, el título, la consola, los mensajes ni el almacenamiento del navegador

#### Scenario: Error de validación del servidor
- **GIVEN** una respuesta `400` con `VALIDATION_FAILED` y `detail` con contenido clínico
- **WHEN** se muestra el error
- **THEN** se describe solo el campo inválido con un mensaje propio y no se muestra el `detail`

### Requirement: Borrador solo en memoria ante sesión expirada
Los borradores de alta y corrección SHALL conservarse exclusivamente en memoria de la vista durante la edición. Cuando una petición informe sesión vencida, la vista SHALL descartar el borrador, pasar la sesión a anónima y redirigir a `/login` con `/historia-clinica` como destino, sin reintentar la escritura ni guardar el texto clínico en el navegador.

#### Scenario: Sesión vencida durante la carga
- **GIVEN** un borrador con texto clínico sin enviar
- **WHEN** una petición responde `401` de sesión vencida
- **THEN** se descarta el borrador, la URL pasa a `/login?next=%2Fhistoria-clinica` y no queda texto clínico en almacenamiento ni en la URL

#### Scenario: Regreso después de reingresar
- **GIVEN** una sesión vencida que descartó un borrador
- **WHEN** el odontólogo vuelve a `/historia-clinica` tras ingresar
- **THEN** no se restaura el borrador anterior y la vista pide una lectura nueva

### Requirement: Render seguro de todo contenido clínico
Todo valor procedente de la API SHALL renderizarse como texto literal o nodos seguros, de modo que `<`, `>`, `&`, comillas y apóstrofos se vean sin interpretarse como HTML. La vista MUST NOT usar APIs que interpreten HTML ni ejecutar código del contenido, incluso en notas, descripciones, motivos y nombres de piezas.

#### Scenario: Contenido con etiquetas y scripts
- **GIVEN** una nota o evolución que contiene `<script>alert(1)</script>` y caracteres especiales
- **WHEN** se muestra el historial
- **THEN** el texto aparece literalmente, no se crea ningún elemento `script` y no se ejecuta código

#### Scenario: Atributos derivados de datos clínicos
- **GIVEN** un valor clínico con comillas y un esquema de URL peligroso
- **WHEN** se renderiza
- **THEN** el valor se usa como texto y no se convierte en atributo de manejo de eventos ni en enlace activo

### Requirement: Fechas del servidor en hora local de Buenos Aires
Los instantes devueltos por el servidor en UTC SHALL mostrarse en `America/Argentina/Buenos_Aires` con el mismo valor temporal, incluidos autoría de entradas, motivos y turnos asociados. El cliente MUST NOT presentar el Instante UTC crudo ni recalcular la autoría.

#### Scenario: Instante de una evolución
- **GIVEN** una evolución creada a las `13:00Z` durante horario de verano argentino
- **WHEN** se muestra en la vista
- **THEN** se visualiza la hora local correspondiente a `America/Argentina/Buenos_Aires` con el mismo instante

### Requirement: Una solicitud por operación clínica
Cada lectura de sección SHALL generar una única petición `GET` y cada alta o corrección confirmada SHALL generar una única petición `POST`, para que la auditoría del servidor (`HC_READ`, `HC_CREATE`, `HC_CORRECT`) corresponda a una acción real del odontólogo. La vista MUST NOT simular eventos de auditoría ni duplicar escrituras por reintentos automáticos.

#### Scenario: Lectura de una sección
- **GIVEN** una sección cargada
- **WHEN** se abre y se cierra sin cambios
- **THEN** se registra una sola lectura en la red y no se envía ninguna escritura

#### Scenario: Confirmación única
- **GIVEN** el botón de confirmación de una corrección
- **WHEN** se activa dos veces rápidamente
- **THEN** se envía una sola petición y la interfaz bloquea la segunda confirmación mientras la primera está en curso

### Requirement: Estados de carga, vacío y error
Cada sección SHALL mostrar un indicador de carga anunciable, un estado vacío propio y un estado de error con reintento para lecturas, sin presentar datos parciales como definitivos. Tras un fallo de escritura SHALL conservarse el borrador en memoria y MUST NOT afirmarse que la operación se completó.

#### Scenario: Fallo de lectura con reintento
- **GIVEN** una respuesta de red fallida al consultar evoluciones
- **WHEN** se activa «Reintentar»
- **THEN** se vuelve a consultar la sección y, con una respuesta válida, se muestran los datos sin texto del error original

#### Scenario: Fallo de escritura
- **GIVEN** una respuesta inesperada al registrar una corrección
- **WHEN** se muestra el aviso de error
- **THEN** el borrador sigue disponible en pantalla y la entrada no aparece como guardada
