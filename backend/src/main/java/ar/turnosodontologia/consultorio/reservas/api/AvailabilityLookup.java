package ar.turnosodontologia.consultorio.reservas.api;

/** Contrato estable de disponibilidad para controllers y futuros consumidores (C-14). */
public interface AvailabilityLookup {

    AvailabilityResult availability(AvailabilityQuery query);
}
