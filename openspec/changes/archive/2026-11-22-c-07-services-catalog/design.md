# Design: C-07 `services-catalog`

## Context

Ver motivación en `proposal.md`. El estado actual que condiciona el diseño:

- El módulo `agenda` ya existe con las cuatro capas (`api`, `application`, `domain`, `infrastructure`) y contiene `WorkingHours` (C-08). El módulo `reservas` existe solo como esqueleto de paquetes (`package-info`), sin código.
- `practice` ya tiene `slot_granularity_min` (default 15, valores permitidos 10/15/20/30/60) desde la migración V5 (C-08), editable por `PUT /api/v1/practice/settings`.
- `InstallationPractice` (shared) cachea en memoria **solo** `id` y `zone` al primer acceso; no expone la granularidad y no se invalida al cambiar settings.
- La infraestructura de límite de tasa de C-04 vive en `shared.api.RateLimiter` (contrato) e `shared.infrastructure.InMemoryRateLimiter` (implementación en memoria, ventanas fijas, claves hasheadas con SHA-256). El bean existente está configurado con 10 intentos por ventana para login.
- La política de seguridad (`auth.infrastructure.SecurityConfig`) ya declara `/api/v1/public/**` como público y `/api/v1/**` como protegido con rol `DENTIST`; CSRF está activo para todo método no seguro salvo `POST /api/v1/auth/login`.
- Los errores usan `application/problem+json` con `code` estable vía `ApiExceptionHandler`; `ValidationException` → 400 con `errors[]`, `BusinessRuleException` → 422, `NotFoundException` → 404, `RateLimitedException` → 429 + `Retry-After`.
- Migraciones existentes: `V1`, `V2`, `V3`, `V5`, `V7`, `V11`. `V4` está libre y el roadmap lo asigna a este change.

## Goals / Non-Goals

**Goals:**
- Modelar `service` con `practice_id`, baja lógica y visibilidad pública independiente.
- CRUD de gestión completo (alta, listado, consulta, edición, reactivación y baja lógica) con validación de duración contra la granularidad vigente.
- Primer endpoint público real del sistema (`GET /api/v1/public/services`) con DTO mínimo, cache corto y rate limit por IP.
- Mantener el monolito modular: `agenda` es dueño de `service`; sin dependencias nuevas entre módulos de negocio.
- Dejar el contrato listo para C-11 (duración del turno), C-12 (slots) y C-14 (reserva pública) sin implementarlos.

**Non-Goals:**
- Cálculo de disponibilidad y generación de slots: C-12.
- Turnos, snapshot de duración y validación de solapamiento: C-11.
- Reserva pública, tokens y anti-abuso (honeypot, límites por contacto): C-14 / PA-05.
- Interfaz de usuario de prestaciones: C-19 (change `[UI]`).
- Precios en ARS: fuera de v1 (PA-07, KB 04 §service).

## Decisions

### D1: Ubicación del módulo y nombres de clases
- **Decisión**: Todo el change vive en el módulo `agenda`, que según KB 08 es dueño de "prestaciones":
  - `agenda.domain`: entidad `PracticeService` (tabla `service`), `PracticeServiceRepository`, `ServiceDurationPolicy`.
  - `agenda.application`: `ServiceCatalogService` (casos de uso de gestión + catálogo público), records `ServiceData` y `PublicServiceData`.
  - `agenda.api`: DTOs `record` con Bean Validation (`CreateServiceRequest`, `UpdateServiceRequest`, `ServiceResponse`, `PublicServiceResponse`).
  - `agenda.infrastructure`: `ServicesController`, `PublicServicesController`, `SpringDataPracticeServiceRepository`.
- **Nombre de la entidad**: `PracticeService`, no `Service`, porque `org.springframework.stereotype.Service` se importa en la capa `application` y un tipo `agenda.domain.Service` colisionaría en el mismo archivo. La tabla sigue llamándose `service` (`@Table(name = "service")`).
- **Alternativa descartada**: poner el catálogo público en `reservas` consumiendo `agenda.api`. Se descarta para no inventar ahora un contrato público de `agenda.api` que C-14 probablemente quiera rediseñar; `reservas → agenda` queda disponible cuando C-14 lo necesite. El controlador público es una proyección de solo lectura del agregado que `agenda` ya posee.

### D2: Modelo de datos y convenciones
- **Decisión**: `V4__service.sql` crea:

  | Columna | Tipo | Restricción |
  |---|---|---|
  | `id` | `uuid` | PK |
  | `practice_id` | `uuid` | NOT NULL, FK `practice(id) ON DELETE RESTRICT` |
  | `name` | `varchar(120)` | NOT NULL |
  | `description` | `varchar(500)` | NULL |
  | `duration_min` | `integer` | NOT NULL, `CHECK (duration_min > 0)` |
  | `active` | `boolean` | NOT NULL DEFAULT true |
  | `public_visible` | `boolean` | NOT NULL DEFAULT false |
  | `created_at` | `timestamptz` | NOT NULL DEFAULT now() |
  | `updated_at` | `timestamptz` | NOT NULL DEFAULT now() |

