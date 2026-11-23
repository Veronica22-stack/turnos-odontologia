# Spec Delta: local-time-display

## ADDED Requirements

### Requirement: Días locales consecutivos
La SPA SHALL calcular, a partir de una fecha local del consultorio (`aaaa-mm-dd`), la fecha local que está N días después, cruzando fines de mes y de año y sin depender de la zona del navegador.

#### Scenario: Fin de mes
- **WHEN** se suman 2 días a `2026-11-30`
- **THEN** el resultado es `2026-12-02`

#### Scenario: Fin de año
- **WHEN** se suma 1 día a `2026-12-31`
- **THEN** el resultado es `2027-01-01`

### Requirement: Etiquetas legibles de un día local
La SPA SHALL producir para una fecha local del consultorio una etiqueta corta (día de la semana abreviado y número, p. ej. `mar` y `24`), una etiqueta media (`mar 24/11`) y una etiqueta larga en español (`martes 24 de noviembre`), con el mismo resultado en cualquier zona del navegador.

#### Scenario: Etiquetas desde otra zona
- **GIVEN** un navegador configurado en `Asia/Tokyo`
- **WHEN** se etiqueta `2026-11-24`
- **THEN** la etiqueta corta es `mar` + `24`, la media `mar 24/11` y la larga `martes 24 de noviembre`

#### Scenario: Fecha inválida
- **WHEN** se etiqueta `2026-02-30`
- **THEN** se rechaza indicando fecha inválida
