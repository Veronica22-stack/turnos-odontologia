# Tasks: C-07 `services-catalog`

> Strict TDD: cada comportamiento se implementa RED → GREEN → TRIANGULATE → REFACTOR. Todo comportamiento tiene al menos un caso feliz y un caso borde. Guardar la salida de cada corrida (roja y verde) como evidencia en la descripción del PR.

## 1. Red de seguridad y preparación

- [x] 1.1 Ejecutar `./gradlew check` en `backend/` y registrar el baseline verde (incluye `MigrationsIT`, `MigrationValidationIT`, `ProductionArchitectureTest` y `AccessPolicyIT`). Red de seguridad obligatoria: se modifican `README.md` y `AccessPolicyIT`.
- [x] 1.2 Confirmar que no existe ninguna migración `V4*` ni la tabla `service`, y que `practice.slot_granularity_min` está disponible (V5).
- [x] 1.3 Confirmar que `hibernate.ddl-auto=validate` está activo y que la entidad nueva deberá coincidir exactamente con la migración V4.

## 2. Migración V4 — esquema de prestaciones

- [x] 2.1 **RED**: crear `ServiceSchemaIT` en `database/` que verifique V4 registrada en `flyway_schema_history`, columnas y tipos de `service`, `CHECK (duration_min > 0)`, defaults `active = true` / `public_visible = false`, índices y revocación de `DELETE`/`TRUNCATE`. Ejecutar `./gradlew integrationTest --tests ServiceSchemaIT` → debe fallar.
- [x] 2.2 **GREEN**: crear `V4__service.sql` con la tabla `service`, checks, índices `(practice_id, name)` y `(practice_id, active, public_visible)`, y `REVOKE DELETE, TRUNCATE ON service FROM ${app_role}`. Ejecutar → debe pasar.
- [x] 2.3 **TRIANGULATE**: agregar casos borde a `ServiceSchemaIT`: duración `0` y `-1` rechazadas, defaults al insertar sin marcas, `DELETE` y `TRUNCATE` rechazados por privilegios, `INSERT`/`SELECT`/`UPDATE` permitidos. Ejecutar → debe pasar.
- [x] 2.4 **REFACTOR**: unificar nombres de constraints/índices (`service_duration_min_check`, `service_practice_name_idx`, `service_public_idx`), verificar arranque con `validate` y confirmar que no se editó ninguna migración ya aplicada.

## 3. Dominio — `PracticeService` y `ServiceDurationPolicy`

- [x] 3.1 **RED**: crear `PracticeServiceTest` con el caso feliz de `create` (nombre y descripción normalizados, `active = true`, `publicVisible = false`, `createdAt`/`updatedAt` presentes). Ejecutar → debe fallar.
- [x] 3.2 **GREEN**: implementar `agenda.domain.PracticeService` (`@Table(name = "service")`, UUID v7, `Instant` para timestamps, factories `create`/`existing`, mutadores `update`, `deactivate`, `activate`).
- [x] 3.3 **TRIANGULATE**: agregar bordes: nombre en blanco, nombre de más de 120 caracteres, descripción de más de 500, `deactivate` conserva `publicVisible`, `activate` reactiva y actualiza `updatedAt`, `update` cambia duración y visibilidad. Ejecutar → debe pasar.
- [x] 3.4 **RED**: crear `ServiceDurationPolicyTest` con el caso feliz (duración 45 con granularidad 15). Ejecutar → debe fallar.
- [x] 3.5 **GREEN**: implementar `ServiceDurationPolicy` con `validate(durationMin, granularityMin)`.
- [x] 3.6 **TRIANGULATE**: agregar bordes: duración 20 con granularidad 15 → `DURATION_NOT_MULTIPLE_OF_GRANULARITY`; duración 0 y -15 → `VALIDATION_FAILED`; duración igual a la granularidad; granularidades 10, 15, 20, 30 y 60 con múltiplos y no múltiplos. Ejecutar → debe pasar.
- [x] 3.7 **REFACTOR**: confirmar que `domain` no importa Spring ni `java.sql`; ejecutar `ProductionArchitectureTest`.

## 4. Persistencia — `PracticeServiceRepository`

- [x] 4.1 **RED**: crear `ServiceRepositoryIT` con guardar/leer por id y listar todas las prestaciones de la práctica ordenadas por nombre. Ejecutar → debe fallar.
- [x] 4.2 **GREEN**: implementar `PracticeServiceRepository` (puerto) y `SpringDataPracticeServiceRepository` (adaptador JPA) con `findAllByPracticeIdOrderByNameAsc` y `findPublicByPracticeId`.
- [x] 4.3 **TRIANGULATE**: agregar bordes: aislamiento entre dos `practice_id`; inactivas presentes en el listado de gestión y ausentes del público; activa no visible ausente del público; intento de `DELETE` físico rechazado por privilegios. Ejecutar → debe pasar.
- [x] 4.4 **REFACTOR**: revisar queries derivadas frente a `@Query`, verificar uso de los índices y que la entidad mapee todas las columnas de V4.

## 5. Aplicación — `ServiceCatalogService`

