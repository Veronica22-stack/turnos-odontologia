# public-booking-ui Specification

## Purpose
Permite que una persona sin cuenta reserve un turno desde la SPA pública (`/reservar`), en el celular o la computadora, eligiendo prestación, día y horario reales y cargando sus datos, y le confirma la reserva sin exponer el enlace de gestión ni sus datos personales.

## Requirements

### Requirement: Flujo de reserva en tres pasos
La vista `/reservar` SHALL guiar la reserva en tres pasos dentro del layout público —prestación, día y horario, datos— con un indicador "Paso N de 3" y un encabezado `h1` por paso que recibe el foco al cambiar de paso. Cada paso posterior al primero SHALL ofrecer "Volver" al paso anterior conservando lo elegido. El paso SHALL reflejarse en la URL (`/reservar`, `/reservar?paso=2`, `/reservar?paso=3`) con una entrada de historial por paso (ver `spa-routing`).

#### Scenario: Inicio del flujo
- **GIVEN** un catálogo público con dos prestaciones
- **WHEN** se navega a `/reservar`
- **THEN** se muestra el paso 1 "Paso 1 de 3" con ambas prestaciones y ningún formulario de datos

#### Scenario: Volver conserva la elección
- **GIVEN** la persona eligió una prestación y un horario y está en el paso 3
- **WHEN** activa "Volver"
- **THEN** se muestra el paso 2 con el mismo día y el mismo horario marcados, y el foco pasa al encabezado del paso

### Requirement: Selección de prestación
El paso 1 SHALL listar las prestaciones de `GET /api/v1/public/services` en el orden recibido, mostrando nombre, duración en minutos y descripción cuando exista, como un grupo de opciones de selección única. Continuar sin elegir una prestación MUST NOT avanzar y SHALL indicar el error en el grupo.

#### Scenario: Elegir prestación
- **GIVEN** las prestaciones "Consulta y diagnóstico" (30 min) y "Limpieza" (45 min)
- **WHEN** la persona elige "Limpieza" y continúa
- **THEN** se muestra el paso 2 con "Limpieza · 45 min" como resumen

#### Scenario: Continuar sin elegir
- **WHEN** la persona continúa sin elegir una prestación
- **THEN** no se consulta la disponibilidad y se muestra "Elegí una prestación." asociado al grupo

### Requirement: Reservas no disponibles
Cuando el catálogo público responda una lista vacía, o la disponibilidad responda `status = NO_ACTIVE_SERVICES`, la vista SHALL mostrar un estado "Reservas online no disponibles" sin pasos ni formulario, sin tratarlo como error de conexión.

#### Scenario: Catálogo vacío
- **GIVEN** `GET /api/v1/public/services` responde `200` con `[]`
- **WHEN** se navega a `/reservar`
- **THEN** se muestra "Reservas online no disponibles" y no se muestra el indicador de pasos

#### Scenario: Prestaciones dadas de baja durante el flujo
- **GIVEN** la persona eligió una prestación
- **WHEN** la disponibilidad responde `200` con `status = NO_ACTIVE_SERVICES`
- **THEN** se muestra el mismo estado "Reservas online no disponibles"

### Requirement: Selección de día
El paso 2 SHALL ofrecer una tira de días locales del consultorio consecutivos que empieza en el día de hoy del consultorio (zona `America/Argentina/Buenos_Aires`, reloj reemplazable en tests), con el primer día seleccionado al entrar. Elegir un día SHALL consultar `GET /api/v1/public/availability` con `serviceId` y `date` en formato `aaaa-mm-dd`.

#### Scenario: Día local distinto del día UTC
- **GIVEN** el reloj fijado en `2026-11-24T01:30:00Z`
- **WHEN** se entra al paso 2
- **THEN** el primer día de la tira es el lunes 23 y se consulta `date=2026-11-23`

#### Scenario: Cambio de día
- **WHEN** la persona elige otro día de la tira
- **THEN** se consulta la disponibilidad de ese día y el día queda marcado con `aria-pressed="true"`

### Requirement: Horarios disponibles
El paso 2 SHALL mostrar los slots de la respuesta como botones con la hora de inicio en `HH:mm` de Buenos Aires, agrupados en "Mañana" (inicio antes de las 13:00) y "Tarde", sin ofrecer horarios que no vinieron en la respuesta. Al elegir un horario SHALL mostrarse el resumen día y rango horario y habilitarse "Continuar".

