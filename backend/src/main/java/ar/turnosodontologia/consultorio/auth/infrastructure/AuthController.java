package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.auth.api.AuthMeResponse;
import ar.turnosodontologia.consultorio.auth.api.ChangePasswordRequest;
import ar.turnosodontologia.consultorio.auth.api.LoginRequest;
import ar.turnosodontologia.consultorio.auth.application.AuthenticateUser;
import ar.turnosodontologia.consultorio.auth.application.ChangeOwnPassword;
import ar.turnosodontologia.consultorio.auth.application.ChangePasswordCommand;
import ar.turnosodontologia.consultorio.auth.application.LoginCommand;
import ar.turnosodontologia.consultorio.auth.application.LoginResult;
import ar.turnosodontologia.consultorio.auth.application.LogoutUser;
import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.shared.domain.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Los cuatro endpoints propios de autenticación (D3/D6/D8); DTOs de entrada y salida sin datos personales. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final String RESPONSE_CSRF_HEADER = "X-CSRF-TOKEN";

    private final AuthenticateUser authenticate;
    private final ChangeOwnPassword changePassword;
    private final LogoutUser logout;
    private final CsrfTokenRepository csrfTokens;
    private final SessionProperties properties;

    AuthController(AuthenticateUser authenticate, ChangeOwnPassword changePassword, LogoutUser logout,
            CsrfTokenRepository csrfTokens, SessionProperties properties) {
        this.authenticate = authenticate;
        this.changePassword = changePassword;
        this.logout = logout;
        this.csrfTokens = csrfTokens;
        this.properties = properties;
    }

    @PostMapping("/login")
    ResponseEntity<AuthMeResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LoginResult result = authenticate.execute(new LoginCommand(
                request.email(), request.password(), httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT)));

        CsrfToken rotated = csrfTokens.generateToken(httpRequest);
        csrfTokens.saveToken(rotated, httpRequest, httpResponse);
        httpResponse.setHeader(RESPONSE_CSRF_HEADER, rotated.getToken());
        httpResponse.setHeader(rotated.getHeaderName(), rotated.getToken());
        HttpSession session = httpRequest.getSession(false);
        SessionCookieWriter.write(httpResponse, properties, session.getId());
        return ResponseEntity.ok(responseOf(result.user(), SessionTimeout.expiresAt(session)));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    AuthMeResponse me(HttpServletRequest request) {
        AuthenticatedUser user = currentUser();
        return responseOf(user, SessionTimeout.expiresAt(request.getSession(false)));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response) {
        changePassword.execute(currentUser(), new ChangePasswordCommand(
                request.currentPassword(), request.newPassword()));
        SessionCookieWriter.delete(response, properties);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser user = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser authenticated
                ? authenticated
                : null;
        logout.execute(user);
        SessionCookieWriter.delete(response, properties);
        csrfTokens.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    private static AuthMeResponse responseOf(AuthenticatedUser user, Instant expiresAt) {
        return new AuthMeResponse(user.userId(), user.role().name(), user.mustChangePassword(), expiresAt);
    }

    private static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new ForbiddenException(SecurityErrorCodes.FORBIDDEN);
    }
}
