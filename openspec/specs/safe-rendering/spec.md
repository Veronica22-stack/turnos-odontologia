# safe-rendering Specification

## Purpose
Garantiza que la SPA nunca interprete como HTML ni ejecute datos que vienen del usuario o de la API (nombres, DNI, notas clínicas, tokens), protegiendo los datos de salud contra XSS (Ley 25.326, regla dura 10).

## Requirements

### Requirement: Texto siempre como texto
Todo dato insertado en la interfaz SHALL mostrarse como texto literal: los caracteres `<`, `>`, `&`, `"` y `'` se ven tal cual y nunca crean elementos.

#### Scenario: Nombre con etiqueta
- **WHEN** se renderiza el nombre `<img src=x onerror=alert(1)>`
- **THEN** la vista muestra ese texto literal y no contiene ningún elemento `img`

#### Scenario: Texto común
- **WHEN** se renderiza el nombre `Pérez & Hijos`
- **THEN** la vista muestra `Pérez & Hijos`

### Requirement: Escape para contextos de cadena
Cuando un dato deba incluirse en una cadena HTML o de atributo, el escape SHALL reemplazar `&`, `<`, `>`, `"` y `'` por sus entidades.

#### Scenario: Escape completo
- **WHEN** se escapa `<a href="x">O'Hara & Co</a>`
- **THEN** el resultado es `&lt;a href=&quot;x&quot;&gt;O&#39;Hara &amp; Co&lt;/a&gt;`

#### Scenario: Cadena vacía
- **WHEN** se escapa una cadena vacía
- **THEN** el resultado es una cadena vacía

### Requirement: Atributos peligrosos rechazados
La creación de elementos MUST rechazar atributos de manejadores de eventos (`on*`) y valores de `href`, `src`, `action` o `formaction` con esquemas distintos de `http:`, `https:`, `mailto:`, `tel:` o rutas relativas, con la única excepción de imágenes inlineadas por el bundler: `data:image/png`, `jpeg`, `gif`, `webp` o `svg+xml` SHALL aceptarse solo como `src` de un elemento `img`.

#### Scenario: Atributo onclick
- **WHEN** se intenta crear un elemento con el atributo `onclick`
- **THEN** la creación falla con un error que nombra el atributo y no se crea el elemento

#### Scenario: Enlace javascript
- **WHEN** se intenta crear un enlace con `href = " JavaScript:alert(1)"`
- **THEN** la creación falla y no se crea el enlace

#### Scenario: Imagen inlineada
- **WHEN** se crea un `img` con `src = "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4="`
- **THEN** el elemento se crea con ese `src`

#### Scenario: Data URI fuera de img
- **WHEN** se intenta crear un enlace con `href = "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4="` o un `img` con `src = "data:text/html;base64,AAAA"`
- **THEN** la creación falla nombrando el atributo

#### Scenario: Enlace relativo
- **WHEN** se crea un enlace con `href = "/agenda"`
- **THEN** el enlace se crea con ese destino

### Requirement: Sin APIs de HTML en el código
El código fuente del frontend (excluidos los tests) MUST NOT usar `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write` ni `eval`/`new Function`, y una verificación automática SHALL fallar si aparecen.

#### Scenario: Código limpio
- **WHEN** se ejecutan los tests del frontend
- **THEN** la verificación confirma que ningún archivo de `src/` (salvo `*.test.ts`) contiene esas APIs

#### Scenario: Uso detectado
- **GIVEN** un archivo de producción con `el.innerHTML = x`
- **WHEN** se ejecuta la verificación
- **THEN** falla nombrando el archivo y la API encontrada
