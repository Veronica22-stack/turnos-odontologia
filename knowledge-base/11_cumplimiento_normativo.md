# Cumplimiento Normativo

Este archivo consolida lo que el proyecto asume sobre normativa argentina y separa con claridad lo **conocido** de lo **por investigar**. **No es asesoramiento legal**: todo debe validarse con un profesional del derecho antes del lanzamiento.

## Estado por norma

| Norma | Estado | Impacto en el producto |
|-------|--------|------------------------|
| Ley 25.326 (Protección de Datos Personales) | Alcance general conocido; detalle a validar | Datos de salud = datos sensibles; consentimiento, finalidad, seguridad, derechos de acceso/rectificación/supresión |
| Ley 26.529 (Derechos del Paciente e Historia Clínica) | Principios conocidos; plazos y formalidades a validar | HC completa, inalterable, con autoría y fecha; acceso restringido |
| Ley 27.706 (Historia Clínica Electrónica) | **No investigada** | Desconocido: puede exigir firma, interoperabilidad, conservación, registros. **Riesgo principal del proyecto** |
| Normativa sanitaria/profesional de odontología | No investigada | Posibles requisitos jurisdiccionales (colegios, matrícula, habilitación) |

## Lo conocido (a validar legalmente)

**Ley 25.326**
- Los datos de salud son sensibles; su tratamiento requiere consentimiento y finalidad legítima (en el contexto sanitario, la ley contempla el tratamiento por profesionales de la salud con secreto profesional; confirmar alcance).
- Requiere medidas de seguridad y confidencialidad, y deber de informar al titular.
- Las bases de datos se registran ante la autoridad de aplicación (AAIP) (ver PA-10).
- Restringe la transferencia de datos a países sin nivel adecuado de protección (relevante para elegir hosting y proveedor de email; ver PA-03, PA-09).
- El titular tiene derechos de acceso, rectificación y supresión; se debe conciliar la supresión con la obligación de conservar la HC (ver PA-11).

**Ley 26.529**
- La HC es el documento obligatorio que registra cronológicamente la atención; pertenece al paciente, que tiene derecho a acceder y a obtener copia.
- Las modificaciones deben hacerse por asientos sucesivos, sin alterar lo anterior, con fecha y firma/identificación del autor; la custodia tiene un plazo mínimo (el valor exacto debe confirmarse; no se fija acá).
- Confidencialidad y acceso limitado.

## Cómo el diseño las refleja (v1)

| Requisito (a validar) | Mecanismo propuesto | Referencias |
|-----------------------|---------------------|-------------|
| HC no se borra ni sobreescribe | Entradas append-only; correcciones por versión; permisos de BD sin UPDATE/DELETE | RN-HC-01/02, DD-07 |
| Autoría y fecha en cada asiento | `author_id` y `created_at` del servidor | RN-HC-05 |
| Acceso solo del profesional tratante | Único rol DENTIST; secretaria sin HC en v2 | RN-HC-03 |
| Trazabilidad de accesos | `audit_log` append-only | RN-AD-01..03 |
| Confidencialidad y seguridad | HTTPS, cifrado en reposo, rate limit, sin datos clínicos en emails/logs/URLs | 08 Seguridad, RN-NO-06 |
| Minimización en reserva pública | Solo nombre, DNI, teléfono, email; sin datos clínicos | RN-RS-02 |
| Consentimiento / aviso de privacidad | Aviso y aceptación en la página de reserva (texto TBD) | PA-10 |

## Por investigar (no se inventan requisitos)

1. **Ley 27.706 y su reglamentación** (PA-01): firma electrónica/digital, inmutabilidad técnica, plazos de conservación, registro de accesos, interoperabilidad, formato, alcance a consultorios privados e independientes, y fecha de exigibilidad.
2. Plazo exacto de conservación de la HC y tratamiento de la baja de datos (PA-11).
3. Inscripción en el Registro de Bases de Datos Personales y contenido del aviso de privacidad (PA-10).
4. Transferencia internacional de datos y hosting/proveedor de email (PA-03, PA-09).
5. Consentimiento informado y su eventual firma (posterior).
6. Pacientes menores de edad (PA-13).
7. Normativa de facturación electrónica ARCA y de obras sociales (posterior, etapa 2).

## Acción recomendada

Antes del change de historia clínica: relevar la Ley 27.706 (texto y reglamentación vigente) y validar con asesoría legal; luego, ajustar `05_reglas_de_negocio.md` (RN-HC) y `04_modelo_de_datos.md`.
