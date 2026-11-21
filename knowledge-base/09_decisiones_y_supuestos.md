# Decisiones y Supuestos

## Decisiones documentadas

### DD-01 — Aplicación web con SPA TypeScript vanilla + API REST Spring Boot/Gradle + PostgreSQL
**Decisión**: Frontend SPA en TypeScript sin framework, responsive/mobile-first; backend REST en Spring Boot con Gradle; base PostgreSQL.
**Contexto**: Stack obligatorio indicado en las restricciones del proyecto; producto real con plazo acotado y costo mínimo.
**Alternativas consideradas**: Frameworks SPA (React/Vue/Angular) y SSR; fuera de lo permitido por la restricción.
**Justificación**: Cumple la restricción; PostgreSQL aporta restricciones de exclusión para evitar solapamientos y es de bajo costo.
**Trade-offs aceptados**: Sin framework hay que construir router, estado y componentes propios (más código base, más disciplina); se compensa con una estructura simple por features.

### DD-02 — Single-tenant ahora, modelado para multi-tenant futuro
**Decisión**: Una instalación por odontólogo; toda tabla de negocio lleva `practice_id`. Decisión **revisable**.
**Contexto**: Un solo profesional por instancia y menor costo/complejidad inicial.
**Alternativas consideradas**: Multi-tenant real desde v1 (aislamiento por filas o por esquema).
**Justificación**: Evita la complejidad de aislamiento, autorización por tenant y facturación desde el inicio, sin cerrar el camino.
**Trade-offs aceptados**: **Riesgo operativo:** N clientes implican N despliegues (actualizaciones, backups, monitoreo, seguridad, certificados), con costo creciente de mantenimiento. Umbral para revisar la decisión: **Suposición:** más de ~5-10 instalaciones o cuando el costo operativo supere el de un esquema multi-tenant.

### DD-03 — Monolito modular
**Decisión**: Un único artefacto con módulos agenda, reservas, pacientes, historia clínica, notificaciones y auth/auditoría.
**Contexto**: Equipo pequeño, plazo acotado, costo mínimo.
**Alternativas consideradas**: Microservicios; monolito sin modularidad.
**Justificación**: Simplicidad operativa con fronteras claras.
**Trade-offs aceptados**: Escalado conjunto; requiere disciplina para no violar fronteras.

### DD-04 — Reserva pública sin cuenta ni verificación
**Decisión**: El paciente ingresa nombre, DNI, teléfono y email; la reserva se confirma al instante; gestión posterior con link de token único enviado por email.
**Contexto**: Minimizar fricción del paciente y no depender de proveedores de verificación (costo).
**Alternativas consideradas**: Cuenta de paciente; verificación por código por email/SMS; aprobación manual del profesional.
**Justificación**: Máxima conversión y mínimo costo.
**Trade-offs aceptados**: **Riesgo de reservas falsas/abuso** y de suplantación (ingresar el DNI de otro). Mitigaciones (rate limit, honeypot, límites por contacto, no sobreescribir datos de pacientes existentes) a definir; ver 10.

### DD-09 — Restricción anti-solapamiento en base de datos
**Decisión**: `EXCLUDE USING gist` sobre `tstzrange` de los turnos `CONFIRMED`, más validación de aplicación.
**Contexto**: Reservas públicas concurrentes 24/7.
**Alternativas consideradas**: Solo validación de aplicación; locks de aplicación; locks advisory.
**Justificación**: Garantía fuerte contra carreras.
**Trade-offs aceptados**: Acopla el modelo a PostgreSQL y requiere `btree_gist`; no cubre bloqueos (otra tabla), que se validan en aplicación.

### DD-05 — Notificaciones solo por email en v1
**Decisión**: Confirmación y recordatorio por email; WhatsApp Cloud API en v2.
**Contexto**: Costo y complejidad (WhatsApp requiere cuenta de negocio, plantillas, costo por conversación).
**Alternativas consideradas**: WhatsApp desde v1; SMS.
**Justificación**: Menor costo y plazo.
**Trade-offs aceptados**: El mercado usa WhatsApp como estándar; **supuesto sin probar** de que email baje el ausentismo (ver `discovery/investigacion-mercado.md` §C y riesgos del discovery).

### DD-06 — Un solo rol interno en v1 (secretaria diferida)
**Decisión**: Solo DENTIST con acceso total; la secretaria pasa a v2.
**Contexto**: El discovery dejaba el rol "a confirmar"; se elimina para acotar alcance y evitar el diseño de permisos finos sobre la HC.
**Alternativas consideradas**: Incluir SECRETARY con acceso solo a agenda.
**Justificación**: Menor alcance y plazo.
**Trade-offs aceptados**: Profesionales con asistente no pueden delegar la agenda en v1; el modelo de roles se deja extensible.

### DD-07 — Historia clínica inmutable con correcciones por versión
**Decisión**: Entradas append-only; las correcciones son nuevas entradas que referencian la original con autor, fecha y motivo.
**Contexto**: Ley 26.529 y riesgo de la Ley 27.706.
**Alternativas consideradas**: Edición con historial de cambios (auditoría de updates); borrado lógico.
**Justificación**: Integridad e inalterabilidad más sencillas de demostrar.
**Trade-offs aceptados**: Vistas más complejas (versión vigente vs historial).