- Índices: `service_practice_name_idx (practice_id, name)` para el listado de gestión y `service_public_idx (practice_id, active, public_visible)` para el filtro público.
- `id` UUID v7 generado por Hibernate (`@UuidGenerator(style = VERSION_7)`), como el resto de las entidades.
- Timestamps en `timestamptz` (UTC en base; `hibernate.jdbc.time_zone=UTC` ya está configurado). `updated_at` lo setea el dominio con `Instant.now()` en cada mutación, igual que `Patient`; no se usa trigger de base para mantener el comportamiento testeable.
- `description` se acota a 500 caracteres en base y en Bean Validation aunque la KB diga `text`: es una entrada de usuario y conviene un límite explícito.
- **Sin precio**: la KB lo excluye de v1.

### D3: Semántica de `active` y `public_visible` + baja lógica
- **Decisión**: dos marcas independientes:
  - `active = false` ⇒ baja lógica. No admite nuevas reservas ni aparece en el catálogo público, pero sigue visible y editable en la gestión y **no** afecta turnos existentes (RN-AG-09).
  - `public_visible = false` ⇒ la prestación es utilizable internamente pero no se publica.
  - El catálogo público exige `active = true AND public_visible = true`.
  - `DELETE /api/v1/services/{id}` solo cambia `active` a `false`; **no** toca `public_visible` (una reactivación conserva la decisión de publicación anterior).
- La base impone la baja lógica: `REVOKE DELETE, TRUNCATE ON service FROM ${app_role}` (mismo patrón que `patient` en V7).
- **Alternativa descartada**: borrado físico con `ON DELETE SET NULL` en turnos. Rompe RN-AG-09 y la trazabilidad histórica.

### D4: Validación de duración contra la granularidad vigente
- **Decisión**: en cada alta/edición, `ServiceCatalogService` lee la granularidad **actual** desde `practice.slot_granularity_min` (vía `PracticeRepository` + `InstallationPractice.id()`), no desde una copia cacheada. Reglas:
  1. `durationMin > 0` → si no, `ValidationException("durationMin", "Min")` → 400 `VALIDATION_FAILED` (además del `@Min(1)` del DTO).
  2. `durationMin % slotGranularityMin == 0` → si no, `BusinessRuleException("DURATION_NOT_MULTIPLE_OF_GRANULARITY")` → 422.
- **Por qué no cachear la granularidad en `InstallationPractice`**: es editable en caliente (`PUT /practice/settings`) y `InstallationPractice` se cachea para toda la vida del proceso; cachearla produciría validaciones con un valor viejo.
- **Por qué 422 y no 400**: el múltiplo depende del estado del servidor (configuración del consultorio), no solo del cuerpo enviado; encaja con "regla de negocio violada" del spec `error-responses`.
- **Alternativa descartada**: validar solo en la base con `CHECK (duration_min % ...)`. La granularidad vive en otra tabla y no puede referenciarse en un `CHECK` de PostgreSQL.

### D5: Contrato de la API de gestión
- **Decisión**:

  | Método | Ruta | Semántica |
  |---|---|---|
  | `GET` | `/api/v1/services` | Lista **todas** las prestaciones del consultorio (activas e inactivas), ordenadas por nombre, con `active` y `publicVisible`. |
  | `GET` | `/api/v1/services/{id}` | Prestación por id, incluidas las inactivas; 404 `SERVICE_NOT_FOUND` si no existe o es de otro consultorio. |
  | `POST` | `/api/v1/services` | Crea; `active = true`; 201 con `Location` y cuerpo. |
  | `PUT` | `/api/v1/services/{id}` | Reemplaza `name`, `description`, `durationMin`, `publicVisible` y `active`; 200. Reactivar es `active = true`. |
  | `DELETE` | `/api/v1/services/{id}` | Baja lógica idempotente; 204; 404 si el id no existe. |

- El listado incluye inactivas a propósito: la pantalla de configuración (C-19) necesita poder reactivarlas; filtrar en el cliente es trivial.
- `PUT` incluye `active` para no necesitar un endpoint extra de reactivación; `DELETE` sigue siendo el gesto explícito de baja.
- Todas las operaciones filtran por `practice_id` de la instalación (single-tenant modelado para multi-tenant, DD-02).
- **Alternativa descartada**: `PATCH` para cambios parciales. El proyecto no usa `PATCH` en ningún endpoint y `PUT` completo es suficiente para un formulario de settings.

