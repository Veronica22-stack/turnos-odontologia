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
| PA-20 | Baja | **Verificación manual pendiente** de las fuentes del informe de Discovery (`docs/discovery/informe-discovery.md` §Verificación de fuentes) | Validación competitiva | Product Owner |
| PA-21 | Baja | **Accesibilidad e idioma**: ¿WCAG nivel objetivo? ¿solo español? | Frontend | Equipo |
| PA-22 | Baja | **Registro del profesional**: ¿matrícula visible en emails/HC? | Datos de consultorio | Profesional |

## Puntos "No evidenciado" del Discovery

Lo que el informe de Discovery (`docs/discovery/informe-discovery.md`) no pudo respaldar con fuentes públicas queda como pregunta abierta: el producto no se diseña suponiendo que esas afirmaciones son ciertas.

| ID | Punto no evidenciado en el Discovery | Cómo lo trata este proyecto | Relacionado |
|----|--------------------------------------|-----------------------------|-------------|
| NE-01 | Cómo resuelven los competidores la **prevención de solapamientos, los bloqueos y los sobreturnos**: casi ningún proveedor lo describe públicamente | Reglas propias RN-AG-01, RN-AG-02 y RN-AG-04, garantizadas en base de datos; no se copian comportamientos no demostrados | RN-AG, C-09, C-11 |
| NE-02 | **Agenda por sillón/box** con prevención de solapamiento por sillón: solo aparece en material comercial (AgendaPro) | v1 con un único sillón (RN-AG-10); multi-sillón en etapas posteriores | RN-AG-10, 06 §Funcionalidades posteriores |
| NE-03 | **Adecuación a la Ley 27.706** (HC electrónica): ningún proveedor la evidencia | No se inventan requisitos; se valida con asesoría legal | PA-01, 11_cumplimiento_normativo |
| NE-04 | **Facturación AFIP/ARCA operativa**: no evidenciada (AgendaPro la declara "próximamente"; otros solo la mencionan en directorios) | Fuera de la v1 | 06 §Funcionalidades posteriores |
| NE-05 | **Gestión de obras sociales/prepagas**: solo Dentalink la documenta | Fuera de la v1 (diferenciador de etapa 2) | 06 §Funcionalidades posteriores |
| NE-06 | **Precios publicados en ARS**: casi ningún proveedor los publica; varios precios vienen de agregadores no oficiales | Sin base de comparación confiable para el precio propio | PA-07 |
| NE-07 | **Efectividad de recordatorios por WhatsApp y agentes de IA**: son afirmaciones comerciales sin demostración independiente | v1 solo email; medir no-show desde el día 1 | IN-05, PA-08 |
| NE-08 | **Adopción y reseñas verificables** (Capterra, GetApp, tiendas): no se pudieron abrir | No se usan cifras de clientes como argumento | PA-20 |
| NE-09 | **Empresa y país de origen** de varios proveedores (Reservo, Medilink, La Historia Clínica Digital, Consultorios.AR, DentalSoft) | Se reportan como "No evidenciado" en la tabla A | PA-20 |
