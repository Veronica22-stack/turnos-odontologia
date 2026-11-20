# Descripción General

## Stack tecnológico

| Capa | Tecnología | Versión mínima |
|------|------------|----------------|
| Frontend | SPA en **TypeScript vanilla (sin framework)**, responsive / mobile-first | TypeScript 5.x (**Suposición**) |
| Bundler frontend | Vite (**Suposición:** no impuesto; alternativa esbuild/tsc) | Vite 5+ |
| Backend | **Spring Boot** + **Gradle**, API REST | Java 21 LTS y Spring Boot 3.x (**Suposición**) |
| Base de datos | **PostgreSQL** (con `btree_gist` para la restricción de exclusión) | 15+ (**Suposición**) |
| Migraciones | Flyway (**Suposición**) | 10+ |
| Email | Proveedor transaccional (TBD; ver 10) | — |
| Contenedores (dev) | Docker Compose: app + PostgreSQL | — |
| Tests | JUnit 5 + Testcontainers (backend); Vitest (frontend) (**Suposición**) | — |

Restricciones obligatorias del stack: frontend TS vanilla responsivo, backend Spring Boot con Gradle (ver `discovery/discovery.md` §9).

## Arquitectura general

**Monolito modular** desplegado como un único artefacto Spring Boot que además sirve la SPA estática (o la SPA se sirve desde un reverse proxy; decisión en 08). Una instalación por odontólogo.

```
 Paciente (anónimo)            Odontólogo (autenticado)
        |                               |
        v                               v
   +-----------------------------------------+
   |  SPA TypeScript vanilla (mobile-first)  |
   |  rutas públicas  |  rutas privadas      |
   +--------------------+--------------------+
                        | REST/JSON (HTTPS)
   +--------------------v--------------------+
   |        Spring Boot (monolito modular)   |
   | agenda | reservas | pacientes | HC |    |
   | notificaciones | auth/auditoría         |
   +----------+---------------------+--------+
              |                     |
        PostgreSQL            Proveedor email
   (EXCLUDE USING gist)       (transaccional)
```

Decisiones de alto nivel (detalle en `09_decisiones_y_supuestos.md`):

- Monolito modular: costo mínimo de operación y despliegue; módulos con fronteras claras por si se separa a futuro.
- Single-tenant hoy, modelado para multi-tenant mañana (ver DD-02).
- Integridad de agenda garantizada en la base (restricción de exclusión) además de la validación de aplicación.
- Infraestructura **local** por ahora; cloud y ubicación de datos son preguntas abiertas.

## Integraciones externas

| Servicio | Propósito | Tipo | Versión |
|----------|-----------|------|---------|
| Proveedor de email transaccional (TBD) | Confirmación y recordatorio de turnos | SMTP o REST | v1 |
| WhatsApp Business Platform (Cloud API) | Recordatorios y confirmación | REST + webhook | v2 |
| Mercado Pago | Seña/cobros | REST + webhook | posterior |
| ARCA (facturación electrónica) | Facturación | Web service | posterior |
| Google Calendar | Sincronización de agenda | REST | posterior |

## API REST (resumen)

Prefijo `/api/v1`. Agrupado por recurso (contrato detallado a definir en el change correspondiente).

| Recurso | Endpoints principales | Acceso |
|---------|-----------------------|--------|
| Auth | `POST /auth/login`, `POST /auth/logout`, `POST /auth/refresh` | Público (login) / Dentista |
| Configuración | `GET/PUT /settings/practice`, `/working-hours`, `/blocks` | Dentista |
| Prestaciones | `GET/POST/PUT /services`, `PATCH /services/{id}/active` | Dentista |
| Agenda | `GET /appointments?from=&to=`, `POST /appointments`, `PATCH /appointments/{id}` | Dentista |
| Público | `GET /public/services`, `GET /public/availability?serviceId=&date=`, `POST /public/bookings` | Anónimo (rate limit) |
| Gestión por token | `GET/DELETE /public/bookings/{token}`, `POST /public/bookings/{token}/reschedule` | Anónimo con token |
| Pacientes | `GET/POST/PUT /patients`, `GET /patients/{id}` | Dentista |
| Historia clínica | `GET /patients/{id}/clinical-record`, `POST .../anamnesis`, `.../odontogram`, `.../evolutions`, `POST .../corrections` | Dentista (auditado) |
| Auditoría | `GET /audit-log` | Dentista |
