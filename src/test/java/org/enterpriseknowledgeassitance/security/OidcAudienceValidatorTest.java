package org.enterpriseknowledgeassitance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcAudienceValidatorTest {

    @Test
    void acceptsTokenWithConfiguredApiAudience() {
        var validator = new OidcAudienceValidator("eka-api");
        var token = token(List.of("account", "eka-api"));

        assertThat(validator.validate(token).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithoutConfiguredApiAudience() {
        var validator = new OidcAudienceValidator("eka-api");
        var token = token(List.of("account"));

        assertThat(validator.validate(token).getErrors())
                .extracting(error -> error.getDescription())
                .containsExactly("Required API audience is missing");
    }

    @Test
    void rejectsMissingAudience() {
        var validator = new OidcAudienceValidator("eka-api");

        assertThat(validator.validate(token(List.of())).hasErrors()).isTrue();
    }

    @Test
    void rejectsBlankConfiguredAudience() {
        assertThatThrownBy(() -> new OidcAudienceValidator(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EKA_OIDC_AUDIENCE must not be blank");
    }

    private static Jwt token(List<String> audiences) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("subject")
                .audience(audiences)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
