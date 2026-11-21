# Spec Delta

## Purpose

Define el consultorio único de la instalación (single-tenant v1, modelado para multi-tenant) y cómo el resto del sistema obtiene su identificador y su zona horaria, para que toda tabla de negocio pueda llevar `practice_id` (DD-02, RN-GL-03).

## ADDED Requirements

### Requirement: Consultorio sembrado por la migración
La migración que crea la tabla del consultorio SHALL insertar exactamente una fila con un identificador fijo, un nombre provisional editable y la zona horaria `America/Argentina/Buenos_Aires`, sin datos personales ni de contacto reales.

#### Scenario: Base recién migrada
- **GIVEN** una base vacía con los roles de migración y de aplicación creados
- **WHEN** la aplicación arranca y aplica las migraciones
- **THEN** la tabla del consultorio contiene exactamente una fila, con zona horaria `America/Argentina/Buenos_Aires` y campos de contacto vacíos

### Requirement: Una sola fila de consultorio en v1
La base de datos SHALL impedir que exista más de una fila de consultorio, aunque la inserción la intente el rol de migración.

#### Scenario: Segunda fila rechazada
- **GIVEN** la base migrada con su fila de consultorio
- **WHEN** el rol de migración intenta insertar otra fila de consultorio
- **THEN** PostgreSQL rechaza la inserción por violación de unicidad

### Requirement: Contexto del consultorio de la instalación
El sistema SHALL exponer a todos los módulos el identificador y la zona horaria del consultorio de la instalación, leídos de la base de datos, para usarlos como `practice_id` y zona de presentación.

#### Scenario: Identificador disponible
- **GIVEN** la base migrada
- **WHEN** un módulo solicita el consultorio de la instalación
- **THEN** obtiene el identificador de la única fila y la zona `America/Argentina/Buenos_Aires`

#### Scenario: Consultorio ausente
- **GIVEN** que la tabla del consultorio no tiene filas
- **WHEN** la aplicación arranca
- **THEN** el arranque falla con un error que indica que falta el consultorio de la instalación

### Requirement: Datos de contacto del consultorio opcionales
Los datos de contacto del consultorio (email, teléfono, dirección) SHALL ser opcionales; el nombre y la zona horaria MUST ser obligatorios y no vacíos, y la zona MUST ser un identificador de zona horaria reconocido por la aplicación.

#### Scenario: Nombre o zona vacíos rechazados
- **WHEN** se intenta guardar el consultorio con nombre o zona horaria vacíos
- **THEN** la base de datos rechaza la operación

#### Scenario: Zona horaria desconocida
- **GIVEN** la fila del consultorio con una zona horaria que no es un identificador válido
- **WHEN** la aplicación arranca
- **THEN** el arranque falla con un error que indica que la zona del consultorio es inválida, sin mostrar otros datos