### DD-08 — Infraestructura local por ahora
**Decisión**: Ejecución local con PostgreSQL instalado en cada equipo (ver DD-10); proveedor cloud y ubicación de los datos quedan abiertos.
**Contexto**: Costo mínimo y decisiones de hosting pendientes.
**Alternativas consideradas**: PaaS/VPS en la nube desde el inicio.
**Justificación**: No bloquear el desarrollo.
**Trade-offs aceptados**: Un producto real orientado a clientes con reservas 24/7 necesita estar siempre disponible; la ejecución local no lo garantiza (ver 10).

### DD-10 — Sin Docker en el proyecto
**Decisión**:
1. No se usa Docker en ninguna etapa (desarrollo, tests ni producción/despliegue).
2. Ejecución local: cada integrante instala PostgreSQL de forma nativa (instalador oficial de Windows, incluye pgAdmin) y crea la base y los roles con un script SQL versionado (`infra/postgres/init/01-roles.sql`, ejecutado con psql o pgAdmin), explicado en el README.
3. Tests automatizados: PostgreSQL embebido con la librería zonky (`io.zonky.test:embedded-postgres`, dependencia solo de test); `./gradlew test` levanta un PostgreSQL real temporal, sin instalación previa y funcional en CI. Reemplaza a Testcontainers. H2 sigue prohibido: la restricción de exclusión requiere PostgreSQL real.
4. Emails en desarrollo: se escriben en el log de la aplicación en lugar de enviarse (sin Mailpit); detalle en C-13.
5. Despliegue en producción (C-27, fuera del MVP): estrategia a definir, sin Dockerfile ni compose; el hosting sigue como pregunta abierta PA-03.
**Contexto**: Decisión explícita del usuario (2026-11-21). Docker Compose se había incorporado como suposición durante la fundación; varios integrantes del equipo no tienen Docker ni saben usarlo.
**Alternativas consideradas**: Docker Compose (app + PostgreSQL + Mailpit), descartado por la barrera de uso del equipo; Testcontainers, descartado porque requiere Docker; H2 en memoria, descartado porque no soporta la restricción de exclusión (`btree_gist`) ni el comportamiento real de PostgreSQL.
**Justificación**: Reduce los requisitos de herramientas del equipo y mantiene tests contra PostgreSQL real.
**Trade-offs aceptados**: Cada integrante instala y mantiene PostgreSQL localmente (riesgo de diferencias de versión); la estrategia de despliegue queda sin definir hasta resolver PA-03.

## Supuestos inferidos

### SU-01 — Versiones del stack
**Supuesto**: Java 21 LTS, Spring Boot 3.x, PostgreSQL 15+, TypeScript 5.x, Vite, Flyway.
**Origen**: Prácticas actuales; el stack base fue confirmado, las versiones no.
**Riesgo si es falso**: Retrabajo menor de configuración.
**Cómo validar**: Confirmar con el equipo antes del primer change técnico.

### SU-02 — El odontólogo es el único usuario con login
**Supuesto**: Ningún otro usuario interno en v1 y el odontólogo es el "profesional tratante" único.
**Origen**: Decisión de roles v1.
**Riesgo si es falso**: Rediseño de permisos.
**Cómo validar**: Entrevista con el primer cliente.

### SU-03 — El DNI identifica al paciente
**Supuesto**: Un DNI corresponde a un paciente; DNI único por instalación.
**Origen**: Datos de reserva decididos (nombre, DNI, teléfono, email).
**Riesgo si es falso**: Duplicados, pacientes sin DNI (menores, extranjeros con otros documentos).
**Cómo validar**: Revisar casos (menores, pasaporte) con el profesional.

### SU-04 — Pacientes menores de edad
**Supuesto**: Un menor reserva con datos de un adulto responsable; no se modela tutor en v1.
**Origen**: Inferido (no discutido).
**Riesgo si es falso**: Incumplimiento de consentimiento o datos erróneos.
**Cómo validar**: Preguntar al profesional y revisar normativa.

### SU-05 — Valores operativos por defecto
**Supuesto**: Granularidad de 15 min, recordatorio 24 h antes, anticipación mínima y horizonte de reserva configurables, zona horaria `America/Argentina/Buenos_Aires`.
**Origen**: Prácticas comunes de agendas.
**Riesgo si es falso**: Ajuste de configuración.
**Cómo validar**: Confirmar con el profesional.

### SU-06 — Autenticación por sesión con cookie
**Supuesto**: Se prefiere sesión servidor con cookie HttpOnly frente a JWT.
**Origen**: Evaluación técnica; decisión final abierta.
**Riesgo si es falso**: Cambio en capa de seguridad y en el cliente HTTP.
**Cómo validar**: Decidir antes del change de auth.

### SU-07 — El cifrado en reposo a nivel disco es suficiente en v1
**Supuesto**: Cifrado de disco/volumen y backups; sin cifrado a nivel columna en v1.
**Origen**: Equilibrio costo/riesgo.
**Riesgo si es falso**: Exigencia regulatoria o de riesgo mayor.
**Cómo validar**: Revisión legal y análisis de riesgo (ver 11).

### SU-08 — Un único huso horario
**Supuesto**: Un consultorio en una sola zona horaria (Argentina, sin horario de verano).
**Origen**: Escala de un profesional.
**Riesgo si es falso**: Errores de horarios.
**Cómo validar**: Confirmar con el primer cliente.

### SU-09 — Frontend servido por el backend
**Supuesto**: La SPA compilada se sirve desde la misma app para simplificar CORS y despliegue.
**Origen**: Objetivo de costo mínimo.
**Riesgo si es falso**: Separar hosting de estáticos más adelante.
**Cómo validar**: Revisar al decidir hosting.
