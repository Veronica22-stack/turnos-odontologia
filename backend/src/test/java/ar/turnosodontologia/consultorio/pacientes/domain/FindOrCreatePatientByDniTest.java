package ar.turnosodontologia.consultorio.pacientes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FindOrCreatePatientByDniTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    @Test
    void creaUnNuevoPacienteSiDniNoExiste() {
        InMemoryPatientRepository repo = new InMemoryPatientRepository();
        FindOrCreatePatientByDni sut = new FindOrCreatePatientByDni(repo);

        FindOrCreatePatientByDni.Result result = sut.execute(
                PRACTICE_ID, "12345678", "Juan", "Perez", "1122334455", "juan@test.com", PatientSource.PUBLIC_BOOKING);

        assertThat(result.created()).isTrue();
        assertThat(result.divergentData()).isFalse();
        assertThat(result.patient().dni()).isEqualTo("12345678");
        assertThat(result.patient().source()).isEqualTo(PatientSource.PUBLIC_BOOKING);
    }

    @Test
    void devuelvePacienteExistenteSinSobreescribirDatos() {
        InMemoryPatientRepository repo = new InMemoryPatientRepository();
        Patient existing = Patient.create(PRACTICE_ID, "Juan", "Perez", "12345678", "1122334455", "juan@test.com", null, null, PatientSource.MANUAL);
        repo.save(existing);
        FindOrCreatePatientByDni sut = new FindOrCreatePatientByDni(repo);

        FindOrCreatePatientByDni.Result result = sut.execute(
                PRACTICE_ID, "12345678", "Juan", "Perez", "1122334455", "juan@test.com", PatientSource.PUBLIC_BOOKING);

        assertThat(result.created()).isFalse();
        assertThat(result.divergentData()).isFalse();
        assertThat(result.patient().source()).isEqualTo(PatientSource.MANUAL);
    }

    @Test
    void detectaDatosDivergentesEnPacienteExistente() {
        InMemoryPatientRepository repo = new InMemoryPatientRepository();
        Patient existing = Patient.create(PRACTICE_ID, "Juan", "Perez", "12345678", "1122334455", "juan@test.com", null, null, PatientSource.MANUAL);
        repo.save(existing);
        FindOrCreatePatientByDni sut = new FindOrCreatePatientByDni(repo);

        FindOrCreatePatientByDni.Result result = sut.execute(
                PRACTICE_ID, "12345678", "Carlos", "Gomez", "9999999999", "otro@test.com", PatientSource.PUBLIC_BOOKING);

        assertThat(result.created()).isFalse();
        assertThat(result.divergentData()).isTrue();
        assertThat(result.patient().firstName()).isEqualTo("Juan");
        assertThat(result.patient().lastName()).isEqualTo("Perez");
    }

    /** Repositorio en memoria para tests unitarios. */
    private static class InMemoryPatientRepository implements PatientRepository {
        private final Map<UUID, Patient> store = new HashMap<>();

        @Override
        public Optional<Patient> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Patient> findByPracticeIdAndDni(UUID practiceId, String dni) {
            return store.values().stream()
                    .filter(p -> p.practiceId().equals(practiceId) && p.dni().equals(dni))
                    .findFirst();
        }

        @Override
        public List<Patient> searchByPracticeId(UUID practiceId, String term, boolean onlyActive) {
            return store.values().stream()
                    .filter(p -> p.practiceId().equals(practiceId))
                    .toList();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S extends Patient> S save(S entity) {
            UUID id = entity.id();
            if (id == null) {
                id = UUID.randomUUID();
                try {
                    java.lang.reflect.Field idField = Patient.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(entity, id);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            store.put(id, entity);
            return entity;
        }
    }
}
