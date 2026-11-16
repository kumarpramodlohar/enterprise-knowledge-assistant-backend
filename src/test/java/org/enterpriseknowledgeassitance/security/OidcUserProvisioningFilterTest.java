package org.enterpriseknowledgeassitance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.enterpriseknowledgeassitance.web.ApiException;
import org.enterpriseknowledgeassitance.web.ErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.HttpStatus;

class OidcUserProvisioningFilterTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authServiceFailureReturnsCorrelatedSafe503WithoutContinuing() throws Exception {
        AuthServiceClient client = mock(AuthServiceClient.class);
        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.AUTHORIZATION_SERVICE_UNAVAILABLE,
                "Authorization service is temporarily unavailable"))
                .when(client).resolve(org.mockito.ArgumentMatchers.any());
        AuthServiceProperties properties = new AuthServiceProperties(true, URI.create("https://auth.example"),
                "client.p12", "password", "ca.pem", null, Duration.ofSeconds(2), 100, 10_000);
        OidcUserProvisioningFilter filter = new OidcUserProvisioningFilter(
                mock(JdbcClient.class), properties, provider(client));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(),
                AuthorityUtils.createAuthorityList("ROLE_USER")));

        filter.doFilterInternal(new MockHttpServletRequest(), response, (request, result) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains(ErrorCodes.AUTHORIZATION_SERVICE_UNAVAILABLE)
                .contains("Authorization service is temporarily unavailable")
                .contains("\"correlationId\":\"unknown\"");
        assertThat(continued).isFalse();
        verify(client).resolve(org.mockito.ArgumentMatchers.argThat(identity ->
                identity.kind().equals("OIDC") && identity.issuer().equals("https://issuer.example")
                        && identity.subject().equals("user-1")));
    }

    @Test
    void nonOidcAuthenticationContinuesWithoutProvisioning() throws Exception {
        AuthServiceClient client = mock(AuthServiceClient.class);
        AuthServiceProperties properties = new AuthServiceProperties(true, URI.create("https://auth.example"),
                "client.p12", "password", "ca.pem", null, Duration.ofSeconds(2), 100, 10_000);
        OidcUserProvisioningFilter filter = new OidcUserProvisioningFilter(
                mock(JdbcClient.class), properties, provider(client));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();
        SecurityContextHolder.clearContext();

        filter.doFilterInternal(new MockHttpServletRequest(), response, (request, result) -> continued.set(true));

        assertThat(continued).isTrue();
        verify(client, never()).resolve(org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuthServiceClient> provider(AuthServiceClient client) {
        ObjectProvider<AuthServiceClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://issuer.example")
                .subject("user-1")
                .build();
    }
}
