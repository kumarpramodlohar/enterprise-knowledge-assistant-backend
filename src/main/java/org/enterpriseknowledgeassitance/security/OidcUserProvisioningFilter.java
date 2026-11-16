package org.enterpriseknowledgeassitance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.enterpriseknowledgeassitance.web.ApiException;
import org.enterpriseknowledgeassitance.web.CorrelationIdFilter;
import org.enterpriseknowledgeassitance.web.ErrorCodes;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Creates new OIDC identities as members with no collection grants. */
@Component
class OidcUserProvisioningFilter extends OncePerRequestFilter {

    private final JdbcClient jdbc;
    private final AuthServiceClient authService;
    private final AuthServiceProperties properties;

    OidcUserProvisioningFilter(JdbcClient jdbc, AuthServiceProperties properties,
            ObjectProvider<AuthServiceClient> provider) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.authService = provider.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()) {
            try {
                if (properties.enabled()) {
                    if (authService == null) {
                        writeUnavailable(response);
                        return;
                    }
                    authService.resolve(IdentityAssertion.oidc(
                            URI.create(jwt.getToken().getIssuer().toString()), jwt.getToken().getSubject()));
                } else {
                    String subject = jwt.getName();
                    boolean exists = Boolean.TRUE.equals(jdbc.sql(
                            "SELECT EXISTS (SELECT 1 FROM app_users WHERE subject = :subject)")
                            .param("subject", subject).query(Boolean.class).single());
                    if (!exists) {
                        jdbc.sql("""
                                INSERT INTO app_users (subject, role)
                                VALUES (:subject, 'MEMBER')
                                ON CONFLICT (subject) DO NOTHING
                                """).param("subject", subject).update();
                    }
                }
            } catch (DataAccessException | ApiException e) {
                writeUnavailable(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static void writeUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json");
        response.getOutputStream().write(("{\"code\":\"" + ErrorCodes.AUTHORIZATION_SERVICE_UNAVAILABLE
                + "\",\"message\":\"Authorization service is temporarily unavailable\",\"correlationId\":\""
                + CorrelationIdFilter.current() + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
