package ar.turnosodontologia.consultorio.historiaclinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntryType;
import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectableEntryStore;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Auditoria fail-closed y escrituras concurrentes contra la base real: si el rastro falla no queda ninguna historia ni
 * entrada nueva, y las carreras terminan en un 409 sin duplicar ni perder entradas.
 */
@Import(ClinicalAuditFailureIT.SwitchableAuditConfiguration.class)
class ClinicalAuditFailureIT extends AbstractIntegrationTest {

    private static final AtomicBoolean AUDIT_FAILING = new AtomicBoolean(false);
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final AnamnesisService anamnesisService;
    private final ClinicalRecordService recordService;
    private final ClinicalCorrections corrections;
    private final AnamnesisEntryRepository anamnesisRepository;

    private UUID userId;
    private UUID patientId;

    ClinicalAuditFailureIT(
            AnamnesisService anamnesisService,
            ClinicalRecordService recordService,
            ClinicalCorrections corrections,
            AnamnesisEntryRepository anamnesisRepository) {
        this.anamnesisService = anamnesisService;
        this.recordService = recordService;
        this.corrections = corrections;
        this.anamnesisRepository = anamnesisRepository;
    }

    @BeforeEach
    void seed() throws SQLException {
        AUDIT_FAILING.set(false);
        cleanRows();
        patientId = UUID.randomUUID();
        try (Connection connection = DATABASE.migratorConnection()) {
            try (PreparedStatement query = connection.prepareStatement("SELECT id FROM app_user WHERE email = ?")) {
                query.setString(1, BOOTSTRAP_EMAIL);
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    userId = UUID.fromString(rows.getString(1));
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) VALUES ('"
                        + patientId + "', '01900000-0000-7000-8000-000000000001', 'Ana', 'Audit', '73000001', '1111')");
            }
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        AUDIT_FAILING.set(false);
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM anamnesis_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM anamnesis_entry");
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM audit_log WHERE entity_type IN ('anamnesis_entry', 'clinical_record')");
            statement.executeUpdate("DELETE FROM patient WHERE dni = '73000001'");
        }
    }

    @Test
    void siFallaLaAuditoriaElAltaNoDejaHistoriaNiEntrada() throws SQLException {
        AUDIT_FAILING.set(true);

        assertThatThrownBy(() -> anamnesisService.register(patientId, userId, content("penicilina"), CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("AUDIT_WRITE_FAILED");

        assertThat(count("clinical_record")).isZero();
        assertThat(count("anamnesis_entry")).isZero();
    }

    @Test
    void siFallaLaAuditoriaLaCorreccionNoDejaEntradaNueva() throws SQLException {
        AnamnesisEntryData v1 = anamnesisService.register(patientId, userId, content("penicilina"), CONTEXT);
        AUDIT_FAILING.set(true);

        assertThatThrownBy(() -> anamnesisService.correct(
                patientId, v1.id(), "alergia mal cargada", content("latex"), userId, CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("AUDIT_WRITE_FAILED");

        assertThat(count("anamnesis_entry")).isEqualTo(1);
        AUDIT_FAILING.set(false);
        assertThat(anamnesisService.read(patientId, userId, CONTEXT).current().id()).isEqualTo(v1.id());
    }

    @Test
    void siFallaLaAuditoriaLaLecturaNoEntregaDatos() {
        anamnesisService.register(patientId, userId, content("penicilina"), CONTEXT);
        AUDIT_FAILING.set(true);

        assertThatThrownBy(() -> anamnesisService.read(patientId, userId, CONTEXT))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> recordService.summary(patientId, userId, CONTEXT))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void dosCorreccionesConcurrentesDeLaMismaEntradaDejanUnaSolaYLaOtraRecibeYaCorregida() throws Exception {
        AnamnesisEntryData v1 = anamnesisService.register(patientId, userId, content("penicilina"), CONTEXT);
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        // Almacen sin la restriccion de "solo la vigente" y con una barrera tras las verificaciones: ambas
        // correcciones pasan los controles previos y solo la restriccion de la base decide quien gana.
        CorrectableEntryStore<AnamnesisEntry> racingStore = new RacingStore(anamnesisRepository, bothChecked);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> outcomes = new ArrayList<>();
            for (int attempt = 0; attempt < 2; attempt++) {
                int version = 2 + attempt;
                outcomes.add(pool.submit(() -> {
                    try {
                        return corrections.correct(
                                ClinicalEntryType.ANAMNESIS, racingStore, patientId, v1.id(), "motivo " + version,
                                userId, CONTEXT,
                                (rid, correction, provenance, original) -> AnamnesisEntry.correct(
                                        rid, version, content("v" + version), correction, provenance));
                    } catch (RuntimeException error) {
                        return error;
                    }
                }));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(60, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(result -> result instanceof AnamnesisEntry).hasSize(1);
            assertThat(results).filteredOn(result -> result instanceof ConflictException).singleElement()
                    .satisfies(error -> assertThat(((ConflictException) error).code())
                            .isEqualTo("CLINICAL_ENTRY_ALREADY_CORRECTED"));
            assertThat(count("anamnesis_entry")).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void altasConcurrentesDeAnamnesisNuncaDuplicanVersion() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> outcomes = new ArrayList<>();
            for (int attempt = 0; attempt < 2; attempt++) {
                int n = attempt;
                outcomes.add(pool.submit(() -> {
                    try {
                        return anamnesisService.register(patientId, userId, content("alta " + n), CONTEXT);
                    } catch (RuntimeException error) {
                        return error;
                    }
                }));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(60, TimeUnit.SECONDS));
            }

            List<Integer> versions = results.stream()
                    .filter(result -> result instanceof AnamnesisEntryData)
                    .map(result -> ((AnamnesisEntryData) result).version())
                    .toList();
            assertThat(versions).doesNotHaveDuplicates().isNotEmpty();
            results.stream().filter(result -> result instanceof RuntimeException).forEach(error ->
                    assertThat(((ConflictException) error).code()).isEqualTo("CLINICAL_RECORD_CONCURRENT_WRITE"));
            assertThat(count("clinical_record")).isEqualTo(1);
            assertThat(count("anamnesis_entry")).isEqualTo(versions.size());
        } finally {
            pool.shutdownNow();
        }
    }

    private static int count(String table) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static AnamnesisContent content(String allergies) {
        return AnamnesisContent.of(allergies, null, null, null, null);
    }

    /** Almacen real de anamnesis con una barrera en el gancho para forzar la carrera entre dos correcciones. */
    private static final class RacingStore implements CorrectableEntryStore<AnamnesisEntry> {
        private final AnamnesisEntryRepository delegate;
        private final CyclicBarrier barrier;

        RacingStore(AnamnesisEntryRepository delegate, CyclicBarrier barrier) {
            this.delegate = delegate;
            this.barrier = barrier;
        }

        @Override
        public Optional<AnamnesisEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
            return delegate.findInRecord(clinicalRecordId, entryId);
        }

        @Override
        public boolean isCorrected(UUID entryId) {
            return delegate.isCorrected(entryId);
        }

        @Override
        public AnamnesisEntry save(AnamnesisEntry entry) {
            return delegate.save(entry);
        }

        @Override
        public void assertCorrectable(AnamnesisEntry original) {
            try {
                barrier.await(30, TimeUnit.SECONDS);
            } catch (Exception error) {
                throw new IllegalStateException("La barrera de la carrera fallo", error);
            }
        }
    }

    /** Envuelve el auditor real y lo hace fallar a pedido (conmutador estatico por defecto apagado). */
    @TestConfiguration(proxyBeanMethods = false)
    static class SwitchableAuditConfiguration {

        @Bean
        @Primary
        AuditLogger switchableAuditLogger(@Qualifier("jdbcAuditLogger") AuditLogger real) {
            return (AuditEvent event) -> {
                if (AUDIT_FAILING.get()) {
                    throw new BusinessRuleException("AUDIT_WRITE_FAILED");
                }
                real.log(event);
            };
        }
    }
}
