package ar.turnosodontologia.consultorio.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void rangoInvalidoSeTraduceA400InvalidRangeSinDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleIllegalArgument(new IllegalArgumentException("end <= start"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_RANGE");
        assertThat(response.getBody().getDetail()).isNull();
    }

    @Test
    void solapamientoDeLaRestriccionSeTraduceAScheduleBlockOverlapSinSql() {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("conflicting key value violates exclusion constraint \"schedule_block_no_overlap\""));

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(error);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("SCHEDULE_BLOCK_OVERLAP");
        assertThat(response.getBody().getDetail()).isNull();
    }

    @Test
    void otraViolacionDeIntegridadConservaConflictGenerico() {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
                "could not execute statement", new SQLException("duplicate key value violates unique constraint \"app_user_email_key\""));

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(error);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("CONFLICT");
        assertThat(response.getBody().getDetail()).isNull();
    }

    @Test
    void exclusionDeTurnosConSqlState23P01SeTraduceASlotTakenSinTextoDePostgres() {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("conflicting key value violates exclusion constraint \"appointment_no_overlap\" "
                        + "Detail: Key (practice_id, period)=(x, y) conflicts with existing key", "23P01"));

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(error);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("APPOINTMENT_SLOT_TAKEN");
        assertThat(response.getBody().getDetail()).isNull();
        assertThat(response.getBody().toString()).doesNotContain("appointment_no_overlap", "practice_id", "23P01");
    }

    @Test
    void elNombreDeLaRestriccionSinSqlStateDeExclusionNoSeTraduceASlotTaken() {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("violates check constraint \"appointment_no_overlap\"", "23514"));

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(error);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("CONFLICT");
    }

    @Test
    void laExclusionDeBloqueosSigueSiendoScheduleBlockOverlapAunConSqlState() {
        DataIntegrityViolationException error = new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("violates exclusion constraint \"schedule_block_no_overlap\"", "23P01"));

        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(error);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("SCHEDULE_BLOCK_OVERLAP");
    }

    @Test
    void rangoInvalidoDeDominioSeTraduceA400InvalidRangeSinDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleDomain(
                new ar.turnosodontologia.consultorio.shared.domain.InvalidRangeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("INVALID_RANGE");
        assertThat(response.getBody().getDetail()).isNull();
    }

    @Test
    void validacionDeCampoSeTraduceA400ValidationFailedConElCampo() {
        ResponseEntity<ProblemDetail> response = handler.handleValidation(
                new ar.turnosodontologia.consultorio.shared.domain.ValidationException("newStartsAt", "REQUIRED"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("VALIDATION_FAILED");
    }
}