#### Scenario: Horarios agrupados
- **GIVEN** slots que empiezan a las `12:00Z`, `12:30Z` y `18:00Z` del 2026-11-24
- **WHEN** se muestra el día
- **THEN** "Mañana" contiene 09:00 y 09:30, "Tarde" contiene 15:00 y no aparece ningún otro horario

#### Scenario: Elegir horario
- **WHEN** la persona elige 09:30 de una prestación de 30 minutos
- **THEN** el botón queda con `aria-pressed="true"` y el resumen muestra "mar 24/11" y "09:30 – 10:00"

### Requirement: Estados de la consulta de horarios
Mientras se consulta un día, la lista SHALL mostrar un estado de carga anunciado. Un día con `slots` vacíos SHALL mostrar "No hay horarios disponibles este día." con una acción para ver el día siguiente. Un error de red o inesperado SHALL mostrar un aviso de conexión con "Reintentar", sin borrar la prestación elegida.

#### Scenario: Día sin horarios
- **GIVEN** la disponibilidad del día responde `200` con `status = OK` y `slots: []`
- **WHEN** se muestra ese día
- **THEN** se ve "No hay horarios disponibles este día." y "Ver el día siguiente" consulta el día posterior

#### Scenario: Falla de conexión al consultar
- **WHEN** la consulta del día falla por red
- **THEN** se muestra el aviso de conexión y "Reintentar" repite la misma consulta

### Requirement: Datos del paciente
El paso 3 SHALL mostrar el resumen del turno y pedir nombre, apellido, DNI, teléfono y email con etiquetas visibles y `autocomplete` adecuados, y una casilla obligatoria «Leí y acepto el aviso de privacidad.» junto a un desplegable «Ver aviso de privacidad» que muestra el aviso dentro del formulario, sin enlaces sin destino. Todos los campos son obligatorios.

#### Scenario: Formulario inicial
- **WHEN** se llega al paso 3
- **THEN** se ven los cinco campos vacíos, la casilla sin marcar, el resumen de prestación, día y horario y el botón "Confirmar reserva"

#### Scenario: Volver y regresar conserva los datos
- **GIVEN** la persona cargó sus datos y volvió al paso 2 sin enviar
- **WHEN** elige otro horario y continúa
- **THEN** los datos cargados siguen en el formulario

#### Scenario: Leer el aviso sin salir del formulario
- **GIVEN** la persona está en el paso 3
- **WHEN** abre «Ver aviso de privacidad»
- **THEN** el texto del aviso se muestra dentro del formulario, la casilla no se marca sola, los datos cargados se conservan y no hay enlaces con `href="#"`

### Requirement: Validación local de los datos
Antes de enviar, la SPA SHALL validar: campos obligatorios no vacíos tras recortar espacios; nombre y apellido de hasta 100 caracteres; DNI con 6 a 9 dígitos ignorando puntos, espacios y guiones; teléfono de hasta 50 caracteres con al menos 6 dígitos; email con formato válido de hasta 255 caracteres; aviso aceptado. Con errores MUST NOT enviar, SHALL marcar cada campo inválido con su mensaje y SHALL mover el foco al primero.

#### Scenario: Envío vacío
- **WHEN** la persona confirma con el formulario vacío
- **THEN** no se envía la reserva, los cinco campos y la casilla muestran su error y el foco queda en "Nombre"

#### Scenario: DNI con puntos
- **WHEN** el DNI es `30.456.789`
- **THEN** el DNI es válido

#### Scenario: DNI corto
- **WHEN** el DNI es `12345`
- **THEN** se muestra "Ingresá un DNI de 6 a 9 números." en ese campo

### Requirement: Campo trampa oculto
El formulario SHALL incluir un campo `website` invisible para personas, fuera del orden de tabulación y oculto para tecnologías de asistencia, que viaja en la reserva con el valor que tenga. La SPA MUST NOT mostrarlo, enfocarlo ni autocompletarlo.

#### Scenario: Campo no perceptible
- **WHEN** se inspecciona el paso 3
- **THEN** el campo `website` tiene `tabindex="-1"`, `autocomplete="off"`, está dentro de un contenedor con `aria-hidden="true"` y no ocupa espacio visible

#### Scenario: Envío normal
- **WHEN** una persona confirma una reserva válida
- **THEN** el cuerpo enviado incluye `website` vacío