### D6: Catálogo público
- **Decisión**: `GET /api/v1/public/services`:
  - Sin autenticación (la ruta ya está declarada en `SecurityConfig` bajo `/api/v1/public/**`).
  - Devuelve solo `active = true AND public_visible = true`, ordenado por nombre.
  - DTO `PublicServiceResponse(id, name, description, durationMin)`: sin `active`, sin `publicVisible`, sin timestamps, sin `practice_id`, sin datos del consultorio.
  - `200` con lista vacía si no hay prestaciones publicadas.
  - Cabecera `Cache-Control: public, max-age=60`.
- **Cache**: se resuelve solo con cabecera HTTP (cliente/proxy), sin cache en memoria del servidor. La lista es pequeña, la consulta es barata y así no hay estado que invalidar al crear/editar/despublicar. Una despublicación puede tardar hasta 60 s en reflejarse en un cliente que cacheó la respuesta; es aceptable para un catálogo y está acotado.
- **Alternativa descartada**: cache en memoria con TTL e invalidación en escrituras. Agrega complejidad y riesgo de datos obsoletos sin beneficio medible en v1.

### D7: Rate limit del catálogo público reutilizando C-04
- **Decisión**: se reutiliza el contrato `shared.api.RateLimiter` y la implementación `InMemoryRateLimiter`, pero con una **instancia dedicada** para lecturas públicas (`PublicReadRateLimiter` en `shared.infrastructure`), no el bean de login (que está fijado en 10 intentos por ventana).
  - Clave: dirección de origen (`HttpServletRequest.getRemoteAddr()`). `InMemoryRateLimiter` hashea la clave con SHA-256 y nunca guarda la IP en claro.
  - Valores por defecto: 120 solicitudes por ventana de 60 s, configurables por propiedades (`app.public-read.rate-limit.max-requests`, `app.public-read.rate-limit.window-seconds`).
  - Al excederse: `RateLimitedException("RATE_LIMITED", retryAfterSeconds)` → 429 + `Retry-After`, sin IP en el cuerpo.
- **Por qué una instancia dedicada y no otro `@Bean` de tipo `RateLimiter`**: agregar un segundo bean del mismo tipo haría ambigua la inyección por tipo en `auth` (que hoy recibe el bean de login sin calificador). La instancia dedicada encapsulada evita tocar C-04.
- **Limitación conocida**: el limitador es en memoria y por instancia; con más de una instancia de la app el límite es por proceso. Suficiente para v1 (una instancia, DD-08) y coherente con C-04.
- **`getRemoteAddr()` vs `X-Forwarded-For`**: v1 usa `getRemoteAddr()` porque confiar en cabeceras reenviadas sin un proxy conocido permite evadir el límite falsificándolas. Configurar `ForwardedHeaderFilter` queda para el despliegue real (PA-03).

### D8: Orden de la migración V4 en bases ya migradas
- **Decisión**: se mantiene `V4` porque el roadmap lo asigna explícitamente y es el número libre correcto para el orden lógico (`V4` antes de `V5`). En bases nuevas (tests con PostgreSQL embebido, CI, instalaciones nuevas) Flyway aplica `V1, V2, V3, V4, V5, V7, V11` en orden, sin cambios de configuración.
- **Problema**: una base local que ya aplicó `V5`, `V7` y `V11` verá `V4` como migración pendiente anterior a la última aplicada; Flyway falla por defecto con "Detected resolved migration not applied to database: 4".
- **Resolución para desarrollo local** (documentar en `tasks.md` y en el README de migraciones si aplica): recrear la base local con `infra/postgres/init/01-roles.sql` (los datos locales no son productivos), o bien ejecutar una única vez con `spring.flyway.out-of-order=true` en el perfil local **sin commitear esa propiedad**. No se habilita `out-of-order` de forma permanente en `application.yml`.
- **Alternativa descartada**: renumerar la migración como `V12`. Contradice `CHANGES.md` y deja el orden del esquema histórico confuso.

