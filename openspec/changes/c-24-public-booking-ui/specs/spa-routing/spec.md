# Spec Delta: spa-routing

## ADDED Requirements

### Requirement: Pasos de reserva en la URL
El flujo `/reservar` SHALL reflejar el paso en la query `paso` (sin parámetro: paso 1; `?paso=2`; `?paso=3`) y apilar una entrada de historial al avanzar (`pushState`), de modo que atrás y adelante recorren los pasos conservando en memoria lo elegido. La URL MUST NOT contener nada más que el número de paso. Sin estado en memoria (recarga), la SPA SHALL normalizar `?paso=2|3` a `/reservar` con `replaceState`; salir de `/reservar` y volver SHALL empezar sin lo elegido.

#### Scenario: Atrás vuelve al paso anterior
- **GIVEN** la persona eligió una prestación (URL `/reservar?paso=2`) y un horario (URL `/reservar?paso=3`)
- **WHEN** usa atrás del navegador
- **THEN** la URL es `/reservar?paso=2`, se muestra el paso 2 con la prestación y el horario aún marcados

#### Scenario: Adelante restaura el paso siguiente
- **GIVEN** la persona volvió del paso 3 al paso 2 con atrás del navegador
- **WHEN** usa adelante del navegador
- **THEN** la URL es `/reservar?paso=3` y se muestra el paso 3 con los datos cargados

#### Scenario: Recarga de un paso sin estado
- **WHEN** se carga directamente `/reservar?paso=3` sin estado en memoria
- **THEN** la URL pasa a `/reservar` sin agregar una entrada de historial y se muestra el paso 1

#### Scenario: Valor de paso inválido
- **WHEN** se carga `/reservar?paso=9` o `/reservar?paso=abc`
- **THEN** se muestra el paso 1 y la URL se normaliza a `/reservar`

#### Scenario: Sin datos en la URL
- **GIVEN** una persona en el paso 3 con datos cargados
- **WHEN** se inspecciona `location.href`
- **THEN** es exactamente `/reservar?paso=3`, sin prestación, horario ni datos personales

## MODIFIED Requirements

### Requirement: Token de gestión fuera de títulos y registros
El parámetro `token` de `/turno/{token}` MUST NOT aparecer en el título del documento, en el parámetro `next` ni en mensajes de consola o de error. Las rutas `/reservar` y `/reservar/confirmado` MUST NOT llevar en la URL, el título del documento ni la consola datos personales del paciente (nombre, DNI, teléfono, email) ni el identificador de la reserva.

#### Scenario: Título de gestión de turno
- **WHEN** se navega a `/turno/AbC-123_x`
- **THEN** el título del documento no contiene `AbC-123_x` y no se escribe nada en la consola que lo contenga

#### Scenario: Confirmación de reserva sin datos en la URL
- **GIVEN** una reserva confirmada con DNI `30456789` e identificador de reserva `7b1e…`
- **WHEN** se muestra `/reservar/confirmado`
- **THEN** la URL es exactamente `/reservar/confirmado`, el título es "Reserva confirmada" con la marca y ni la URL, ni el título, ni la consola contienen el DNI, el email ni el identificador
