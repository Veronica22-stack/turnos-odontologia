package ar.turnosodontologia.consultorio.pacientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.pacientes.domain.Patient;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientRepository;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientSource;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PatientsServiceTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private InstallationPractice installationPractice;

    @Mock
    private AuditLogger auditLogger;

    private PatientsService service;

    @BeforeEach
    void setUp() {
        service = new PatientsService(patientRepository, installationPractice, auditLogger);
    }

    @Test
    void buscaPacientesDelConsultorioYSoloActivos() {
        Patient patient = patient("Perez", "12345678");
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(patientRepository.searchByPracticeId(PRACTICE_ID, "per", true)).thenReturn(List.of(patient));

        List<PatientSummaryData> result = service.searchPatients(" per ", true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isEqualTo("Juan");
        assertThat(result.get(0).dni()).isEqualTo("12345678");
        verify(auditLogger, never()).log(any());
    }

    @Test
    void consultarFichaRegistraPatientReadConIdentificadores() {
        Patient patient = patient("Perez", "12345678");
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(patientRepository.findById(patient.id())).thenReturn(Optional.of(patient));

        PatientData result = service.getPatientById(patient.id(), USER_ID);

        assertThat(result.firstName()).isEqualTo("Juan");
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.PATIENT_READ);
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().patientId()).isEqualTo(patient.id());
    }

    @Test
    void fichaInexistenteLanzaNotFoundYNoAudita() {
        UUID missing = UUID.randomUUID();
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(patientRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPatientById(missing, USER_ID))
                .isInstanceOf(NotFoundException.class);
        verify(auditLogger, never()).log(any());
    }

    @Test
    void crearPacienteNormalizaDniYUsaOrigenManual() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientData created = service.createPatient(
                "Juan", "Perez", "12.345.678", "11223344", "juan@test.com",
                LocalDate.of(1990, 5, 20), "Calle 1");

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(captor.capture());
        assertThat(captor.getValue().dni()).isEqualTo("12345678");
        assertThat(captor.getValue().practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(created.source()).isEqualTo("MANUAL");
        assertThat(created.birthDate()).isEqualTo(LocalDate.of(1990, 5, 20));
    }

    @Test
    void dniDuplicadoSeTraduceAConflict() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(patientRepository.save(any(Patient.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"patient_practice_dni_unique\""));

        assertThatThrownBy(() -> service.createPatient(
                "Juan", "Perez", "12345678", "11223344", null, null, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void actualizarFichaRegistraPatientUpdate() {
        Patient patient = patient("Perez", "12345678");
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(patientRepository.findById(patient.id())).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientData updated = service.updatePatient(
                patient.id(), USER_ID, "Juan Carlos", "Perez", "99887766",
                "nuevo@test.com", LocalDate.of(1985, 1, 1), "Calle 2");

        assertThat(updated.firstName()).isEqualTo("Juan Carlos");
        assertThat(updated.phone()).isEqualTo("99887766");
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).log(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.PATIENT_UPDATE);
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    void bajaLogicaDesactivaSinBorrar() {
        Patient patient = patient("Perez", "12345678");
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(patientRepository.findById(patient.id())).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deactivatePatient(patient.id());

        assertThat(patient.isActive()).isFalse();
        verify(patientRepository).save(eq(patient));
    }

    private static Patient patient(String lastName, String dni) {
        Patient patient = Patient.create(
                PRACTICE_ID, "Juan", lastName, dni, "11223344", "juan@test.com", null, null, PatientSource.MANUAL);
        ReflectionTestUtils.setField(patient, "id", UUID.randomUUID());
        return patient;
    }
}
