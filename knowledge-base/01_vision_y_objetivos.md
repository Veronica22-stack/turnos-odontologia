# Visión y Objetivos

## Propósito del sistema

Aplicación web para que un **odontólogo independiente en Argentina** gestione su consultorio de punta a punta: agenda de turnos, reserva online autogestionada por el paciente y historia clínica (HC) digital.

Hoy el profesional coordina turnos por WhatsApp o teléfono, lleva la ficha en papel u otra herramienta y no envía recordatorios automáticos (ver `discovery/discovery.md` §1). Las soluciones fuertes del mercado son genéricas de salud o están orientadas a clínicas, y casi ninguna evidencia adecuación normativa argentina ni precios en ARS (ver `discovery/investigacion-mercado.md`, secciones A y C). El producto es **real, orientado a clientes**, con **plazo acotado** (fecha TBD) y con el **menor costo posible** como prioridad de calidad número uno.

## Objetivos por actor

| Actor | Objetivo principal | Objetivos secundarios |
|-------|--------------------|-----------------------|
| Odontólogo (usuario interno único) | Tener la agenda sin solapamientos y sin coordinar turnos a mano | Definir prestaciones con duración, horarios y bloqueos; registrar anamnesis, odontograma y evoluciones; reducir ausencias con recordatorios |
| Paciente (anónimo, público) | Reservar, cancelar y reprogramar su turno 24/7 sin llamar | No crear cuenta; recibir confirmación y recordatorio por email |
| Sistema | Enviar confirmaciones y recordatorios por email; garantizar integridad de agenda y HC | Dejar trazabilidad de accesos a datos clínicos |
| Operador del producto (quien despliega/mantiene) | Mantener bajo el costo operativo por instalación | **Suposición:** actor no modelado en la app; relevante por el riesgo de N despliegues (ver 09 y 10) |

## Alcance v1 (MVP)

- Agenda: vistas diaria y semanal, horarios de atención, bloqueos, prestaciones con duración variable, sin solapamientos (garantizado a nivel de base de datos).
- Reserva online 24/7 por link público, **sin cuenta ni verificación**, confirmación inmediata y autogestión (cancelar/reprogramar) mediante link con token único enviado por email.
- Notificaciones por **email únicamente**: confirmación y recordatorio.
- Historia clínica: datos del paciente, anamnesis, odontograma básico y evoluciones; nunca se borra ni se sobreescribe (correcciones = nuevas entradas/versiones con autor y fecha).
- Auditoría de accesos a datos clínicos.
- Un solo usuario interno (el odontólogo) con acceso total.
- Cumplimiento desde v1: Ley 25.326 y Ley 26.529 (ver `11_cumplimiento_normativo.md`).
- Una instalación por odontólogo (single-tenant), modelada para no bloquear una migración futura a multi-tenant.

## Fuera de alcance (v1)

- Recordatorios por WhatsApp (Cloud API) — v2.
- Cobros, caja, seña/depósito con Mercado Pago; política de cancelación.
- Obras sociales y prepagas; facturación electrónica ARCA.
- Rol de secretaria/asistente.
- Lista de espera, seguimiento de ausentes y recall.
- Periodontograma, presupuestos, planes de tratamiento, imágenes/radiografías.
- Múltiples profesionales, sillones/boxes, sucursales.
- Campañas, API pública, firma de consentimientos, Google Calendar.
- Multi-tenant real y hosting en la nube (hoy: ejecución local).

## Métricas de éxito

**Suposición:** métricas propuestas, a validar con el profesional.

| Métrica | Meta orientativa | Cómo se mide |
|---------|------------------|--------------|
| % de turnos reservados online | Mayoría de turnos nuevos | Cuenta de `appointment.origin = PUBLIC` |
| Tasa de ausentismo (no-show) | Baja respecto a línea base actual (a relevar) | Turnos con estado `NO_SHOW` / total |
| Tiempo del profesional en coordinación de turnos | Reducción percibida | Entrevista de seguimiento |
| Incidentes de doble reserva | 0 | Violaciones de la restricción de exclusión (log) |
| Costo operativo mensual por instalación | El mínimo posible (meta numérica TBD) | Facturación de infraestructura |

Riesgo declarado: no está probado que el recordatorio solo por email baje el ausentismo (el mercado usa WhatsApp como estándar). Ver `10_preguntas_abiertas.md`.
