# settings-ui Specification

## Purpose

Define el comportamiento observable de la pantalla de configuración del odontólogo: consulta y edición de datos del consultorio, horarios, prestaciones, bloqueos y parámetros de reserva, con validaciones, estados vacíos y de error, avisos de impacto y copia del enlace público.

## Requirements

### Requirement: Vista de configuración por secciones
La SPA SHALL mostrar en `/configuracion`, dentro del layout privado y con «Configuración» marcada como sección actual, las secciones de datos del consultorio y parámetros de reserva, horarios de atención, prestaciones y bloqueos. Cada sección SHALL cargar su información desde el endpoint de gestión correspondiente y SHALL permitir guardar cambios de forma independiente.

#### Scenario: Carga con datos existentes
- **GIVEN** una sesión autenticada y un consultorio con prestaciones, horarios y bloqueos configurados
- **WHEN** se navega a `/configuracion`
- **THEN** se muestran las cuatro secciones con los valores devueltos por la API y «Configuración» figura como página actual en la navegación

#### Scenario: Guardar una sección no descarta las demás
- **GIVEN** la vista cargada con cambios sin guardar en la sección de bloqueos
- **WHEN** se guarda correctamente la sección de horarios
- **THEN** el estado de la sección de horarios se actualiza y el borrador de bloqueos permanece disponible

### Requirement: Estados vacíos y de error por sección
Cuando una sección no tiene datos SHALL mostrar un estado vacío con una acción para crear el primer registro. Cuando una consulta falla, la sección afectada SHALL mostrar un error con una acción de reintento, sin impedir el uso de las otras secciones. Un 401 SHALL delegar en el manejo de sesión vencida de la SPA.

#### Scenario: Sin horarios configurados
- **GIVEN** un consultorio sin tramos de atención
- **WHEN** se abre la sección de horarios
- **THEN** se muestra el estado vacío con la acción «Agregar tramo» y no se muestra una grilla vacía sin explicación

#### Scenario: Sin prestaciones ni bloqueos
- **GIVEN** un consultorio sin prestaciones y sin bloqueos
- **WHEN** se abre `/configuracion`
- **THEN** las secciones de prestaciones y bloqueos muestran cada una su estado vacío con su acción de alta

#### Scenario: Fallo de una consulta
- **GIVEN** una respuesta de error al consultar el listado de prestaciones
- **WHEN** se renderiza la vista
- **THEN** solo la sección de prestaciones muestra el error con «Reintentar» y las secciones restantes siguen utilizables

#### Scenario: Sesión vencida durante la carga
- **GIVEN** una sesión expirada
- **WHEN** alguna consulta de configuración responde 401
- **THEN** la SPA redirige a `/login` conservando `/configuracion` como destino, sin mostrar datos parciales de la sección fallida

### Requirement: Edición de datos y parámetros del consultorio
La SPA SHALL enviar nombre, zona horaria, contacto y parámetros de reserva a `PUT /api/v1/practice/settings` y SHALL validar antes del envío que la granularidad sea uno de 10, 15, 20, 30 o 60 minutos, la anticipación mínima sea ≥ 0, el horizonte esté entre 1 y 365 días y el recordatorio entre 1 y 168 horas. Los errores del servidor SHALL vincularse al campo correspondiente o a una alerta de la sección.

#### Scenario: Guardado válido
- **GIVEN** valores válidos de nombre, contacto, granularidad 15, anticipación 120, horizonte 30 y recordatorio 24
- **WHEN** se confirma el guardado
- **THEN** se envía la solicitud y la sección refleja los valores devueltos por la API con un aviso de éxito

#### Scenario: Granularidad no permitida
- **WHEN** se intenta guardar con granularidad 25 minutos
- **THEN** no se envía la solicitud y el campo muestra un mensaje que indica los valores permitidos

#### Scenario: Parámetro fuera de rango
- **WHEN** se intenta guardar con horizonte 0 días o recordatorio 169 horas
- **THEN** no se envía la solicitud y cada campo inválido muestra su mensaje vinculado por accesibilidad

#### Scenario: Rechazo del servidor
- **GIVEN** una respuesta 400 `VALIDATION_FAILED` con errores por campo
- **WHEN** se guarda la configuración
- **THEN** los mensajes se muestran en los campos indicados por el servidor, sin exponer el texto técnico de la respuesta

