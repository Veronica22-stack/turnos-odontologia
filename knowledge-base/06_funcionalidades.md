# Funcionalidades

Organizadas por épica e historia de usuario (US-NNN). Etiqueta de etapa: **[MVP]** = v1; **[v2]** o **[Posterior]** = fuera de v1. Las funcionalidades posteriores se listan al final para trazabilidad.

## Épica 1: Acceso del profesional

### US-001 — Iniciar sesión [MVP]
**Como** odontólogo **quiero** iniciar sesión de forma segura **para** acceder a mi agenda y a las historias clínicas.

**Criterios de aceptación**:
- [ ] Login con credenciales; errores genéricos (sin revelar si el usuario existe).
- [ ] Bloqueo temporal tras intentos fallidos; sesión expira por inactividad.
- [ ] Logout invalida la sesión; login y fallos quedan auditados.

**Reglas relacionadas**: RN-AU-01, RN-AU-03, RN-AU-04, RN-AD-01

### US-002 — Cambiar mi contraseña [MVP]
**Como** odontólogo **quiero** cambiar mi contraseña **para** mantener la cuenta segura.

**Criterios de aceptación**:
- [ ] Exige contraseña actual y cumple política mínima.
- [ ] Primer login con credencial de bootstrap obliga a cambiarla.

**Reglas relacionadas**: RN-AU-03

## Épica 2: Configuración de la agenda

### US-010 — Definir horarios de atención [MVP]
**Como** odontólogo **quiero** definir mis días y tramos horarios **para** que solo se ofrezcan horarios en los que atiendo.

**Criterios de aceptación**:
- [ ] Varios tramos por día; sin solapamiento entre tramos.
- [ ] Cambios no afectan turnos ya confirmados (se avisa de conflictos).

**Reglas relacionadas**: RN-AG-03, RN-AG-06

### US-011 — Gestionar prestaciones con duración [MVP]
**Como** odontólogo **quiero** crear, editar y desactivar prestaciones con su duración **para** que cada turno ocupe el tiempo real.

**Criterios de aceptación**:
- [ ] Duración > 0 y múltiplo configurable de la granularidad (**Suposición**).
- [ ] Desactivar no borra; no afecta turnos existentes.
- [ ] Marca de visibilidad pública.

**Reglas relacionadas**: RN-AG-05, RN-AG-09

### US-012 — Bloquear horarios [MVP]
**Como** odontólogo **quiero** bloquear rangos (vacaciones, trámites) **para** que no se puedan reservar.

**Criterios de aceptación**:
- [ ] Un bloqueo quita esos slots de la disponibilidad pública.
- [ ] Si hay turnos confirmados dentro del rango, se listan y no se cancelan automáticamente.

**Reglas relacionadas**: RN-AG-02, RN-AG-08

### US-013 — Configurar parámetros de reserva [MVP]
**Como** odontólogo **quiero** definir anticipación mínima y horizonte máximo de reserva **para** controlar cuándo pueden reservar los pacientes.

**Criterios de aceptación**:
- [ ] Valores con defaults razonables y editables.

**Reglas relacionadas**: RN-RS-10

## Épica 3: Agenda del profesional

### US-020 — Ver agenda diaria y semanal [MVP]
**Como** odontólogo **quiero** ver mis turnos por día y por semana **para** organizar mi jornada, también desde el celular.

**Criterios de aceptación**:
- [ ] Vista diaria y semanal responsive (mobile-first).
- [ ] Muestra bloqueos, horarios de atención y estado de cada turno.

### US-021 — Cargar un turno manualmente [MVP]
**Como** odontólogo **quiero** crear un turno para un paciente (ej. pedido por teléfono) **para** mantener toda la agenda en un solo lugar.

**Criterios de aceptación**:
- [ ] Selecciona paciente existente o crea uno; elige prestación y horario.
- [ ] Rechaza solapamiento con turno o bloqueo con mensaje claro.

**Reglas relacionadas**: RN-AG-01, RN-AG-02, RN-AG-04

### US-022 — Cancelar, reprogramar y marcar estado de un turno [MVP]
**Como** odontólogo **quiero** cancelar, reprogramar y marcar un turno como completado o ausente **para** reflejar la realidad de la jornada.

**Criterios de aceptación**:
- [ ] Cancelar libera el slot; se notifica al paciente por email.
- [ ] Estados `COMPLETED` y `NO_SHOW` quedan registrados.

**Reglas relacionadas**: RN-AG-01, RN-NO-04

## Épica 4: Reserva online del paciente

### US-030 — Reservar un turno online [MVP]
**Como** paciente **quiero** elegir prestación, día y hora desde un link **para** reservar sin llamar, a cualquier hora.

**Criterios de aceptación**:
- [ ] Sin cuenta ni verificación; ingresa nombre, DNI, teléfono y email.
- [ ] Solo ofrece slots libres, dentro de horario, sin bloqueos y con duración suficiente.
- [ ] Confirmación inmediata en pantalla y por email.
- [ ] Funciona en celular.
- [ ] Protegido con rate limit y honeypot.

**Reglas relacionadas**: RN-RS-01, RN-RS-02, RN-RS-03, RN-RS-08, RN-AG-01..03, RN-PA-01..03

### US-031 — Cancelar mi turno desde el link [MVP]
**Como** paciente **quiero** cancelar mi turno desde el link del email **para** liberar el horario sin contactar al consultorio.

