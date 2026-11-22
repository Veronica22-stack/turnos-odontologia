package ar.turnosodontologia.consultorio.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogoutUserTest {

    private FakeInvalidation invalidation;
    private LogoutUser useCase;

    @BeforeEach
    void setUp() {
        invalidation = new FakeInvalidation();
        useCase = new LogoutUser(invalidation);
    }

    @Test
    void logoutConSesionLaInvalidaEnElRegistro() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), Role.DENTIST, true);

        useCase.execute(user);

        assertThat(invalidation.currentInvalidations).isEqualTo(1);
        assertThat(invalidation.invalidatedUsers).isEmpty();
    }

    @Test
    void logoutSinSesionEsIdempotenteYNoEmiteAuditoria() {
        useCase.execute(null);

        assertThat(invalidation.currentInvalidations).isZero();
        assertThat(invalidation.invalidatedUsers).isEmpty();
    }

    private static final class FakeInvalidation implements SessionInvalidation {

        private int currentInvalidations;
        private final java.util.List<UUID> invalidatedUsers = new java.util.ArrayList<>();

        @Override
        public void invalidateAllSessions(UUID userId) {
            invalidatedUsers.add(userId);
        }

        @Override
        public void invalidateCurrentSession() {
            currentInvalidations++;
        }
    }
}
