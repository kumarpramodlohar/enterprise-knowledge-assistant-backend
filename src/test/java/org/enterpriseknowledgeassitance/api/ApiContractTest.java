package org.enterpriseknowledgeassitance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.UUID;
import org.enterpriseknowledgeassitance.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=pw"})
@AutoConfigureMockMvc
@Import({PostgresTestSupport.class, ApiContractTest.Probe.class})
class ApiContractTest {

    @RestController
    static class QueryProbe {
        @PostMapping("/test/chat")
        ChatQueryResponse chat(@Valid @RequestBody ChatQueryRequest r) {
            return new ChatQueryResponse(null, true, List.of(), "c", 1, 0);
        }
    }

    @TestConfiguration
    static class Probe {
        @Bean
        QueryProbe queryProbe() {
            return new QueryProbe();
        }
    }

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Autowired MockMvc mvc;

    @Test
    void validChatRequestIsAccepted() {
        var req = new ChatQueryRequest("What is the refund policy?",
                new ChatFilters(List.of(UUID.randomUUID()), "v1", "policy", List.of("hr")));
        assertThat(validator.validate(req)).isEmpty();
        assertThat(validator.validate(new ChatQueryRequest("Q?", null))).isEmpty();
    }

    @Test
    void blankOrOversizedQuestionIsRejected() {
        assertThat(validator.validate(new ChatQueryRequest("   ", null))).isNotEmpty();
        assertThat(validator.validate(new ChatQueryRequest(null, null))).isNotEmpty();
        assertThat(validator.validate(new ChatQueryRequest("x".repeat(2001), null))).isNotEmpty();
    }

    @Test
    void paginationBoundsAreEnforced() {
        assertThat(validator.validate(new DocumentListQuery(null, null, null, null, 0, 100))).isEmpty();
        assertThat(validator.validate(new DocumentListQuery(null, null, null, null, -1, 20))).isNotEmpty();
        assertThat(validator.validate(new DocumentListQuery(null, null, null, null, 0, 101))).isNotEmpty();
        assertThat(validator.validate(new DocumentListQuery(null, null, null, null, 0, 0))).isNotEmpty();
        var defaults = new DocumentListQuery(null, null, null, null, null, null);
        assertThat(defaults.pageOrDefault()).isZero();
        assertThat(defaults.sizeOrDefault()).isEqualTo(20);
    }

    @Test
    void invalidBodyMapsTo400ErrorEnvelope() throws Exception {
        mvc.perform(post("/test/chat").with(httpBasic("dev", "pw"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        mvc.perform(post("/test/chat").with(httpBasic("dev", "pw"))
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validBodyReturnsContractShape() throws Exception {
        mvc.perform(post("/test/chat").with(httpBasic("dev", "pw"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence").value(true))
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.timingMs").value(1));
    }

    @Test
    void protectedApiRequiresAuthentication() throws Exception {
        mvc.perform(post("/test/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }
}
