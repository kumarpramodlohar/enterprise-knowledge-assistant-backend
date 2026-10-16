package org.enterpriseknowledgeassitance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.enterpriseknowledgeassitance.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Keycloak sign-in combined with the local (Ollama) profile. */
@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=pw",
        "EKA_OIDC_ISSUER_URI=http://localhost:9/realms/eka"})
@ActiveProfiles({"local", "oidc"})
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class LocalOidcProfileTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext ctx;

    @Test
    void basicAuthIsDisabledAndTokensAreRequired() throws Exception {
        assertThat(ctx.getBeansOfType(UserDetailsService.class)).doesNotContainKey("localUsers");
        mvc.perform(get("/api/v1/me").with(httpBasic("dev", "pw"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenAuthenticatesAndProvisionsMember() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject("kc-local-oidc-user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }
}