**Criterios de aceptación**:
- [ ] El token identifica un único turno; confirmación previa antes de cancelar.
- [ ] Cancelar libera el slot y notifica al profesional (**Suposición:** por email o visible en la agenda).
- [ ] Token inválido/expirado muestra mensaje genérico.

**Reglas relacionadas**: RN-RS-04..07

### US-032 — Reprogramar mi turno desde el link [MVP]
**Como** paciente **quiero** elegir otro horario disponible **para** cambiar mi turno sin perderlo.

**Criterios de aceptación**:
- [ ] Reprogramación atómica; si el nuevo slot se ocupó, se conserva el original.
- [ ] Nuevo email de confirmación y recordatorio recalculado.

**Reglas relacionadas**: RN-RS-09, RN-NO-04

## Épica 5: Notificaciones por email

### US-040 — Recibir confirmación por email [MVP]
**Como** paciente **quiero** recibir un email con los datos de mi turno y el link de gestión **para** tener el comprobante.

**Criterios de aceptación**:
- [ ] Se envía al confirmar, reprogramar y cancelar.
- [ ] Sin datos clínicos.

**Reglas relacionadas**: RN-NO-01, RN-NO-06

### US-041 — Recibir recordatorio por email [MVP]
**Como** paciente **quiero** un recordatorio antes de mi turno **para** no olvidarlo.

**Criterios de aceptación**:
- [ ] Un solo recordatorio por turno, N horas antes (configurable).
- [ ] No se envía si el turno fue cancelado; fallos se reintentan sin duplicar.

**Reglas relacionadas**: RN-NO-02, RN-NO-03, RN-NO-04, RN-NO-05

## Épica 6: Pacientes

### US-050 — Gestionar fichas de pacientes [MVP]
**Como** odontólogo **quiero** buscar, ver y editar los datos de mis pacientes **para** mantener su información actualizada.

**Criterios de aceptación**:
- [ ] Búsqueda por nombre, DNI, teléfono.
- [ ] Baja lógica, nunca borrado físico.
- [ ] Lecturas de ficha auditadas.

**Reglas relacionadas**: RN-PA-01..04, RN-AD-01

## Épica 7: Historia clínica

### US-060 — Registrar anamnesis [MVP]
**Como** odontólogo **quiero** registrar la anamnesis del paciente **para** conocer sus antecedentes y riesgos.

**Criterios de aceptación**:
- [ ] Entrada con autor y fecha/hora generados por el servidor.
- [ ] Nueva versión no sobreescribe la anterior.

**Reglas relacionadas**: RN-HC-01, RN-HC-02, RN-HC-05

### US-061 — Registrar odontograma básico [MVP]
**Como** odontólogo **quiero** registrar el estado de cada pieza dental **para** documentar la condición bucal.

**Criterios de aceptación**:
- [ ] Notación FDI; estados de un catálogo base.
- [ ] Estado actual derivado de la última entrada vigente; historial conservado.

**Reglas relacionadas**: RN-HC-01, RN-HC-02, RN-HC-07

### US-062 — Registrar evoluciones [MVP]
**Como** odontólogo **quiero** registrar lo realizado en cada atención **para** tener el seguimiento del tratamiento.

**Criterios de aceptación**:
- [ ] Puede asociarse a un turno.
- [ ] Orden cronológico; autor y fecha automáticos.

**Reglas relacionadas**: RN-HC-01, RN-HC-05

### US-063 — Corregir una entrada de la HC [MVP]
**Como** odontólogo **quiero** corregir un error en una entrada **para** que la HC sea fiel sin perder el original.

**Criterios de aceptación**:
- [ ] La corrección es una nueva entrada que referencia la original y exige motivo.
- [ ] La original sigue visible en el historial, marcada como corregida.
- [ ] No existe botón/endpoint de edición ni borrado.

**Reglas relacionadas**: RN-HC-01, RN-HC-02, RN-HC-07

### US-064 — Consultar el registro de accesos [Posterior: C-23 fuera del alcance del TP; el registro en sí lo cubre C-03]
**Como** odontólogo **quiero** ver quién y cuándo accedió a datos clínicos **para** cumplir con trazabilidad.

**Criterios de aceptación**:
- [ ] Listado filtrable por paciente y fecha; solo lectura.

**Reglas relacionadas**: RN-AD-01..03

## Funcionalidades posteriores (no MVP)

| Funcionalidad | Etapa | Notas |
|---------------|-------|-------|
| Recordatorios/confirmación por WhatsApp (Cloud API) | v2 | Canal estándar del mercado; mitiga el riesgo del email-only |
| Rol secretaria/asistente (sin acceso a HC) | v2 | Requiere RBAC real y separación de permisos |
| Seña/cobros con Mercado Pago, caja | Posterior | Introduce política de cancelación |
| Obras sociales y prepagas | Posterior | Diferenciador de etapa 2 |
| Facturación electrónica ARCA | Posterior | Diferenciador de etapa 2 |
| Lista de espera | Posterior | |
| Seguimiento de ausentes y recall periódico | Posterior | |
| Periodontograma | Posterior | |
| Presupuestos y planes de tratamiento | Posterior | |
| Imágenes/radiografías | Posterior | Implica almacenamiento y cifrado de archivos |
| Multi-profesional, sillones/boxes, sucursales | Posterior | Implica multi-tenant/recursos |
| Campañas, API pública, Google Calendar | Posterior | |
| Firma de consentimientos | Posterior | Depende de requisitos de firma (Ley 27.706, TBD) |
