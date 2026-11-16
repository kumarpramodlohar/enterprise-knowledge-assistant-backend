package org.enterpriseknowledgeassitance.security;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eka.auth-service")
public record AuthServiceProperties(
        boolean enabled,
        URI baseUri,
        String clientKeyStore,
        String clientKeyStorePassword,
        String trustedCaCertificate,
        String localNamespace,
        Duration timeout,
        int pageSize,
        int scopeLimit) {

    public AuthServiceProperties {
        timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
        pageSize = pageSize == 0 ? 100 : pageSize;
        scopeLimit = scopeLimit == 0 ? 10_000 : scopeLimit;
    }

    void validate() {
        if (!enabled) {
            return;
        }
        if (baseUri == null || !"https".equalsIgnoreCase(baseUri.getScheme()) || baseUri.getHost() == null
                || baseUri.getUserInfo() != null || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null
                || clientKeyStore == null || clientKeyStore.isBlank()
                || clientKeyStorePassword == null || clientKeyStorePassword.isEmpty()
                || trustedCaCertificate == null || trustedCaCertificate.isBlank()
                || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(2)) > 0
                || pageSize < 1 || pageSize > 500 || scopeLimit < 1 || scopeLimit > 1_000_000) {
            throw new IllegalStateException("Invalid enabled auth-service configuration");
        }
    }
}
