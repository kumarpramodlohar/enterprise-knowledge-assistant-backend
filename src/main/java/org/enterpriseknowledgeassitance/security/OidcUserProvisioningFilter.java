package org.enterpriseknowledgeassitance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Creates new OIDC identities as members with no collection grants. */
@Component
class OidcUserProvisioningFilter extends OncePerRequestFilter {

    private final JdbcClient jdbc;

    OidcUserProvisioningFilter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()) {
            jdbc.sql("""
                    INSERT INTO app_users (subject, role)
                    VALUES (:subject, 'MEMBER')
                    ON CONFLICT (subject) DO NOTHING
                    """).param("subject", jwt.getName()).update();
        }
        chain.doFilter(request, response);
    }
}
