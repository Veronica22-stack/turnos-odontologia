package ar.turnosodontologia.consultorio.shared.api;

/** Valores de reserva vigentes; deliberadamente sin nombre, contacto ni dirección del consultorio. */
public record PracticeBookingSettings(
        String timezone,
        int slotGranularityMin,
        int minNoticeMin,
        int maxAdvanceDays) {
}
