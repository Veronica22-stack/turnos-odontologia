# Matriz de tests — C-14 `public-booking-api` y C-15 `booking-token-management`

Responsable: Gabriel (según `CHANGES.md` §División de tareas). Esta matriz cubre **cada escenario de cada spec delta** de los dos changes implementados en esta entrega, con el criterio `feliz / error de negocio / borde` y el test que lo verifica.

Leyenda: **OK** = hay un test que verifica directamente el comportamiento · **PARCIAL** = cubierto de forma indirecta, en otra capa o por otro change · **FALTA** = no hay test que lo verifique (se indica qué agregar).

Abreviaturas de archivos de test:

| Prefijo | Archivo |
|---|---|
| `PBIT` | `backend/src/test/java/.../reservas/infrastructure/PublicBookingsEndpointsIT.java` |
| `BMTIT` | `backend/src/test/java/.../reservas/infrastructure/BookingManagementEndpointsIT.java` |
| `PUCU` | `backend/src/test/java/.../reservas/application/PublicBookingUseCaseTest.java` |
| `BMU` | `backend/src/test/java/.../reservas/application/BookingManagementUseCaseTest.java` |
| `BTC` | `backend/src/test/java/.../reservas/domain/BookingTokenCryptoTest.java` |
| `BTS` | `backend/src/test/java/.../database/BookingTokenSchemaIT.java` |
| `BTP` | `backend/src/test/java/.../database/BookingTokenPrivilegesIT.java` |
| `ARCH` | `architecture/ProductionArchitectureTest.java`, `architecture/ModuleBoundariesRulesTest.java`, `architecture/AvailabilityArchitectureTest.java`, `architecture/AgendaApiContractsTest.java` |
| `C-11/C-12/C-13` | tests de los changes previos citados por nombre |

## C-14 `public-booking-api`

### `public-booking`

| Requisito | Escenario | Tipo | Test(s) | Estado |
|---|---|---|---|---|
| Public booking endpoint | Reserva pública correcta | feliz | `PBIT.reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado`, `PUCU.reservaCorrectaCreaTurnoYTokenHasheado` | OK |
| Public booking endpoint | El cliente no elige duración ni fin | borde | `PBIT.elClienteNoPuedeElegirFinNiDuracion` (Ignora `endsAt`/`durationMin` del cuerpo) | OK |
| Booking validation | Slot demasiado próximo (anticipación mínima) | error de negocio | `PUCU.inicioDentroDeLaAnticipacionMinimaEs422`, `PBIT.inicioDentroDeLaAnticipacionMinimaDevuelve422` (`BOOKING_TOO_SOON`) | OK |
| Booking validation | Prestación no pública | error de negocio | `PBIT.prestacionNoPublicaEsRechazada` (404 `SERVICE_NOT_FOUND`, sin turno creado) | OK |
| Slot contention | Carrera por el mismo slot | error de negocio | `PBIT.slotOcupadoDevuelve409SinCrearOtroTurno` (conflicto secuencial); `C-11 AppointmentConcurrencyIT` (carrera real en el dominio) | PARCIAL |
| Anti-abuse controls | Honeypot completo | borde | `PBIT.honeypotDevuelveRespuestaGenericaSinEfectos` | OK |
| Anti-abuse controls | Rate limit excedido | error de negocio | `PUCU.rateLimitRechazaAntesDeCrearNada`, `PBIT.rateLimitDevuelve429ConRetryAfter` (429 + `Retry-After`) | OK |
| Anti-abuse controls | Límite por contacto | error de negocio | `PUCU.limitePorContactoRechazaLaReserva` | OK |
| Privacy notice acceptance | Aceptación ausente | error de negocio | `PBIT.sinAceptarAvisoDePrivacidadDevuelve400` | OK |
| Privacy notice acceptance | Aceptación registrada | feliz | `PBIT.reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado` (versión e instante en `booking_token`) | OK |
| Management token issuance | Token emitido al reservar, solo hash | feliz | `PBIT.reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado`, `BTC.hashEsSha256HexDe64CaracteresYEstable` | OK |
| Management token issuance | Token para un recordatorio días después | borde | `reservas/infrastructure/BookingManagementLinkProviderTest.emiteTokenNuevoHasheadoConVigenciaPosteriorAlTurno` (hash y `endsAt + TTL`) + `C-13 NotificationDispatcherIT` (link en el cuerpo) | OK |
| Patient reuse | DNI ya existente sin sobreescritura | borde | `PBIT.dniExistenteNoSobreescribeLosDatosDelPaciente` + `C-10 FindOrCreatePatientByDniTest` | OK |
| Booking privacy in logs | Registro sin PII | borde | `PBIT.datosPersonalesNoAparecenEnLogs` (`OutputCaptureExtension`) y `PBIT.reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado` (body sin PII) | OK |

