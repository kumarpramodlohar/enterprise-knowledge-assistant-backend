package org.enterpriseknowledgeassitance.security;

import java.net.URI;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** An identity assertion constructed only from an authenticated backend principal. */
public record IdentityAssertion(String kind, String issuer, String subject, String namespace, String username) {

    public static IdentityAssertion from(Authentication authentication, String localNamespace) {
        if (authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()) {
            return oidc(URI.create(jwt.getToken().getIssuer().toString()), jwt.getToken().getSubject());
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated identity is required");
        }
        return local(localNamespace, authentication.getName());
    }

    static IdentityAssertion oidc(URI issuer, String subject) {
        if (issuer == null || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Authenticated OIDC identity is incomplete");
        }
        return new IdentityAssertion("OIDC", issuer.toString(), subject, null, null);
    }

    static IdentityAssertion local(String namespace, String username) {
        if (namespace == null || namespace.isBlank() || username == null || username.isBlank()) {
            throw new IllegalArgumentException("Authenticated local identity is incomplete");
        }
        return new IdentityAssertion("LOCAL", null, null, namespace, username);
    }

    Map<String, Object> toWire() {
        if ("OIDC".equals(kind)) {
            return Map.of("kind", kind, "issuer", issuer, "subject", subject);
        }
        if ("LOCAL".equals(kind)) {
            return Map.of("kind", kind, "namespace", namespace, "username", username);
        }
        throw new IllegalStateException("Unsupported authenticated identity kind");
    }

    public String publicSubject() {
        return "OIDC".equals(kind) ? subject : username;
    }
}
