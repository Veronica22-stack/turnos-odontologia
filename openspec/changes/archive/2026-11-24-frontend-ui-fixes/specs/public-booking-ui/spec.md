## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Contenido del aviso de privacidad
El aviso de privacidad SHALL explicar, en referencia a la Ley 25.326, para qué se usan los datos (gestión del turno y recordatorios), qué datos se piden, los derechos de acceso, rectificación y supresión, que la AAIP es el órgano de control y cómo contactar al consultorio. El mismo texto SHALL mostrarse en el paso 3 y en la ruta pública `/privacidad`, sin datos de personas.

#### Scenario: Aviso en el paso 3
- **WHEN** se abre «Ver aviso de privacidad» en el paso 3
- **THEN** el texto menciona la Ley 25.326, la finalidad, los datos pedidos, los derechos, la AAIP y el contacto

#### Scenario: Aviso en su propia página
- **WHEN** se navega a `/privacidad`
- **THEN** se muestra el título «Aviso de privacidad» con el mismo texto del paso 3
