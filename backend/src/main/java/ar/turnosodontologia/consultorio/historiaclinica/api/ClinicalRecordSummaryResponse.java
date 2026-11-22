package ar.turnosodontologia.consultorio.historiaclinica.api;

import java.time.Instant;
import java.util.UUID;

/** Resumen de la historia clinica; si no esta abierta, {@code opened = false} y el resto nulo. */
public record ClinicalRecordSummaryResponse(
        boolean opened, UUID clinicalRecordId, Instant openedAt, AnamnesisEntryResponse currentAnamnesis) {
}
