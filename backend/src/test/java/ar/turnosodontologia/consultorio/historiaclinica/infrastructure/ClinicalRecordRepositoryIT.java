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
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalRecordRepositoryIT extends AbstractIntegrationTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private final ClinicalRecordRepository records;
    private final AnamnesisEntryRepository anamnesis;

    private UUID userId;
    private UUID patientId;
    private UUID otherPatientId;

    ClinicalRecordRepositoryIT(ClinicalRecordRepository records, AnamnesisEntryRepository anamnesis) {
        this.records = records;
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
            patientId = insertPatient(connection, "72000001");
            otherPatientId = insertPatient(connection, "72000002");
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM anamnesis_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            ClinicalFixtures.deleteEvolutions();
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('72000001', '72000002')");
        }
    }

    @Test
    void sinHistoriaClinicaLaBusquedaDevuelveVacio() {
        assertThat(records.findByPatientId(patientId)).isEmpty();
    }

    @Test
    void laAperturaEsIdempotenteYDevuelveLaMismaHistoria() throws SQLException {
        ClinicalRecord first = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        ClinicalRecord second = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW.plusSeconds(60));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.openedAt()).isEqualTo(NOW);
        assertThat(first.patientId()).isEqualTo(patientId);
        assertThat(first.openedBy()).isEqualTo(userId);
        assertThat(records.findByPatientId(patientId)).get().extracting(ClinicalRecord::id).isEqualTo(first.id());
        assertThat(countRecords(patientId)).isEqualTo(1);
    }

    @Test
    void cadaPacienteTieneSuPropiaHistoria() {
        ClinicalRecord one = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        ClinicalRecord other = records.openIfAbsent(PRACTICE_ID, otherPatientId, userId, NOW);

        assertThat(one.id()).isNotEqualTo(other.id());
    }

    @Test
    void dosAperturasConcurrentesDejanUnaSolaHistoria() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<UUID>> results = List.of(
                    pool.submit(() -> openAfter(start)),
                    pool.submit(() -> openAfter(start)));
            start.countDown();

            assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
            assertThat(countRecords(patientId)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void listaLaAnamnesisPorVersionDescendenteYCalculaLasCorreccionesYLaMaxima() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        assertThat(anamnesis.maxVersion(record.id())).isZero();
        assertThat(anamnesis.findNewestFirst(record.id())).isEmpty();

        AnamnesisEntry v1 = anamnesis.save(AnamnesisEntry.register(record.id(), 1, content("penicilina"), provenance()));
        AnamnesisEntry v2 = anamnesis.save(AnamnesisEntry.correct(
                record.id(), 2, content("sin alergias"),
                new Correction(v1.id(), CorrectionReason.of("alergia mal cargada")), provenance()));
        AnamnesisEntry v3 = anamnesis.save(AnamnesisEntry.register(record.id(), 3, content("latex"), provenance()));

        assertThat(anamnesis.maxVersion(record.id())).isEqualTo(3);
        assertThat(anamnesis.findNewestFirst(record.id())).extracting(AnamnesisEntry::version).containsExactly(3, 2, 1);
        assertThat(anamnesis.correctedByIndex(record.id())).isEqualTo(Map.of(v1.id(), v2.id()));
        assertThat(anamnesis.isCorrected(v1.id())).isTrue();
        assertThat(anamnesis.isCorrected(v2.id())).isFalse();
        assertThat(anamnesis.isCorrected(v3.id())).isFalse();
    }

    @Test
    void persisteYRecuperaElContenidoYLaCorreccionSinPerderCampos() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        AnamnesisEntry v1 = anamnesis.save(AnamnesisEntry.register(
                record.id(), 1, AnamnesisContent.of("penicilina", "ibuprofeno", "asma", "fuma", "nota"), provenance()));
        AnamnesisEntry v2 = anamnesis.save(AnamnesisEntry.correct(
                record.id(), 2, content("latex"),
                new Correction(v1.id(), CorrectionReason.of("motivo")), provenance()));

        AnamnesisEntry loaded1 = anamnesis.findInRecord(record.id(), v1.id()).orElseThrow();
        AnamnesisEntry loaded2 = anamnesis.findInRecord(record.id(), v2.id()).orElseThrow();

        assertThat(loaded1.content())
                .isEqualTo(AnamnesisContent.of("penicilina", "ibuprofeno", "asma", "fuma", "nota"));
        assertThat(loaded1.authorId()).isEqualTo(userId);
        assertThat(loaded1.createdAt()).isEqualTo(NOW);
        assertThat(loaded2.correctsEntryId()).isEqualTo(v1.id());
        assertThat(loaded2.correctionReason()).isEqualTo("motivo");
    }

    @Test
    void unaEntradaDeOtraHistoriaNoSeEncuentraEnLaHistoriaConsultada() {
        ClinicalRecord record = records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW);
        ClinicalRecord other = records.openIfAbsent(PRACTICE_ID, otherPatientId, userId, NOW);
        AnamnesisEntry foreign = anamnesis.save(AnamnesisEntry.register(other.id(), 1, content("x"), provenance()));

        assertThat(anamnesis.findInRecord(record.id(), foreign.id())).isEmpty();
        assertThat(anamnesis.findInRecord(other.id(), foreign.id())).isPresent();
    }

    private UUID openAfter(CountDownLatch start) throws InterruptedException {
        start.await();
        return records.openIfAbsent(PRACTICE_ID, patientId, userId, NOW).id();
    }

    private static AnamnesisContent content(String allergies) {
        return AnamnesisContent.of(allergies, null, null, null, null);
    }

    private EntryProvenance provenance() {
        return new EntryProvenance(userId, NOW);
    }

    private static int countRecords(UUID patient) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM clinical_record WHERE patient_id = '" + patient + "'")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static UUID insertPatient(Connection connection, String dni) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                    + id + "', '" + PRACTICE_ID + "', 'Ana', 'Repo', '" + dni + "', '1111')");
        }
        return id;
    }
}
