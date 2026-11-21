# app-layout Specification

## Purpose
Define los dos marcos visuales de la SPA —el app shell privado del profesional y el app shell público de la reserva— con su navegación responsive, la marca y el uso exclusivo de los tokens del design system.

## Requirements

### Requirement: App shell privado
Las rutas privadas SHALL renderizarse dentro de un layout con encabezado (logo y nombre de la marca, nombre del usuario y acción "Salir"), navegación principal con "Agenda", "Pacientes" y "Configuración" en ese orden, y un área de contenido principal (`main`).

#### Scenario: Estructura privada
- **GIVEN** una sesión autenticada de "Dra. Romero"
- **WHEN** se muestra `/agenda`
- **THEN** existen un `header` con la marca, "Dra. Romero" y un botón "Salir", un `nav` con nombre accesible "Principal" con los tres enlaces en orden, y un `main` con el `h1` "Agenda"

### Requirement: Item de navegación actual
El enlace de la sección actual SHALL marcarse con `aria-current="page"` y solo ese.

#### Scenario: Configuración actual
- **WHEN** se muestra `/configuracion`
- **THEN** solo el enlace "Configuración" tiene `aria-current="page"`

### Requirement: Navegación responsive
En anchos menores a 1024 px la navegación privada SHALL mostrarse como barra inferior fija con objetivos táctiles de al menos 44 px; desde 1024 px MUST mostrarse como barra lateral izquierda, sin ocultar contenido ni producir desplazamiento horizontal.

#### Scenario: Mobile 390x844
- **WHEN** se muestra `/agenda` a 390x844
- **THEN** la navegación está fija al pie, cada enlace mide al menos 44 px de alto y el documento no tiene desplazamiento horizontal

#### Scenario: Desktop 1440x900
- **WHEN** se muestra `/agenda` a 1440x900
- **THEN** la navegación es una columna a la izquierda del contenido

### Requirement: App shell público
Las rutas públicas (`/reservar`, `/reservar/confirmado`, `/turno/{token}`, `/login`) SHALL renderizarse en un layout con encabezado de marca sin navegación privada, contenido centrado de ancho legible y pie con enlaces a "Política de privacidad" y "Términos y condiciones".

#### Scenario: Estructura pública
- **WHEN** se muestra `/reservar`
- **THEN** existen un `header` con la marca, un `main` y un `footer` con los dos enlaces legales, y no existe el `nav` "Principal"

### Requirement: Marca desde el design system
El nombre visible del producto SHALL tomarse de `design-system/brand.js` y el logo de `design-system/assets/logo.svg`, con texto alternativo que indique que el logo es provisional mientras lo sea.

#### Scenario: Nombre de marca
- **WHEN** se muestra cualquier layout
- **THEN** el nombre de la marca mostrado es el definido en `brand.js` y el logo tiene un `alt` no vacío

### Requirement: Estilos solo con tokens
Los estilos de la SPA SHALL importar `design-system/tokens.css` y MUST NOT declarar colores, tamaños de fuente, espaciados, radios ni sombras literales; una verificación automática SHALL fallar si aparecen.

#### Scenario: CSS con color literal
- **GIVEN** una hoja de estilos de `src/` con `color: #333`
- **WHEN** se ejecuta la verificación de estilos
- **THEN** falla nombrando el archivo y el valor

#### Scenario: CSS del change
- **WHEN** se ejecuta la verificación sobre los estilos de este change
- **THEN** pasa

### Requirement: Accesibilidad del shell
Ambos layouts SHALL ofrecer un enlace "Saltar al contenido" como primer elemento enfocable, foco visible con `--focus-ring` y regiones `header`, `nav` (privado), `main` y `footer` (público).

#### Scenario: Primer tab
- **WHEN** se presiona Tab por primera vez en cualquier layout
- **THEN** el foco queda en "Saltar al contenido" y al activarlo el foco pasa al `main`
