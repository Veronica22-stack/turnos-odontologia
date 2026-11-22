# Tasks: C-08 `working-hours-booking-settings`

## 1. Base de datos y migración Flyway

- [x] 1.1 Crear migración `V5__working_hours_and_booking_settings.sql` con la tabla `working_hours`, columnas nuevas en `practice` (`slot_granularity_min`, `min_notice_min`, `max_advance_days`, `reminder_hours_before`), checks y permisos para `${app_role}`, y verificar con `./gradlew integrationTest --tests MigrationsIT`
- [x] 1.2 Crear test de integración de esquema de base de datos (`WorkingHoursSchemaIT`) que verifique constraints (checks de horarios, weekdays 1..7, granularidad y rangos) y permisos de lectura/escritura del rol de aplicación

## 2. Dominio y persistencia de configuración del consultorio (`shared`)

- [x] 2.1 Actualizar entidad de dominio `Practice` con los campos de parámetros de reserva y sus métodos accesores, y verificar con tests unitarios
- [x] 2.2 Actualizar `PracticeRepository` y su implementación JPA para persistir los nuevos atributos de `Practice`, verificando con `PracticeRepositoryIT`
- [x] 2.3 Crear DTOs de request y response para settings del consultorio con Bean Validation (`PracticeSettingsRequest`, `PracticeSettingsResponse`), y verificar validación en tests unitarios

## 3. Dominio y persistencia de horarios de atención (`agenda`)

- [x] 3.1 Crear Value Object / Entidad `WorkingHours` en `agenda.domain` con validaciones de inicio < fin y día de la semana (1..7), y verificar con test unitario `WorkingHoursTest`
- [x] 3.2 Crear servicio de dominio / validador `WorkingHoursScheduleValidator` que rechace solapamientos entre tramos de un mismo día, y verificar con `WorkingHoursScheduleValidatorTest` (casos felices, solapamiento parcial, total e idéntico)
- [x] 3.3 Crear `WorkingHoursRepository` e implementación JPA en `agenda.infrastructure`, y verificar con `WorkingHoursRepositoryIT`

## 4. Casos de uso y controladores REST

- [x] 4.1 Implementar casos de uso `GetPracticeSettings` y `UpdatePracticeSettings` en `shared.application`, exponer `GET /api/v1/practice/settings` y `PUT /api/v1/practice/settings` con controlador REST, y verificar con `PracticeSettingsEndpointsIT`
- [x] 4.2 Implementar casos de uso `GetWorkingHours` y `ReplaceWorkingHours` en `agenda.application`, exponer `GET /api/v1/working-hours` y `PUT /api/v1/working-hours`, y verificar con `WorkingHoursEndpointsIT`
- [x] 4.3 Verificar control de acceso y seguridad: 401 en peticiones sin autenticación, 403 en falta de rol DENTIST o falta de CSRF en PUT, y verificar con tests de seguridad dedicados

## 5. Reglas de arquitectura y verificación integral

- [x] 5.1 Ejecutar reglas de arquitectura ArchUnit (`./gradlew test --tests ModuleBoundariesRulesTest`) y verificar que las dependencias entre `agenda`, `shared` y demás módulos respeten las fronteras
- [x] 5.2 Ejecutar suite completa de tests de backend (`./gradlew check`) verificando 100% de tests unitarios y de integración en verde

## Workflow follow-up

- Presentar artefactos para revisión y aprobación antes de iniciar apply.
