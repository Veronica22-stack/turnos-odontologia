## MODIFIED Requirements

### Requirement: Rutas declaradas
La SPA SHALL reconocer las rutas públicas `/reservar`, `/reservar/confirmado`, `/privacidad`, `/turno/{token}` y `/login`, y las rutas privadas `/agenda`, `/pacientes` y `/configuracion`, resolviendo la ruta por el `pathname` de la URL (History API, sin `#`).

#### Scenario: Ruta pública con parámetro
- **WHEN** se navega a `/turno/AbC-123_x`
- **THEN** se muestra la vista de gestión de turno con el parámetro `token` igual a `AbC-123_x` dentro del layout público

#### Scenario: Ruta privada con sesión
- **GIVEN** una sesión autenticada
- **WHEN** se navega a `/pacientes`
- **THEN** se muestra la vista de pacientes dentro del layout privado con "Pacientes" marcado como actual

#### Scenario: Barra final
- **WHEN** se navega a `/agenda/` con una sesión autenticada
- **THEN** se resuelve la misma ruta que `/agenda`

#### Scenario: Aviso de privacidad sin sesión
- **GIVEN** no hay sesión iniciada
- **WHEN** se navega a `/privacidad`
- **THEN** se muestra el aviso de privacidad dentro del layout público, con título «Aviso de privacidad» y sin redirigir a `/login`
