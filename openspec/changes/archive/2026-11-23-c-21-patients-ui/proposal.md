# Proposal: C-21 `patients-ui`

## Why

La ruta privada `/pacientes` hoy es un *placeholder* (C-05): el odontólogo no puede buscar, consultar ni editar las fichas de sus pacientes desde la interfaz, aunque el backend ya expone esos contratos (C-10). Sin esta pantalla, US-050 queda sin resolver para el usuario final: mantener los datos de contacto actualizados exige interactuar con la API a mano, y no se puede verificar visualmente si un paciente tiene datos informados por una reserva pública que requieren revisión (RN-PA-03). Es el primer change de la Fase 6 orientado a pacientes y desbloquea C-22 (`clinical-record-ui`), que necesita elegir un paciente desde esta vista.

Governance: **MEDIO**. Change **[UI]** con política `system-and-mockups`: los mockups aprobados son requisito previo al apply.

## What Changes

- Reemplazar el *placeholder* de `/pacientes` por la vista operativa de pacientes, dentro del layout privado y con el guard de sesión y el redirect de cambio de contraseña ya definidos (sin tocar el router).
- Listado/búsqueda de pacientes activos por nombre, apellido, DNI o teléfono, con `GET /api/v1/patients?q=…` a través de un gateway tipado; estados inicial, cargando, resultados, sin coincidencias, sin pacientes y error de conexión (US-050).
- Ficha individual con `GET /api/v1/patients/{id}` (que ya audita `PATIENT_READ` en el servidor), mostrando datos de filiación y contacto, origen y fechas; con estados de carga, no encontrado (404 `PATIENT_NOT_FOUND`) y error.
- Edición de la ficha con `PUT /api/v1/patients/{id}` y validación local previa, sobre los campos que el contrato permite modificar (nombre, apellido, teléfono, email, fecha de nacimiento y dirección; el DNI no es editable desde la UI). Los errores del servidor se traducen a mensajes propios por campo, nunca se muestra `title`/`detail` crudo.
- Baja lógica con `DELETE /api/v1/patients/{id}` y confirmación accesible: la ficha queda inactiva (nunca borrado físico, RN-PA-04) y desaparece del listado habitual (RN-PA-03/RN-PA-01). Sin controles de borrado físico en la interfaz.
- Indicación accesible de «datos para revisar» cuando el paciente tiene información pendiente de verificación según RN-PA-03 (reserva por DNI existente que no sobreescribió la ficha). El contrato actual de C-10 **no expone aún** ese marcador: el gateway lo decodifica de forma opcional y la vista solo lo muestra cuando el backend lo informa, sin inferirlo del origen ni inventarlo. Se registra como pedido al backend en `design.md`.
- Render seguro con `h()`/`textContent`, sin `innerHTML`; sin datos personales en URLs, títulos, consola ni almacenamiento; fechas UTC mostradas en `America/Argentina/Buenos_Aires` (reglas duras 6, 8, 9, 10 y 11).
- Mockups HTML aprobados (design system Revision 1, política `system-and-mockups`) de listado, ficha, edición, confirmación de baja y estados vacíos/de error, en 390×844 y 1440×900, **antes** del apply.
- Tests Vitest: búsqueda, ficha, edición, baja lógica, decodificación del contrato y render seguro; Strict TDD con ciclos RED → GREEN → TRIANGULATE → REFACTOR.

## Capabilities

### New Capabilities

- `patients-ui`: comportamiento observable de la pantalla de gestión de fichas de pacientes —búsqueda y listado, ficha y auditoría indirecta, edición con validación, baja lógica, indicador de datos para revisar (RN-PA-03) y estados vacíos/de error—.

### Modified Capabilities

- `spa-routing`: la ruta privada `/pacientes` debe mostrar la vista operativa de pacientes en lugar del contenido de marcador de posición, manteniendo guard de sesión, redirect por cambio de contraseña y título/layout actuales.

## Impact

- **Frontend**: `frontend/src/app/routes.ts` (sustitución del placeholder e inyección del gateway), nuevo módulo `frontend/src/features/patients/` (tipos y decodificadores, gateway, mensajes, validación pura, estado de la vista, componentes de listado/ficha/formulario), `frontend/src/styles/patients.css` con tokens del design system y tests Vitest colocated. Sin frameworks ni dependencias de runtime nuevas.
- **APIs consumidas (sin cambios de backend)**: `GET /api/v1/patients`, `GET /api/v1/patients/{id}`, `PUT /api/v1/patients/{id}` y `DELETE /api/v1/patients/{id}`. CSRF de mutaciones ya resuelto por `cookieCsrfTransport` (C-05); sesión vencida gestionada por `onUnauthorized` de `main.ts`.
- **Diseño**: `design-system/README.md` (Revision 1 aprobada), `brand.js`, `tokens.css` y `components.html` (sección Pacientes y estados vacíos); mockups en `openspec/changes/c-21-patients-ui/mockups/` (fase posterior al propose).
- **Dependencias**: C-06 (`login-ui`) y C-10 (`patients-management`), ambas archivadas y confirmadas. No modifica el change activo C-25 (`booking-management-ui`).
- **Sin impacto en historia clínica**: no se accede ni se escriben datos clínicos; la edición de la ficha no altera entradas de HC ni el DNI identificador.
