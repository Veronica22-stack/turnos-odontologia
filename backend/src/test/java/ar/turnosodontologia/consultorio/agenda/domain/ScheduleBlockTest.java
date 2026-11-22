package ar.turnosodontologia.consultorio.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ScheduleBlockTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final Instant NINE = Instant.parse("2026-12-10T09:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-12-10T11:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-12-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-12-02T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(CREATED, ZoneOffset.UTC);

    @Test
    void creaBloqueoNuevoConRangoYMotivoValidos() {
        ScheduleBlock block = ScheduleBlock.create(PRACTICE_ID, NINE, ELEVEN, "Vacaciones", FIXED_CLOCK);

        assertThat(block.id()).isNull();
        assertThat(block.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(block.startsAt()).isEqualTo(NINE);
        assertThat(block.endsAt()).isEqualTo(ELEVEN);
        assertThat(block.reason()).isEqualTo("Vacaciones");
        assertThat(block.createdAt()).isEqualTo(CREATED);
        assertThat(block.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void reconstruyeBloqueoExistenteConDatosPersistidos() {
        ScheduleBlock block = ScheduleBlock.existing(ID, PRACTICE_ID, NINE, ELEVEN, "Trámite", CREATED, UPDATED);

        assertThat(block.id()).isEqualTo(ID);
        assertThat(block.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(block.startsAt()).isEqualTo(NINE);
        assertThat(block.endsAt()).isEqualTo(ELEVEN);
        assertThat(block.reason()).isEqualTo("Trámite");
        assertThat(block.createdAt()).isEqualTo(CREATED);
        assertThat(block.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void idSeGeneraComoUuidVersion7AlPersistir() throws NoSuchFieldException {
        Field idField = ScheduleBlock.class.getDeclaredField("id");
        UuidGenerator generator = idField.getAnnotation(UuidGenerator.class);

        assertThat(generator).isNotNull();
        assertThat(generator.style()).isEqualTo(UuidGenerator.Style.VERSION_7);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void motivoVacioONuloSeRechaza(String reason) {
        assertThatThrownBy(() -> ScheduleBlock.create(PRACTICE_ID, NINE, ELEVEN, reason, FIXED_CLOCK))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("reason"));
    }

    @Test
    void motivoMasLargoQueQuinientosCaracteresSeRechaza() {
        String tooLong = "x".repeat(501);

        assertThatThrownBy(() -> ScheduleBlock.create(PRACTICE_ID, NINE, ELEVEN, tooLong, FIXED_CLOCK))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).field()).isEqualTo("reason"));
    }

    @Test
    void motivoDeQuinientosCaracteresSeAcepta() {
        ScheduleBlock block = ScheduleBlock.create(PRACTICE_ID, NINE, ELEVEN, "x".repeat(500), FIXED_CLOCK);

        assertThat(block.reason()).hasSize(500);
    }

    @Test
    void rangoInvertidoONuloSeRechaza() {
        assertThatThrownBy(() -> ScheduleBlock.create(PRACTICE_ID, ELEVEN, NINE, "Vacaciones", FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduleBlock.create(PRACTICE_ID, NINE, NINE, "Vacaciones", FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduleBlock.create(PRACTICE_ID, null, ELEVEN, "Vacaciones", FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduleBlock.create(PRACTICE_ID, NINE, null, "Vacaciones", FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void edicionActualizaMarcasConRelojInyectadoSinCambiarCreacion() {
        ScheduleBlock block = ScheduleBlock.existing(ID, PRACTICE_ID, NINE, ELEVEN, "Trámite", CREATED, CREATED);
        Clock updateClock = Clock.fixed(UPDATED, ZoneOffset.UTC);

        block.update(NINE, Instant.parse("2026-12-10T12:00:00Z"), "Trámite reprogramado", updateClock);

        assertThat(block.endsAt()).isEqualTo(Instant.parse("2026-12-10T12:00:00Z"));
        assertThat(block.reason()).isEqualTo("Trámite reprogramado");
        assertThat(block.createdAt()).isEqualTo(CREATED);
        assertThat(block.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void edicionValidaRangoYMotivoIgualQueCreacion() {
        ScheduleBlock block = ScheduleBlock.existing(ID, PRACTICE_ID, NINE, ELEVEN, "Trámite", CREATED, CREATED);

        assertThatThrownBy(() -> block.update(ELEVEN, NINE, "Trámite", FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> block.update(NINE, ELEVEN, "", FIXED_CLOCK))
                .isInstanceOf(ValidationException.class);
        assertThat(block.endsAt()).isEqualTo(ELEVEN);
        assertThat(block.reason()).isEqualTo("Trámite");
        assertThat(block.updatedAt()).isEqualTo(CREATED);
    }
}
