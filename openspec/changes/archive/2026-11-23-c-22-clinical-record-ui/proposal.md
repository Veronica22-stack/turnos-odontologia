# Proposal

## Why

Los changes `C-16`, `C-17` y `C-18` ya dejaron una API de historia clínica inmutable, auditada y con correcciones por nueva entrada, pero la SPA todavía no ofrece ninguna pantalla para registrar ni consultar esos datos: el odontólogo no puede cargar anamnesis, marcar el odontograma, escribir evoluciones ni corregir un asiento sin salir de la aplicación (US-060..063, Flujo 6). Sin esta interfaz el contrato clínico existe solo en el backend y el cumplimiento de RN-HC-01/02/04/07/08 depende de que el cliente lo respete: hay que construirla con el mismo modelo append-only y sin exponer datos de salud en URLs, consola, errores ni almacenamiento del navegador.

## What Changes

- Agregar la ruta privada `/historia-clinica` con selección de paciente y tres secciones: anamnesis, odontograma FDI interactivo y evoluciones; el paciente se elige desde el flujo ya existente de `/pacientes` y su identidad nunca viaja en la URL.
- Mostrar la versión vigente de cada tipo de entrada y el historial completo, con las entradas corregidas marcadas y la referencia a la corrección, sin botones ni controles de edición o borrado en ningún estado de la vista.
- Permitir corregir una entrada vigente con `reason` obligatorio y validación local, enviando siempre `POST .../corrections` con el contenido del tipo correspondiente; los errores `400`, `404`, `409` y `422` del contrato se traducen a mensajes propios sin eco del contenido clínico.
- Respetar el contrato real de C-16/C-17/C-18: anamnesis (`allergies`, `medications`, `medicalConditions`, `habits`, `notes`), odontograma (códigos FDI permanentes y temporales, catálogo `HEALTHY`/`CARIES`/`FILLED`/`MISSING`/`CROWN`/`ROOT_CANAL`, caras solo para `CARIES` y `FILLED`) y evoluciones (`description` y `appointmentId` opcional).
- Mantener el borrador de una carga o corrección solo en memoria: si la sesión vence durante la carga se descarta y se redirige a `/login` con `next`, sin persistir texto clínico en `localStorage`, `sessionStorage`, `history.state`, título ni consola.
- Renderizar todo contenido clínico como texto (`textContent`/nodos seguros) y con fechas en `America/Argentina/Buenos_Aires`; el odontograma debe ser operable por teclado y lector de pantalla con etiqueta por pieza, estado por texto y foco visible.
- Gobernanza **ALTO**: datos de salud (Leyes 25.326, 26.529 y 27.706 pendiente de relevamiento, PA-01). Las decisiones clínicas no resueltas (PA-17, PA-18) se registran como supuestos a validar con el profesional; este change no modifica la API ni la base y requiere aprobación explícita de los mockups antes del apply.

## Capabilities

### New Capabilities

- `clinical-record-ui`: comportamiento observable de la interfaz de historia clínica por paciente —alta y consulta de anamnesis, odontograma y evoluciones, correcciones con motivo, historial inmutable y marcado, privacidad de datos de salud y accesibilidad del odontograma.

### Modified Capabilities

- `spa-routing`: agregar `/historia-clinica` como ruta privada con los guards, el layout y la navegación existentes, sin agregar identificadores de paciente ni datos clínicos a la URL.

## Impact

- Frontend: nueva ruta en `frontend/src/app/routes.ts`, entrada en `frontend/src/layouts/private-layout.ts`, módulos `frontend/src/features/clinical-record/**` (tipos, gateway, mensajes, vistas, validación, odontograma y pruebas) y estilos derivados de `design-system/tokens.css`; cableado de dependencias en `frontend/src/main.ts`.
- Contrato consumido: `POST/GET /api/v1/patients/{patientId}/clinical-record/{anamnesis|odontogram|evolutions}` y `POST .../clinical-record/{tipo}/{entryId}/corrections`; `GET /api/v1/patients` y `/patients/{id}` para elegir paciente. Sin cambios de API ni de esquema.
- Seguridad y cumplimiento: sin endpoints `PUT`/`PATCH`/`DELETE` ni controles que los sugieran, sin datos clínicos en URL, título, consola, errores ni almacenamiento del navegador, respuestas no cacheables según el backend, auditoría `HC_READ`/`HC_CREATE`/`HC_CORRECT` generada por el servidor en cada operación.
- Dependencias ya archivadas: `C-06` (login/sesión), `C-16` (núcleo HC), `C-17` (odontograma), `C-18` (evoluciones) y `C-21` (UI de pacientes). No se toca `c-25-booking-management-ui`.
- Mockups: anamnesis, odontograma, evoluciones, historial de correcciones y accesibilidad del odontograma, a 390×844 y 1440×900, con aprobación previa al apply (política `system-and-mockups`, design system Revision 1).