### D9: Errores y códigos estables
- **Decisión**: se usan los códigos existentes y se agregan solo los necesarios:
  - `VALIDATION_FAILED` (400) para nombre/descripción/duración inválidos por Bean Validation o dominio.
  - `SERVICE_NOT_FOUND` (404) para id inexistente o de otro consultorio.
  - `DURATION_NOT_MULTIPLE_OF_GRANULARITY` (422) para duración no múltiplo.
  - `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `PASSWORD_CHANGE_REQUIRED` (403), `CSRF_REQUIRED` (403) y `RATE_LIMITED` (429) ya existen.
- Ningún error incluye el nombre de la prestación, la duración enviada ni datos del consultorio (spec `error-responses`).
- La violación de unicidad no aplica: no se agrega restricción de nombre único en v1 (ver Open Questions).

### D10: Estrategia de tests (Strict TDD)
- **Decisión**:
  - Unitarios puros (sin Spring): `PracticeServiceTest` (creación, validación de campos, baja/reactivación, independencia de marcas, `updatedAt`), `ServiceDurationPolicyTest` (múltiplo exacto, no múltiplo, duración inválida, granularidades 10/15/20/30/60).
  - Unitarios de aplicación con dobles: `ServiceCatalogServiceTest` (filtro por `practice_id`, 404, lectura de granularidad vigente, filtro público).
  - Integración con PostgreSQL embebido zonky: `ServiceSchemaIT` (V4 registrada, checks, defaults, índices, DELETE/TRUNCATE revocados), `ServiceRepositoryIT` (persistencia, orden, consultas), `ServicesEndpointsIT` (CRUD, 401/403/CSRF, 404, 422), `PublicServicesIT` (filtro, campos, cache, 429, solo GET).
  - ArchUnit: sin cambios esperados; se corre `ProductionArchitectureTest` como red de seguridad.
- Cada comportamiento se implementa RED → GREEN → TRIANGULATE → REFACTOR con al menos un caso feliz y un borde (ver `tasks.md`).

## Risks / Trade-offs

- **[Riesgo] Cambiar `slot_granularity_min` deja prestaciones existentes con duración no múltiplo**
  - *Mitigación*: C-07 valida al crear/editar la prestación, no al cambiar la configuración (eso es de C-08 y está fuera de alcance). Se documenta el hueco: C-08 debería advertir o bloquear el cambio de granularidad si hay prestaciones incompatibles. Se registra como riesgo para C-12/C-19 y no se modifica C-08 en este change.
- **[Riesgo] `V4` posterior a migraciones ya aplicadas en bases locales**
  - *Mitigación*: D8 (recrear la base local o `out-of-order` puntual). Tests y CI usan base nueva.
- **[Riesgo] Cache de 60 s muestra una prestación recién despublicada**
  - *Mitigación*: ventana acotada y aceptada para un catálogo; `max-age=60` es corto y no hay datos sensibles.
- **[Riesgo] Rate limit en memoria evadible con múltiples instancias o cabeceras falsas**
  - *Mitigación*: v1 corre una instancia (DD-08) y usa `getRemoteAddr()`; el endurecimiento por proxy queda para el despliegue (PA-03).
- **[Riesgo] Nombres duplicados de prestaciones confunden al paciente en el catálogo público**
  - *Mitigación*: no se impone unicidad porque la KB no la define (Open Questions); la UI de C-19 puede advertir duplicados. Agregar `UNIQUE (practice_id, lower(name))` más adelante requiere migración nueva.
- **[Trade-off] `DELETE` y `PUT active=false` producen el mismo efecto**
  - *Mitigación*: se documenta; `DELETE` es el gesto de baja y `PUT` permite reactivar. Ambos son idempotentes.

## Migration Plan

1. Crear `V4__service.sql` y ejecutar `./gradlew integrationTest --tests ServiceSchemaIT --tests MigrationsIT`.
2. En bases nuevas o de tests, Flyway aplica `V4` en orden; verificar `flyway_schema_history`.
3. En una base local ya migrada: recrear la base con `infra/postgres/init/01-roles.sql` (recomendado) o correr una vez con `spring.flyway.out-of-order=true` local, sin commitear la propiedad.
4. `hibernate.ddl-auto=validate` valida que la entidad `PracticeService` coincida con `V4` al arrancar.
5. Rollback: al ser una migración aditiva y pre-producción, se revierte restaurando un backup o recreando la base; no se edita `V4` una vez aplicada.

## Open Questions

Estos puntos no están definidos en la KB. Se elige un valor conservador y se marcan como `(TBD)` para confirmar con el profesional; ninguno bloquea el apply ni cambia el enfoque.

- **(TBD) Unicidad de nombre de prestación por consultorio**: la KB no la exige. v1 permite nombres repetidos; si se confirma que deben ser únicos, se agrega `UNIQUE (practice_id, lower(name))` en una migración nueva.
- **(TBD) Tope máximo de duración**: la KB solo exige `> 0`. v1 no impone un máximo (más allá del rango de `integer`); un tope razonable (p. ej. 480 min) puede agregarse luego.
- **(TBD) Campos públicos exactos**: la KB dice "campos públicos" sin enumerarlos. v1 expone `id`, `name`, `description`, `durationMin`; si el negocio pide mostrar el nombre del consultorio, se agrega en C-14.
- **(TBD) Valores del rate limit público**: la KB no fija números. v1 usa 120 solicitudes / 60 s por IP, configurable.
- **(TBD) TTL exacto del cache corto**: la KB dice "cache corto" sin número. v1 usa 60 s.
