# Preguntas Abiertas

## Inconsistencias detectadas

### IN-01 — Rol de secretaria en v1
**Discovery dice** (`discovery/discovery.md` §2 y §11): la secretaria es un rol "a confirmar para v1".
**Decisión confirmada dice**: solo el odontólogo en v1; secretaria diferida a v2.
**Impacto**: Alcance de RBAC y de la regla "HC solo para el profesional tratante".
**Resolución propuesta**: Se toma la decisión confirmada (DD-06). Actualizar el discovery para cerrar la pregunta.

### IN-02 — Seña/cobros vs. "sin seña" en v1
**Discovery dice**: Mercado Pago/seña aparece como funcionalidad opcional y el mercado la usa para reducir ausencias (AgendaPro).
**Decisión confirmada dice**: sin política de cancelación ni depósito en v1.
**Impacto**: Sin seña ni verificación, el riesgo de reservas falsas y de ausentismo aumenta (ver PA-05, PA-08).
**Resolución propuesta**: No hay conflicto de alcance, pero sí de riesgo; aceptar explícitamente y revisarlo con datos tras el lanzamiento.

### IN-03 — "Local only" vs. reservas 24/7 públicas
**Documento A dice**: infraestructura local por ahora.
**Documento B dice**: el paciente reserva 24/7 por un link público y se envían recordatorios programados.
**Impacto**: Una app solo local no es accesible desde Internet ni envía recordatorios si el equipo está apagado; el producto real requiere disponibilidad.
**Resolución propuesta**: Definir pronto un hosting mínimo (VPS/PaaS de bajo costo) aunque el desarrollo siga local (PA-03).

### IN-04 — Estado `scale: single_user` en el estado compartido vs. actores
**Documento A dice**: `kb.discovery.scale = single_user` (un profesional por instancia).
**Documento B dice**: hay pacientes anónimos concurrentes en las páginas públicas.
**Impacto**: Ninguno funcional; es una convención (un usuario interno). Aclarado para evitar sub-dimensionar rate limiting y concurrencia.
**Resolución propuesta**: Mantener `single_user` y documentar que la concurrencia pública existe (diseñar con constraints en BD).

### IN-05 — La evidencia del recordatorio por email
**Discovery dice**: el mercado usa WhatsApp como estándar y el email solo es un supuesto sin probar.
**Decisión confirmada dice**: email-only en v1.
**Impacto**: Posible ausentismo alto sin mejora.
**Resolución propuesta**: Medir el no-show desde el día 1 (métrica) y priorizar WhatsApp en v2 si no mejora.

## Preguntas abiertas (priorizadas)

| ID | Prioridad | Pregunta | Bloquea | Decisor |
|----|-----------|----------|---------|---------|
| PA-01 | **Alta** | **Ley 27.706 (Historia Clínica Electrónica) y su reglamentación**: ¿qué exige exactamente (firma digital/electrónica, inmutabilidad, plazo de conservación, registro de accesos, interoperabilidad, consentimiento, alcance sobre consultorios privados)? **No relevado; no se inventan requisitos.** | Diseño final del módulo HC (change de HC) | Product Owner + asesoría legal |
| PA-02 | **Alta** | **Fecha límite concreta del MVP** | Planificación, recorte de alcance | Product Owner |
| PA-03 | **Alta** | **Hosting y ubicación de los datos**: ¿qué proveedor cloud/VPS, en qué país? La Ley 25.326 restringe la transferencia internacional de datos personales a países sin nivel de protección adecuado (a verificar con asesoría legal); datos de salud son sensibles | Despliegue real, contratación, cumplimiento | Product Owner + legal |
| PA-04 | **Alta** | **Método de autenticación del odontólogo**: sesión con cookie vs JWT, política de contraseñas, 2FA, recuperación de contraseña (¿cómo se recupera sin secretaria ni otro admin?) | Change de auth | Equipo técnico |
| PA-05 | **Alta** | **Reservas falsas/abuso** sin cuenta ni verificación: ¿rate limit, honeypot, límite de turnos futuros por DNI/email/teléfono, CAPTCHA, verificación opcional por email? | Change de reservas públicas | Product Owner + técnico |
| PA-06 | **Alta** | **Backups y recuperación**: frecuencia, cifrado, ubicación, retención, prueba de restauración (RPO/RTO). Crítico para la HC | Despliegue | Equipo técnico |
| PA-07 | Alta | **Precio y modelo de cobro en ARS** (suscripción, licencia, implantación) y costo por instalación objetivo | Modelo de negocio, "menor costo" | Product Owner |
| PA-08 | Media | **Adopción y ausentismo**: ¿el email alcanza? ¿línea base de no-show del primer cliente? ¿cuándo pasar a WhatsApp? | Roadmap v2 | Product Owner |
| PA-09 | Media | **Proveedor de email transaccional** (costo, entregabilidad, dominio/SPF/DKIM, ubicación de datos) | Change de notificaciones | Equipo técnico |
| PA-10 | Media | **Registro de la base de datos** en el Registro Nacional de Bases de Datos Personales (AAIP) y requisitos de consentimiento/aviso de privacidad en la reserva pública | Lanzamiento | Legal |
| PA-11 | Media | **Retención y eliminación**: plazo de conservación de HC (Ley 26.529) y de datos de pacientes que no son pacientes clínicos (solo reservas); derecho de acceso/rectificación/supresión de la Ley 25.326 frente a la inmutabilidad de la HC | Modelo de datos, políticas | Legal |
| PA-12 | Media | **Operación de N despliegues single-tenant**: ¿quién actualiza, monitorea y da soporte?; umbral para pasar a multi-tenant | Escalabilidad del negocio | Product Owner |
| PA-13 | Media | **Menores de edad y pacientes sin DNI**: ¿cómo se modelan (responsable, otro documento)? | Modelo de pacientes | Profesional / legal |
| PA-14 | Media | **Notificación al profesional** de reservas/cancelaciones del paciente (¿email al odontólogo?) | Change de notificaciones | Product Owner |
| PA-15 | Media | **Política de datos de reserva vs. HC**: ¿un paciente que solo reservó (sin atención) tiene `clinical_record`? ¿Se borran sus datos de reserva si nunca asistió? | Modelo de datos | Legal / Product Owner |
| PA-16 | Baja | **Nombre y marca del producto**; dominio para el link público | Branding, emails | Product Owner |
| PA-17 | Baja | **Odontograma**: catálogo de estados, notación (FDI vs sistema local), dentición temporal/permanente | Change de HC | Profesional |
| PA-18 | Baja | **Estructura de la anamnesis**: campos mínimos (formulario del profesional) | Change de HC | Profesional |
| PA-19 | Baja | **Anticipación mínima, horizonte de reserva y ventana de recordatorio** por defecto | Configuración | Profesional |
| PA-20 | Baja | **Verificación manual pendiente** de las fuentes de la sección E del informe de mercado (`discovery/investigacion-mercado.md`) | Validación competitiva | Product Owner |
| PA-21 | Baja | **Accesibilidad e idioma**: ¿WCAG nivel objetivo? ¿solo español? | Frontend | Equipo |
| PA-22 | Baja | **Registro del profesional**: ¿matrícula visible en emails/HC? | Datos de consultorio | Profesional |
