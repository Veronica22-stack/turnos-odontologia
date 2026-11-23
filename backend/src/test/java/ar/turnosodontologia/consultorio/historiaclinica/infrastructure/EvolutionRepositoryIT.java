package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecordRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.Correction;
import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectionReason;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionDescription;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCode;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothPosition;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvolutionRepositoryIT extends AbstractIntegrationTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private final ClinicalRecordRepository records;
    private final EvolutionEntryRepository evolutions;
    private final OdontogramEntryRepository odontogram;
    private final AnamnesisEntryRepository anamnesis;

    private UUID userId;
    private UUID patientId;
    private UUID otherPatientId;

    EvolutionRepositoryIT(
            ClinicalRecordRepository records, EvolutionEntryRepository evolutions,
            OdontogramEntryRepository odontogram, AnamnesisEntryRepository anamnesis) {
        this.records = records;
        this.evolutions = evolutions;
        this.odontogram = odontogram;
        this.anamnesis = anamnesis;
    }

    @BeforeEach
    void seed() throws SQLException {
        cleanRows();
        try (Connection connection = DATABASE.migratorConnection()) {
            try (PreparedStatement query = connection.prepareStatement("SELECT id FROM app_user WHERE email = ?")) {
                query.setString(1, BOOTSTRAP_EMAIL);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    userId = UUID.fromString(rows.getString(1));
                }
            }
            patientId = insertPatient(connection, "77100001");
            otherPatientId = insertPatient(connection, "77100002");
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        ClinicalFixtures.deleteEvolutions();
        AppointmentFixtures.cleanAll();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM odontogram_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM odontogram_entry");
            statement.executeUpdate("DELETE FROM anamnesis_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('77100001', '77100002')");
        }
    }

    @Test
    void persisteYRecuperaLaEvolucionSinPerderCampos() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);

        EvolutionEntry saved = evolutions.save(
                EvolutionEntry.register(record.id(), EvolutionDescription.of("Control de rutina"), null, provenance(0)));
        EvolutionEntry loaded = evolutions.findInRecord(record.id(), saved.id()).orElseThrow();

        assertThat(loaded.description()).isEqualTo("Control de rutina");
        assertThat(loaded.appointmentId()).isNull();
        assertThat(loaded.authorId()).isEqualTo(userId);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.isCorrection()).isFalse();
    }

    @Test
    void persisteElTurnoAsociadoYLoConservaEnLaCorreccion() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        UUID service = AppointmentFixtures.service(30, true, true);
        UUID appointment = AppointmentFixtures.appointment(
                patientId, service, "2026-01-10T10:00:00Z", "2026-01-10T10:30:00Z", "COMPLETED");

        EvolutionEntry original = evolutions.save(
                EvolutionEntry.register(record.id(), EvolutionDescription.of("original"), appointment, provenance(0)));
        EvolutionEntry correction = evolutions.save(EvolutionEntry.correct(
                record.id(), original, EvolutionDescription.of("corregida"),
                new Correction(original.id(), CorrectionReason.of("error de carga")), provenance(1)));

        assertThat(evolutions.findInRecord(record.id(), original.id()).orElseThrow().appointmentId())
                .isEqualTo(appointment);
        EvolutionEntry loaded = evolutions.findInRecord(record.id(), correction.id()).orElseThrow();
        assertThat(loaded.appointmentId()).isEqualTo(appointment);
        assertThat(loaded.correctsEntryId()).isEqualTo(original.id());
        assertThat(loaded.correctionReason()).isEqualTo("error de carga");
    }

    @Test
    void listaLasEvolucionesDeLaMasNuevaALaMasAntigua() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        assertThat(evolutions.findNewestFirst(record.id())).isEmpty();

        EvolutionEntry first = saveAt(record, "primera", 0);
        EvolutionEntry second = saveAt(record, "segunda", 5);
        EvolutionEntry third = saveAt(record, "tercera", 9);

        assertThat(evolutions.findNewestFirst(record.id())).extracting(EvolutionEntry::id)
                .containsExactly(third.id(), second.id(), first.id());
    }

    @Test
    void conIgualInstanteElIdMayorVaPrimero() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        EvolutionEntry a = saveAt(record, "a", 0);
        EvolutionEntry b = saveAt(record, "b", 0);
        EvolutionEntry c = saveAt(record, "c", 0);

        List<UUID> expected = List.of(a.id(), b.id(), c.id()).stream()
                .sorted(java.util.Comparator.reverseOrder()).toList();

        assertThat(evolutions.findNewestFirst(record.id())).extracting(EvolutionEntry::id).isEqualTo(expected);
    }

    @Test
    void noEncuentraEntradasDeOtraHistoriaNiDeAnamnesisNiDeOdontograma() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        ClinicalRecord other = records.openIfAbsent(PRACTICE_ID, otherPatientId, userId, NOW);
        EvolutionEntry foreign = saveAt(other, "ajena", 0);
        AnamnesisEntry anamnesisEntry = anamnesis.save(AnamnesisEntry.register(
                record.id(), 1, AnamnesisContent.of("penicilina", null, null, null, null), provenance(0)));
        OdontogramEntry odontogramEntry = odontogram.save(OdontogramEntry.register(
                record.id(), new ToothPosition(ToothCode.of("36"), null), ToothCondition.CARIES, null, provenance(0)));

        assertThat(evolutions.findInRecord(record.id(), foreign.id())).isEmpty();
        assertThat(evolutions.findInRecord(other.id(), foreign.id())).isPresent();
        assertThat(evolutions.findInRecord(record.id(), anamnesisEntry.id())).isEmpty();
        assertThat(evolutions.findInRecord(record.id(), odontogramEntry.id())).isEmpty();
        assertThat(evolutions.findNewestFirst(record.id())).isEmpty();
    }

    @Test
    void sabeSiUnaEvolucionFueCorregida() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        EvolutionEntry original = saveAt(record, "original", 0);
        EvolutionEntry correction = evolutions.save(EvolutionEntry.correct(
                record.id(), original, EvolutionDescription.of("corregida"),
                new Correction(original.id(), CorrectionReason.of("motivo")), provenance(1)));

        assertThat(evolutions.isCorrected(original.id())).isTrue();
        assertThat(evolutions.isCorrected(correction.id())).isFalse();
    }

    private EvolutionEntry saveAt(ClinicalRecord record, String text, long seconds) {
        return evolutions.save(
                EvolutionEntry.register(record.id(), EvolutionDescription.of(text), null, provenance(seconds)));
    }

    private EntryProvenance provenance(long seconds) {
        return new EntryProvenance(userId, NOW.plusSeconds(seconds));
    }

    private static UUID insertPatient(Connection connection, String dni) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) "
                        + "VALUES (?::uuid, ?::uuid, 'Ana', 'Test', ?, '1111')")) {
            insert.setString(1, id.toString());
            insert.setString(2, PRACTICE_ID.toString());
            insert.setString(3, dni);
            insert.executeUpdate();
        }
        return id;
    }
}
