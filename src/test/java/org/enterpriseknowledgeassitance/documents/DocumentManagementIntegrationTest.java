package org.enterpriseknowledgeassitance.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.enterpriseknowledgeassitance.FakeEmbeddingConfig;
import org.enterpriseknowledgeassitance.PostgresTestSupport;
import org.enterpriseknowledgeassitance.extraction.ExtractedDocument;
import org.enterpriseknowledgeassitance.extraction.ExtractedSegment;
import org.enterpriseknowledgeassitance.ingestion.ChunkIndexer;
import org.enterpriseknowledgeassitance.ingestion.Chunker;
import org.enterpriseknowledgeassitance.ingestion.SearchFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=pw"})
@AutoConfigureMockMvc
@Import({PostgresTestSupport.class, FakeEmbeddingConfig.class})
class DocumentManagementIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ChunkIndexer indexer;
    @Autowired Chunker chunker;
    @Autowired PurgeService purge;

    UUID manage = UUID.randomUUID();
    UUID readOnly = UUID.randomUUID();
    UUID hidden = UUID.randomUUID();
    UUID docM;
    UUID docR;
    UUID docH;

    @BeforeEach
    void seed() throws Exception {
        jdbc.sql("DELETE FROM document_audit").update();
        jdbc.sql("DELETE FROM documents").update();
        jdbc.sql("DELETE FROM collection_grants").update();
        jdbc.sql("DELETE FROM app_users").update();
        jdbc.sql("DELETE FROM collections").update();
        for (UUID c : List.of(manage, readOnly, hidden)) {
            jdbc.sql("INSERT INTO collections (id, name) VALUES (:i, :n)").param("i", c).param("n", "c" + c).update();
        }
        jdbc.sql("INSERT INTO app_users (subject, role) VALUES ('dev', 'MEMBER')").update();
        jdbc.sql("INSERT INTO collection_grants VALUES ('dev', :m, 'MANAGE'), ('dev', :r, 'READ')")
                .param("m", manage).param("r", readOnly).update();
        docM = indexedDocument(manage, "m.txt", "policy", "hr,finance", "refund policy for customers");
        docR = indexedDocument(readOnly, "r.txt", "guide", "travel", "travel guide for staff");
        docH = indexedDocument(hidden, "h.txt", "policy", "hr", "hidden secret document");
    }

    private UUID indexedDocument(UUID col, String name, String type, String tags, String text) throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO documents (id, collection_id, file_name, content_type, size_bytes, checksum_sha256,
                    document_type, tags, status, uploaded_by)
                VALUES (:id, :c, :n, 'text/plain', 10, :h, :t, string_to_array(:tags, ','), 'INDEXED', 'u')""")
                .param("id", id).param("c", col).param("n", name).param("t", type).param("tags", tags)
                .param("h", id.toString().replace("-", "") + "0".repeat(32)).update();
        jdbc.sql("INSERT INTO ingestion_jobs (document_id, status) VALUES (:d, 'SUCCEEDED')").param("d", id).update();
        jdbc.sql("INSERT INTO document_extraction_segments (document_id, seq, text) VALUES (:d, 0, :t)")
                .param("d", id).param("t", text).update();
        indexer.index(id, chunker.chunk(id, new ExtractedDocument(List.of(new ExtractedSegment(text, null, null, 0)))));
        return id;
    }

    private ResultActions call(org.springframework.test.web.servlet.RequestBuilder rb) throws Exception {
        return mvc.perform(rb);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.with(httpBasic("dev", "pw"));
    }

    private long count(String sql, UUID id) {
        return jdbc.sql(sql).param("d", id).query(Long.class).single();
    }

    @Test
    void listReturnsOnlyPermittedDocumentsWithPaginationAndFilters() throws Exception {
        call(auth(get("/api/v1/documents"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items[?(@.id=='" + docH + "')]").isEmpty());
        call(auth(get("/api/v1/documents").param("size", "1").param("page", "1"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalItems").value(2));
        call(auth(get("/api/v1/documents").param("tag", "hr"))).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(docM.toString()));
        call(auth(get("/api/v1/documents").param("documentType", "guide")))
                .andExpect(jsonPath("$.items[0].id").value(docR.toString()));
        call(auth(get("/api/v1/documents").param("collectionId", readOnly.toString())))
                .andExpect(jsonPath("$.totalItems").value(1));
        call(auth(get("/api/v1/documents").param("collectionId", hidden.toString())))
                .andExpect(jsonPath("$.totalItems").value(0));
        call(auth(get("/api/v1/documents").param("status", "FAILED"))).andExpect(jsonPath("$.totalItems").value(0));
        call(auth(get("/api/v1/documents").param("status", "INDEXED"))).andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void listRejectsOutOfRangePagingAndRequiresAuthentication() throws Exception {
        call(auth(get("/api/v1/documents").param("size", "101"))).andExpect(status().isBadRequest());
        call(auth(get("/api/v1/documents").param("size", "0"))).andExpect(status().isBadRequest());
        call(auth(get("/api/v1/documents").param("page", "-1"))).andExpect(status().isBadRequest());
        call(auth(get("/api/v1/documents").param("status", "BOGUS"))).andExpect(status().isBadRequest());
        call(get("/api/v1/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void detailExposesOnlyPermittedMetadata() throws Exception {
        call(auth(get("/api/v1/documents/" + docM))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("m.txt")).andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.tags[0]").value("hr")).andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content").doesNotExist()).andExpect(jsonPath("$.checksum").doesNotExist())
                .andExpect(jsonPath("$.uploadedBy").doesNotExist());
        call(auth(get("/api/v1/documents/" + docH))).andExpect(status().isNotFound());
        call(auth(get("/api/v1/documents/" + UUID.randomUUID()))).andExpect(status().isNotFound());
        call(get("/api/v1/documents/" + docM)).andExpect(status().isUnauthorized());
    }

    @Test
    void failedDocumentShowsOnlyStableFailureReason() throws Exception {
        jdbc.sql("UPDATE documents SET status = 'FAILED', failure_reason = 'EMBEDDING_PROVIDER_FAILED' WHERE id = :d")
                .param("d", docM).update();
        call(auth(get("/api/v1/documents/" + docM))).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("EMBEDDING_PROVIDER_FAILED"));
    }

    @Test
    void reindexQueuesOnceIsIdempotentAndKeepsOldChunksUntilReplaced() throws Exception {
        long chunksBefore = count("SELECT count(*) FROM document_chunks WHERE document_id = :d", docM);
        call(auth(post("/api/v1/documents/" + docM + "/reindex"))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        call(auth(post("/api/v1/documents/" + docM + "/reindex"))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));
        assertThat(count("SELECT count(*) FROM ingestion_jobs WHERE document_id = :d AND status = 'QUEUED'", docM))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM document_audit WHERE document_id = :d AND action = 'REINDEX'", docM))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM document_chunks WHERE document_id = :d", docM)).isEqualTo(chunksBefore);
    }

    @Test
    void failedDocumentCanBeRetriedViaReindex() throws Exception {
        jdbc.sql("UPDATE documents SET status = 'FAILED', failure_reason = 'EMPTY' WHERE id = :d")
                .param("d", docM).update();
        call(auth(post("/api/v1/documents/" + docM + "/reindex"))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED")).andExpect(jsonPath("$.failureReason").doesNotExist());
    }

    @Test
    void reindexAndDeleteRequireManagePermissionAndHideExistence() throws Exception {
        call(auth(post("/api/v1/documents/" + docR + "/reindex"))).andExpect(status().isNotFound());
        call(auth(delete("/api/v1/documents/" + docR))).andExpect(status().isNotFound());
        call(auth(post("/api/v1/documents/" + docH + "/reindex"))).andExpect(status().isNotFound());
        call(auth(delete("/api/v1/documents/" + docH))).andExpect(status().isNotFound());
        call(post("/api/v1/documents/" + docM + "/reindex")).andExpect(status().isUnauthorized());
        call(delete("/api/v1/documents/" + docM)).andExpect(status().isUnauthorized());
        assertThat(count("SELECT count(*) FROM documents WHERE id = :d AND deleted_at IS NULL", docR)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM document_audit WHERE document_id = :d", docR)).isZero();
    }

    @Test
    void deleteExcludesContentImmediatelyAndPersistsPurgeDeadline() throws Exception {
        assertThat(indexer.search("refund policy", Set.of(manage), 5, 0.0, SearchFilter.NONE)).isNotEmpty();

        call(auth(delete("/api/v1/documents/" + docM))).andExpect(status().isNoContent());

        call(auth(get("/api/v1/documents/" + docM))).andExpect(status().isNotFound());
        call(auth(get("/api/v1/documents"))).andExpect(jsonPath("$.totalItems").value(1));
        call(auth(post("/api/v1/documents/" + docM + "/reindex"))).andExpect(status().isNotFound());
        call(auth(delete("/api/v1/documents/" + docM))).andExpect(status().isNotFound());
        assertThat(indexer.search("refund policy", Set.of(manage), 5, 0.0, SearchFilter.NONE)).isEmpty();

        double days = jdbc.sql("SELECT extract(epoch FROM purge_after - deleted_at) / 86400 FROM documents WHERE id = :d")
                .param("d", docM).query(Double.class).single();
        assertThat(days).isBetween(6.99, 7.01);
        assertThat(jdbc.sql("SELECT deleted_by FROM documents WHERE id = :d").param("d", docM)
                .query(String.class).single()).isEqualTo("dev");
        assertThat(count("SELECT count(*) FROM document_audit WHERE document_id = :d AND action = 'DELETE'", docM))
                .isEqualTo(1);
    }

    @Test
    void deleteCancelsPendingJobsAndAllowsReuploadOfSameContent() throws Exception {
        jdbc.sql("UPDATE documents SET status = 'QUEUED' WHERE id = :d").param("d", docM).update();
        jdbc.sql("INSERT INTO ingestion_jobs (document_id, status) VALUES (:d, 'QUEUED')").param("d", docM).update();
        call(auth(delete("/api/v1/documents/" + docM))).andExpect(status().isNoContent());
        assertThat(count("SELECT count(*) FROM ingestion_jobs WHERE document_id = :d AND status = 'QUEUED'", docM))
                .isZero();
    }

    @Test
    void purgeRemovesEverythingOnlyAfterDeadlineAndAuditsOutcome() throws Exception {
        call(auth(delete("/api/v1/documents/" + docM))).andExpect(status().isNoContent());
        assertThat(purge.purgeDue()).isZero();
        assertThat(count("SELECT count(*) FROM documents WHERE id = :d", docM)).isEqualTo(1);

        jdbc.sql("UPDATE documents SET purge_after = now() - interval '1 minute' WHERE id = :d")
                .param("d", docM).update();
        assertThat(purge.purgeDue()).isEqualTo(1);

        assertThat(count("SELECT count(*) FROM documents WHERE id = :d", docM)).isZero();
        assertThat(count("SELECT count(*) FROM document_chunks WHERE document_id = :d", docM)).isZero();
        assertThat(count("SELECT count(*) FROM document_extraction_segments WHERE document_id = :d", docM)).isZero();
        assertThat(count("SELECT count(*) FROM ingestion_jobs WHERE document_id = :d", docM)).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM vector_store_ollama WHERE metadata->>'documentId' = :d")
                .param("d", docM.toString()).query(Long.class).single()).isZero();
        assertThat(count("SELECT count(*) FROM document_audit WHERE document_id = :d AND action = 'PURGE' "
                + "AND outcome = 'SUCCESS'", docM)).isEqualTo(1);
        // Documents that are not deleted are never purged.
        assertThat(count("SELECT count(*) FROM documents WHERE id = :d", docR)).isEqualTo(1);
    }

    @Test
    void failedPurgeIsAuditedAndRetriedOnNextRun() throws Exception {
        call(auth(delete("/api/v1/documents/" + docM))).andExpect(status().isNoContent());
        jdbc.sql("UPDATE documents SET purge_after = now() - interval '1 minute' WHERE id = :d")
                .param("d", docM).update();
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION block_purge() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'blocked'; END $$ LANGUAGE plpgsql""").update();
        jdbc.sql("CREATE TRIGGER block_purge BEFORE DELETE ON documents FOR EACH ROW EXECUTE FUNCTION block_purge()")
                .update();
        try {
            assertThat(purge.purgeDue()).isZero();
            assertThat(count("SELECT count(*) FROM documents WHERE id = :d", docM)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM document_audit WHERE document_id = :d AND action = 'PURGE' "
                    + "AND outcome = 'FAILED'", docM)).isEqualTo(1);
        } finally {
            jdbc.sql("DROP TRIGGER block_purge ON documents").update();
        }
        assertThat(purge.purgeDue()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM documents WHERE id = :d", docM)).isZero();
    }
}
