# Spec Delta: spa-shell

## ADDED Requirements

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
