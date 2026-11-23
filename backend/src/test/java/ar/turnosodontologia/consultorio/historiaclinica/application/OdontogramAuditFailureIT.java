package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntryType;
import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectableEntryStore;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCode;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothPosition;
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
 * Auditoria fail-closed del odontograma y carrera de correcciones contra la base real: si el rastro falla no queda
 * historia ni entrada nueva, y dos correcciones simultaneas de la misma entrada terminan en un 409.
 */
@Import(OdontogramAuditFailureIT.SwitchableAuditConfiguration.class)
class OdontogramAuditFailureIT extends AbstractIntegrationTest {

    private static final AtomicBoolean AUDIT_FAILING = new AtomicBoolean(false);
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final OdontogramService service;
    private final ClinicalCorrections corrections;
    private final OdontogramEntryRepository repository;

    private UUID userId;
    private UUID patientId;

    OdontogramAuditFailureIT(
            OdontogramService service, ClinicalCorrections corrections, OdontogramEntryRepository repository) {
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
                        + patientId + "', '01900000-0000-7000-8000-000000000001', 'Ana', 'Audit', '78000001', '1111')");
            }
        }
    }

    @AfterEach
    void cleanRows() throws SQLException {
        AUDIT_FAILING.set(false);
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM odontogram_entry WHERE corrects_entry_id IS NOT NULL");
            statement.executeUpdate("DELETE FROM odontogram_entry");
            ClinicalFixtures.deleteEvolutions();
            statement.executeUpdate("DELETE FROM clinical_record");
            statement.executeUpdate("DELETE FROM audit_log WHERE entity_type IN ('odontogram_entry', 'clinical_record')");
            statement.executeUpdate("DELETE FROM patient WHERE dni = '78000001'");
        }
    }

    @Test
    void siFallaLaAuditoriaElLoteNoDejaHistoriaNiEntradas() throws SQLException {
        AUDIT_FAILING.set(true);

        assertThatThrownBy(() -> service.register(patientId, userId, List.of(
                command("16", ToothCondition.CARIES), command("21", ToothCondition.CROWN)), CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("AUDIT_WRITE_FAILED");

        assertThat(count("clinical_record")).isZero();
        assertThat(count("odontogram_entry")).isZero();
    }

    @Test
    void siFallaLaAuditoriaLaCorreccionNoDejaEntradaNueva() throws SQLException {
        OdontogramEntryData original = service.register(
                patientId, userId, List.of(command("46", ToothCondition.CARIES)), CONTEXT).get(0);
        AUDIT_FAILING.set(true);

        assertThatThrownBy(() -> service.correct(
                patientId, original.id(), "motivo", ToothCondition.HEALTHY, null, userId, CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("AUDIT_WRITE_FAILED");

        assertThat(count("odontogram_entry")).isEqualTo(1);
        AUDIT_FAILING.set(false);
        assertThat(service.read(patientId, userId, CONTEXT).current()).extracting(OdontogramEntryData::id)
                .containsExactly(original.id());
    }

    @Test
    void siFallaLaAuditoriaLaLecturaNoEntregaDatos() {
        service.register(patientId, userId, List.of(command("46", ToothCondition.CARIES)), CONTEXT);
        AUDIT_FAILING.set(true);

        assertThatThrownBy(() -> service.read(patientId, userId, CONTEXT)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void dosCorreccionesConcurrentesDeLaMismaEntradaDejanUnaSolaYLaOtraRecibeYaCorregida() throws Exception {
        OdontogramEntryData original = service.register(
                patientId, userId, List.of(command("46", ToothCondition.CARIES)), CONTEXT).get(0);
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        // Almacen con una barrera tras las verificaciones previas: ambas correcciones las pasan y solo la restriccion
        // UNIQUE (corrects_entry_id) de la base decide quien gana.
        CorrectableEntryStore<OdontogramEntry> racingStore = new RacingStore(repository, bothChecked);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> outcomes = new ArrayList<>();
            for (int attempt = 0; attempt < 2; attempt++) {
                int n = attempt;
                outcomes.add(pool.submit(() -> {
                    try {
                        return corrections.correct(
                                ClinicalEntryType.ODONTOGRAM, racingStore, patientId, original.id(), "motivo " + n,
                                userId, CONTEXT,
                                (rid, correction, provenance, orig) -> OdontogramEntry.correct(
                                        rid, orig, ToothCondition.HEALTHY, null, correction, provenance));
                    } catch (RuntimeException error) {
                        return error;
                    }
                }));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(60, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(result -> result instanceof OdontogramEntry).hasSize(1);
            assertThat(results).filteredOn(result -> result instanceof ConflictException).singleElement()
                    .satisfies(error -> assertThat(((ConflictException) error).code())
                            .isEqualTo("CLINICAL_ENTRY_ALREADY_CORRECTED"));
            assertThat(count("odontogram_entry")).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    private static OdontogramEntryCommand command(String tooth, ToothCondition condition) {
        return new OdontogramEntryCommand(new ToothPosition(ToothCode.of(tooth), null), condition, null);
    }

    private static int count(String table) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    /** Almacen real del odontograma con una barrera en el gancho para forzar la carrera entre dos correcciones. */
    private static final class RacingStore implements CorrectableEntryStore<OdontogramEntry> {
        private final OdontogramEntryRepository delegate;
        private final CyclicBarrier barrier;

        RacingStore(OdontogramEntryRepository delegate, CyclicBarrier barrier) {
            this.delegate = delegate;
            this.barrier = barrier;
        }

        @Override
        public Optional<OdontogramEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
            return delegate.findInRecord(clinicalRecordId, entryId);
        }

        @Override
        public boolean isCorrected(UUID entryId) {
            return delegate.isCorrected(entryId);
        }

        @Override
        public OdontogramEntry save(OdontogramEntry entry) {
            return delegate.save(entry);
        }

        @Override
        public void assertCorrectable(OdontogramEntry original) {
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
