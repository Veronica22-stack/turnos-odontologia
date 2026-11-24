# Reflexión escrita — turnos-odontologia (La Muela)

**Integrantes** (usuario en GitHub):

- Lucas Hernán Orsingher (Lucas-Orsingher), líder
- Elian Navarro (elcrack205)
- Gabriel Sánchez (GSánchez)
- Verónica Silva (Veronica22-stack)
- Mayra Muñoz (May)
- Tomás Efraín Donoso Portillo (Efrain-Donoso)

## Información del Discovery incorrecta, no verificable o inventada

El agente no inventó sistemas, pero mucho de lo que dijo no era verificable o era contradictorio.

| Qué decía el Discovery | Problema | Cómo lo detectamos |
|---|---|---|
| Precios de AgendaPro: Individual $7.900, Full $15.900; "precios de Salud no listados" | **Incorrecto al verificar**: hoy son Individual $9.900, Básico $19.900, Premium Salud y Estética $39.900 y Pro IA $129.900; el plan Full ya no existe | Verificación manual de Verónica Silva (2026-11-24). Se corrigieron la ficha A.2.1 y el hallazgo 1 |
| AgendaPro "desde $13.900" y "cumple las Leyes 25.326 y 26.529" | El precio contradice la página de planes; el cumplimiento es afirmación comercial y cita una certificación chilena | Lo marcó el propio agente (hallazgos F.1 y F.2) |
| La Historia Clínica Digital: ARCA, leyes argentinas, ARS 50.000/mes | Lo confirmamos en la URL, pero es un directorio de terceros titulado "Precios en México", sin sitio oficial ni empresa | Verificación manual (fuente 6) y F.9: sigue como afirmación comercial |
| Reservo es argentino; Clinic Cloud se usa en Argentina; precios de Dentalink y Doctoralia | No verificables: dominio .cl con contenido argentino, un blog de un competidor, agregadores en vez del proveedor | Hallazgos F.5, F.6, F.8 y F.10 |
| Consultorios.AR, DentalSoft, ODONTSIS y 11 candidatos del encargo | Evidencia casi nula o ninguna (PDF nunca abierto, HTTP 403, perfil "no verificado") | El agente los marcó "No evidenciado" o los excluyó en lugar de inventarles funciones |

La regla de evidencia obligó al agente a marcar "No evidenciado" y "afirmación comercial". La verificación manual de seis fuentes confirmó cuatro, confirmó otra como afirmación de terceros y corrigió los precios de AgendaPro, que cambiaron en ocho días. Capterra (HTTP 403) y el video oficial quedaron sin verificar, y así lo registramos.

## Dónde usamos Ajustar y qué habría pasado con Continuar

| Etapa | Qué estaba mal | Qué habría pasado con Continuar |
|---|---|---|
| Discovery, sección D (MVP) | La primera versión ponía como imprescindibles agenda multi-profesional y por sillón, WhatsApp, seña con Mercado Pago, roles y exportación | Un roadmap con lo que el Discovery dejaba para después. Se movieron a etapas posteriores |
| Knowledge-base | Faltaba la regla de sillón/box y las preguntas abiertas no recogían lo "No evidenciado" | Afirmaciones comerciales tratadas como requisitos. Se agregaron RN-AG-10 y NE-01 a NE-07 |
| Knowledge-base → C-01 (Lucas) | El plan usaba Docker Compose y Testcontainers; varios integrantes no tienen Docker | La mitad del equipo sin poder correr los tests. Se rehízo sin Docker (DD-10) |
| Propose de C-11, change principal (Lucas) | `CHANGES.md` estaba desactualizado y faltaban dependencias; varias decisiones de errores y puertos no coincidían con C-16 | Implementar sobre módulos inexistentes. Luego, una carrera reprogramar/cancelar llevó a volver al diseño: lock por consultorio |
| Propose de C-06 (Mayra) | Los mockups suponían un contrato de C-04 que no era el real | La pantalla de login habría fallado contra el backend. Se hizo la revisión 2 de mockups |
| Propose de C-13 y C-18 (Verónica) | El roadmap pedía migración V9 (ya había hasta V13); el CHECK de evoluciones aceptaba textos solo con saltos de línea | Flyway rechaza la migración y quedan evoluciones vacías en una tabla inmutable. Se usó V14 y un CHECK que exige texto |

## Especificación aprobada vs. prompt suelto

Con una especificación aprobada, el agente dejó de decidir por nosotros. Las decisiones aparecían como preguntas (Q1, Q2…) o marcadas "[Aprobar]" antes del código: en C-17 hubo seis sobre el odontograma y en C-13 quince decisiones con su alternativa descartada. Con un prompt suelto se toman en silencio.

Cada escenario (feliz, error de negocio y borde) se volvió un test en Strict TDD: al cierre de C-11 había 566 tests unitarios y 364 de integración en verde. El verify lo hizo un agente distinto del que implementó.

El costo fue tiempo, y cuando salteamos el proceso se notó: el pedido P1 de C-25 a C-15 se aplicó antes de escribir su change.

## Qué parte de la fundación aportó más y cuál menos

**Más valor: las reglas del agente (`AGENTS.md` / `CLAUDE.md`)**, seguidas de la knowledge-base. Las reglas duras se cumplieron igual en los seis integrantes sin repetirlas: historia clínica append-only, sin datos personales en logs, auditoría de cada lectura, sin `innerHTML`, sin dependencias ni commits sin aprobación. La knowledge-base llegó intacta a los specs: C-16 a C-18 heredaron los supuestos legales de PA-01 sin inventar requisitos.

**Menos valor: el roadmap en su detalle.** El árbol de dependencias y los gates permitieron trabajar seis personas en paralelo, pero los números de migración estaban todos corridos (C-09 decía V6 y fue V8; C-13, V9 y fue V14) y los estados no se actualizaban al ritmo de los merges.

## Qué hizo cada integrante y en qué etapa

| Integrante | Otros nombres de autor | Etapas y evidencia en el repositorio |
|---|---|---|
| Lucas Hernán Orsingher (líder) | Nombre completo (merges) | Fundación completa; C-01, C-02 y el change principal C-11 con el ciclo OPSX completo. Aprobó mockups y mergeó los PR |
| Elian Navarro | — | Propose, apply y archive del backend núcleo: C-03, C-04, C-09 y C-12 |
| Gabriel Sánchez | "Gabriel Sánchez" (merge) | Apply y archive de C-08, C-10, C-14, C-15 y su pedido P1; matriz de tests (`docs/qa/matriz-de-tests.md`) |
| Verónica Silva | "Veronica" | Discovery: informe PDF y verificación manual de fuentes. Ciclo OPSX de C-13, C-16, C-17 y C-18; justificación de skills y evidencia de instalación |
| Mayra Muñoz | Correo con un punto de más (`mayrampinto.@gmail.com`) en parte de sus commits | Revisión de la knowledge-base contra el informe. Frontend base y reserva pública con mockups: C-05, C-06, C-24 y C-25 |
| Tomás Efraín Donoso Portillo | Nombre completo (merges) | Revisión del roadmap contra el MVP. Frontend del profesional con mockups: C-07, C-19, C-20, C-21 y C-22 |
