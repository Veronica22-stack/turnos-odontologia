package ar.turnosodontologia.consultorio.auth.infrastructure;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** Escribe la cookie opaca de sesión con los atributos fijos del change (D2). */
final class SessionCookieWriter {

    private SessionCookieWriter() {
    }

    static void write(HttpServletResponse response, SessionProperties properties, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from(properties.cookieName(), sessionId)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static void delete(HttpServletResponse response, SessionProperties properties) {
        ResponseCookie cookie = ResponseCookie.from(properties.cookieName(), "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