### `appointments`, `patients-management`, `database-migrations`, `api-access-policy`, `error-responses`, `module-boundaries`, `runtime-configuration`

| Spec | Escenario | Tipo | Test(s) | Estado |
|---|---|---|---|---|
| appointments | Alta pública reutiliza el núcleo | feliz | `PBIT.reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado` | OK |
| appointments | Conflicto de slot traducido a `SLOT_TAKEN` | error de negocio | `PBIT.slotOcupadoDevuelve409SinCrearOtroTurno`, `PUCU.slotNoOfrecidoDevuelve409SinCrearNada` | OK |
| appointments | Sin sobrebooking público | borde | `PBIT.reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado` (consulta SQL: `overbooking=false` y `duration_min_snapshot=30`) | OK |
| appointments | Conteo para el límite por contacto | error de negocio | `PUCU.limitePorContactoRechazaLaReserva` | OK |
| appointments | Turnos cancelados no cuentan | borde | `agenda/infrastructure/ActiveBookingsLookupIT.soloCuentaTurnosFuturosConfirmados` (CONFIRMED futuro = 1; CANCELLED y pasado no cuentan) | OK |
| patients-management | Reutilización sin sobreescritura | borde | `C-10 FindOrCreatePatientByDniTest` | OK (en dominio) |
| patients-management | Creación con origen `PUBLIC_BOOKING` | feliz | El adaptador fija el origen; sin aserción en BD | PARCIAL |
| patients-management | Sin exposición de datos personales | borde | Contrato con solo IDs y banderas; verificado por `ARCH` | PARCIAL |
| database-migrations | Esquema y restricciones de `booking_token` | feliz | `BTS.bookingTokenTieneLasColumnasYRestriccionesDelDiseno` | OK |
| database-migrations | Hash duplicado rechazado | error de negocio | `BTS.hashDuplicadoEsRechazadoYVariosTokensPorTurnoSeAceptan` | OK |
| database-migrations | `DELETE`/`TRUNCATE` denegados | error de negocio | `BTP.laAplicacionNoPuedeBorrarNiTruncar` | OK |
| database-migrations | Numeración y Flyway validate | borde | `BTS.laMigracionV16QuedaRegistradaComoExitosa`, `BTS.laMigracionV16SeAplicaDesdeUnaBaseMigradaHastaV15SinVersionesFueraDeOrden` | OK |
| api-access-policy | Reserva anónima permitida | feliz | `PBIT.reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado` | OK |
| api-access-policy | Mutación sin CSRF → 403 | error de negocio | `PBIT.sinCsrfDevuelve403YNoCreaTurno` | OK |
| api-access-policy | Sin exención de CSRF | borde | `SecurityConfig` (solo `/auth/login` exento) + test anterior | PARCIAL |
| error-responses | `SLOT_TAKEN` 409 sin mensaje de BD | error de negocio | `PBIT.slotOcupadoDevuelve409SinCrearOtroTurno` | OK |
| error-responses | Honeypot sin filtrar información | borde | `PBIT.honeypotDevuelveRespuestaGenericaSinEfectos` | OK |
| error-responses | `VALIDATION_FAILED` con `errors[]` | error de negocio | `PBIT.validacionIncompletaDevuelve400` | OK |
| module-boundaries | Acceso solo por `api` ajena | borde | `ARCH` (`ProductionArchitectureTest`) | OK |
| module-boundaries | Sin acceso a capas internas | borde | `ARCH` (`ModuleBoundariesRulesTest`) | OK |
| module-boundaries | SPI implementada en `reservas` | feliz | `C-13 NotificationDispatcherIT` + `ARCH` | OK |
| runtime-configuration | `RATE_LIMIT_PUBLIC_PER_MIN` cableado | feliz | `PUCU.rateLimitRechazaAntesDeCrearNada`, `PBIT.rateLimitDevuelve429ConRetryAfter`, `BMTIT.enumeracionExcedidaDevuelve429ConRetryAfter` | OK |
| runtime-configuration | Vigencia = `endsAt + BOOKING_TOKEN_TTL_DAYS` | feliz | `PBIT.reservaAnonimaConCsrfCreaTurnoYTokenSoloHasheado` (64 hex y `expires_at`) | OK |
| runtime-configuration | Configuración inválida al arrancar | borde | `PUCU.configuracionDeTtlNoPositivaFallaAlArrancar` (TTL 0 y -1 → `IllegalArgumentException`) | OK |

