# Discovery — Sistema de gestión odontológica (nombre a definir)

**Fecha**: 2026-11-16
**Fuentes investigadas**: 18 sistemas (ver `discovery/sources/` y el informe completo en `discovery/investigacion-mercado.md`, secciones A-F)

## 1. Problema que resuelve

El odontólogo independiente en Argentina gestiona su consultorio con herramientas
dispersas: turnos coordinados por WhatsApp o teléfono, ficha clínica en papel o en
otra herramienta, y sin recordatorios automáticos. Las soluciones fuertes del
mercado (AgendaPro, Dentalink) son genéricas de salud o están orientadas a
clínicas, y casi ninguna evidencia adecuación normativa argentina ni precios
en ARS. El producto busca ser la herramienta integral (agenda + reserva online +
ficha clínica) a la escala de un profesional independiente.

## 2. Usuarios / roles

- **Odontólogo (profesional independiente)**: configura su agenda y prestaciones,
  atiende y registra la historia clínica.
- **Paciente**: reserva, cancela y reprograma su turno por sí mismo, sin llamar.
- **Secretaria/asistente** (a confirmar para v1): carga y gestiona turnos en nombre
  del profesional, sin acceso a la historia clínica.

## 3. Casos de uso

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

## 4. Competidores / soluciones existentes

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

## 5. Funcionalidades necesarias

- Agenda de un profesional: vistas diaria y semanal, prestaciones con duración
  variable, horarios de atención, bloqueos y prevención de solapamientos.
- Reserva online 24/7 por link, con cancelación y reprogramación autogestionadas
  por el paciente (sin cuenta obligatoria).
- Confirmación y recordatorio de turno **solo por email** en la v1.
- Ficha clínica: datos del paciente, anamnesis, odontograma básico y evoluciones.

## 6. Funcionalidades opcionales

- Recordatorios y confirmación por WhatsApp (API oficial de Meta) — v2.
- Cobros, caja y seña online con Mercado Pago.
- Obras sociales y prepagas; facturación electrónica ARCA (diferenciadores de etapa 2).
- Lista de espera, seguimiento de ausentes y recall periódico.
- Periodontograma, presupuestos, planes de tratamiento e imágenes/radiografías.
- Múltiples profesionales, sillones/boxes y sucursales.
- Campañas, API pública, firma de consentimientos.

## 7. Reglas de negocio

- Sin política de cancelación ni seña en la v1: el paciente puede cancelar o
  reprogramar en cualquier momento.
- Un profesional no puede tener dos turnos superpuestos; los bloqueos y horarios
  fuera de atención no se pueden reservar.
- La historia clínica no se elimina ni se sobreescribe: las correcciones quedan
  registradas (Ley 26.529).
- El acceso a la historia clínica es exclusivo del profesional tratante.

## 8. Integraciones

- **v1**: proveedor de email transaccional (confirmaciones y recordatorios).
- **Posteriores**: WhatsApp Business Platform (Cloud API), Mercado Pago,
  ARCA (factura electrónica), Google Calendar.

## 9. Restricciones

- **Stack obligatorio**: frontend en TypeScript vanilla con diseño responsivo;
  backend en Spring Boot con Gradle.
- **Plazo acotado**: hay fecha límite para el MVP (fecha concreta a definir).
- **Cumplimiento legal desde la v1**: Ley 25.326 (protección de datos personales,
  datos de salud como sensibles) y Ley 26.529 (derechos del paciente e historia
  clínica).
- Producto real orientado a clientes, no solo prototipo.

## 10. Riesgos

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

## 11. Preguntas abiertas

- ¿Cuál es la fecha límite concreta del MVP?
- ¿Qué exige exactamente la normativa para la HC electrónica (firma, inmutabilidad,
  plazo de conservación, registro de accesos)?
- ¿Precio y modelo de cobro en ARS?
- ¿La secretaria existe como rol en la v1?
- ¿Dónde se alojan los datos (hosting, jurisdicción) y cómo se respaldan?
- ¿Cómo se identifica al paciente sin cuenta (email, teléfono, DNI) y cómo se
  evitan reservas falsas?
- Verificación manual pendiente de las fuentes de la sección E del informe.
