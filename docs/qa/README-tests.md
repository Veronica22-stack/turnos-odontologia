# README de tests

Guía para clonar el proyecto y ejecutar toda la suite (backend + frontend), con foco en los tests de C-14 y C-15. Complemento de la [matriz de tests](matriz-de-tests.md) y del [README general](../../README.md).

## 1. Requisitos

| Herramienta | Versión | Uso |
|---|---|---|
| JDK | 21 (Gradle descarga el toolchain si no está instalado) | backend |
| Gradle | Wrapper incluido (`backend/gradlew`, 9.8.2) | backend |
| Node.js | 22 (`frontend/.nvmrc`) | frontend |
| PostgreSQL | 17+ **solo para ejecutar la app**; los tests usan un PostgreSQL embebido (zonky) | tests: no hace falta instalarlo |

No se usa Docker ni Testcontainers (decisión DD-10).

## 2. Clonar y preparar

```bash
git clone <URL-del-repositorio>
cd turnos-odontologia
cp .env.example .env          # PowerShell: Copy-Item .env.example .env
```

El `.env` es necesario para **arrancar la aplicación**, no para correr los tests: `EmbeddedPostgresSupport` crea una base temporal con sus propios roles y properties vía `@DynamicPropertySource`. Nunca versionar credenciales reales.

## 3. Backend

Desde `backend/`:

```bash
./gradlew test             # unitarios + reglas de arquitectura (ArchUnit), sin base de datos
./gradlew integrationTest  # solo *IT, con PostgreSQL embebido
./gradlew check            # todo lo anterior (lo que se usa en CI/verify)
./gradlew check jacocoTestReport   # cobertura en build/reports/jacoco/test/html/index.html
```

En PowerShell: `.\gradlew test`. Usar una terminal **no elevada** (PostgreSQL se niega a arrancar como Administrador en Windows).

### Ejecutar un grupo o test puntual

```bash
./gradlew test --tests '*BookingTokenCryptoTest' --tests '*PublicBookingUseCaseTest'
./gradlew integrationTest --tests '*PublicBookingsEndpointsIT' --tests '*BookingManagementEndpointsIT'
./gradlew integrationTest --tests '*BookingTokenSchemaIT'
./gradlew test --tests '*ProductionArchitectureTest' --tests '*AgendaApiContractsTest'
```

### Tests nuevos de C-14 / C-15

| Capa | Clase |
|---|---|
| Dominio | `reservas/domain/BookingTokenCryptoTest` |
| Aplicación | `reservas/application/PublicBookingUseCaseTest`, `BookingManagementUseCaseTest` |
| Infraestructura | `reservas/infrastructure/BookingManagementLinkProviderTest` |
| Base de datos | `database/BookingTokenSchemaIT`, `BookingTokenPrivilegesIT` |
| Endpoint | `reservas/infrastructure/PublicBookingsEndpointsIT`, `BookingManagementEndpointsIT` |
| Arquitectura | `architecture/BookingAuditBoundaryTest` (arista `reservas → auditoria.api`) |
| Contrato reutilizado | `agenda/infrastructure/ActiveBookingsLookupIT` (conteo por contacto) |

El resultado de la última corrida completa: **787 unitarios + 542 integración, 0 fallos**, ver los `verify-report.md` de C-14 y C-15.

## 4. Frontend

Desde `frontend/`:

```bash
npm ci
npm test
npm run typecheck
npm run build
```

## 5. Estructura de la suite

- `*Test` → unitarios (`./gradlew test`), sin base de datos, fakes manuales o Mockito.
- `*IT` → integración (`./gradlew integrationTest`), extienden `AbstractIntegrationTest` y usan `EmbeddedPostgresSupport`; patrón de endpoint: `MockMvcBuilders.webAppContextSetup(...).apply(springSecurity())`.
- Reglas de arquitectura: `ProductionArchitectureTest`, `ModuleBoundariesRulesTest`, `AvailabilityArchitectureTest`, `AgendaApiContractsTest` (fallan el build si se rompe el grafo de módulos).
- Helpers: `support/AppointmentFixtures` (turnos/prestaciones/horarios y limpieza, incluye `booking_token` antes de `appointment`), `notificaciones/NotificationTestConfig` (`MutableClock` y `ControllableEmailSender`).

## 6. Nota para este entorno (Windows + OneDrive)

Si OneDrive bloquea archivos dentro de `backend/build/` (`Unable to delete directory ... build/classes/java/main`), se puede redirigir el directorio de build a una ruta externa sin modificar el repo, mediante un init script:

```groovy
// %TEMP%/turnos-build-init.gradle
allprojects {
    layout.buildDirectory.set(file('C:/Users/Usuario/AppData/Local/Temp/opencode/turnos-build/' + project.name))
}
```

```bash
.\gradlew check --init-script "C:\Users\Usuario\AppData\Local\Temp\opencode\turnos-build-init.gradle"
```

También conviene excluir `node_modules/`, `backend/build/` y `backend/.gradle/` de la sincronización de OneDrive.

## 7. Trazabilidad

La correspondencia escenario → test (feliz / error de negocio / borde) está en [matriz-de-tests.md](matriz-de-tests.md). Las matrices de C-01, C-02, C-05, C-06 y C-11 están en la sección «Matriz de cobertura/trazabilidad de escenarios» de sus respectivos `openspec/changes/archive/*/verify-report.md`.
