# Proposal: `frontend-ui-fixes`

## Why

Al usar la SPA de punta a punta aparecieron defectos de interfaz que bloquean o confunden el uso real: el botón «Agregar tramo» de Horarios de atención siempre creaba tramos para el lunes, por lo que la reserva pública solo ofrecía turnos los lunes; las ventanas emergentes y el odontograma se veían rotos; quedaban marcadores provisionales `(TBD · PA-xx)` visibles para pacientes y profesional; y el enlace al aviso de privacidad del paso 3 de la reserva era un enlace muerto (`href="#"`). Este change documenta y cierra esos arreglos, ya implementados y verificados en el working tree.

Governance: **MEDIO** (lógica y presentación de frontend). No toca backend, API ni datos. Sin dependencias nuevas (regla dura 12).

## What Changes

- **Horarios de atención (`/configuracion`)**: cada día de la semana (lunes a domingo) tiene su propio botón «Agregar tramo» (nombre accesible «Agregar tramo el <día>»), también en el estado vacío; las filas se mantienen ordenadas por día y cada input tiene un id único. La grilla y «Guardar horarios» ocupan todo el ancho (antes el botón se estiraba como una columna).
- **Ventanas emergentes** (agenda «Nuevo turno», detalle/reprogramar, diálogo de confirmación, corrección de historia clínica): padding interno, borde, radio, sombra, fondo semitransparente, tamaño máximo responsive con margen al borde de la pantalla y acciones alineadas con separación. Solo CSS con tokens.
- **Odontograma**: la marca de estado de cada pieza queda dentro de la celda y la etiqueta del cuadrante va en su propia fila (antes se superponían). Solo CSS.
- **Sin marcadores provisionales**: se quitan de la interfaz «(TBD · PA-04)», «(TBD · PA-10)», «(TBD · PA-05)», «(TBD · datos)» y «(TBD)» en login, reserva pública, gestión de turno, pie público y configuración, reemplazados por textos finales en español.
- **Aviso de privacidad**: la casilla del paso 3 dice «Leí y acepto el aviso de privacidad.» y debajo hay un desplegable «Ver aviso de privacidad» con el texto del aviso (Ley 25.326: finalidad, datos que se piden, derechos de acceso/rectificación/supresión, AAIP, contacto). Nueva ruta pública `/privacidad` con el mismo aviso, enlazada desde el pie público; se elimina el enlace «Términos y condiciones», que no tenía destino.

Fuera de alcance: texto legal definitivo revisado por un profesional (PA-10 sigue abierta para la versión final), datos de contacto del consultorio en el pie, recuperación de contraseña (PA-04), cambios de backend.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities
- `settings-ui`: se agrega el alta de tramos para cualquier día de la semana desde su propio bloque.
- `public-booking-ui`: el paso 3 muestra el aviso de privacidad en un desplegable dentro del formulario (sin enlace muerto) y el aviso tiene un contenido mínimo definido.
- `spa-routing`: se declara la ruta pública `/privacidad`.
- `app-layout`: el pie público enlaza solo a «Política de privacidad» (`/privacidad`) y ninguna vista muestra marcadores provisionales.

## Impact

- **Código** (`frontend/`, ya implementado): `src/features/settings/working-hours-section.ts`, `src/features/settings/practice-section.ts`, `src/features/booking/{details-step,booking-messages,booking-ui,privacy-notice}.ts`, `src/features/manage-booking/{manage-messages,manage-view}.ts`, `src/features/auth/login-view.ts`, `src/layouts/public-layout.ts`, `src/app/routes.ts`; estilos `src/styles/{agenda,settings,clinical-record,booking}.css`; tests Vitest correspondientes.
- **Backend / API / datos**: sin cambios.
- **Dependencias**: ninguna nueva.
