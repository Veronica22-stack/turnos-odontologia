## MODIFIED Requirements

### Requirement: App shell público
Las rutas públicas (`/reservar`, `/reservar/confirmado`, `/privacidad`, `/turno/{token}`, `/login`) SHALL renderizarse en un layout con encabezado de marca sin navegación privada, contenido centrado de ancho legible y pie con la marca y un enlace funcional a "Política de privacidad" que lleva a `/privacidad`. El pie MUST NOT incluir enlaces sin destino.

#### Scenario: Estructura pública
- **WHEN** se muestra `/reservar`
- **THEN** existen un `header` con la marca, un `main` y un `footer` con el enlace "Política de privacidad" hacia `/privacidad`, y no existe el `nav` "Principal"

#### Scenario: Pie sin enlaces muertos
- **WHEN** se muestra cualquier ruta pública
- **THEN** ningún enlace del `footer` tiene `href="#"` y no aparece "Términos y condiciones"

## ADDED Requirements

### Requirement: Textos finales sin marcadores provisionales
Ninguna vista, pública o privada, SHALL mostrar marcadores provisionales de desarrollo como «(TBD)», «(TBD · PA-xx)» o «(TBD · datos)»; donde falte un dato definitivo SHALL mostrarse un texto final en español (por ejemplo, remitir a comunicarse con el consultorio).

#### Scenario: Pantallas públicas sin marcadores
- **WHEN** se muestran `/login`, `/reservar` (todos sus pasos y estados vacíos), `/turno/{token}` con enlace inválido y el pie público
- **THEN** ningún texto contiene «TBD» ni identificadores de preguntas abiertas como «PA-04»

#### Scenario: Configuración sin marcadores
- **WHEN** se muestra la sección de datos del consultorio en `/configuracion`
- **THEN** la ayuda de la zona horaria dice que se fija en la instalación y no se puede editar, sin «(TBD)»
