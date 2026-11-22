<div class="cover" markdown="1">

# Informe de Discovery

<p class="subtitle">Sistemas de gestión de turnos y agenda para consultorios odontológicos en Argentina</p>

**Materia**: Metodología I — Tecnicatura Universitaria en Programación

**Trabajo**: Trabajo de Integración — del Discovery al primer Change, con SDD y Active Stack

**Proyecto**: turnos-odontologia

**Integrantes**: Lucas (líder) · Elian · Gabriel · Veronica · Mayra · Tomas

**Responsable del informe**: Veronica · **Verificación de fuentes**: Mayra

**Fecha de consulta de las fuentes**: 2026-11-16

**Fecha del informe**: 2026-11-22

</div>

## Resumen ejecutivo

**Qué se investigó.** Se relevaron 18 sistemas que gestionan turnos y agenda de pacientes odontológicos,
con prioridad en Argentina, luego Latinoamérica y referentes internacionales. Para cada uno se revisaron
agenda, turnos digitales, automatización, funcionalidad clínica, administración, integraciones, operación,
seguridad y modelo comercial, solo con fuentes públicas (sitios oficiales, páginas de precios, centros de
ayuda y directorios), todas consultadas el 2026-11-16. Lo que no tiene respaldo público figura como
"No evidenciado", y se distingue la **funcionalidad comprobada** de la **afirmación comercial** del proveedor.

**Principales hallazgos.**

- **AgendaPro** (4,00/5) y **Dentalink** (3,85/5) lideran la matriz ponderada. AgendaPro tiene la mejor
  evidencia de reserva online, WhatsApp, Mercado Pago y precios en ARS, pero es un producto multirrubro.
  Dentalink es el más completo en lo clínico y el único que documenta obras sociales para Argentina.
- **Estándar de mercado**: agenda por profesional, reserva online 24/7, recordatorios automáticos (cada vez
  más por WhatsApp) y ficha clínica con odontograma.
- **Vacíos en Argentina**: la facturación AFIP/ARCA casi no existe ("próximamente" en AgendaPro), las obras
  sociales solo aparecen documentadas en Dentalink, casi nadie publica precios en ARS y ningún proveedor
  evidencia adecuación a la Ley 27.706 de historia clínica electrónica.
- La **prevención de solapamientos, los bloqueos y los sobreturnos** casi nunca se describen públicamente.
- La evidencia es débil en parte del mercado local: 5 de los 18 sistemas solo tienen respaldo de directorios
  de terceros, y varias capacidades de IA por WhatsApp son afirmaciones comerciales sin demostración.

**MVP recomendado.** Una herramienta integral para el **odontólogo independiente**:

- **Imprescindible**: agenda con duración variable por prestación, bloqueos y prevención de solapamientos;
  reserva online por link con cancelación y reprogramación autogestionadas; recordatorios; ficha con
  anamnesis, odontograma y evoluciones; auditoría y exportación de datos.
- **Diferenciador**: lista de espera, recall y seguimiento de ausentes, obras sociales y precios publicados en ARS.
- **Etapas posteriores**: facturación ARCA, periodontograma, presupuestos e imágenes, multi sucursal y API pública.

El Discovery confirmado con el equipo acotó la v1 a recordatorios **solo por email**, con WhatsApp y Mercado
Pago para la v2 (ver Anexo, puntos 5 y 6).

## Consigna y metodología

Se le encargó al agente, como analista de mercado SaaS de salud digital y odontología, una búsqueda
exhaustiva de sistemas odontológicos que gestionen turnos y agenda de pacientes, priorizando Argentina,
luego Latinoamérica y finalmente soluciones internacionales relevantes. Solo se aceptaron fuentes
verificables (sitios oficiales, páginas de precios, centros de ayuda, marketplaces y reseñas), con URL y
fecha de consulta.

- **Fecha de consulta de todas las fuentes**: 2026-11-16
- **Alcance**: 18 sistemas (Argentina primero, luego Latinoamérica e internacionales). Detalle por sistema en `discovery/sources/<slug>.md`.
- **Regla de evidencia**: solo se registran funciones demostradas o descritas en la fuente consultada. "(afirmación comercial)" marca declaraciones del proveedor sin documentación; "No evidenciado" indica ausencia en las fuentes revisadas. Un "No evidenciado" no prueba que la función no exista; puntúa como ausente.
Aclaraciones sobre candidatos del encargo: Dentaltix es un marketplace de insumos dentales y no ofrece software de agenda (excluido). Clinicminds se posiciona en clínicas estéticas/MedSpas, no en odontología (excluido). Flexibook, Odontosys (hallado como ODONTSIS), Turnito, DrApp, Encuadrado, Bewe, Siempre Turnos, MiTurno, Gesden, Medicloud y Odontología Online no pudieron verificarse con fuentes oficiales; solo ODONTSIS tiene un perfil institucional y se incluyó.

El resultado estructurado del checklist de once puntos de `discovery-research` se encuentra en
`docs/discovery/checklist-discovery.md` (se reproduce como Anexo al final de este informe).

## A. Tabla comparativa (ordenada por relevancia para Argentina)

### A.1 Resumen

