# api-client Specification

## Purpose
Define cómo la SPA llama a la API del backend: mismo origen con credenciales de sesión, protección CSRF, validación del cuerpo recibido y un único modelo de error que las pantallas pueden mostrar sin filtrar datos sensibles.

## Requirements

### Requirement: Llamadas al mismo origen bajo /api/v1
Toda llamada a la API SHALL dirigirse a rutas relativas bajo `/api/v1` del mismo origen, enviando las cookies del mismo origen y la cabecera `Accept: application/json`; una ruta absoluta o con otro origen MUST rechazarse sin enviar la petición.

#### Scenario: GET relativo
- **WHEN** la SPA pide `GET /auth/me`
- **THEN** se envía una petición a `/api/v1/auth/me` con credenciales del mismo origen y `Accept: application/json`

#### Scenario: Ruta con otro origen
- **WHEN** se pide la ruta `https://evil.example/x`
- **THEN** la llamada falla con un error de uso y no se ejecuta ninguna petición de red

### Requirement: Cabecera CSRF en métodos que modifican estado
Las peticiones `POST`, `PUT`, `PATCH` y `DELETE` SHALL incluir la cabecera `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN` cuando exista; las peticiones `GET` y `HEAD` MUST NOT incluirla.

#### Scenario: POST con cookie CSRF
- **GIVEN** la cookie `XSRF-TOKEN=abc123`
- **WHEN** la SPA envía `POST /auth/logout`
- **THEN** la petición lleva la cabecera `X-XSRF-TOKEN: abc123`

#### Scenario: GET sin cabecera CSRF
- **GIVEN** la cookie `XSRF-TOKEN=abc123`
- **WHEN** la SPA envía `GET /auth/me`
- **THEN** la petición no lleva la cabecera `X-XSRF-TOKEN`

#### Scenario: POST sin cookie CSRF
- **GIVEN** que no existe la cookie `XSRF-TOKEN`
- **WHEN** la SPA envía `POST /auth/logout`
- **THEN** la petición se envía sin la cabecera `X-XSRF-TOKEN`

### Requirement: Cuerpo JSON validado
Una respuesta exitosa con cuerpo SHALL validarse contra la forma esperada por quien llama; un cuerpo que no es JSON o no tiene la forma esperada MUST producir un error `invalid-response` en lugar de datos.

#### Scenario: Forma válida
- **GIVEN** una respuesta 200 `{"id":"u1","fullName":"Dra. Romero"}`
- **WHEN** se decodifica como usuario de sesión
- **THEN** el resultado es exitoso con esos datos

#### Scenario: Forma inválida
- **GIVEN** una respuesta 200 `{"id":42}`
- **WHEN** se decodifica como usuario de sesión
- **THEN** el resultado es un error de tipo `invalid-response`

#### Scenario: Respuesta sin contenido
- **GIVEN** una respuesta 204
- **WHEN** se completa la llamada
- **THEN** el resultado es exitoso sin datos

### Requirement: Errores problem+json normalizados
Una respuesta de error con `application/problem+json` SHALL convertirse en un error `problem` con `status`, `code`, la lista de `errors` por campo si existe y el `errorId` si existe; los textos de la respuesta MUST NOT mostrarse tal cual al usuario.

#### Scenario: Error de dominio
- **GIVEN** una respuesta 409 `application/problem+json` con `code = "SLOT_TAKEN"`
- **WHEN** se completa la llamada
- **THEN** el resultado es un error `problem` con `status = 409` y `code = "SLOT_TAKEN"`

#### Scenario: Errores de validación por campo
- **GIVEN** una respuesta 400 con `code = "VALIDATION_FAILED"` y `errors = [{"field":"email","code":"Email"}]`
- **WHEN** se completa la llamada
- **THEN** el error `problem` expone `errors` con el campo `email` y el código `Email`

#### Scenario: Error sin problem+json
- **GIVEN** una respuesta 502 con cuerpo HTML
- **WHEN** se completa la llamada
- **THEN** el resultado es un error `problem` con `status = 502` y `code = "UNEXPECTED_RESPONSE"`

### Requirement: Fallas de red y timeout
Una petición que no obtiene respuesta SHALL producir un error `network`, y una que supera el tiempo máximo (15 s por defecto, configurable) MUST abortarse y producir un error `timeout`.

#### Scenario: Sin conexión
- **GIVEN** que la red rechaza la petición
- **WHEN** se completa la llamada
- **THEN** el resultado es un error de tipo `network`

#### Scenario: Timeout
- **GIVEN** un servidor que no responde y un tiempo máximo de 15 s
- **WHEN** transcurren 15 s
- **THEN** la petición se aborta y el resultado es un error de tipo `timeout`

### Requirement: Sesión vencida
Una respuesta 401 a una llamada que no sea la consulta inicial de sesión SHALL producir un error `unauthorized` y notificar que la sesión venció.

#### Scenario: 401 en una llamada privada
- **GIVEN** una sesión autenticada
- **WHEN** una llamada a `GET /patients` recibe 401
- **THEN** el resultado es un error `unauthorized` y se emite el aviso de sesión vencida una sola vez

### Requirement: Datos sensibles fuera de la consola
El cliente HTTP MUST NOT escribir en la consola cuerpos de petición o respuesta, cabeceras ni rutas con parámetros.

#### Scenario: Error con datos personales
- **GIVEN** una llamada `POST /public/bookings` con un DNI en el cuerpo que falla con 500
- **WHEN** se completa la llamada
- **THEN** no se escribe nada en la consola que contenga el DNI