### Requirement: Configuración de horarios con validación local
La SPA SHALL permitir varios tramos por día y SHALL rechazar en el formulario, antes de enviar, todo tramo con hora de fin igual o anterior a la de inicio y todo solapamiento entre tramos del mismo día. El guardado SHALL reemplazar el conjunto completo mediante `PUT /api/v1/working-hours` y SHALL advertir que los turnos confirmados no se cancelan automáticamente.

#### Scenario: Tramos válidos
- **GIVEN** dos tramos separados para el lunes, 09:00–13:00 y 14:00–18:00
- **WHEN** se guarda la sección
- **THEN** se envía la lista completa y la vista muestra los tramos devueltos por la API

#### Scenario: Tramo invertido
- **WHEN** se carga un tramo con inicio 13:00 y fin 09:00
- **THEN** el formulario marca el tramo como inválido, explica que el fin debe ser posterior al inicio y no envía la solicitud

#### Scenario: Tramos superpuestos
- **GIVEN** tramos de 09:00–13:00 y 12:30–18:00 para el mismo día
- **WHEN** se intenta guardar
- **THEN** se señalan ambos tramos solapados, no se envía la solicitud y se ofrece la corrección

#### Scenario: Tramos adyacentes
- **GIVEN** tramos de 09:00–13:00 y 13:00–18:00 para el mismo día
- **WHEN** se guarda
- **THEN** la validación local los acepta y la solicitud se envía

#### Scenario: Aviso sobre turnos confirmados
- **GIVEN** un profesional con turnos confirmados
- **WHEN** modifica sus horarios de atención
- **THEN** antes de confirmar se le advierte que los cambios no cancelan ni reprograman turnos ya confirmados

### Requirement: Gestión de prestaciones con baja lógica
La SPA SHALL listar prestaciones activas e inactivas, crearlas y editarlas mediante `POST`/`PUT /api/v1/services`, y darlas de baja mediante `DELETE /api/v1/services/{id}` sin ofrecer borrado físico. La duración SHALL ser mayor que cero y múltiplo de la granularidad vigente; una prestación inactiva SHALL poder reactivarse y SHALL conservar su visibilidad pública previa.

#### Scenario: Alta de prestación
- **GIVEN** una granularidad vigente de 15 minutos
- **WHEN** se crea una prestación con duración 45 minutos y visibilidad pública
- **THEN** la lista incorpora la prestación activa con los datos devueltos por la API

#### Scenario: Duración no múltiplo
- **GIVEN** una granularidad vigente de 15 minutos
- **WHEN** se intenta guardar una prestación con duración 20 minutos
- **THEN** el formulario muestra el error de duración, no envía la solicitud y la lista permanece sin cambios

#### Scenario: Baja lógica y reactivación
- **GIVEN** una prestación activa
- **WHEN** se confirma su baja y luego se reactiva
- **THEN** la prestación queda marcada como inactiva y vuelve a activa sin desaparecer del listado ni perder su visibilidad pública

#### Scenario: Conflicto del servidor
- **GIVEN** una respuesta 422 `DURATION_NOT_MULTIPLE_OF_GRANULARITY` tras un cambio de granularidad en el servidor
- **WHEN** se edita una prestación
- **THEN** se muestra un mensaje de error de negocio en la sección y se mantiene el formulario con los valores cargados

### Requirement: Bloqueos y aviso de turnos afectados
La SPA SHALL crear, editar, listar y eliminar bloqueos mediante `/api/v1/schedule-blocks`, mostrando inicio y fin en la hora del consultorio y enviando instantes ISO-8601 con zona. Antes de eliminar SHALL pedir confirmación. La respuesta de alta o edición SHALL mostrar los turnos afectados devueltos en `affectedAppointments`, sin cancelarlos.

#### Scenario: Crear bloqueo con turnos afectados
- **GIVEN** un rango válido con motivo no vacío y turnos dentro del rango
- **WHEN** se confirma el bloqueo
- **THEN** se guarda, se lista en hora local y se muestra el aviso con la cantidad de turnos afectados sin modificarlos

#### Scenario: Rango inválido o motivo ausente
- **WHEN** se intenta crear un bloqueo con fin anterior al inicio o sin motivo
- **THEN** el formulario marca los campos inválidos y no envía la solicitud

#### Scenario: Solapamiento rechazado
- **GIVEN** una respuesta 409 `SCHEDULE_BLOCK_OVERLAP` del servidor
- **WHEN** se guarda un bloqueo que se solapa con otro
- **THEN** se muestra un aviso de conflicto que invita a elegir otro rango, sin crear el bloqueo