- [x] 5.1 **RED**: crear `ServiceCatalogServiceTest` con dobles de repositorio y `PracticeRepository`: crear usa la práctica de la instalación y la granularidad vigente; listar devuelve activas e inactivas; obtener un id inexistente lanza `SERVICE_NOT_FOUND`. Ejecutar → debe fallar.
- [x] 5.2 **GREEN**: implementar `agenda.application.ServiceCatalogService` con `listServices`, `getService`, `createService`, `updateService`, `deactivateService` y `listPublicServices`, y los records `ServiceData` / `PublicServiceData`.
- [x] 5.3 **TRIANGULATE**: agregar bordes: la granularidad cambia entre dos llamadas y la segunda usa el valor nuevo (no cacheado); duración no múltiplo en alta y en edición → 422; `deactivate` repetido no falla; `get`/`update`/`delete` de otra práctica → 404; el catálogo público excluye inactivas y no visibles. Ejecutar → debe pasar.
- [x] 5.4 **REFACTOR**: `@Transactional(readOnly = true)` en lecturas y de escritura en mutaciones; `application` sin depender de `api` ni `infrastructure`.

## 6. API de gestión — `/api/v1/services`

- [x] 6.1 **RED**: crear `ServicesEndpointsIT`: `POST` responde 201, `GET` lista, `GET /{id}` responde 200, `PUT` responde 200 y `DELETE` responde 204. Ejecutar → debe fallar.
- [x] 6.2 **GREEN**: crear los DTOs `record` con Bean Validation (`CreateServiceRequest`, `UpdateServiceRequest`, `ServiceResponse`) y `agenda.infrastructure.ServicesController` con `@PreAuthorize("hasRole('DENTIST')")`.
- [x] 6.3 **TRIANGULATE**: agregar bordes: 401 sin sesión; 403 `CSRF_REQUIRED` en `POST`/`PUT`/`DELETE` sin `X-XSRF-TOKEN`; 400 `VALIDATION_FAILED` con nombre vacío; 422 `DURATION_NOT_MULTIPLE_OF_GRANULARITY`; 404 `SERVICE_NOT_FOUND`; `DELETE` repetido responde 204; `PUT` con `active = true` reactiva; el listado incluye inactivas. Ejecutar → debe pasar.
- [x] 6.4 **REFACTOR**: mapeo DTO ↔ `ServiceData` en el controlador, sin exponer entidades JPA ni `practice_id` en las respuestas.

## 7. Catálogo público y límite de tasa

- [x] 7.1 **RED**: crear `PublicReadRateLimiterTest` en `shared/infrastructure/`: permite N solicitudes, bloquea la N+1 y devuelve el instante de reintento; no expone la clave original. Ejecutar → debe fallar.
- [x] 7.2 **GREEN**: implementar `shared.infrastructure.PublicReadRateLimiter` con una instancia dedicada de `InMemoryRateLimiter` y límites por propiedades (`app.public-read.rate-limit.max-requests` = 120, `window-seconds` = 60).
- [x] 7.3 **TRIANGULATE**: agregar bordes: dos claves distintas no se afectan; la ventana se reinicia al avanzar el `Clock`; un límite configurado en 1 bloquea la segunda solicitud; un límite no positivo se rechaza al construir. Ejecutar → debe pasar.
- [x] 7.4 **RED**: crear `PublicServicesIT`: `GET /api/v1/public/services` responde 200 sin sesión, devuelve solo activas y visibles con `id`, `name`, `description` y `durationMin`, y lista vacía cuando no hay publicadas. Ejecutar → debe fallar.
- [x] 7.5 **GREEN**: implementar `PublicServiceResponse`, `listPublicServices` y `agenda.infrastructure.PublicServicesController`.
- [x] 7.6 **TRIANGULATE**: agregar bordes: cabecera `Cache-Control: public, max-age=60`; 429 `RATE_LIMITED` con `Retry-After` tras superar el límite; `POST`/`PUT`/`DELETE` rechazados sin modificar prestaciones; orden por nombre; inactiva visible y activa no visible excluidas; el cuerpo no contiene `active`, `publicVisible`, timestamps ni `practice_id`. Ejecutar → debe pasar.
- [x] 7.7 **REFACTOR**: extraer la obtención de IP y el armado del 429 a un helper; confirmar que no se loguea la IP ni el catálogo completo.

## 8. Seguridad, arquitectura y verificación integral

- [x] 8.1 Actualizar `AccessPolicyIT` (archivo existente; el baseline de 1.1 es la red de seguridad) con dos casos: `/api/v1/services` sin sesión → 401 y `/api/v1/public/services` sin sesión → 200.
- [x] 8.2 Ejecutar `ProductionArchitectureTest` y confirmar que no se agregaron aristas entre módulos (todo el change vive en `agenda` + `shared`).
- [x] 8.3 Ejecutar `./gradlew check` completo y confirmar unitarios e integración en verde al 100%.
- [x] 8.4 Documentar en `README.md` (§3 Backend) la nota operativa de V4: en una base local que ya aplicó `V5`/`V7`/`V11`, recrear la base con `infra/postgres/init/01-roles.sql` o correr una vez con `spring.flyway.out-of-order=true` sin commitear la propiedad.

## 9. Cierre

- [x] 9.1 Presentar artefactos, decisiones y evidencia TDD (salidas roja/verde) para revisión. No commitear ni pushear hasta pedido explícito del usuario.
- [x] 9.2 Conventional commit sugerido para cuando se autorice: `feat(C-07): catálogo de prestaciones con baja lógica y catálogo público`.
