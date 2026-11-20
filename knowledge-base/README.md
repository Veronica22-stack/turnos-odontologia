# Sistema de Gestión Odontológica (nombre a definir) — Base de Conocimiento

Base de conocimiento del proyecto, construida a partir del discovery (`discovery/discovery.md`, `discovery/investigacion-mercado.md`) y de las decisiones confirmadas con el usuario.

## Índice de Archivos

| Archivo | Contenido |
|---------|-----------|
| [01_vision_y_objetivos.md](01_vision_y_objetivos.md) | Propósito, objetivos por actor, alcance v1, fuera de alcance, métricas |
| [02_descripcion_general.md](02_descripcion_general.md) | Stack, arquitectura general, integraciones, resumen de API REST |
| [03_actores_y_roles.md](03_actores_y_roles.md) | Odontólogo, paciente anónimo, matriz de permisos, rutas públicas |
| [04_modelo_de_datos.md](04_modelo_de_datos.md) | Entidades, ERD (mermaid), restricción anti-solapamiento, seed data |
| [05_reglas_de_negocio.md](05_reglas_de_negocio.md) | Reglas RN-AU, AG, RS, PA, NO, HC, AD, GL |
| [06_funcionalidades.md](06_funcionalidades.md) | Historias de usuario por épica (MVP vs posteriores) |
| [07_flujos_principales.md](07_flujos_principales.md) | Login, setup, reserva, cancelar/reprogramar por token, recordatorios, HC |
| [08_arquitectura_propuesta.md](08_arquitectura_propuesta.md) | Monolito modular, estructura backend/frontend, seguridad, variables de entorno |
| [09_decisiones_y_supuestos.md](09_decisiones_y_supuestos.md) | Decisiones (DD) y supuestos (SU) |
| [10_preguntas_abiertas.md](10_preguntas_abiertas.md) | Inconsistencias y preguntas priorizadas |
| [11_cumplimiento_normativo.md](11_cumplimiento_normativo.md) | Leyes 25.326, 26.529, 27.706: conocido vs por investigar |

## Quick Start para Desarrolladores

1. Entender el dominio → [01](01_vision_y_objetivos.md), [03](03_actores_y_roles.md)
2. Entender los datos → [04](04_modelo_de_datos.md)
3. Entender las reglas → [05](05_reglas_de_negocio.md), [11](11_cumplimiento_normativo.md)
4. Entender la arquitectura → [02](02_descripcion_general.md), [08](08_arquitectura_propuesta.md)
5. Implementar → [07](07_flujos_principales.md), [06](06_funcionalidades.md)
6. Antes de codificar → [09](09_decisiones_y_supuestos.md), [10](10_preguntas_abiertas.md)

## Resumen Ejecutivo

Aplicación web para un odontólogo independiente en Argentina: agenda, reserva online 24/7 sin cuenta (con cancelación/reprogramación por link con token) e historia clínica inmutable. Stack: TypeScript vanilla + Spring Boot (Gradle) + PostgreSQL, monolito modular, single-tenant modelado para evolucionar a multi-tenant. Menor costo posible como prioridad. Riesgos principales: requisitos de la Ley 27.706 sin relevar, reservas falsas sin verificación, e hosting/datos aún indefinidos. Todo lo inferido está marcado como **Suposición:**.