### Requirement: Envío de la reserva
Confirmar con datos válidos SHALL enviar una única `POST /api/v1/public/bookings` con `serviceId`, `startsAt` (instante UTC del slot elegido), nombre, apellido, DNI, teléfono y email recortados, `website` y `privacyAccepted: true`. Durante el envío el botón SHALL mostrar "Reservando…" con `aria-busy="true"`, los campos quedan de solo lectura y un segundo envío MUST ignorarse.

#### Scenario: Doble toque
- **GIVEN** un envío en curso
- **WHEN** la persona vuelve a confirmar
- **THEN** se envía una sola reserva

#### Scenario: Instante enviado
- **GIVEN** el slot elegido con `startsAt = 2026-11-24T12:30:00Z`
- **WHEN** se confirma
- **THEN** el cuerpo contiene `"startsAt":"2026-11-24T12:30:00Z"` y no contiene fin ni duración

### Requirement: Horario tomado mientras se reservaba
Ante `409 SLOT_TAKEN`, o `422` con `BOOKING_TOO_SOON`, `BOOKING_BEYOND_HORIZON` o `APPOINTMENT_IN_PAST`, la SPA SHALL volver al paso 2 en el mismo día, recargar su disponibilidad, mostrar un aviso de que el horario ya no está disponible y destacar hasta tres horarios libres más cercanos al elegido, conservando los datos del paso 3.

#### Scenario: Conflicto con alternativas
- **GIVEN** la persona eligió 10:30 y la reserva responde `409 SLOT_TAKEN`
- **WHEN** la nueva disponibilidad del día trae 10:00, 11:00, 11:30 y 15:00
- **THEN** se ve "Ese horario se acaba de ocupar", se sugieren 10:00, 11:00 y 11:30, no hay horario marcado y al elegir uno y continuar el formulario conserva los datos

#### Scenario: Conflicto sin horarios ese día
- **GIVEN** un `409 SLOT_TAKEN`
- **WHEN** la nueva disponibilidad del día viene vacía
- **THEN** se ve el aviso de horario ocupado junto con "No hay horarios disponibles este día." y la acción para ver el día siguiente

### Requirement: Límite de solicitudes
Ante `429 RATE_LIMITED` en cualquier llamada del flujo, la SPA SHALL mostrar un aviso de advertencia con la espera aproximada derivada de `Retry-After` ("en 1 minuto", "en unos N minutos" o "en unos minutos" si falta), conservar lo elegido y los datos, y no reintentar automáticamente.

#### Scenario: Reserva limitada
- **GIVEN** la reserva responde `429` con `Retry-After: 40`
- **WHEN** se procesa la respuesta
- **THEN** se muestra "Demasiados intentos seguidos" con "Probá de nuevo en 1 minuto.", el formulario conserva los datos y el botón vuelve a estar habilitado

#### Scenario: Disponibilidad limitada
- **WHEN** la consulta de un día responde `429` sin `Retry-After`
- **THEN** se muestra el aviso con "Probá de nuevo en unos minutos." en lugar de los horarios

### Requirement: Otros rechazos de la reserva
La SPA SHALL traducir los demás rechazos a mensajes propios, nunca al `title` o `detail` del servidor: `400 VALIDATION_FAILED` marca los campos de `errors[]` conocidos; `422 BOOKING_CONTACT_LIMIT` muestra un aviso de límite de turnos activos; `404 SERVICE_NOT_FOUND` o `422 SERVICE_INACTIVE` vuelven al paso 1 con el catálogo recargado; `403 CSRF_REQUIRED` pide recargar la página; red, timeout o error inesperado muestran un aviso que pide revisar el email antes de reintentar.

#### Scenario: Error de validación del servidor
- **GIVEN** la reserva responde `400 VALIDATION_FAILED` con `errors[{field:'email', code:'Email'}]`
- **WHEN** se procesa la respuesta
- **THEN** el campo email muestra "Ingresá un email válido." y recibe el foco

#### Scenario: Límite de turnos por contacto
- **WHEN** la reserva responde `422 BOOKING_CONTACT_LIMIT`
- **THEN** se muestra un aviso que indica que ya hay turnos activos con esos datos y cómo gestionarlos, sin reintentar

#### Scenario: Respuesta perdida
- **WHEN** la reserva falla por timeout
- **THEN** se muestra un aviso que no afirma que la reserva falló y pide revisar el email antes de intentar de nuevo, y el formulario conserva los datos

