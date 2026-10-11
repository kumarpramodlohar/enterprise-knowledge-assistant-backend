package org.enterpriseknowledgeassitance.chat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.enterpriseknowledgeassitance.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=pw"})
@AutoConfigureMockMvc
@Import(PostgresTestSupport.class)
class ChatControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void requestWithoutReadableCollectionsReturnsInsufficientEvidenceWithoutCallingProviders() throws Exception {
        jdbc.sql("DELETE FROM collection_grants WHERE subject = 'dev'").update();
        jdbc.sql("DELETE FROM app_users WHERE subject = 'dev'").update();
        mvc.perform(post("/api/v1/chat/query").with(httpBasic("dev", "pw"))
                        .contentType("application/json").content("{\"question\":\"Where is the policy?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence").value(true))
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.retrievalMs").isNumber());
    }

    @Test
    void rejectsBlankQuestionsBeforeRetrieval() throws Exception {
        mvc.perform(post("/api/v1/chat/query").with(httpBasic("dev", "pw"))
                        .contentType("application/json").content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
