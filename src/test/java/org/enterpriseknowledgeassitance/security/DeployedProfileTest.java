package org.enterpriseknowledgeassitance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "OPENAI_API_KEY=unused",
        "EKA_OIDC_ISSUER_URI=http://localhost:9/realms/eka"})
@ActiveProfiles("deployed")
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class DeployedProfileTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext ctx;
    @Autowired JdbcClient jdbc;

    @Test
    void localAuthIsNotAvailableAndBasicCredentialsAreRejected() throws Exception {
        assertThat(ctx.getBeansOfType(UserDetailsService.class)).doesNotContainKey("localUsers");
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void firstOidcRequestCreatesDefaultMemberWithoutCollectionGrants() throws Exception {
        String subject = "new-oidc-user";
        jdbc.sql("DELETE FROM collection_grants WHERE subject = :s").param("s", subject).update();
        jdbc.sql("DELETE FROM app_users WHERE subject = :s").param("s", subject).update();

        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value(subject))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.readableCollectionIds").isEmpty())
                .andExpect(jsonPath("$.manageableCollectionIds").isEmpty());
    }
}
