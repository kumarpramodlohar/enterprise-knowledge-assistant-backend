package org.enterpriseknowledgeassitance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.enterpriseknowledgeassitance.web.ApiException;
import org.enterpriseknowledgeassitance.web.CorrelationIdFilter;
import org.enterpriseknowledgeassitance.web.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AuthServiceClientTest {

    private static final String BASE = "https://auth.example";
    private static final IdentityAssertion IDENTITY =
            IdentityAssertion.oidc(URI.create("https://issuer.example/realm"), "user-1");

    @Test
    void resolvePostsOnlyTheTypedIdentityAndDoesNotRetry() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/internal/v1/users/resolve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"identity":{"kind":"OIDC","issuer":"https://issuer.example/realm","subject":"user-1"}}
                        """))
                .andRespond(withSuccess("""
                        {"status":"EXISTING"}
                        """, MediaType.APPLICATION_JSON));

        new AuthServiceClient(builder.build(), 100, 10_000).resolve(IDENTITY);

        server.verify();
    }

    @Test
    void permissionDenialRemainsNonDisclosingNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/internal/v1/permissions/check"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"identity":{"kind":"OIDC","issuer":"https://issuer.example/realm","subject":"user-1"},
                         "collectionId":"00000000-0000-0000-0000-000000000001","permission":"READ"}
                        """))
                .andRespond(withSuccess("""
                        {"decision":"DENY"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new AuthServiceClient(builder.build(), 100, 10_000)
                .requirePermission(IDENTITY, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        Permission.READ))
                .isInstanceOf(org.enterpriseknowledgeassitance.web.NotFoundException.class);
        server.verify();
    }

    @Test
    void scopeReadFollowsCursorsAndRequiresOneRevision() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String identity = """
                {"identity":{"kind":"OIDC","issuer":"https://issuer.example/realm","subject":"user-1"},"pageSize":100}
                """;
        server.expect(requestTo(BASE + "/internal/v1/scopes/page"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(identity))
                .andRespond(withSuccess("""
                        {"role":"MEMBER","revision":"rev-1","entries":[
                          {"collectionId":"00000000-0000-0000-0000-000000000001","permission":"MANAGE"}],
                         "nextCursor":"opaque-cursor"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/internal/v1/scopes/page"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"identity":{"kind":"OIDC","issuer":"https://issuer.example/realm","subject":"user-1"},
                         "pageSize":100,"cursor":"opaque-cursor"}
                        """))
                .andRespond(withSuccess("""
                        {"role":"MEMBER","revision":"rev-1","entries":[
                          {"collectionId":"00000000-0000-0000-0000-000000000002","permission":"READ"}],
                         "nextCursor":null}
                        """, MediaType.APPLICATION_JSON));

        AuthServiceClient.PolicyScopes scopes = new AuthServiceClient(builder.build(), 100, 10_000).scopes(IDENTITY);

        assertThat(scopes.role()).isEqualTo(Role.MEMBER);
        assertThat(scopes.revision()).isEqualTo("rev-1");
        assertThat(scopes.entries()).containsExactly(
                new AuthServiceClient.ScopeEntry(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        Permission.MANAGE),
                new AuthServiceClient.ScopeEntry(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        Permission.READ));
        server.verify();
    }

    @Test
    void remoteFailureAndMalformedScopeFailClosed() {
        RestClient.Builder unavailableBuilder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer unavailableServer = MockRestServiceServer.bindTo(unavailableBuilder).build();
        unavailableServer.expect(requestTo(BASE + "/internal/v1/scopes/page"))
                .andRespond(withServerError());
        assertUnavailable(() -> new AuthServiceClient(unavailableBuilder.build(), 100, 10_000).scopes(IDENTITY));
        unavailableServer.verify();

        RestClient.Builder malformedBuilder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedBuilder).build();
        malformedServer.expect(requestTo(BASE + "/internal/v1/scopes/page"))
                .andRespond(withSuccess("""
                        {"role":"MEMBER","revision":"rev-1","entries":[
                          {"collectionId":"not-a-uuid","permission":"READ"}],"nextCursor":null}
                        """, MediaType.APPLICATION_JSON));
        assertUnavailable(() -> new AuthServiceClient(malformedBuilder.build(), 100, 10_000).scopes(IDENTITY));
        malformedServer.verify();
    }

    @Test
    void scopeRevisionMismatchFailsClosedRatherThanCombiningPages() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/internal/v1/scopes/page"))
                .andRespond(withSuccess("""
                        {"role":"MEMBER","revision":"rev-1","entries":[],"nextCursor":"cursor"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/internal/v1/scopes/page"))
                .andRespond(withSuccess("""
                        {"role":"MEMBER","revision":"rev-2","entries":[],"nextCursor":null}
                        """, MediaType.APPLICATION_JSON));

        assertUnavailable(() -> new AuthServiceClient(builder.build(), 100, 10_000).scopes(IDENTITY));
        server.verify();
    }

    @Test
    void forwardsOnlySafeCorrelationId() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/internal/v1/users/resolve"))
                .andExpect(header(CorrelationIdFilter.HEADER, "unknown"))
                .andRespond(withSuccess("""
                        {"status":"CREATED"}
                        """, MediaType.APPLICATION_JSON));

        new AuthServiceClient(builder.build(), 100, 10_000).resolve(IDENTITY);
        server.verify();
    }

    @Test
    void enabledConfigurationRequiresHttpsAndBoundedTimeout() {
        AuthServiceProperties valid = new AuthServiceProperties(true, URI.create("https://auth.example"),
                "/run/secrets/client.p12", "secret", "/run/secrets/auth-ca.pem",
                "local-dev", Duration.ofSeconds(2), 100, 10_000);
        valid.validate();

        assertThatThrownBy(() -> new AuthServiceProperties(true, URI.create("http://auth.example"),
                "/run/secrets/client.p12", "secret", "/run/secrets/auth-ca.pem",
                "local-dev", Duration.ofSeconds(2), 100, 10_000).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuthServiceProperties(true, URI.create("https://auth.example"),
                "/run/secrets/client.p12", "secret", "/run/secrets/auth-ca.pem",
                "local-dev", Duration.ofSeconds(3), 100, 10_000).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuthServiceProperties(true, URI.create("https://auth.example?token=bad"),
                "/run/secrets/client.p12", "secret", "/run/secrets/auth-ca.pem",
                "local-dev", Duration.ofSeconds(2), 100, 10_000).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    private void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.status().value()).isEqualTo(503);
                    assertThat(api.code()).isEqualTo(ErrorCodes.AUTHORIZATION_SERVICE_UNAVAILABLE);
                    assertThat(api.getMessage()).isEqualTo("Authorization service is temporarily unavailable");
                });
    }
}