## C-15 `booking-token-management`

### `booking-token-management`

| Requisito | Escenario | Tipo | Test(s) | Estado |
|---|---|---|---|---|
| Token-based lookup | Consulta con token válido (incluye `serviceId` para reprogramar, pedido P1 de C-25) | feliz | `BMTIT.vistaConTokenValidoEsAcotadaYAudita` (`$.serviceId`), `BMU.vistaConTokenValidoEsAcotadaYAudita` | OK |
| Token-based lookup | Comparación en tiempo constante | borde | `BTC.comparacionEnTiempoConstanteAceptaIgualesYRechazaDistintos` | OK |
| Token-based lookup | Sin acceso a datos ajenos | borde | `BMTIT.tokenSoloDaAccesoAlTurnoVinculado` (dos turnos/tokens, cada token devuelve solo su turno) | OK |
| Generic token errors | Token expirado | error de negocio | `BTC.tokenSoloEsUsableAntesDeExpirarYSinRevocar` (dominio) y `BMTIT.tokenExpiradoDevuelveElMismo404Generico` (HTTP, token con `expires_at` en el pasado) | OK |
| Generic token errors | Token revocado | error de negocio | `BMTIT.tokenInvalidoRevocadoYExpiradoDanElMismoError` | OK |
| Generic token errors | Turno pasado, solo lectura | borde | `BMTIT.turnoPasadoEsSoloLectura`, `BMU.turnoPasadoEsSoloLectura` | OK |
| Cancellation by token | Cancelación correcta (`PATIENT`) | feliz | `BMTIT.cancelarConTokenRegistraPatientYRevocaElToken`, `BMU.cancelarRevocaTodosLosTokensYRegistraPatient` | OK |
| Cancellation by token | Cancelación sin CSRF | error de negocio | `BMTIT.sinCsrfNoCancela` | OK |
| Rescheduling by token | Reprogramación correcta | feliz | `BMTIT.reprogramarMueveElMismoTurnoYConservaElToken`, `BMU.reprogramacionCorrectaConservaTokenYAjustaVigencia` | OK |
| Rescheduling by token | Conflicto en el nuevo slot | error de negocio | `BMTIT.reprogramarASlotOcupadoDevuelve409YConservaElOriginal`, `BMU.reprogramacionConConflictoConservaElTurnoOriginal` | OK |
| Rescheduling by token | Reprogramación sin CSRF | error de negocio | `BMTIT.reprogramacionSinCsrfDevuelve403` (403 `CSRF_REQUIRED` y turno intacto) | OK |
| Anti-enumeration rate limit | Enumeración de tokens | error de negocio | `BMTIT.enumeracionExcedidaDevuelve429ConRetryAfter` (429 + `Retry-After`) | OK |
| Anti-enumeration rate limit | `Referrer-Policy: no-referrer` | borde | `BMTIT.vistaConTokenValidoEsAcotadaYAudita` (header) | OK |
| Token use audit | Uso auditado | feliz | `BMTIT.vistaConTokenValidoEsAcotadaYAudita`, `BMU.vistaConTokenValidoEsAcotadaYAudita` | OK |
| Token use audit | Fallo de auditoría | error de negocio | `BMU.falloDeAuditoriaRevierteElUsoDelToken` (fail-closed desde gestión por token) | OK |

