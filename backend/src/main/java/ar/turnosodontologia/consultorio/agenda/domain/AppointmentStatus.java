package ar.turnosodontologia.consultorio.agenda.domain;

/** Estado del turno; CANCELLED, COMPLETED y NO_SHOW son terminales (design D6). */
public enum AppointmentStatus {
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW
}
