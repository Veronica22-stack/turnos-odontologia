package ar.turnosodontologia.consultorio.pacientes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PatientTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    @Test
    void creaPacienteValidoYNormalizaDniYTextos() {
        Patient p = Patient.create(
                PRACTICE_ID,
                "  Juan Carlos ",
                " Perez ",
                "  12.345.678  ",
                " +54 9 11 1234-5678 ",
                "  JUAN@ejemplo.com  ",
                LocalDate.of(1990, 5, 20),
                " Av. Siempre Viva 742 ",
                PatientSource.MANUAL);

        assertThat(p.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(p.firstName()).isEqualTo("Juan Carlos");
        assertThat(p.lastName()).isEqualTo("Perez");
        assertThat(p.dni()).isEqualTo("12345678");
        assertThat(p.phone()).isEqualTo("+54 9 11 1234-5678");
        assertThat(p.email()).isEqualTo("juan@ejemplo.com");
        assertThat(p.birthDate()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(p.address()).isEqualTo("Av. Siempre Viva 742");
        assertThat(p.source()).isEqualTo(PatientSource.MANUAL);
        assertThat(p.isActive()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void nombreVacioLanzaExcepcion(String invalidName) {
        assertThatThrownBy(() -> Patient.create(
                PRACTICE_ID, invalidName, "Perez", "12345678", "11223344", null, null, null, PatientSource.MANUAL))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("first_name"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void apellidoVacioLanzaExcepcion(String invalidLastName) {
        assertThatThrownBy(() -> Patient.create(
                PRACTICE_ID, "Juan", invalidLastName, "12345678", "11223344", null, null, null, PatientSource.MANUAL))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("last_name"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", "12345", "1234567890"})
    void dniInvalidoLanzaExcepcion(String invalidDni) {
        assertThatThrownBy(() -> Patient.create(
                PRACTICE_ID, "Juan", "Perez", invalidDni, "11223344", null, null, null, PatientSource.MANUAL))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("dni"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void telefonoVacioLanzaExcepcion(String invalidPhone) {
        assertThatThrownBy(() -> Patient.create(
                PRACTICE_ID, "Juan", "Perez", "12345678", invalidPhone, null, null, null, PatientSource.MANUAL))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("phone"));
    }

    @Test
    void actualizacionModificaDatosYNormaliza() {
        Patient p = Patient.create(
                PRACTICE_ID, "Juan", "Perez", "12345678", "11223344", "juan@test.com", null, null, PatientSource.MANUAL);

        p.update(" Juan Manuel ", " Perez Gomez ", " 99887766 ", " nuevo@test.com ", LocalDate.of(1985, 1, 1), " Calle 123 ");

        assertThat(p.firstName()).isEqualTo("Juan Manuel");
        assertThat(p.lastName()).isEqualTo("Perez Gomez");
        assertThat(p.phone()).isEqualTo("99887766");
        assertThat(p.email()).isEqualTo("nuevo@test.com");
        assertThat(p.birthDate()).isEqualTo(LocalDate.of(1985, 1, 1));
        assertThat(p.address()).isEqualTo("Calle 123");
    }

    @Test
    void desactivacionYReactivacion() {
        Patient p = Patient.create(
                PRACTICE_ID, "Juan", "Perez", "12345678", "11223344", null, null, null, PatientSource.MANUAL);

        assertThat(p.isActive()).isTrue();
        p.deactivate();
        assertThat(p.isActive()).isFalse();
        p.activate();
        assertThat(p.isActive()).isTrue();
    }
}
