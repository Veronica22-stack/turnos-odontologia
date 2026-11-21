# Spec Delta: platform-health

## Purpose

Permite a operadores, herramientas de despliegue y monitoreo saber si la aplicación está viva y puede usar su base de datos, sin revelar información interna.

## ADDED Requirements

### Requirement: Endpoint de salud público
El sistema SHALL exponer `GET /actuator/health` sin autenticación, respondiendo `200` con cuerpo JSON `{"status":"UP"}` cuando la aplicación y la base de datos están disponibles.

#### Scenario: Aplicación sana
- **GIVEN** la aplicación arrancó y la base de datos está accesible
- **WHEN** un cliente sin credenciales hace `GET /actuator/health`
- **THEN** recibe `200` con `Content-Type` JSON y `status` igual a `UP`

### Requirement: Salud refleja la base de datos
El sistema SHALL responder `503` con `status` igual a `DOWN` en `GET /actuator/health` cuando la base de datos no es accesible.

#### Scenario: Base de datos caída
- **GIVEN** la aplicación está en ejecución
- **WHEN** la base de datos deja de aceptar conexiones y un cliente hace `GET /actuator/health`
- **THEN** recibe `503` y `status` igual a `DOWN`

### Requirement: Salud no expone detalles
La respuesta de salud MUST NOT incluir detalles de componentes (nombres de base, versiones, rutas, hosts, espacio en disco) a clientes no autenticados, y los demás endpoints de actuator MUST NOT estar expuestos por HTTP.

#### Scenario: Respuesta sin detalles
- **WHEN** un cliente sin credenciales hace `GET /actuator/health`
- **THEN** el cuerpo contiene solo `status` y no contiene las claves `components` ni `details`

#### Scenario: Otros endpoints de actuator no disponibles
- **WHEN** un cliente hace `GET /actuator/env` o `GET /actuator/info`
- **THEN** la respuesta no es `200` y no contiene valores de configuración