### `appointments`, `audit-log`, `error-responses`, `module-boundaries`, `api-access-policy`, `runtime-configuration`

| Spec | Escenario | Tipo | Test(s) | Estado |
|---|---|---|---|---|
| appointments | Cancelación por el paciente (`PATIENT`) | feliz | `BMTIT.cancelarConTokenRegistraPatientYRevocaElToken` | OK |
| appointments | Sin duplicar la máquina de estados | borde | `BMU.segundaCancelacionNoPublicaOtroEvento` (segundo uso → 404 genérico, una sola auditoría) | OK |
| audit-log | Registro `BOOKING_TOKEN_USE` sin usuario | feliz | `BMTIT.vistaConTokenValidoEsAcotadaYAudita`, `BMU.vistaConTokenValidoEsAcotadaYAudita` | OK |
| audit-log | `details` con `outcome` y `reason_code`, sin token | borde | `BMU.vistaConTokenValidoEsAcotadaYAudita` y consulta SQL en `BMTIT.vistaConTokenValidoEsAcotadaYAudita` | OK |
| error-responses | Error indistinto inválido/revocado | borde | `BMTIT.tokenInvalidoRevocadoYExpiradoDanElMismoError` (cuerpo idéntico salvo `instance`, que es la ruta solicitada) | OK |
| error-responses | `SLOT_TAKEN` al reprogramar | error de negocio | `BMTIT.reprogramarASlotOcupadoDevuelve409YConservaElOriginal` | OK |
| error-responses | `RATE_LIMITED` por enumeración | error de negocio | `BMTIT.enumeracionExcedidaDevuelve429ConRetryAfter` | OK |
| module-boundaries | `reservas → auditoria.api` permitido | feliz | `ARCH` (`ProductionArchitectureTest`) | OK |
| module-boundaries | Solo `auditoria.api` | borde | `architecture/BookingAuditBoundaryTest.reservasSoloDependeDeAuditoriaApi` y `auditoriaNoDependeDeReservas` | OK |
| module-boundaries | Auditoría sigue aislada | borde | `ARCH` (`ModuleBoundariesRulesTest`, escenarios existentes) | OK |
| api-access-policy | Consulta anónima con token | feliz | `BMTIT.vistaConTokenValidoEsAcotadaYAudita` | OK |
| api-access-policy | Mutación anónima con CSRF | feliz | `BMTIT.cancelarConTokenRegistraPatientYRevocaElToken`, `BMTIT.reprogramarMueveElMismoTurnoYConservaElToken` | OK |
| api-access-policy | Sin exención de CSRF | borde | `SecurityConfig` + `BMTIT.sinCsrfNoCancela` | PARCIAL |
| runtime-configuration | Límite de intentos por IP | error de negocio | `BMTIT.enumeracionExcedidaDevuelve429ConRetryAfter` (con `RATE_LIMIT_PUBLIC_PER_MIN=3`) | OK |
| runtime-configuration | Clave = `remoteAddr`, nunca `X-Forwarded-For` | borde | `BMTIT.enumeracionExcedidaDevuelve429ConRetryAfter` envía `X-Forwarded-For` falso y sigue limitando por `remoteAddr` | OK |

## Resumen

| Change | Escenarios | OK | PARCIAL | FALTA |
|---|---|---|---|---|
| C-14 | 38 | 34 | 4 | 0 |
| C-15 | 30 | 29 | 1 | 0 |

Las matrices de los changes anteriores de la responsabilidad (`C-01`, `C-02`, `C-05`, `C-06`, `C-11`) ya existen dentro de sus `verify-report.md`, en la sección «Matriz de cobertura/trazabilidad de escenarios». `C-08` y `C-10` no tienen una matriz separada: sus escenarios se verifican en los `verify-report.md`/tests de esos changes.

No quedan escenarios en **FALTA**. Los **PARCIAL** restantes son cobertura indirecta aceptable (por ejemplo, la carrera real de dos reservas concurrentes se prueba en el dominio de C-11, no en el endpoint de C-14; la no exención de CSRF se garantiza por la cadena de seguridad y se verifica en los IT de 403). Esta matriz se creó como archivo nuevo; la implementación y los tests se ajustaron según la verificación independiente (ver `verify-report.md`).