#### Scenario: Eliminación confirmada
- **GIVEN** un bloqueo existente
- **WHEN** se elige eliminar y se confirma el diálogo
- **THEN** se envía la eliminación, el bloqueo desaparece del listado y un nuevo intento sin confirmar no la envía

### Requirement: Copia del enlace público de reserva
La vista SHALL ofrecer un botón «Copiar link público de reserva» que componga la URL de `/reservar` del mismo origen de la aplicación, sin tokens ni datos personales. Si el portapapeles no está disponible o falla, SHALL mostrar la URL en un campo de solo lectura para copiarla manualmente.

#### Scenario: Copia correcta
- **GIVEN** un navegador con acceso al portapapeles
- **WHEN** se activa el botón
- **THEN** se copia la URL `/reservar` del mismo origen y se muestra un aviso de éxito

#### Scenario: Portapapeles indisponible
- **GIVEN** un navegador sin API de portapapeles o que la rechaza
- **WHEN** se activa el botón
- **THEN** se muestra la URL en un campo de solo lectura con un aviso para copiarla manualmente y no se interrumpe el uso de la vista

### Requirement: Visualización localizada y render seguro
Todo dato proveniente de la API o ingresado por el profesional SHALL mostrarse como texto literal, sin interpretarse como HTML. Los instantes SHALL mostrarse en `America/Argentina/Buenos_Aires`, con independencia de la zona horaria del navegador, y el código de la vista MUST NOT usar APIs que inserten HTML dinámico.

#### Scenario: Texto con marcas HTML
- **GIVEN** un nombre de prestación o motivo de bloqueo que contiene `<img src=x onerror=alert(1)>`
- **WHEN** se renderiza la sección correspondiente
- **THEN** el texto aparece literalmente y no se crea ningún elemento `img` ni se ejecuta código

#### Scenario: Instante UTC en hora local
- **GIVEN** un bloqueo que empieza a las 12:00 UTC y un navegador en `Europe/Madrid`
- **WHEN** se muestra el bloqueo
- **THEN** la hora mostrada es 09:00 de `America/Argentina/Buenos_Aires` con la fecha local correcta

### Requirement: Alta de tramos para cualquier día de la semana
La sección de horarios SHALL mostrar siempre los siete días (lunes a domingo), también sin tramos cargados, y cada día SHALL ofrecer su propia acción «Agregar tramo» con nombre accesible «Agregar tramo el <día>». Un tramo agregado SHALL quedar en el día elegido y el guardado SHALL enviar los tramos ordenados por día.

#### Scenario: Estado vacío con los siete días
- **GIVEN** un consultorio sin tramos de atención
- **WHEN** se abre la sección de horarios
- **THEN** se ven los siete días, cada uno con su acción «Agregar tramo el <día>»

#### Scenario: Tramo agregado a un día distinto del lunes
- **GIVEN** un consultorio sin tramos de atención
- **WHEN** se agrega un tramo con «Agregar tramo el martes» y se guarda
- **THEN** se envía un único tramo con día de la semana 2 y ningún tramo para el lunes

#### Scenario: Tramo agregado junto a tramos existentes
- **GIVEN** tramos cargados para lunes y martes
- **WHEN** se agrega un tramo el viernes y se guarda
- **THEN** se envía la lista completa ordenada por día, con el nuevo tramo en el viernes

### Requirement: Índice de secciones con sección actual
El índice «En esta página» de `/configuracion` SHALL marcar con `aria-current` un único enlace, el de la sección en la que está la persona. Al activar un enlace SHALL marcarlo como actual y desplazar la vista hasta su sección sin provocar una navegación del router ni re-renderizar la vista. Al desplazarse, el índice SHALL marcar la sección visible y, al llegar al final de la página, la última sección.

#### Scenario: Estado inicial
- **WHEN** se abre `/configuracion`
- **THEN** «Consultorio y reserva» es el único enlace del índice marcado como actual

#### Scenario: Clic en un enlace del índice
- **GIVEN** la vista de configuración cargada
- **WHEN** se activa el enlace «Bloqueos»
- **THEN** «Bloqueos» queda como único enlace actual, la vista se desplaza a esa sección y no se re-renderiza ni cambia la ruta

#### Scenario: Desplazamiento manual
- **GIVEN** la vista de configuración cargada
- **WHEN** la persona se desplaza hasta que la sección de horarios queda en la parte superior visible
- **THEN** el índice marca «Horarios de atención» como actual

#### Scenario: Final de la página
- **GIVEN** una última sección demasiado corta para llegar a la parte superior visible
- **WHEN** la persona se desplaza hasta el final de la página
- **THEN** el índice marca la última sección como actual
