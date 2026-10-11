package org.enterpriseknowledgeassitance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "OPENAI_API_KEY=unused",
        "EKA_OIDC_ISSUER_URI=http://localhost:9/realms/eka"})
@ActiveProfiles("deployed")
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class DeployedProfileTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext ctx;

    @Test
    void localAuthIsNotAvailableAndBasicCredentialsAreRejected() throws Exception {
        assertThat(ctx.getBeansOfType(UserDetailsService.class)).doesNotContainKey("localUsers");
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