### Requirement: Página de confirmación
Ante `201`, la SPA SHALL navegar a `/reservar/confirmado` reemplazando la entrada del historial y mostrar "¡Turno reservado!", prestación, fecha y rango horario de Buenos Aires, consultorio y el email ingresado enmascarado, explicando que el enlace para cancelar o reprogramar llega solo por email. El email completo MUST NOT mostrarse ni conservarse tras el envío. El enmascarado conserva los 2 primeros caracteres de la parte local (el primero si tiene 1 o 2), `•••` y el dominio completo.

#### Scenario: Reserva confirmada
- **GIVEN** una respuesta `201` con `serviceName = "Limpieza"`, `startsAt = 2026-11-24T13:30:00Z`, `endsAt = 2026-11-24T14:15:00Z`
- **WHEN** se procesa la respuesta
- **THEN** la URL es `/reservar/confirmado` y se ve "Limpieza", "martes 24 de noviembre" y "10:30 – 11:15"

#### Scenario: Email enmascarado
- **GIVEN** la persona ingresó `maria@gmail.com`
- **WHEN** se muestra la confirmación
- **THEN** se ve `ma•••@gmail.com` y el texto `maria@gmail.com` no aparece en la página

#### Scenario: Email con parte local corta
- **GIVEN** la persona ingresó `jo@ejemplo.com`
- **WHEN** se muestra la confirmación
- **THEN** se ve `j•••@ejemplo.com`

#### Scenario: Atrás desde la confirmación
- **GIVEN** la confirmación visible
- **WHEN** la persona vuelve atrás en el navegador
- **THEN** no se reenvía la reserva y el flujo empieza de nuevo sin datos personales cargados

### Requirement: Confirmación genérica
Si `/reservar/confirmado` se abre sin datos en memoria (recarga, acceso directo) o la reserva respondió `200` sin datos del turno, la SPA SHALL mostrar una confirmación genérica que remite al email y ofrece reservar otro turno, sin mostrar datos de ningún turno.

#### Scenario: Recarga de la confirmación
- **WHEN** se carga `/reservar/confirmado` directamente
- **THEN** se ve "Revisá tu email" con un enlace a `/reservar` y ningún dato de turno

#### Scenario: Respuesta genérica del servidor
- **GIVEN** la reserva responde `200` con `status = "ACCEPTED"` y sin `startsAt`
- **WHEN** se procesa la respuesta
- **THEN** se navega a `/reservar/confirmado` con la confirmación genérica

### Requirement: Datos personales sin rastros
El flujo MUST NOT escribir nombre, apellido, DNI, teléfono, email ni el identificador de la reserva en la consola, la URL, el título, `localStorage` ni `sessionStorage`. Tras la confirmación, o al abandonar `/reservar`, los datos del formulario SHALL descartarse de la memoria salvo los necesarios para mostrar la confirmación una vez.

#### Scenario: Sin rastros tras reservar
- **GIVEN** una reserva completa con datos ficticios
- **WHEN** se inspeccionan consola, `location.href`, `document.title` y ambos almacenamientos
- **THEN** ninguno contiene el DNI, el email, el teléfono ni el nombre ingresados

### Requirement: Uso desde el celular
El flujo SHALL ser usable desde 320 px de ancho sin desplazamiento horizontal de la página, con objetivos táctiles de al menos `--touch-target` y teclados adecuados (`inputmode="numeric"` para DNI, `type="tel"` para teléfono, `type="email"` para email), y la acción principal de los pasos 1 y 2 visible al pie de la pantalla en mobile.

#### Scenario: Teclados del celular
- **WHEN** se inspecciona el paso 3
- **THEN** el DNI tiene `inputmode="numeric"`, el teléfono `type="tel"` y el email `type="email"`

### Requirement: Contenido del aviso de privacidad
El aviso de privacidad SHALL explicar, en referencia a la Ley 25.326, para qué se usan los datos (gestión del turno y recordatorios), qué datos se piden, los derechos de acceso, rectificación y supresión, que la AAIP es el órgano de control y cómo contactar al consultorio. El mismo texto SHALL mostrarse en el paso 3 y en la ruta pública `/privacidad`, sin datos de personas.

#### Scenario: Aviso en el paso 3
- **WHEN** se abre «Ver aviso de privacidad» en el paso 3
- **THEN** el texto menciona la Ley 25.326, la finalidad, los datos pedidos, los derechos, la AAIP y el contacto

#### Scenario: Aviso en su propia página
- **WHEN** se navega a `/privacidad`
- **THEN** se muestra el título «Aviso de privacidad» con el mismo texto del paso 3
