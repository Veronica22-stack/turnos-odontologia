package ar.turnosodontologia.consultorio.pacientes.api;

import java.util.List;
import java.util.UUID;

/** Operaciones de pacientes que la reserva pública necesita, sin exponer datos personales (C-14). */
public interface PatientBookingOperations {

    /**
     * Busca o crea al paciente por DNI. Si ya existe, NO sobreescribe sus datos (RN-PA-03).
     *
     * @return identificador opaco, flag de creación y flag de datos divergentes
     */
    PatientBookingResult findOrCreate(UUID practiceId, String dni, String firstName, String lastName,
            String phone, String email);

    /** Identificadores opacos de pacientes que coinciden por DNI, email o teléfono. */
    List<UUID> findIdsByContact(UUID practiceId, String dni, String email, String phone);
}
