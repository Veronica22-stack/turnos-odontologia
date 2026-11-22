package ar.turnosodontologia.consultorio.historiaclinica.application;

import java.time.Instant;
import java.util.UUID;

/** Resumen de la historia clinica de un paciente; si no esta abierta, solo {@code opened = false}. */
public record ClinicalRecordSummaryData(
        boolean opened, UUID clinicalRecordId, Instant openedAt, AnamnesisEntryData currentAnamnesis) {
}
