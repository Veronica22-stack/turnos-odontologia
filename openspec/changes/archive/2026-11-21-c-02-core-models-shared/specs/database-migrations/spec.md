# Spec Delta

## ADDED Requirements

### Requirement: Privilegios mínimos sobre las tablas core
El rol de aplicación SHALL poder leer y actualizar la fila del consultorio pero MUST NOT insertar ni eliminar consultorios; SHALL poder leer, insertar y actualizar cuentas de usuario pero MUST NOT eliminarlas.

#### Scenario: Consultorio sin alta ni baja desde la aplicación
- **GIVEN** la base migrada hasta `V2`
- **WHEN** el rol de aplicación intenta `INSERT` y luego `DELETE` sobre la tabla del consultorio
- **THEN** PostgreSQL rechaza ambas sentencias por falta de permisos, mientras que `SELECT` y `UPDATE` se ejecutan con éxito

#### Scenario: Cuentas sin borrado desde la aplicación
- **GIVEN** la base migrada hasta `V2` con una cuenta de usuario
- **WHEN** el rol de aplicación intenta `DELETE` sobre la tabla de cuentas
- **THEN** PostgreSQL rechaza la sentencia por falta de permisos, mientras que `SELECT`, `INSERT` y `UPDATE` se ejecutan con éxito

### Requirement: Esquema validado contra el modelo
Al arrancar, la aplicación SHALL verificar que el esquema migrado coincide con el modelo persistente (tablas, columnas y tipos) y MUST negarse a arrancar si no coincide, sin modificar el esquema.

#### Scenario: Esquema coincidente
- **GIVEN** una base vacía
- **WHEN** la aplicación arranca y aplica `V1` y `V2`
- **THEN** el arranque es exitoso y ambas migraciones figuran como exitosas en el historial
