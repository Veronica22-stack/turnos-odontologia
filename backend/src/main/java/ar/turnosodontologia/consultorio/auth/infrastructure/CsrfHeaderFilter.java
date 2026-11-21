package ar.turnosodontologia.consultorio.auth.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Expone la CSRF vigente como cookie legible por el SPA y como cabecera de respuesta (D3). */
@Component
class CsrfHeaderFilter extends OncePerRequestFilter {

    private static final String RESPONSE_HEADER = "X-CSRF-TOKEN";

    private final CsrfTokenRepository tokens;

    CsrfHeaderFilter(CsrfTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = tokens.loadToken(request);
        if (token == null) {
            token = tokens.generateToken(request);
            tokens.saveToken(token, request, response);
        }
        response.setHeader(RESPONSE_HEADER, token.getToken());
        chain.doFilter(request, response);
    }
}
