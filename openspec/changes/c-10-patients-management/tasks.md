# Tasks: C-10 `patients-management`

## 1. Base de datos y migración Flyway

- [x] 1.1 Crear migración `V7__patient.sql` con la tabla `patient`, índices de búsqueda, restricción de unicidad `UNIQUE (practice_id, dni)` y revocación de `DELETE` para `${app_role}`, y verificar con `./gradlew integrationTest --tests MigrationsIT`
- [x] 1.2 Crear test de integración de esquema (`PatientSchemaIT`) que valide constraints de tabla, unicidad de DNI por consultorio y rechazo de DELETE físico desde el rol de aplicación

## 2. Dominio y persistencia de pacientes (`pacientes`)

- [x] 2.1 Crear enum `PatientSource` (`MANUAL`, `PUBLIC_BOOKING`) y entidad de dominio `Patient` con normalización de DNI, validación de campos obligatorios y métodos de mutación controlada, y verificar con tests unitarios `PatientTest`
- [x] 2.2 Crear repositorio `PatientRepository` y su implementación Spring Data JPA `SpringDataPatientRepository` con soporte para búsqueda insensible a mayúsculas/minúsculas por término en nombre, apellido, DNI o teléfono, y verificar con `PatientRepositoryIT`
- [x] 2.3 Implementar servicio de dominio `FindOrCreatePatientByDni` respetando la regla RN-PA-03 (no sobreescribir datos si el DNI ya existe), y verificar con `FindOrCreatePatientByDniTest` (casos de alta nueva, paciente existente con mismos datos, y paciente existente con datos divergentes preservados)

## 3. Casos de uso y auditoría

- [x] 3.1 Implementar casos de uso `SearchPatients`, `GetPatientById`, `CreatePatient`, `UpdatePatient` y `DeactivatePatient` en `pacientes.application`, integrando el puerto `AuditLogger` para emitir `PATIENT_READ` en consultas de ficha individual, y verificar con tests unitarios
- [x] 3.2 Crear DTOs de entrada y salida (`PatientResponse`, `PatientSummaryResponse`, `CreatePatientRequest`, `UpdatePatientRequest`) con Bean Validation estricta y sin exponer entidades JPA, y verificar con tests unitarios de validación

## 4. Endpoints REST y seguridad

- [x] 4.1 Implementar controlador `PatientsController` exponiendo `GET /api/v1/patients`, `GET /api/v1/patients/{id}`, `POST /api/v1/patients`, `PUT /api/v1/patients/{id}` y `DELETE /api/v1/patients/{id}`, y verificar con `PatientsEndpointsIT`
- [x] 4.2 Verificar que cada endpoint exija sesión autenticada con rol `DENTIST`, que las mutaciones requieran cabecera `X-XSRF-TOKEN`, y que se capturen violaciones de DNI duplicado respondiendo 409

## 5. Reglas de arquitectura y verificación integral

- [x] 5.1 Actualizar `ModuleBoundaries` para permitir la arista de dependencia `pacientes → auditoria.api`, y verificar que pase `ModuleBoundariesRulesTest`
- [x] 5.2 Ejecutar suite completa de tests de backend (`./gradlew check`) verificando 100% de tests unitarios y de integración en verde

## Workflow follow-up

- Presentar artefactos para revisión y aprobación antes de iniciar apply.
