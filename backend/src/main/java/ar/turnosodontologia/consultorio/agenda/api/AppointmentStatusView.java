package ar.turnosodontologia.consultorio.agenda.api;

/** Estado del turno visible para otros modulos y para la API REST. */
public enum AppointmentStatusView {
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW
}
