# Spec Delta: spa-shell

## Purpose

Define cómo arranca la SPA sin framework: dónde se monta, en qué idioma declara el documento y qué ocurre si la página no tiene el contenedor esperado. Las pantallas y estilos llegan en los changes de UI posteriores.

## ADDED Requirements

### Requirement: Montaje en el contenedor raíz
Al cargarse, la SPA SHALL montar un único nodo raíz de la aplicación dentro del elemento `#app` del documento, creado mediante la API del DOM sin interpretar HTML de cadenas.

#### Scenario: Contenedor presente
- **GIVEN** un documento con un elemento `#app` vacío
- **WHEN** se ejecuta el arranque de la SPA
- **THEN** `#app` contiene exactamente un nodo raíz marcado con `data-app-root`

#### Scenario: Arranque repetido
- **GIVEN** la SPA ya montada
- **WHEN** el arranque se ejecuta otra vez sobre el mismo documento
- **THEN** `#app` sigue conteniendo exactamente un nodo raíz

### Requirement: Contenedor faltante
Si el documento no tiene un elemento `#app`, el arranque SHALL fallar con un error que nombre el selector esperado, sin modificar el documento.

#### Scenario: Sin contenedor
- **GIVEN** un documento sin `#app`
- **WHEN** se ejecuta el arranque de la SPA
- **THEN** se lanza un error cuyo mensaje incluye `#app` y el `body` queda sin cambios

### Requirement: Idioma del documento
La página de la SPA SHALL declarar el idioma `es-AR` en el elemento `html`.

#### Scenario: Idioma declarado
- **WHEN** se carga `index.html`
- **THEN** el atributo `lang` del elemento `html` es `es-AR`