| # | Producto / Proveedor | País · Segmento | Reserva online | Recordatorios | Fuente y fecha |
|---|---|---|---|---|---|
| 1 | **AgendaPro** / AgendaPro Inc | Argentina (versión /ar del sitio, con contacto +54 11) · Multirrubro (belleza, estética, salud, fitness) | Reserva online 24/7 (sitio, Google, Instagram, Facebook, link) | Recordatorios automáticos por WhatsApp y email | https://agendapro.com/ar · consultada el 2026-11-16 |
| 2 | **Dentalink** / Dentalink, operada por Engenis SpA (pie de página) | No indicado explícitamente en el sitio · Clínicas y profesionales dentales: odontólogo independiente, consultorio, clínica | Agenda online 24/7 | Recordatorios y confirmaciones automáticos por WhatsApp y email | https://web-es.softwaredentalink.com/ · consultada el 2026-11-16 |
| 3 | **La Historia Clínica Digital** / No evidenciado (el directorio no indica empresa). | Argentina (según el público objetivo descrito en el directorio de terceros). · Consultorio propio o grupos pequeños de profesionales de la salud, incluida odontología (categoría relacionada del directorio) | Portal de pacientes | Recordatorios por WhatsApp | https://www.comparasoftware.com/la-historia-clinica-digital · consultada el 2026-11-16 |
| 4 | **Medilink (Latam)** / Medilink | No indicado en el sitio (teléfonos de Chile, Colombia y México) · Consultas individuales, centros médicos y de salud | Agendamiento online | Recordatorios por email y WhatsApp | https://www.softwaremedilink.com/ · consultada el 2026-11-16 |
| 5 | **Consultorios.AR (Consultorios Argentinos)** / No evidenciado. | Argentina (nombre y folleto en dominio aac.org.ar, según resultado de búsqueda). · Consultorios pequeños (folleto) | No evidenciado. | No evidenciado. | https://aac.org.ar/beneficios/imagenes/consultorios2.pdf · consultada el 2026-11-16 |
| 6 | **Reservo** / No evidenciado (la página no muestra razón social). | Contradictorio: el dominio es .cl pero el contenido consultado dice 'consultorios argentinos', AFIP/ARCA y pesos argentinos. · Consultorios médicos en general | Turnero online | No evidenciado. | https://www.reservo.cl/ · consultada el 2026-11-16 |
| 7 | **DentalSoft** / No evidenciado. | Argentina según resumen de búsqueda del listado GetApp (el fetch directo devolvió HTTP 403). · Clínicas dentales y odontólogos individuales (según listado). | No evidenciado. | No evidenciado. | https://www.getapp.com/all-software/a/dentalsoft/ · consultada el 2026-11-16 |
| 8 | **ODONTSIS (Odontosys)** / ODONTSIS Software Especializado (perfil de ConnectAmericas, aún 'no verificado'). | Argentina · Consultorios odontológicos | No evidenciado. | No evidenciado. | https://connectamericas.com/node/5979 · consultada el 2026-11-16 |
| 9 | **Doctoralia (Doctoralia Pro / para especialistas)** / Doctoralia / Docplanner (Clinic Cloud pertenece a Doctoralia España SL, según su sitio). | Disponible en Argentina, Brasil, Chile, Colombia, España, Perú, Polonia, Turquía (Capterra, vía resumen de búsqueda). · Médicos y especialistas | Reserva online y recordatorios (resumen de terceros) | Recordatorios automáticos | https://www.capterra.com/p/253301/Doctoralia-Pro/ · consultada el 2026-11-16 |
| 10 | **Clinic Cloud** / Doctoralia España SL (pie de página). | España · Clínicas y consultas propias | Reserva online (Doctoralia) | Recordatorios y confirmaciones automáticas. | https://www.clinic-cloud.com/ · consultada el 2026-11-16 |
| 11 | **Medesk** / Medesk (© 2026). | No evidenciado (declara uso en más de 50 países). · Clínicas dentales (página de odontología) | Cita en línea eligiendo fecha y clínica | Confirmaciones y alertas | https://www.medesk.net/es/solutiones/odontologia · consultada el 2026-11-16 |
| 12 | **DENTIDESK** / DENTIDESK (oficina en Providencia, Santiago | Chile · Clínicas dentales, centros médicos, instituciones educacionales y sindicatos. | Agendamiento online (integración, sin detalle) | Recordatorios personalizados en agenda | https://dentidesk.com · consultada el 2026-11-16 |
| 13 | **Dentally** / Henry Schein One UK Ltd (empresa 02940919, Gillingham, Kent). | Reino Unido. · Clínicas dentales (versiones locales para Australia, Canadá, Irlanda, Nueva Zelanda). | Reserva online por portal | Mensajería automática, recordatorios y confirmaciones. | https://www.dentally.com/ · consultada el 2026-11-16 |
| 14 | **Curve Dental** / CD Newco, LLC (Alpharetta, Georgia). | Estados Unidos (dirección) · Consultorio y grupos dentales. | Reserva online y recordatorios (sin detalle de canales). | Recordatorios. | https://www.curvedental.com/ · consultada el 2026-11-16 |
| 15 | **NexHealth** / NexHealth (© 2026, entidad legal No evidenciada). | Estados Unidos (soporte y teléfono estadounidense). · Prácticas dentales y médicas | Reserva online, recall, lista de espera. | Recordatorios SMS/email | https://www.nexhealth.com/ · consultada el 2026-11-16 |
| 16 | **Open Dental** / Open Dental Software. | No evidenciado en la página consultada. · Prácticas dentales | Web Sched (sin detalle). | Mensajería automatizada (sin detalle). | https://www.opendental.com/ · consultada el 2026-11-16 |
| 17 | **Dentrix (Henry Schein One)** / Henry Schein One (American Fork, Utah). | Estados Unidos · Prácticas dentales. | Reserva online. | Recordatorios automáticos, encuestas. | https://www.dentrix.com/ · consultada el 2026-11-16 |
| 18 | **Weave** / Weave Communications, Inc | Estados Unidos. · Pequeñas empresas, foco en salud. | Solicitudes de turnos en web | Recordatorios, recall, confirmaciones por texto. | https://www.getweave.com/ · consultada el 2026-11-16 |

La puntuación de cada sistema está en la sección B. El relevamiento completo de cada sistema
(agenda, turnos digitales, automatización, clínica, administración, integraciones, operación,
seguridad, modelo comercial, fortalezas y adopción) está en A.2.

### A.2 Fichas por sistema

### A.2.1 AgendaPro

- **URL oficial**: https://agendapro.com/ar
- **Empresa**: AgendaPro Inc. (pie de página © 2026)
- **País de origen**: Argentina (versión /ar del sitio, con contacto +54 11); presencia declarada en otros países de la región. País de origen corporativo: No evidenciado en la página consultada.
- **Segmento**: Multirrubro (belleza, estética, salud, fitness). Odontología aparece como rubro de Salud con página propia (/ar/dental/software-odontologico). Odontólogo independiente, consultorio, clínica, centro multidisciplinario; cadena y laboratorio: No evidenciado.
- **Modalidad**: SaaS en la nube con apps móviles iOS y Android (según la página).

**Agenda.** Agenda por profesional; vista diaria y por sucursal; agenda por odontólogo, práctica y sillón con reportes de ocupación por sillón (página dental). Duración variable por prestación, bloqueos, sobreturnos y prevención de solapamientos: No evidenciado.

**Turnos digitales.** Reserva online 24/7 (sitio, Google, Instagram, Facebook, link). Agente IA por WhatsApp agenda turnos (afirmación comercial). Confirmación y cancelación con un clic. Reprogramación y lista de espera: No evidenciado.

**Automatización.** Recordatorios automáticos por WhatsApp y email; el asistente insiste si el paciente no confirma; envío 1, 2 o 3 días antes (FAQ). Seguimiento de presupuestos pendientes (afirmación comercial). Marketing por email incluido en planes (500/1.000 correos/mes). Seguimiento de ausentes y recuperación específica dental: No evidenciado.

**Clínica.** Odontograma digital (estado por pieza, sesión por sesión); anamnesis, evoluciones, alergias, medicamentos, radiografías en la ficha; firmas electrónicas; presupuestos; tratamientos en cuotas con control de saldos. Periodontograma: No evidenciado. Plan de tratamiento clínico explícito: No evidenciado. Visor/almacenamiento de imágenes: No evidenciado.

**Administración.** Caja y cobros; Mercado Pago, link de pago, QR, efectivo; seña al reservar; comisiones e inventario (plan Full); reportes. Obras sociales/prepagas: No evidenciado (solo etiqueta 'Prepaga' en una captura). Facturación AFIP/ARCA: 'próximamente' según página de planes.

**Integraciones.** WhatsApp (Meta; el comercio paga los mensajes con su número), Google Calendar y Maps, Mercado Pago, Instagram/Facebook, API pública (US$200/mes), sitio propio de reservas. AFIP/ARCA: próximamente. Radiología: No evidenciado. Firma electrónica en ficha.

**Operación.** Sucursales (vista por sucursal); acceso restringido por profesional, sistema de roles No evidenciado; auditoría, exportación de datos, soporte y capacitación: No evidenciado.

**Seguridad y cumplimiento.** La página dental declara servidores AWS, cifrado, respaldos automáticos y cita las leyes 25.326 y 26.529 (afirmación comercial, sin documentación técnica verificada). Menciona una certificación CENS que corresponde a Chile. Ley 27.706: No evidenciado. Trazabilidad/auditoría: No evidenciado.

**Modelo comercial.** Prueba gratis: 14 días según la home. Plan gratis permanente: no. Precios publicados en ARS con IVA; cobro mensual (anual con 15% de ahorro); precio congelado 12 meses; extras visibles listados arriba.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Precios públicos en ARS; Mercado Pago y seña; reservas desde Google/Instagram; sillón como eje de agenda; WhatsApp. - **Limitaciones**: Sin facturación ARCA (próximamente); periodontograma y obras sociales No evidenciados; producto horizontal; precios de Salud no listados. - **Diferenciales evidentes**: Dice diferenciarse por reservas desde Google/Instagram, agentes de IA por WhatsApp y cobro de seña con Mercado Pago (afirmación comercial). Evidencia visible: precios públicos en ARS.

**Evidencia de adopción.** Afirma más de 30.000 negocios, más de 6.000 en marketplace, 190 M de turnos (unidad poco clara), 4,8 con más de 1.200 reseñas (afirmaciones comerciales no verificadas). Reseñas verificables: revisar Capterra.

**Fuentes consultadas:**

- https://agendapro.com/ar (consultado 2026-11-16) - Home: segmentos, reservas, WhatsApp, Mercado Pago, clientes, 'desde $13.900'
- https://agendapro.com/ar/planes (consultado 2026-11-16) - Planes, precios ARS, extras, API US$200
- https://agendapro.com/ar/dental/software-odontologico (consultado 2026-11-16) - Funciones dentales: odontograma, sillón, recordatorios, seguridad

### A.2.2 Dentalink

- **URL oficial**: https://web-es.softwaredentalink.com/
- **Empresa**: Dentalink, operada por Engenis SpA (pie de página); familia Medilink/Healthatom (según sitio de Medilink).
- **País de origen**: No indicado explícitamente en el sitio. La forma 'SpA' es chilena y el teléfono de contacto de la web es de España (+34); presencia declarada en más de 20 países. Sitio de ayuda con módulo exclusivo para Argentina.
- **Segmento**: Clínicas y profesionales dentales: odontólogo independiente, consultorio, clínica; grupo dental (testimonio). Cadena y laboratorio: No evidenciado.
- **Modalidad**: SaaS 100% web, sin servidores físicos (según la página). App móvil: No evidenciado.

**Agenda.** Control de agenda con reprogramación inteligente de huecos. Múltiples profesionales y sillones/boxes: se menciona 'gabinetes' en un testimonio; detalle No evidenciado. Duración variable, bloqueos, sobreturnos, solapamientos: No evidenciado.

**Turnos digitales.** Agenda online 24/7; reservas desde redes sociales (según un testimonio); confirmaciones por WhatsApp; Contact Center IA reprograma y confirma (afirmación comercial); tareas automáticas de reagenda. Link de reserva y lista de espera: No evidenciado.

**Automatización.** Recordatorios y confirmaciones automáticos por WhatsApp y email; recordatorios de seguimiento y revisiones; encuestas y campañas por email en plan Titanium; tareas de reagenda de anuladas o no asistidas.

**Clínica.** Odontograma interactivo, periodontograma, imágenes y documentos, análisis de RX con IA (comercial), presupuestos con esquema facial y simulador, módulos de ortodoncia y estética facial (Titanium), consentimientos y firmas (opcional). Anamnesis y plan de tratamiento: No evidenciado explícitamente.

**Administración.** Caja y cobros en tiempo real; control de pagos; gastos e inventario (Pro); reportes de gestión (Pro). Obras sociales con cobros y conciliaciones (ayuda Argentina). Mercado Pago, facturación ARCA, señas: No evidenciado.

**Integraciones.** WhatsApp y email (Business API: No evidenciado). Telemedicina y firma documental (opcionales). Google Calendar, API pública, Mercado Pago, ARCA, radiología externa: No evidenciado.

**Operación.** Roles: No evidenciado (un testimonio menciona distintos usuarios). Sucursales: No evidenciado explícitamente. Exportación: reportes Excel. Soporte: email, teléfono y acompañamiento humano sin costo extra.

**Seguridad y cumplimiento.** Declara cumplimiento RGPD (Europa), trazabilidad y registros auditables, 100% web. Normativa argentina (Ley 25.326, 26.529, 27.706): No evidenciado en el sitio.

**Modelo comercial.** Sin precios publicados; sin plan gratis; prueba por acuerdo con ventas; sin permanencia.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Cobertura clínica odontológica más completa entre los sitios verificados de la región (periodontograma, RX, presupuestos); módulo de obras sociales y receta electrónica documentado para Argentina. - **Limitaciones**: Precio no publicado; facturación y compliance orientados a España; sin evidencia de Mercado Pago ni ARCA. - **Diferenciales evidentes**: Ofrece periodontograma, análisis de RX con IA, contact center IA y módulo de obras sociales para Argentina (afirmaciones comerciales, salvo el módulo de ayuda, que es documentación oficial).

**Evidencia de adopción.** Afirma más de 15.000 clientes, 12 millones de pacientes y 45 millones de citas/año (afirmaciones comerciales). Siete testimonios propios en el sitio.

**Fuentes consultadas:**

- https://web-es.softwaredentalink.com/ (consultado 2026-11-16) - Home: funciones, cifras, seguridad
- https://web-es.softwaredentalink.com/en/planes (consultado 2026-11-16) - Planes Esencial/Pro/Titanium, sin precios, condiciones
- https://ayuda.softwaredentalink.com/es/collections/19196578-modulo-exclusivo-para-argentina (consultado 2026-11-16) - Módulo Argentina: obras sociales y receta electrónica
- https://www.capterra.com/p/196361/Dentalink/ (consultado 2026-11-16) - No cargó (HTTP 403)

### A.2.3 La Historia Clínica Digital

- **URL oficial**: https://www.comparasoftware.com/la-historia-clinica-digital
- **Empresa**: No evidenciado (el directorio no indica empresa).
- **País de origen**: Argentina (según el público objetivo descrito en el directorio de terceros).
- **Segmento**: Consultorio propio o grupos pequeños de profesionales de la salud, incluida odontología (categoría relacionada del directorio). Clínica, cadena, laboratorio: No evidenciado.
- **Modalidad**: SaaS en la nube, acceso por navegador (directorio de terceros).

**Agenda.** Agenda con portal de pacientes (detalle No evidenciado). Multiprofesional, sillones, bloqueos, sobreturnos, solapamientos: No evidenciado.

**Turnos digitales.** Portal de pacientes; recordatorios por WhatsApp. Reserva 24/7, link, redes, chatbot, cancelación, reprogramación, lista de espera: No evidenciado.

**Automatización.** Recordatorios por WhatsApp. Ausentes, recuperación, controles periódicos, campañas: No evidenciado.

**Clínica.** Historia clínica por especialidad; IA de evolución sin almacenar audio (comercial). Odontograma, periodontograma, imágenes, presupuestos, planes: No evidenciado.

**Administración.** Facturación AFIP/ARCA; control de acceso y análisis de rendimiento (directorio). Caja, señas, Mercado Pago, obras sociales: No evidenciado.

**Integraciones.** Sistema nacional de recetas (comercial), AFIP/ARCA. Resto: No evidenciado.

**Operación.** Control de acceso (mencionado). Sucursales, auditoría, exportación, soporte, capacitación: No evidenciado.

**Seguridad y cumplimiento.** Declara cumplimiento de Leyes 26.529 y 25.326 (afirmación comercial, no verificada). Ley 27.706: No evidenciado. Respaldo y trazabilidad: No evidenciado.

**Modelo comercial.** Prueba 15 días sin tarjeta; precio ARS publicado en FAQ del directorio; sin plan gratis.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Compliance y ARCA declarados; precio en ARS claro. - **Limitaciones**: Sin funciones odontológicas evidenciadas; evidencia solo de tercero; sin reseñas. - **Diferenciales evidentes**: IA clínica, receta digital y ARCA integrados (afirmaciones comerciales reproducidas por un directorio).

**Evidencia de adopción.** Sin reseñas verificables (0 comentarios en el directorio).

**Fuentes consultadas:**

- https://www.comparasoftware.com/la-historia-clinica-digital (consultado 2026-11-16) - Ficha de directorio de terceros: funciones, precios, compliance declarado

### A.2.4 Medilink (Latam)

- **URL oficial**: https://www.softwaremedilink.com/
- **Empresa**: Medilink; enlaza marcas hermanas Healthatom, Dentalink, Gerty y Dentalink Market.
- **País de origen**: No indicado en el sitio (teléfonos de Chile, Colombia y México). Fuentes de terceros discrepan (Chile 2009 / Ecuador).
- **Segmento**: Consultas individuales, centros médicos y de salud; software específico para kinesiología, psicología, nutrición, etc. Odontología: No evidenciado (solo enlace a Dentalink).
- **Modalidad**: SaaS en línea.

**Agenda.** Agenda por profesional; resto No evidenciado.

**Turnos digitales.** Agendamiento online; pacientes se agendan solos por IA (comercial). Resto No evidenciado.

**Automatización.** Recordatorios por email y WhatsApp; confirmaciones. Resto No evidenciado.

**Clínica.** Fichas por especialidad. Odontograma y demás: No evidenciado.

**Administración.** Boletas, facturadores electrónicos, cajas. ARCA, Mercado Pago, obras sociales: No evidenciado.

**Integraciones.** Facturadores electrónicos, reporte RIPS (Colombia). Resto No evidenciado.

**Operación.** No evidenciado (solo enlaces a políticas de privacidad y acuerdo de datos).

**Seguridad y cumplimiento.** Enlaza política de privacidad y acuerdo de procesamiento de datos; medidas técnicas y normativa argentina: No evidenciado.

**Modelo comercial.** Precios de planes no publicados; USD 50/mes el contact center; sin permanencia.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Marca consolidada en la región; IA de atención. - **Limitaciones**: Sin evidencia dental ni fiscal argentina. - **Diferenciales evidentes**: IA de contact center (afirmación comercial).

**Evidencia de adopción.** Declara más de 15.000 clínicas y 100.000 profesionales (afirmación comercial).

**Fuentes consultadas:**

- https://www.softwaremedilink.com/ (consultado 2026-11-16) - Home: funciones, precios del contact center, cifras

### A.2.5 Consultorios.AR (Consultorios Argentinos)

- **URL oficial**: https://aac.org.ar/beneficios/imagenes/consultorios2.pdf
- **Empresa**: No evidenciado.
- **País de origen**: Argentina (nombre y folleto en dominio aac.org.ar, según resultado de búsqueda).
- **Segmento**: Consultorios pequeños (folleto); odontología: No evidenciado.
- **Modalidad**: Plataforma web (folleto); detalle No evidenciado.

**Agenda.** Varios consultorios desde una cuenta; vistas de agenda. Resto No evidenciado.

**Turnos digitales.** No evidenciado.

**Automatización.** No evidenciado.

**Clínica.** Historia clínica flexible con adjuntos de imágenes; odontograma No evidenciado.

**Administración.** No evidenciado.

**Integraciones.** No evidenciado.

**Operación.** Multi-consultorio. Resto No evidenciado.

**Seguridad y cumplimiento.** ISO 27001 como estándar de construcción (afirmación comercial no verificada). Leyes argentinas: No evidenciado.

**Modelo comercial.** No evidenciado.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Origen argentino declarado. - **Limitaciones**: Sin evidencia verificable suficiente. - **Diferenciales evidentes**: ISO 27001 declarado (afirmación comercial).

**Evidencia de adopción.** No evidenciado.

**Fuentes consultadas:**

- https://aac.org.ar/beneficios/imagenes/consultorios2.pdf (consultado 2026-11-16) - Folleto promocional (solo resumen de búsqueda; no abierto)

### A.2.6 Reservo

- **URL oficial**: https://www.reservo.cl/
- **Empresa**: No evidenciado (la página no muestra razón social).
- **País de origen**: Contradictorio: el dominio es .cl pero el contenido consultado dice 'consultorios argentinos', AFIP/ARCA y pesos argentinos.
- **Segmento**: Consultorios médicos en general; odontología: No evidenciado.
- **Modalidad**: Web/SaaS (turnero online); detalle No evidenciado.

**Agenda.** Turnero online (sin detalle).

**Turnos digitales.** Turnero online. Resto No evidenciado.

**Automatización.** No evidenciado.

**Clínica.** Ficha clínica electrónica (sin detalle). Odontograma: No evidenciado.

**Administración.** Mercado Pago y AFIP/ARCA (mencionados sin detalle).

**Integraciones.** Mercado Pago, AFIP/ARCA.

**Operación.** No evidenciado.

**Seguridad y cumplimiento.** No evidenciado.

**Modelo comercial.** No evidenciado.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Menciona Mercado Pago y ARCA. - **Limitaciones**: Información mínima; sin odontología. - **Diferenciales evidentes**: No evidenciado.

**Evidencia de adopción.** No evidenciado.

**Fuentes consultadas:**

- https://www.reservo.cl/ (consultado 2026-11-16) - Home consultada: turnero, Mercado Pago, ARCA

### A.2.7 DentalSoft

- **URL oficial**: https://www.getapp.com/all-software/a/dentalsoft/
- **Empresa**: No evidenciado.
- **País de origen**: Argentina según resumen de búsqueda del listado GetApp (el fetch directo devolvió HTTP 403).
- **Segmento**: Clínicas dentales y odontólogos individuales (según listado).
- **Modalidad**: Plataforma en la nube (según listado).

**Agenda.** No evidenciado.

**Turnos digitales.** No evidenciado.

**Automatización.** No evidenciado.

**Clínica.** Historia clínica (sin detalle). Resto No evidenciado.

**Administración.** Facturación (sin detalle).

**Integraciones.** No evidenciado.

**Operación.** No evidenciado.

**Seguridad y cumplimiento.** No evidenciado.

**Modelo comercial.** No evidenciado.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Orientación declarada a Argentina. - **Limitaciones**: Sin evidencia verificable. - **Diferenciales evidentes**: No evidenciado.

**Evidencia de adopción.** No evidenciado.

**Fuentes consultadas:**

- https://www.getapp.com/all-software/a/dentalsoft/ (consultado 2026-11-16) - Listado no accesible (HTTP 403); solo resumen de búsqueda

### A.2.8 ODONTSIS (Odontosys)

- **URL oficial**: https://connectamericas.com/node/5979
- **Empresa**: ODONTSIS Software Especializado (perfil de ConnectAmericas, aún 'no verificado').
- **País de origen**: Argentina; fundada en 1992; 3 empleados (perfil).
- **Segmento**: Consultorios odontológicos; clientes listados: Prodontomed, Sindicato Empleados Públicos de Córdoba, Odontología 24 hs, Arquitectura Bucal SRL.
- **Modalidad**: No evidenciado.

**Agenda.** No evidenciado.

**Turnos digitales.** No evidenciado.

**Automatización.** No evidenciado.

**Clínica.** No evidenciado.

**Administración.** Control de gastos/insumos/ingresos (declarado). Resto No evidenciado.

**Integraciones.** No evidenciado.

**Operación.** Soporte técnico, garantía y capacitación personalizada (declarado).

**Seguridad y cumplimiento.** No evidenciado.

**Modelo comercial.** No evidenciado.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Trayectoria declarada desde 1992. - **Limitaciones**: Sin evidencia de funciones de turnos. - **Diferenciales evidentes**: No evidenciado.

**Evidencia de adopción.** Cuatro clientes listados en el perfil (no verificados).

**Fuentes consultadas:**

- https://connectamericas.com/node/5979 (consultado 2026-11-16) - Perfil institucional: país, año, clientes

### A.2.9 Doctoralia (Doctoralia Pro / para especialistas)

- **URL oficial**: https://www.capterra.com/p/253301/Doctoralia-Pro/
- **Empresa**: Doctoralia / Docplanner (Clinic Cloud pertenece a Doctoralia España SL, según su sitio).
- **País de origen**: Disponible en Argentina, Brasil, Chile, Colombia, España, Perú, Polonia, Turquía (Capterra, vía resumen de búsqueda).
- **Segmento**: Médicos y especialistas; consultorio. Odontólogos: No evidenciado en fuente abierta. Resto No evidenciado.
- **Modalidad**: Directorio de pacientes con agenda SaaS y app móvil (App Store 'Doctoralia para especialistas', resultado de búsqueda).

**Agenda.** No evidenciado.

**Turnos digitales.** Reserva online y recordatorios (resumen de terceros). Resto No evidenciado.

**Automatización.** Recordatorios automáticos. Resto No evidenciado.

**Clínica.** Expediente médico (Plus). Odontograma: No evidenciado.

**Administración.** No evidenciado.

**Integraciones.** Integración con Clinic Cloud (resumen de terceros).

**Operación.** Permanencia de 12 meses (Pabau).

**Seguridad y cumplimiento.** No evidenciado.

**Modelo comercial.** Precios en euros mensuales sin IVA, discrepantes entre Capterra y Pabau; sin precios ARS.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Demanda de pacientes (directorio). - **Limitaciones**: Precios en euros; sin evidencia dental ni fiscal argentina. - **Diferenciales evidentes**: Alcance de pacientes del directorio.

**Evidencia de adopción.** No evidenciado.

**Fuentes consultadas:**

- https://www.capterra.com/p/253301/Doctoralia-Pro/ (consultado 2026-11-16) - Referencia de Capterra (solo resumen de búsqueda)
- https://pabau.com/es/blog/precios-de-doctoralia-pro/ (consultado 2026-11-16) - Reseña de competidor, no neutral

### A.2.10 Clinic Cloud

- **URL oficial**: https://www.clinic-cloud.com/
- **Empresa**: Doctoralia España SL (pie de página).
- **País de origen**: España; grupo presente en 13 países incluido México.
- **Segmento**: Clínicas y consultas propias; multiprofesional y multisede. Módulo dental: No evidenciado en la página consultada.
- **Modalidad**: SaaS en la nube.

**Agenda.** Multiprofesional y multisede; optimiza huecos. Sillones, duración, bloqueos, sobreturnos: No evidenciado.

**Turnos digitales.** Reserva online (Doctoralia); WhatsApp. Resto No evidenciado.

**Automatización.** Recordatorios y confirmaciones automáticas.

**Clínica.** Odontograma, imágenes, presupuestos: No evidenciado.

**Administración.** Facturas, bonos, mutuas, cobros (modelo España). ARCA y Mercado Pago: No evidenciado.

**Integraciones.** Doctoralia; integración con aseguradoras SEOGA 'próximamente'.

**Operación.** Multisede. Resto No evidenciado.

**Seguridad y cumplimiento.** Cumplimiento RGPD, cifrado, trazabilidad (afirmación comercial). Normativa argentina: No evidenciado.

**Modelo comercial.** Prueba gratis sin tarjeta (duración No evidenciada); 50% de descuento temporal; sin precios.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Marca Doctoralia, IA, WhatsApp. - **Limitaciones**: Fiscalidad española; sin odontología evidenciada. - **Diferenciales evidentes**: Integración con Doctoralia.

**Evidencia de adopción.** '+12.000 doctores' (afirmación comercial).

**Fuentes consultadas:**

- https://www.clinic-cloud.com/ (consultado 2026-11-16) - Home: funciones, RGPD, cifras
- https://clinic-cloud.com/software-clinicas-dentales/ (consultado 2026-11-16) - HTTP 404

### A.2.11 Medesk

- **URL oficial**: https://www.medesk.net/es/solutiones/odontologia
- **Empresa**: Medesk (© 2026).
- **País de origen**: No evidenciado (declara uso en más de 50 países).
- **Segmento**: Clínicas dentales (página de odontología); resto No evidenciado.
- **Modalidad**: SaaS en la nube.

**Agenda.** Agenda inteligente; huecos; iCal y Google Calendar. Resto No evidenciado.

**Turnos digitales.** Cita en línea eligiendo fecha y clínica. WhatsApp: No evidenciado.

**Automatización.** Confirmaciones y alertas; mensajes automáticos.

**Clínica.** Odontograma digital. Periodontograma, imágenes, presupuestos: No evidenciado.

**Administración.** Facturación y pagos; factura electrónica (país No evidenciado).

**Integraciones.** iCal, Google Calendar.

**Operación.** Roles; backups. Exportación: No evidenciado.

**Seguridad y cumplimiento.** RGPD, backups, eliminación de datos (afirmación comercial). Normativa argentina: No evidenciado.

**Modelo comercial.** Demo gratuita; sin precios.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Roles y backup declarados; odontograma. - **Limitaciones**: Sin WhatsApp ni normativa Argentina. - **Diferenciales evidentes**: No evidenciado.

**Evidencia de adopción.** 'Más de 50 países' (comercial).

**Fuentes consultadas:**

- https://www.medesk.net/es/solutiones/odontologia (consultado 2026-11-16) - Página odontología: agenda, odontograma, RGPD

### A.2.12 DENTIDESK

- **URL oficial**: https://dentidesk.com
- **Empresa**: DENTIDESK (oficina en Providencia, Santiago; CEO Carolina Calderón según el sitio).
- **País de origen**: Chile; clientes declarados en República Dominicana y Cabo Verde.
- **Segmento**: Clínicas dentales, centros médicos, instituciones educacionales y sindicatos.
- **Modalidad**: SaaS en la nube.

**Agenda.** Agendar, modificar y reprogramar; multisucursal. Sillones, bloqueos, sobreturnos: No evidenciado.

**Turnos digitales.** Agendamiento online (integración, sin detalle). WhatsApp automático: futuro.

**Automatización.** Recordatorios personalizados en agenda; canal No evidenciado.

**Clínica.** Odontograma; ficha de Periodoncia; consentimiento firmado. Imágenes y presupuestos: No evidenciado.

**Administración.** Boletas y facturas (SII Chile, Alegra en RD); más de 20 reportes. ARCA y Mercado Pago: No evidenciado.

**Integraciones.** SII, Alegra, agendamiento online.

**Operación.** Multisucursal. Roles: No evidenciado.

**Seguridad y cumplimiento.** Declara plataforma 'segura' sin detalle. Normativa argentina: No evidenciado.

**Modelo comercial.** Prueba 15 días; precios no publicados.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Odontograma flexible; multisucursal. - **Limitaciones**: Fiscal chilena; WhatsApp futuro. - **Diferenciales evidentes**: Odontograma multi-nomenclatura.

**Evidencia de adopción.** Sin cifras; testimonio de Cabo Verde.

**Fuentes consultadas:**

- https://dentidesk.com (consultado 2026-11-16) - Home: funciones, integraciones, prueba

### A.2.13 Dentally

- **URL oficial**: https://www.dentally.com/
- **Empresa**: Henry Schein One UK Ltd (empresa 02940919, Gillingham, Kent).
- **País de origen**: Reino Unido.
- **Segmento**: Clínicas dentales (versiones locales para Australia, Canadá, Irlanda, Nueva Zelanda).
- **Modalidad**: SaaS en la nube.

**Agenda.** Agenda y diario; multiprofesional y sillones No evidenciado.

**Turnos digitales.** Reserva online por portal; lista de espera; recordatorios y confirmaciones.

**Automatización.** Mensajería automática, recordatorios y confirmaciones.

**Clínica.** Charting; imágenes IA. Periodontograma, presupuestos: No evidenciado.

**Administración.** Pagos vía portal/partners. Facturación: No evidenciado.

**Integraciones.** Marketplace de partners, NHS, Developer Hub (detalle API No evidenciado).

**Operación.** No evidenciado.

**Seguridad y cumplimiento.** Enlace a página de seguridad sin detalle. Normativa argentina: No evidenciado.

**Modelo comercial.** Demo; sin precios.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Lista de espera; portal. - **Limitaciones**: Mercado UK. - **Diferenciales evidentes**: Lista de espera y portal.

**Evidencia de adopción.** Tres testimonios propios.

**Fuentes consultadas:**

- https://www.dentally.com/ (consultado 2026-11-16) - Home: funciones y empresa

### A.2.14 Curve Dental

- **URL oficial**: https://www.curvedental.com/
- **Empresa**: CD Newco, LLC (Alpharetta, Georgia).
- **País de origen**: Estados Unidos (dirección); clientes en EE.UU. y Canadá.
- **Segmento**: Consultorio y grupos dentales.
- **Modalidad**: Nube desde 2004, en navegador sin servidor local.

**Agenda.** Scheduling (sin detalle).

**Turnos digitales.** Reserva online y recordatorios (sin detalle de canales).

**Automatización.** Recordatorios.

**Clínica.** Charting, perio, imágenes, planes de tratamiento.

**Administración.** Facturación a seguros y pacientes, Curve Pay, membresías.

**Integraciones.** Página de partners sin nombres visibles.

**Operación.** No evidenciado.

**Seguridad y cumplimiento.** Infraestructura declarada compatible con HIPAA (afirmación comercial). Normativa argentina: No evidenciado.

**Modelo comercial.** Demo; sin precios.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Clínica completa. - **Limitaciones**: Orientado a EE.UU. - **Diferenciales evidentes**: Perio charting y migración de datos.

**Evidencia de adopción.** 100.000+ usuarios, 6.000+ sedes, 4,6/5 con 500+ reseñas (afirmación comercial).

**Fuentes consultadas:**

- https://www.curvedental.com/ (consultado 2026-11-16) - Home: funciones, cifras, HIPAA

### A.2.15 NexHealth

- **URL oficial**: https://www.nexhealth.com/
- **Empresa**: NexHealth (© 2026, entidad legal No evidenciada).
- **País de origen**: Estados Unidos (soporte y teléfono estadounidense).
- **Segmento**: Prácticas dentales y médicas; capa de comunicación sobre otros sistemas.
- **Modalidad**: SaaS; capa sobre PMS existentes.

**Agenda.** Depende del PMS.

**Turnos digitales.** Reserva online, recall, lista de espera.

**Automatización.** Recordatorios SMS/email; recall.

**Clínica.** No evidenciado (no es PMS).

**Administración.** Pagos y tarjeta en archivo; verificación de seguro.

**Integraciones.** Unos 100 PMS, API pública, Google Maps.

**Operación.** No evidenciado.

**Seguridad y cumplimiento.** HIPAA: No evidenciado en la página.

**Modelo comercial.** Sin precios publicados.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: API y sincronización; booking desde Google. - **Limitaciones**: Sin clínica; foco EE.UU. - **Diferenciales evidentes**: Sincronización con unos 100 PMS y API (afirmación comercial).

**Evidencia de adopción.** 20.000+ prácticas; 4,8 en G2 y 4,7 en Capterra (afirmación comercial).

**Fuentes consultadas:**

- https://www.nexhealth.com/ (consultado 2026-11-16) - Home: funciones, integraciones, cifras

### A.2.16 Open Dental

- **URL oficial**: https://www.opendental.com/
- **Empresa**: Open Dental Software.
- **País de origen**: No evidenciado en la página consultada.
- **Segmento**: Prácticas dentales; resto No evidenciado.
- **Modalidad**: No evidenciado (menciona .NET y base de datos).

**Agenda.** Bloqueos (blockouts); resto No evidenciado.

**Turnos digitales.** Web Sched (sin detalle).

**Automatización.** Mensajería automatizada (sin detalle).

**Clínica.** Odontograma gráfico 3D. Resto No evidenciado.

**Administración.** Facturación y estados de cuenta (versión 26.2).

**Integraciones.** Bridges, clearinghouses.

**Operación.** No evidenciado.

**Seguridad y cumplimiento.** HIPAA: No evidenciado.

**Modelo comercial.** Precio no publicado; versión de prueba.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Personalizable. - **Limitaciones**: Sin evidencia local; sin detalle. - **Diferenciales evidentes**: Personalización e integraciones.

**Evidencia de adopción.** No evidenciado.

**Fuentes consultadas:**

- https://www.opendental.com/ (consultado 2026-11-16) - Home: funciones y versiones

### A.2.17 Dentrix (Henry Schein One)

- **URL oficial**: https://www.dentrix.com/
- **Empresa**: Henry Schein One (American Fork, Utah).
- **País de origen**: Estados Unidos; sitio para Canadá.
- **Segmento**: Prácticas dentales.
- **Modalidad**: No indicada claramente (sugiere local frente a la nube de Ascend).

**Agenda.** Herramientas de agenda (detalle No evidenciado).

**Turnos digitales.** Reserva online.

**Automatización.** Recordatorios automáticos, encuestas.

**Clínica.** Imágenes; charting. Perio y presupuestos: No evidenciado.

**Administración.** Elegibilidad, reclamos, cobros.

**Integraciones.** API Exchange, imágenes, analytics.

**Operación.** No evidenciado.

**Seguridad y cumplimiento.** Servicios de ciberseguridad y backup; sin certificaciones listadas.

**Modelo comercial.** Sin precios.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Escala. - **Limitaciones**: Foco EE.UU. - **Diferenciales evidentes**: Escala e integraciones.

**Evidencia de adopción.** 35.000+ prácticas (afirmación comercial).

**Fuentes consultadas:**

- https://www.dentrix.com/ (consultado 2026-11-16) - Home: funciones y cifras

### A.2.18 Weave

- **URL oficial**: https://www.getweave.com/
- **Empresa**: Weave Communications, Inc. (Lehi, Utah).
- **País de origen**: Estados Unidos.
- **Segmento**: Pequeñas empresas, foco en salud.
- **Modalidad**: SaaS de comunicaciones.

**Agenda.** Reserva vía PMS.

**Turnos digitales.** Solicitudes de turnos en web; IA reserva con PMS.

**Automatización.** Recordatorios, recall, confirmaciones por texto.

**Clínica.** No evidenciado (no aplica).

**Administración.** Pagos, text-to-pay, planes de pago.

**Integraciones.** PMS (sin lista).

**Operación.** No evidenciado.

**Seguridad y cumplimiento.** HIPAA: No evidenciado.

**Modelo comercial.** Sin precios.

**Fortalezas, limitaciones y diferenciales.** - **Fortalezas**: Comunicación unificada. - **Limitaciones**: Telefonía EE.UU. - **Diferenciales evidentes**: Telefonía e IA integradas.

**Evidencia de adopción.** No evidenciado.

**Fuentes consultadas:**

- https://www.getweave.com/ (consultado 2026-11-16) - Home: funciones


## B. Matriz de puntuación 0-5

Ponderaciones: Gestión de turnos y automatización 25%; Funcionalidad odontológica clínica 20%; Integraciones locales y WhatsApp 15%; Administración, cobros y facturación 15%; Experiencia del paciente 10%; Seguridad, exportación y trazabilidad 10%; Precio y facilidad de adopción 5%. Escala: 0 = no evidenciado; 1 = mención mínima; 3 = cobertura parcial evidenciada; 5 = cobertura amplia y evidenciada. Los puntajes son juicio del analista sobre la evidencia pública y deben revisarse; las afirmaciones comerciales puntúan menos que las funciones documentadas. Se ordena por total ponderado.

| Sistema | Turnos y autom. (25%) | Clínica (20%) | Integr. locales y WhatsApp (15%) | Admin/cobros (15%) | Exp. paciente (10%) | Seguridad (10%) | Precio/adopción (5%) | Total | Justificación |
|---|---|---|---|---|---|---|---|---|---|
| AgendaPro | 5 | 3 | 4 | 4 | 4 | 3 | 5 | **4.00** | Mejor cobertura verificable de turnos, WhatsApp, Mercado Pago y precios en ARS; clínica odontológica parcial y sin ARCA. |
| Dentalink | 4 | 5 | 3 | 4 | 4 | 3 | 2 | **3.85** | Más completo en clínica y con módulo de obras sociales Argentina; sin precios, ARCA ni Mercado Pago evidenciados. |
| Dentally | 4 | 3 | 3 | 2 | 4 | 2 | 1 | **3.00** | Agenda con lista de espera y portal; sin localización argentina. |
| Curve Dental | 3 | 5 | 2 | 3 | 2 | 2 | 1 | **2.95** | Cobertura clínica más completa (perio, imágenes, tratamientos); turnos genéricos y foco EE.UU. |
| La Historia Clínica Digital | 3 | 2 | 3 | 3 | 3 | 3 | 4 | **2.85** | Declara ARCA, receta y compliance argentinos con precio claro, pero la evidencia es de directorio y sin odontología específica. |
| Clinic Cloud | 4 | 1 | 3 | 2 | 3 | 3 | 2 | **2.65** | Agenda y WhatsApp fuertes; sin módulo dental ni fiscalidad argentina evidenciados. |
| Medesk | 3 | 3 | 1 | 2 | 3 | 3 | 2 | **2.50** | Agenda, odontograma y roles evidenciados; sin WhatsApp, Mercado Pago ni ARCA. |
| Dentrix (Henry Schein One) | 3 | 3 | 2 | 2 | 3 | 2 | 1 | **2.50** | Cobertura amplia pero orientada a seguros de EE.UU. |
| NexHealth | 4 | 0 | 4 | 2 | 4 | 1 | 1 | **2.45** | Referente de reserva online y API; sin módulo clínico y foco EE.UU. |
| Medilink (Latam) | 3 | 1 | 2 | 3 | 3 | 1 | 2 | **2.20** | Agenda y WhatsApp sólidos pero sin odontología ni ARCA evidenciados. |
| Doctoralia (Doctoralia Pro / para especialistas) | 3 | 1 | 2 | 1 | 4 | 1 | 3 | **2.05** | Valor principal es captación de pacientes; funciones de gestión odontológica no evidenciadas. |
| DENTIDESK | 2 | 3 | 2 | 2 | 1 | 1 | 2 | **2.00** | Odontograma y agenda básica; fiscalidad chilena y WhatsApp no disponible. |
| Open Dental | 2 | 3 | 2 | 2 | 1 | 1 | 2 | **2.00** | Evidencia parcial en página principal; sin localización argentina. |
| Weave | 3 | 0 | 2 | 3 | 3 | 1 | 1 | **1.95** | Referente de comunicación y recall; sin clínica. |
| Reservo | 2 | 1 | 3 | 2 | 2 | 0 | 1 | **1.70** | Menciona turnero, Mercado Pago y ARCA pero sin detalle ni odontología. |
| Consultorios.AR (Consultorios Argentinos) | 2 | 2 | 0 | 0 | 0 | 2 | 0 | **1.10** | Evidencia mínima de agenda multi-consultorio e historia flexible; resto no evidenciado. |
| DentalSoft | 1 | 1 | 0 | 1 | 1 | 0 | 0 | **0.70** | Sólo descripción genérica de un listado; sin evidencia de funciones. |
| ODONTSIS (Odontosys) | 0 | 0 | 0 | 1 | 0 | 0 | 0 | **0.15** | Sólo perfil institucional; ninguna función de agenda evidenciada. |

Los totales altos reflejan evidencia publicada, no calidad del producto: sistemas con sitios poco informativos (por ejemplo ODONTSIS, DentalSoft) puntúan bajo por falta de evidencia.

## C. Análisis competitivo

### Funcionalidades estándar de mercado
- Agenda por profesional con vistas diaria/semanal y reserva online 24/7 (AgendaPro, Dentalink, Dentally, Medesk, NexHealth).
- Recordatorios y confirmaciones automáticas, cada vez más por WhatsApp (AgendaPro, Dentalink, Clinic Cloud, Medilink).
- Ficha clínica con odontograma digital (AgendaPro, Dentalink, Medesk, DENTIDESK, Open Dental).
- Caja y cobros básicos, reportes y multi sucursal.

### Diferenciadores reales (evidenciados)
- Agenda con sillón como eje y reportes de ocupación por sillón (AgendaPro, página dental).
- Cobro de seña con Mercado Pago al reservar y precios públicos en ARS (AgendaPro).
- Periodontograma, módulos de ortodoncia y estética, y cobros/conciliaciones de obras sociales documentados en ayuda (Dentalink).
- Capa de reserva desde Google Maps y API sobre cualquier sistema (NexHealth, referencia internacional); lista de espera (Dentally, NexHealth).
- Agentes de IA por WhatsApp para agendar y confirmar (AgendaPro, Dentalink, Medilink; afirmación comercial, sin demostración independiente).

### Vacíos frecuentes en el mercado argentino
- Facturación AFIP/ARCA: AgendaPro la declara "próximamente"; Dentalink no la evidencia; solo La Historia Clínica Digital y Reservo la mencionan (con evidencia débil).
- Obras sociales y prepagas: solo Dentalink documenta su gestión (alta, cobros, conciliaciones); la mayoría no la evidencia.
- Precios publicados en ARS: solo AgendaPro y La Historia Clínica Digital (vía directorio); casi todos exigen cotización.
- Cumplimiento declarado de Ley 25.326 y 26.529 con documentación: solo afirmaciones comerciales (AgendaPro, La Historia Clínica Digital); Ley 27.706 no aparece en ninguna fuente de proveedor.
- Periodontograma, plan de tratamiento por etapas e imágenes integradas: raros en productos con enfoque de turnos.
- Sobreturnos, bloqueos y prevención de solapamientos casi nunca descritos públicamente.
- Evidencia de adopción verificable (reseñas) escasa para productos locales.

### Oportunidades de innovación
- Combinar agenda por sillón y duración variable por prestación con prevención explícita de solapamientos y sobreturnos controlados.
- Obras sociales/prepagas como entidad de primer nivel (coberturas, autorizaciones, liquidación) junto a facturación ARCA nativa.
- Cobro de seña y Mercado Pago integrados a la reserva, con política de cancelación visible para el paciente.
- WhatsApp Business API oficial para confirmación, reprogramación y lista de espera con cola de cancelaciones.
- Recuperación de pacientes por controles periódicos (recall) y presupuestos pendientes.
- Seguridad y trazabilidad documentadas: registro de auditoría, exportación de datos y mapeo a Leyes 25.326, 26.529 y 27.706 (validar con asesoría legal).
- Precios transparentes en ARS con plan de entrada para odontólogo independiente.

## D. Recomendación final

### Cinco competidores prioritarios para demo
1. AgendaPro: referente directo en Argentina con mayor evidencia de turnos, WhatsApp y Mercado Pago.
2. Dentalink: referente clínico odontológico y de obras sociales para Argentina.
3. La Historia Clínica Digital: declara ARCA, receta y compliance argentinos; validar con demo.
4. Medilink / Doctoralia (Clinic Cloud): jugadores regionales con contact center IA y captación de pacientes; validar su presencia dental.
5. Dentally (o NexHealth): referentes internacionales de portal de reserva y lista de espera.

### Tres referentes de UX
- AgendaPro: reserva desde Google/Instagram/link y cobro de seña.
- NexHealth: reserva desde Google Maps, recall de un clic, formularios previos.
- Dentally: portal del paciente con lista de espera.
(La valoración de UX es una hipótesis basada en descripciones públicas; requiere demo.)

### MVP sugerido
**Imprescindibles**
- Agenda multi-profesional y por sillón/box, vistas diaria y semanal, duración variable por prestación, bloqueos y prevención de solapamientos.
- Reserva online con link y confirmación/cancelación/reprogramación por el paciente.
- Recordatorios y confirmación por WhatsApp (Business API) y email.
- Ficha del paciente con anamnesis y odontograma básico; roles y permisos; registro de auditoría; exportación de datos.
- Seña con Mercado Pago.

**Diferenciadores**
- Lista de espera automática y cola de cancelaciones.
- Seguimiento de ausentes y recall periódico.
- Obras sociales/prepagas en la ficha y cobros.
- Precios publicados en ARS.

**Etapas posteriores**
- Facturación ARCA, periodontograma, presupuestos y planes de tratamiento, imágenes, firma de consentimientos, multi sucursal, campañas, API pública, adecuación documentada a Ley 27.706.

## Verificación de fuentes

Sección a cargo de **Mayra** (según la división de tareas de `CHANGES.md`). La consigna exige abrir y
comprobar personalmente al menos 5 fuentes citadas, una de cada tipo. Para cada una se registra qué
afirmaba el informe, si eso está realmente en la URL (**Sí / No / Parcial**), qué se encontró y qué se
corrigió. Las afirmaciones falsas o no respaldadas se corrigen en el informe (Ajustar) y se registran
como hallazgo.

### Fuentes verificadas manualmente

| # | Tipo | URL | Afirmación del informe | ¿Está en la URL? | Observación |
|---|---|---|---|---|---|
| 1 | Sitio oficial | https://agendapro.com/ar | AgendaPro permite reserva 24/7 desde Google, Instagram, Facebook y link, y cobra seña con Mercado Pago; declara más de 30.000 negocios. | _(completar)_ | _(completar)_ |
| 2 | Página de precios | https://agendapro.com/ar/planes | Planes Individual $7.900, Full $15.900 y PRO IA $69.900 (ARS, IVA incluido, rubro Belleza); API US$200/mes; facturación electrónica "próximamente". | _(completar)_ | _(completar)_ |
| 3 | Centro de ayuda | https://ayuda.softwaredentalink.com/es/collections/19196578-modulo-exclusivo-para-argentina | Dentalink tiene módulo para Argentina con 4 artículos: crear obra social, cobrar con obra social, conciliaciones y receta electrónica; no menciona AFIP/ARCA. | _(completar)_ | _(completar)_ |
| 4 | Página de planes del proveedor | https://web-es.softwaredentalink.com/en/planes | Dentalink ofrece Esencial, Pro y Titanium sin precios publicados, sin permanencia. | _(completar)_ | _(completar)_ |
| 5 | Página de producto dental | https://agendapro.com/ar/dental/software-odontologico | AgendaPro: odontograma digital, agenda por sillón, recordatorios por WhatsApp; periodontograma y obras sociales no se muestran; cita Leyes 25.326 y 26.529. | _(completar)_ | _(completar)_ |
| 6 | Marketplace / reseña verificada | https://www.comparasoftware.com/la-historia-clinica-digital | La Historia Clínica Digital: primer usuario ARS 50.000/mes, prueba 15 días, ARCA y Leyes 26.529/25.326 declaradas; 0 reseñas. | _(completar)_ | _(completar)_ |
| 7 | Directorio (reseñas) a completar | https://www.capterra.com/p/196361/Dentalink/ | Pendiente: no cargó (HTTP 403); verificar manualmente calificación y reseñas de Dentalink. | _(completar)_ | _(completar)_ |
| 8 | Video oficial | No evidenciado | No se revisó ningún video oficial; ninguna afirmación del informe depende de video. Se sugiere buscar demos en los canales de AgendaPro y Dentalink. | _(completar)_ | _(completar)_ |

Fecha de la verificación manual: _(completar)_

### Hallazgos del análisis

Discrepancias y hallazgos del analista (el usuario puede ampliarlos):

1. **AgendaPro, precio inconsistente**: la home indica "desde $13.900 por mes" mientras que /ar/planes muestra Individual $7.900 y Full $15.900 (promo de 3 meses a $990). Los planes de Salud, que serían los aplicables a odontología, no listan precios. Un resumen de Capterra indica desde US$19 (no verificado).
2. **AgendaPro, seguridad**: la página dental cita Leyes 25.326 y 26.529 y una certificación CENS que es chilena; es afirmación comercial sin documentación.
3. **AgendaPro, facturación**: la página de planes dice facturación electrónica "próximamente"; no hay evidencia de AFIP/ARCA operativa.
4. **Dentalink, origen no claro**: razón social Engenis SpA, teléfono de contacto de España, ayuda con módulo para Argentina; su facturación es "VeriFactu" (España) y no menciona ARCA. dentalink.global/ar no resolvió.
5. **Dentalink, precio**: el sitio no publica precios; un agregador indicó US$29 (no oficial).
6. **Reservo**: el dominio reservo.cl sirvió contenido para consultorios argentinos (ARCA, pesos); no se pudo confirmar la entidad ni el país real.
7. **Medilink, origen**: terceros discrepan (Chile 2009 / Ecuador) y el sitio no lo indica; medilink.com.ar no resolvió.
8. **Doctoralia, precio**: Capterra indica Starter 69 EUR y Pabau 89 EUR + IVA; no hay precios en ARS; doctoralia.com.ar devolvió error de conexión.
9. **La Historia Clínica Digital**: la ficha del directorio titula "Precios en México" pero muestra ARS; no hay sitio oficial ni empresa identificada.
10. **Clinic Cloud**: /software-clinicas-dentales devolvió 404; los contadores de la home mostraban "0+"; el uso en Argentina solo lo afirma un blog de un competidor (Medesk).
11. **DENTIDESK**: WhatsApp automático figura como integración futura; un resumen de terceros lo llama "líder en Latinoamérica" (afirmación comercial).
12. **Cumplimiento normativo**: ningún proveedor evidencia adecuación a la Ley 27.706; un folleto citado en una búsqueda (AAC) afirma que más del 60% de las plataformas no cumpliría la legislación argentina; no se abrió el PDF.
13. **Precios genéricos de mercado**: un artículo de Develop Argentina (contenido de un proveedor de software a medida) estima SaaS entre $15.000 y $80.000 por mes; no está atado a un producto.
14. **Candidatos no verificables**: Dentaltix (marketplace de insumos, no software), Clinicminds (estética), y los demás listados en "Limitaciones".

### Hallazgos de la verificación manual

_(Completar con lo encontrado al abrir las fuentes: afirmaciones confirmadas, corregidas o no respaldadas.)_

### Limitaciones de la investigación

- La herramienta de búsqueda es limitada y algunos dominios no resolvieron o bloquearon la consulta: dentalink.global, medilink.com.ar, consultorios.ar (error de DNS); doctoralia.com.ar y flexibook.com (conexión rechazada); Capterra, GetApp y Clinicminds (HTTP 403); clinic-cloud.com/software-clinicas-dentales (404).
- Se consultaron páginas principales; funciones, precios por rubro Salud, ayuda técnica y videos pueden estar en subpáginas, detrás de login o cargadas por JavaScript y no se vieron.
- Sin acceso a demos, cuentas de prueba ni sitios con contenido gated; las funciones no mostradas figuran como "No evidenciado".
- Para DentalSoft, Doctoralia y Consultorios.AR la evidencia proviene de resúmenes de búsqueda o directorios de terceros, no de sitios oficiales.
- Reseñas verificables (Capterra, G2, App Store, Google Play) no se pudieron abrir; la adopción se reporta solo como afirmación comercial.
- No se verificaron Turnito, DrApp, Encuadrado, Bewe, Siempre Turnos, MiTurno, Gesden, Medicloud, Odontología Online, Flexibook ni Clinicminds (este último con foco estético).
- Los puntajes de la matriz son juicio del analista y dependen de la evidencia disponible, no de pruebas de uso.

## Referencias

Todas las fuentes fueron consultadas el 2026-11-16, salvo indicación contraria.

1. **AgendaPro** — Home: segmentos, reservas, WhatsApp, Mercado Pago, clientes, 'desde $13.900'. https://agendapro.com/ar (consultado el 2026-11-16).
2. **AgendaPro** — Planes, precios ARS, extras, API US$200. https://agendapro.com/ar/planes (consultado el 2026-11-16).
3. **AgendaPro** — Funciones dentales: odontograma, sillón, recordatorios, seguridad. https://agendapro.com/ar/dental/software-odontologico (consultado el 2026-11-16).
4. **Dentalink** — Home: funciones, cifras, seguridad. https://web-es.softwaredentalink.com/ (consultado el 2026-11-16).
5. **Dentalink** — Planes Esencial/Pro/Titanium, sin precios, condiciones. https://web-es.softwaredentalink.com/en/planes (consultado el 2026-11-16).
6. **Dentalink** — Módulo Argentina: obras sociales y receta electrónica. https://ayuda.softwaredentalink.com/es/collections/19196578-modulo-exclusivo-para-argentina (consultado el 2026-11-16).
7. **Dentalink** — No cargó (HTTP 403). https://www.capterra.com/p/196361/Dentalink/ (consultado el 2026-11-16).
8. **La Historia Clínica Digital** — Ficha de directorio de terceros: funciones, precios, compliance declarado. https://www.comparasoftware.com/la-historia-clinica-digital (consultado el 2026-11-16).
9. **Medilink (Latam)** — Home: funciones, precios del contact center, cifras. https://www.softwaremedilink.com/ (consultado el 2026-11-16).
10. **Consultorios.AR (Consultorios Argentinos)** — Folleto promocional (solo resumen de búsqueda; no abierto). https://aac.org.ar/beneficios/imagenes/consultorios2.pdf (consultado el 2026-11-16).
11. **Reservo** — Home consultada: turnero, Mercado Pago, ARCA. https://www.reservo.cl/ (consultado el 2026-11-16).
12. **DentalSoft** — Listado no accesible (HTTP 403); solo resumen de búsqueda. https://www.getapp.com/all-software/a/dentalsoft/ (consultado el 2026-11-16).
13. **ODONTSIS (Odontosys)** — Perfil institucional: país, año, clientes. https://connectamericas.com/node/5979 (consultado el 2026-11-16).
14. **Doctoralia (Doctoralia Pro / para especialistas)** — Referencia de Capterra (solo resumen de búsqueda). https://www.capterra.com/p/253301/Doctoralia-Pro/ (consultado el 2026-11-16).
15. **Doctoralia (Doctoralia Pro / para especialistas)** — Reseña de competidor, no neutral. https://pabau.com/es/blog/precios-de-doctoralia-pro/ (consultado el 2026-11-16).
16. **Clinic Cloud** — Home: funciones, RGPD, cifras. https://www.clinic-cloud.com/ (consultado el 2026-11-16).
17. **Clinic Cloud** — HTTP 404. https://clinic-cloud.com/software-clinicas-dentales/ (consultado el 2026-11-16).
18. **Medesk** — Página odontología: agenda, odontograma, RGPD. https://www.medesk.net/es/solutiones/odontologia (consultado el 2026-11-16).
19. **DENTIDESK** — Home: funciones, integraciones, prueba. https://dentidesk.com (consultado el 2026-11-16).
20. **Dentally** — Home: funciones y empresa. https://www.dentally.com/ (consultado el 2026-11-16).
21. **Curve Dental** — Home: funciones, cifras, HIPAA. https://www.curvedental.com/ (consultado el 2026-11-16).
22. **NexHealth** — Home: funciones, integraciones, cifras. https://www.nexhealth.com/ (consultado el 2026-11-16).
23. **Open Dental** — Home: funciones y versiones. https://www.opendental.com/ (consultado el 2026-11-16).
24. **Dentrix (Henry Schein One)** — Home: funciones y cifras. https://www.dentrix.com/ (consultado el 2026-11-16).
25. **Weave** — Home: funciones. https://www.getweave.com/ (consultado el 2026-11-16).

---

## Anexo — Checklist de Discovery (discovery-research, 11 puntos)

**Fecha**: 2026-11-16
**Fuentes investigadas**: 18 sistemas (ver `discovery/sources/` y el informe completo en `discovery/investigacion-mercado.md`, secciones A-F)

### 1. Problema que resuelve

El odontólogo independiente en Argentina gestiona su consultorio con herramientas
dispersas: turnos coordinados por WhatsApp o teléfono, ficha clínica en papel o en
otra herramienta, y sin recordatorios automáticos. Las soluciones fuertes del
mercado (AgendaPro, Dentalink) son genéricas de salud o están orientadas a
clínicas, y casi ninguna evidencia adecuación normativa argentina ni precios
en ARS. El producto busca ser la herramienta integral (agenda + reserva online +
ficha clínica) a la escala de un profesional independiente.

### 2. Usuarios / roles

- **Odontólogo (profesional independiente)**: configura su agenda y prestaciones,
  atiende y registra la historia clínica.
- **Paciente**: reserva, cancela y reprograma su turno por sí mismo, sin llamar.
- **Secretaria/asistente** (a confirmar para v1): carga y gestiona turnos en nombre
  del profesional, sin acceso a la historia clínica.

### 3. Casos de uso

1. Como paciente, quiero reservar un turno online 24/7 para no depender de llamar
   o escribir en horario de atención.
2. Como paciente, quiero cancelar o reprogramar mi turno desde un link para liberar
   el horario sin contactar al consultorio.
3. Como odontólogo, quiero definir mis prestaciones con su duración, mis horarios y
   mis bloqueos para que la agenda nunca se solape.
4. Como odontólogo, quiero registrar anamnesis, odontograma y evoluciones del
   paciente para tener su historia clínica digital.
5. Como sistema, quiero enviar confirmación y recordatorio por email para reducir
   las ausencias.

### 4. Competidores / soluciones existentes

| Competidor | Problema que resuelve | Pricing | Diferenciadores |
|---|---|---|---|
| AgendaPro (CL, opera en AR) | Agenda y reservas multi-rubro con vertical dental | Publicado en ARS (rubro Belleza); planes Salud sin precio | Reserva desde Google/Instagram/link, WhatsApp, seña con Mercado Pago; ARCA "próximamente" |
| Dentalink (CL) | Gestión clínica odontológica integral | Planes sin precio publicado | Único con módulo Argentina documentado (obras sociales, receta electrónica) |
| La Historia Clínica Digital (AR) | HC digital y gestión para profesionales | ARS 50.000/mes primer usuario (vía directorio de terceros) | Declara ARCA y Leyes 25.326/26.529 (evidencia débil) |
| Dentally (UK) | Gestión dental en la nube | Sin precio ARS | Portal del paciente con lista de espera |
| NexHealth (US) | Captación y reserva de pacientes | Sin precio publicado | Reserva desde Google Maps, recall de un clic, formularios previos |

**Notas**: puntuación ponderada y detalle de los 18 sistemas en
`investigacion-mercado.md` (A tabla, B matriz, C análisis, D recomendación).
Referentes de UX: AgendaPro, NexHealth y Dentally (hipótesis sin demo). Vacíos
detectados en Argentina: facturación ARCA casi inexistente, obras sociales solo
en Dentalink, precios en ARS casi nunca publicados, ningún proveedor evidencia
adecuación a la Ley 27.706.

### 5. Funcionalidades necesarias

- Agenda de un profesional: vistas diaria y semanal, prestaciones con duración
  variable, horarios de atención, bloqueos y prevención de solapamientos.
- Reserva online 24/7 por link, con cancelación y reprogramación autogestionadas
  por el paciente (sin cuenta obligatoria).
- Confirmación y recordatorio de turno **solo por email** en la v1.
- Ficha clínica: datos del paciente, anamnesis, odontograma básico y evoluciones.

### 6. Funcionalidades opcionales

- Recordatorios y confirmación por WhatsApp (API oficial de Meta) — v2.
- Cobros, caja y seña online con Mercado Pago.
- Obras sociales y prepagas; facturación electrónica ARCA (diferenciadores de etapa 2).
- Lista de espera, seguimiento de ausentes y recall periódico.
- Periodontograma, presupuestos, planes de tratamiento e imágenes/radiografías.
- Múltiples profesionales, sillones/boxes y sucursales.
- Campañas, API pública, firma de consentimientos.

### 7. Reglas de negocio

- Sin política de cancelación ni seña en la v1: el paciente puede cancelar o
  reprogramar en cualquier momento.
- Un profesional no puede tener dos turnos superpuestos; los bloqueos y horarios
  fuera de atención no se pueden reservar.
- La historia clínica no se elimina ni se sobreescribe: las correcciones quedan
  registradas (Ley 26.529).
- El acceso a la historia clínica es exclusivo del profesional tratante.

### 8. Integraciones

- **v1**: proveedor de email transaccional (confirmaciones y recordatorios).
- **Posteriores**: WhatsApp Business Platform (Cloud API), Mercado Pago,
  ARCA (factura electrónica), Google Calendar.

### 9. Restricciones

- **Stack obligatorio**: frontend en TypeScript vanilla con diseño responsivo;
  backend en Spring Boot con Gradle.
- **Plazo acotado**: hay fecha límite para el MVP (fecha concreta a definir).
- **Cumplimiento legal desde la v1**: Ley 25.326 (protección de datos personales,
  datos de salud como sensibles) y Ley 26.529 (derechos del paciente e historia
  clínica).
- Producto real orientado a clientes, no solo prototipo.

### 10. Riesgos

- **Principal — normativa de salud**: los requisitos de la historia clínica
  electrónica (Ley 27.706 y su reglamentación, integridad, inalterabilidad,
  autoría, conservación, consentimiento, trazabilidad) no están relevados en
  detalle; un error acá compromete el producto.
- **Supuesto sin probar**: que el recordatorio solo por email alcance para bajar
  el ausentismo (el mercado usa WhatsApp como estándar).
- **Supuesto sin probar**: que el odontólogo independiente migre desde
  WhatsApp/papel o desde AgendaPro/Dentalink.
- **Supuesto sin probar**: disposición a pagar y precio en ARS.
- **Evidencia competitiva débil**: 5 de los 18 sistemas solo tienen respaldo de
  directorios de terceros; varias afirmaciones son comerciales (ver sección F
  del informe).

### 11. Preguntas abiertas

- ¿Cuál es la fecha límite concreta del MVP?
- ¿Qué exige exactamente la normativa para la HC electrónica (firma, inmutabilidad,
  plazo de conservación, registro de accesos)?
- ¿Precio y modelo de cobro en ARS?
- ¿La secretaria existe como rol en la v1?
- ¿Dónde se alojan los datos (hosting, jurisdicción) y cómo se respaldan?
- ¿Cómo se identifica al paciente sin cuenta (email, teléfono, DNI) y cómo se
  evitan reservas falsas?
- Verificación manual pendiente de las fuentes de la sección E del informe.
