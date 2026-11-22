package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentOriginView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.agenda.api.CancelledByView;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentOrigin;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentStatus;
import ar.turnosodontologia.consultorio.agenda.domain.CancelledBy;

/** Conversion de los enums de dominio a los enums publicos de {@code agenda.api}. */
final class AppointmentMapping {

    private AppointmentMapping() {
    }

    static AppointmentOriginView origin(AppointmentOrigin origin) {
        return origin == null ? null : AppointmentOriginView.valueOf(origin.name());
    }

    static AppointmentStatusView status(AppointmentStatus status) {
        return status == null ? null : AppointmentStatusView.valueOf(status.name());
    }

    static CancelledByView cancelledBy(CancelledBy cancelledBy) {
        return cancelledBy == null ? null : CancelledByView.valueOf(cancelledBy.name());
    }
}
