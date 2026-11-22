package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Operaciones de turno que agenda ofrece a otros modulos (design D16). En C-11 expone SOLO la reprogramacion
 * (consumidor previsto: C-15). El alta y la cancelacion no se publican: son operaciones internas de
 * {@code AppointmentService} usadas por el controller del odontologo.
 *
 * <p>Aplica las mismas reglas, atomicidad, lock por consultorio, traduccion de errores y eventos que la API interna.
 * Las reglas de reserva publica que no dependen de datos concurrentes (anticipacion minima, horizonte, granularidad,
 * visibilidad publica) las valida el consumidor antes de llamar; las operaciones publicas pasan
 * {@code overbooking = false}.
 */
public interface AppointmentOperations {

    /**
     * Mueve un turno {@code CONFIRMED} futuro conservando identificador, paciente, prestacion y duracion snapshot.
     *
     * @throws ar.turnosodontologia.consultorio.shared.domain.DomainException con el mismo codigo que la API interna
     *     ({@code APPOINTMENT_SLOT_TAKEN}, {@code APPOINTMENT_SLOT_BLOCKED}, {@code OUTSIDE_WORKING_HOURS}, etc.)
     */
    AppointmentView reschedule(UUID appointmentId, Instant newStartsAt, boolean overbooking);
}
