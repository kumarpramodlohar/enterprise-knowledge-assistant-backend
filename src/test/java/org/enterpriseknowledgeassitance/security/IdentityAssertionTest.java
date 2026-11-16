package org.enterpriseknowledgeassitance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentityAssertionTest {

    @Test
    void oidcAndLocalIdentitiesWithSameSubjectRemainDistinct() {
        IdentityAssertion oidc = IdentityAssertion.oidc(URI.create("https://issuer.example"), "shared-name");
        IdentityAssertion local = IdentityAssertion.local("local-dev", "shared-name");

        assertThat(oidc.publicSubject()).isEqualTo(local.publicSubject());
        assertThat(oidc.toWire()).isEqualTo(Map.of(
                "kind", "OIDC", "issuer", "https://issuer.example", "subject", "shared-name"));
        assertThat(local.toWire()).isEqualTo(Map.of(
                "kind", "LOCAL", "namespace", "local-dev", "username", "shared-name"));
    }

    @Test
    void localIdentityRequiresConfiguredNamespace() {
        assertThatThrownBy(() -> IdentityAssertion.local("", "user"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
