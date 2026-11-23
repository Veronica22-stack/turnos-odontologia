package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecordRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.Correction;
import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectionReason;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCode;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothPosition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothSurface;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
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

class OdontogramRepositoryIT extends AbstractIntegrationTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final ToothPosition WHOLE_36 = new ToothPosition(ToothCode.of("36"), null);
    private static final ToothPosition OCCLUSAL_36 = new ToothPosition(ToothCode.of("36"), ToothSurface.OCCLUSAL);

    private final ClinicalRecordRepository records;
    private final OdontogramEntryRepository odontogram;
    private final AnamnesisEntryRepository anamnesis;

    private UUID userId;
    private UUID patientId;
    private UUID otherPatientId;

    OdontogramRepositoryIT(
            ClinicalRecordRepository records, OdontogramEntryRepository odontogram,
            AnamnesisEntryRepository anamnesis) {
        this.records = records;
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
            patientId = insertPatient(connection, "77000001");
            otherPatientId = insertPatient(connection, "77000002");
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM odontogram_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM odontogram_entry");
            statement.executeUpdate("DELETE FROM anamnesis_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            ClinicalFixtures.deleteEvolutions();
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('77000001', '77000002')");
        }
    }

    @Test
    void persisteYRecuperaLaEntradaSinPerderCampos() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);

        OdontogramEntry saved = odontogram.save(OdontogramEntry.register(
                record.id(), OCCLUSAL_36, ToothCondition.CARIES, "mancha", provenance(0)));
        OdontogramEntry loaded = odontogram.findInRecord(record.id(), saved.id()).orElseThrow();

        assertThat(loaded.position()).isEqualTo(OCCLUSAL_36);
        assertThat(loaded.condition()).isEqualTo(ToothCondition.CARIES);
        assertThat(loaded.notes()).isEqualTo("mancha");
        assertThat(loaded.authorId()).isEqualTo(userId);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.isCorrection()).isFalse();
    }

    @Test
    void persisteLaPiezaCompletaSinCaraYLaCorreccionConMotivo() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        OdontogramEntry original = odontogram.save(OdontogramEntry.register(
                record.id(), WHOLE_36, ToothCondition.MISSING, null, provenance(0)));
        OdontogramEntry correction = odontogram.save(OdontogramEntry.correct(
                record.id(), original, ToothCondition.HEALTHY, null,
                new Correction(original.id(), CorrectionReason.of("error de carga")), provenance(1)));

        OdontogramEntry loaded = odontogram.findInRecord(record.id(), correction.id()).orElseThrow();

        assertThat(loaded.position()).isEqualTo(WHOLE_36);
        assertThat(loaded.correctsEntryId()).isEqualTo(original.id());
        assertThat(loaded.correctionReason()).isEqualTo("error de carga");
    }

    @Test
    void listaLasEntradasDeLaHistoriaDeLaMasNuevaALaMasAntigua() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        assertThat(odontogram.findNewestFirst(record.id())).isEmpty();

        OdontogramEntry first = odontogram.save(OdontogramEntry.register(
                record.id(), WHOLE_36, ToothCondition.CARIES, null, provenance(0)));
        OdontogramEntry second = odontogram.save(OdontogramEntry.register(
                record.id(), WHOLE_36, ToothCondition.CROWN, null, provenance(5)));
        OdontogramEntry third = odontogram.save(OdontogramEntry.register(
                record.id(), new ToothPosition(ToothCode.of("11"), null), ToothCondition.MISSING, null, provenance(9)));

        assertThat(odontogram.findNewestFirst(record.id())).extracting(OdontogramEntry::id)
                .containsExactly(third.id(), second.id(), first.id());
    }

    @Test
    void conIgualInstanteElIdMayorVaPrimero() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        OdontogramEntry a = odontogram.save(OdontogramEntry.register(
                record.id(), WHOLE_36, ToothCondition.CARIES, null, provenance(0)));
        OdontogramEntry b = odontogram.save(OdontogramEntry.register(
                record.id(), new ToothPosition(ToothCode.of("11"), null), ToothCondition.CARIES, null, provenance(0)));

        List<UUID> expected = a.id().compareTo(b.id()) > 0 ? List.of(a.id(), b.id()) : List.of(b.id(), a.id());

        assertThat(odontogram.findNewestFirst(record.id())).extracting(OdontogramEntry::id).isEqualTo(expected);
    }

    @Test
    void noEncuentraEntradasDeOtraHistoriaNiDeAnamnesis() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        ClinicalRecord other = records.openIfAbsent(PRACTICE_ID, otherPatientId, userId, NOW);
        OdontogramEntry foreign = odontogram.save(OdontogramEntry.register(
                other.id(), WHOLE_36, ToothCondition.CARIES, null, provenance(0)));
        AnamnesisEntry anamnesisEntry = anamnesis.save(AnamnesisEntry.register(
                record.id(), 1, AnamnesisContent.of("penicilina", null, null, null, null), provenance(0)));

        assertThat(odontogram.findInRecord(record.id(), foreign.id())).isEmpty();
        assertThat(odontogram.findInRecord(other.id(), foreign.id())).isPresent();
        assertThat(odontogram.findInRecord(record.id(), anamnesisEntry.id())).isEmpty();
        assertThat(odontogram.findNewestFirst(record.id())).isEmpty();
    }

    @Test
    void sabeSiUnaEntradaFueCorregida() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        OdontogramEntry original = odontogram.save(OdontogramEntry.register(
                record.id(), WHOLE_36, ToothCondition.CARIES, null, provenance(0)));
        OdontogramEntry correction = odontogram.save(OdontogramEntry.correct(
                record.id(), original, ToothCondition.HEALTHY, null,
                new Correction(original.id(), CorrectionReason.of("motivo")), provenance(1)));

        assertThat(odontogram.isCorrected(original.id())).isTrue();
        assertThat(odontogram.isCorrected(correction.id())).isFalse();
    }

    @Test
    void existsNewerAtPositionDistingueLaPiezaCompletaDeCadaCara() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        OdontogramEntry wholeOld = odontogram.save(OdontogramEntry.register(
                record.id(), WHOLE_36, ToothCondition.CARIES, null, provenance(0)));
        OdontogramEntry occlusalOld = odontogram.save(OdontogramEntry.register(
                record.id(), OCCLUSAL_36, ToothCondition.CARIES, null, provenance(0)));

        assertThat(odontogram.existsNewerAtPosition(record.id(), wholeOld)).isFalse();
        assertThat(odontogram.existsNewerAtPosition(record.id(), occlusalOld)).isFalse();

        odontogram.save(OdontogramEntry.register(record.id(), WHOLE_36, ToothCondition.CROWN, null, provenance(5)));

        assertThat(odontogram.existsNewerAtPosition(record.id(), wholeOld)).isTrue();
        assertThat(odontogram.existsNewerAtPosition(record.id(), occlusalOld)).isFalse();

        odontogram.save(OdontogramEntry.register(
                record.id(), OCCLUSAL_36, ToothCondition.FILLED, null, provenance(6)));

        assertThat(odontogram.existsNewerAtPosition(record.id(), occlusalOld)).isTrue();
    }

    @Test
    void existsNewerAtPositionIgnoraOtrasPiezasYOtrasHistoriasYDesempataPorId() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        ClinicalRecord other = records.openIfAbsent(PRACTICE_ID, otherPatientId, userId, NOW);
        OdontogramEntry current = odontogram.save(OdontogramEntry.register(
                record.id(), WHOLE_36, ToothCondition.CARIES, null, provenance(0)));
        odontogram.save(OdontogramEntry.register(
                record.id(), new ToothPosition(ToothCode.of("37"), null), ToothCondition.CROWN, null, provenance(9)));
        odontogram.save(OdontogramEntry.register(other.id(), WHOLE_36, ToothCondition.CROWN, null, provenance(9)));

        assertThat(odontogram.existsNewerAtPosition(record.id(), current)).isFalse();

        OdontogramEntry sameInstant = odontogram.save(OdontogramEntry.register(
                record.id(), WHOLE_36, ToothCondition.CROWN, null, provenance(0)));
        boolean sameInstantIsNewer = sameInstant.id().compareTo(current.id()) > 0;

        assertThat(odontogram.existsNewerAtPosition(record.id(), current)).isEqualTo(sameInstantIsNewer);
        assertThat(odontogram.existsNewerAtPosition(record.id(), sameInstant)).isEqualTo(!sameInstantIsNewer);
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
