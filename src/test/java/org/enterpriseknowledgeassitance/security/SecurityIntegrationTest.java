package org.enterpriseknowledgeassitance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.UUID;
import org.enterpriseknowledgeassitance.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=pw"})
@AutoConfigureMockMvc
@Import({PostgresTestSupport.class, SecurityIntegrationTest.Probe.class})
class SecurityIntegrationTest {

    @RestController
    static class ProbeController {
        @Autowired
        AccessService access;

        @GetMapping("/test/collections/{id}")
        String read(@PathVariable UUID id, Principal p) {
            access.requireRead(p.getName(), id);
            return "secret-content";
        }

        @GetMapping("/test/collections/{id}/manage")
        String manage(@PathVariable UUID id, Principal p) {
            access.requireManage(p.getName(), id);
            return "ok";
        }

        @GetMapping("/test/documents/{id}")
        String doc(@PathVariable UUID id, Principal p) {
            access.requireReadableDocument(p.getName(), id);
            return "ok";
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("db password is hunter2");
        }
    }

    @TestConfiguration
    static class Probe {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired AccessService access;

    UUID granted = UUID.randomUUID();
    UUID other = UUID.randomUUID();

    @BeforeEach
    void seed() {
        jdbc.sql("DELETE FROM collection_grants").update();
        jdbc.sql("DELETE FROM app_users").update();
        jdbc.sql("DELETE FROM documents").update();
        jdbc.sql("DELETE FROM collections").update();
        for (UUID c : new UUID[] {granted, other}) {
            jdbc.sql("INSERT INTO collections (id, name) VALUES (:i, :n)").param("i", c).param("n", "c-" + c).update();
        }
        jdbc.sql("INSERT INTO app_users (subject, role) VALUES ('dev', 'MEMBER'), ('boss', 'ADMIN')").update();
        jdbc.sql("INSERT INTO collection_grants VALUES ('dev', :c, 'READ')").param("c", granted).update();
    }

    @Test
    void unauthenticatedRequestsReturn401WithEnvelope() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        mvc.perform(get("/api/v1/me").with(httpBasic("dev", "nope"))).andExpect(status().isUnauthorized());
    }

    @Test
    void meResolvesRoleAndGrantsFromDatabase() throws Exception {
        mvc.perform(get("/api/v1/me").with(httpBasic("dev", "pw")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.readableCollectionIds[0]").value(granted.toString()));
    }

    @Test
    void grantedReadAllowedButManageDenied() throws Exception {
        mvc.perform(get("/test/collections/" + granted).with(httpBasic("dev", "pw"))).andExpect(status().isOk());
        mvc.perform(get("/test/collections/" + granted + "/manage").with(httpBasic("dev", "pw")))
                .andExpect(status().isNotFound());
    }

    @Test
    void ungrantedCollectionReturns404WithoutLeakingContent() throws Exception {
        mvc.perform(get("/test/collections/" + other).with(httpBasic("dev", "pw")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(header().string("X-Correlation-Id", containsString("-")));
    }

    @Test
    void citationDocumentInUngrantedCollectionIsHidden() throws Exception {
        UUID doc = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO documents (id, collection_id, file_name, content_type, size_bytes,
                    checksum_sha256, status, uploaded_by)
                VALUES (:d, :c, 'a.txt', 'text/plain', 1, :h, 'INDEXED', 'x')
                """).param("d", doc).param("c", other).param("h", "c".repeat(64)).update();
        mvc.perform(get("/test/documents/" + doc).with(httpBasic("dev", "pw"))).andExpect(status().isNotFound());
        assertThat(access.canRead("boss", other)).isTrue();
    }

    @Test
    void unknownSubjectHasNoAccess() {
        assertThat(access.canRead("stranger", granted)).isFalse();
        assertThat(access.readableCollectionIds("stranger")).isEmpty();
    }

    @Test
    void adminReadsAllCollections() {
        assertThat(access.readableCollectionIds("boss")).containsExactlyInAnyOrder(granted, other);
    }

    @Test
    void correlationIdIsEchoedWhenSafeAndReplacedWhenNot() throws Exception {
        mvc.perform(get("/api/v1/me").header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
        mvc.perform(get("/api/v1/me").header("X-Correlation-Id", "bad value\"{"))
                .andExpect(header().string("X-Correlation-Id", containsString("-")));
    }

    @Test
    void unexpectedErrorsAreGenericAndCorrelated() throws Exception {
        mvc.perform(get("/test/boom").with(httpBasic("dev", "pw")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
