package ar.turnosodontologia.consultorio.agenda.domain;

import java.time.Instant;

/**
 * Puerto de eventos de turno (design D11). {@code agenda.application} no puede depender de {@code agenda.api}, asi
 * que el adaptador de infrastructure mapea estos tipos de dominio a los eventos publicos. Se invoca dentro de la
 * transaccion, despues del flush exitoso.
 */
public interface AppointmentEventsPublisher {

    void confirmed(Appointment appointment);

    void cancelled(Appointment appointment);

    void rescheduled(Appointment appointment, Instant previousStartsAt, Instant previousEndsAt);
}
