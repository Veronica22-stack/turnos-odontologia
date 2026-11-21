# spa-shell Specification

## Purpose
Define cómo arranca la SPA sin framework: dónde se monta, en qué idioma declara el documento y qué ocurre si la página no tiene el elemento esperado. Las pantallas y estilos llegan en los changes de UI posteriores.

## Requirements

### Requirement: Montaje en el elemento raíz
Al cargarse, la SPA SHALL montar un único nodo raíz de la aplicación dentro del elemento `#app` del documento, creado mediante la API del DOM sin interpretar HTML de cadenas.

#### Scenario: Elemento #app presente
- **GIVEN** un documento con un elemento `#app` vacío
- **WHEN** se ejecuta el arranque de la SPA
- **THEN** `#app` contiene exactamente un nodo raíz marcado con `data-app-root`

#### Scenario: Arranque repetido
- **GIVEN** la SPA ya montada
- **WHEN** el arranque se ejecuta otra vez sobre el mismo documento
- **THEN** `#app` sigue conteniendo exactamente un nodo raíz

### Requirement: Elemento #app faltante
Si el documento no tiene un elemento `#app`, el arranque SHALL fallar con un error que nombre el selector esperado, sin modificar el documento.

#### Scenario: Sin elemento #app
- **GIVEN** un documento sin `#app`
- **WHEN** se ejecuta el arranque de la SPA
- **THEN** se lanza un error cuyo mensaje incluye `#app` y el `body` queda sin cambios

### Requirement: Idioma del documento
La página de la SPA SHALL declarar el idioma `es-AR` en el elemento `html`.

#### Scenario: Idioma declarado
- **WHEN** se carga `index.html`
- **THEN** el atributo `lang` del elemento `html` es `es-AR`

### Requirement: Arranque con ruta inicial
Tras montar el nodo raíz, el arranque SHALL cargar la sesión, resolver la ruta de la URL actual con sus guards y renderizar la vista correspondiente dentro de su layout, mostrando un indicador de carga accesible mientras la sesión es `unknown`.

#### Scenario: Arranque en ruta pública
- **GIVEN** la URL `/reservar` y `GET /api/v1/auth/me` respondiendo 401
- **WHEN** se ejecuta el arranque de la SPA
- **THEN** el nodo raíz contiene el layout público con la vista de reserva

#### Scenario: Arranque mientras se consulta la sesión
- **GIVEN** la URL `/agenda` y la consulta de sesión todavía pendiente
- **WHEN** se ejecuta el arranque de la SPA
- **THEN** el nodo raíz muestra un indicador con `role="status"` y ningún contenido privado

### Requirement: Política de referrer
La página de la SPA SHALL declarar la política de referrer `no-referrer`, para que la URL con el token de gestión de turno no se envíe a otros sitios.

#### Scenario: Referrer declarado
- **WHEN** se carga `index.html`
- **THEN** existe `<meta name="referrer" content="no-referrer">`
