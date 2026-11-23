package ar.turnosodontologia.consultorio.historiaclinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntryType;
import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectableEntryStore;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionDescription;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntryRepository;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
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
 * Auditoria fail-closed de las evoluciones y carrera de correcciones contra la base real: si el rastro falla no queda
 * historia ni entrada nueva, y dos correcciones simultaneas de la misma evolucion terminan en un 409.
 */
@Import(EvolutionAuditFailureIT.SwitchableAuditConfiguration.class)
class EvolutionAuditFailureIT extends AbstractIntegrationTest {

    private static final AtomicBoolean AUDIT_FAILING = new AtomicBoolean(false);
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final EvolutionService service;
    private final ClinicalCorrections corrections;
    private final EvolutionEntryRepository repository;

    private UUID userId;
    private UUID patientId;

    EvolutionAuditFailureIT(
            EvolutionService service, ClinicalCorrections corrections, EvolutionEntryRepository repository) {
        this.service = service;
        this.corrections = corrections;
        this.repository = repository;
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
                        + patientId + "', '01900000-0000-7000-8000-000000000001', 'Ana', 'Audit', '78100001', '1111')");
            }
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        AUDIT_FAILING.set(false);
        ClinicalFixtures.deleteEvolutions();
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM audit_log WHERE entity_type IN ('evolution_entry', 'clinical_record')");
            statement.executeUpdate("DELETE FROM patient WHERE dni = '78100001'");
        }
    }

    @Test
    void siFallaLaAuditoriaElAltaNoDejaHistoriaNiEntradas() throws SQLException {
        AUDIT_FAILING.set(true);

        assertThatThrownBy(() -> service.register(patientId, userId, command("Control de rutina"), CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("AUDIT_WRITE_FAILED");

        assertThat(count("clinical_record")).isZero();
        assertThat(count("evolution_entry")).isZero();
    }

    @Test
    void siFallaLaAuditoriaLaCorreccionNoDejaEntradaNueva() throws SQLException {
        EvolutionEntryData original = service.register(patientId, userId, command("original"), CONTEXT);
        AUDIT_FAILING.set(true);

        assertThatThrownBy(() -> service.correct(
                patientId, original.id(), "motivo", EvolutionDescription.of("corregida"), userId, CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("AUDIT_WRITE_FAILED");

        assertThat(count("evolution_entry")).isEqualTo(1);
        AUDIT_FAILING.set(false);
        assertThat(service.read(patientId, userId, CONTEXT)).extracting(EvolutionEntryData::id)
                .containsExactly(original.id());
    }

    @Test
    void siFallaLaAuditoriaLaLecturaNoEntregaDatos() {
        service.register(patientId, userId, command("original"), CONTEXT);
        AUDIT_FAILING.set(true);

        assertThatThrownBy(() -> service.read(patientId, userId, CONTEXT)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void dosCorreccionesConcurrentesDeLaMismaEvolucionDejanUnaSolaYLaOtraRecibeYaCorregida() throws Exception {
        EvolutionEntryData original = service.register(patientId, userId, command("original"), CONTEXT);
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        // Almacen con una barrera tras las verificaciones previas: ambas correcciones las pasan y solo la restriccion
        // UNIQUE (corrects_entry_id) de la base decide quien gana.
        CorrectableEntryStore<EvolutionEntry> racingStore = new RacingStore(repository, bothChecked);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> outcomes = new ArrayList<>();
            for (int attempt = 0; attempt < 2; attempt++) {
                int n = attempt;
                outcomes.add(pool.submit(() -> {
                    try {
                        return corrections.correct(
                                ClinicalEntryType.EVOLUTION, racingStore, patientId, original.id(), "motivo " + n,
                                userId, CONTEXT,
                                (rid, correction, provenance, orig) -> EvolutionEntry.correct(
                                        rid, orig, EvolutionDescription.of("corregida " + n), correction, provenance));
                    } catch (RuntimeException error) {
                        return error;
                    }
                }));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(60, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(result -> result instanceof EvolutionEntry).hasSize(1);
            assertThat(results).filteredOn(result -> result instanceof ConflictException).singleElement()
                    .satisfies(error -> assertThat(((ConflictException) error).code())
                            .isEqualTo("CLINICAL_ENTRY_ALREADY_CORRECTED"));
            assertThat(count("evolution_entry")).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    private static EvolutionCommand command(String description) {
        return new EvolutionCommand(EvolutionDescription.of(description), null);
    }

    private static int count(String table) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    /** Almacen real de evoluciones con una barrera en el gancho para forzar la carrera entre dos correcciones. */
    private static final class RacingStore implements CorrectableEntryStore<EvolutionEntry> {
        private final EvolutionEntryRepository delegate;
        private final CyclicBarrier barrier;

        RacingStore(EvolutionEntryRepository delegate, CyclicBarrier barrier) {
            this.delegate = delegate;
            this.barrier = barrier;
        }

        @Override
        public Optional<EvolutionEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
            return delegate.findInRecord(clinicalRecordId, entryId);
        }

        @Override
        public boolean isCorrected(UUID entryId) {
            return delegate.isCorrected(entryId);
        }

        @Override
        public EvolutionEntry save(EvolutionEntry entry) {
            return delegate.save(entry);
        }

        @Override
        public void assertCorrectable(EvolutionEntry original) {
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
