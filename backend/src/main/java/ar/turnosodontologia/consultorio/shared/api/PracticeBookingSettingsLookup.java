package ar.turnosodontologia.consultorio.shared.api;

import java.util.UUID;

/** Puerta de solo lectura de la configuración de reserva, con tipos del JDK. */
public interface PracticeBookingSettingsLookup {

    PracticeBookingSettings findBookingSettings(UUID practiceId);
}
