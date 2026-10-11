package org.enterpriseknowledgeassitance.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.enterpriseknowledgeassitance.PostgresTestSupport;
import org.enterpriseknowledgeassitance.extraction.ExtractedDocument;
import org.enterpriseknowledgeassitance.extraction.ExtractedSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = {"EKA_DB_PASSWORD=unused", "EKA_LOCAL_USER_PASSWORD=unused"})
@Import({PostgresTestSupport.class, ChunkIndexerIntegrationTest.Fake.class})
class ChunkIndexerIntegrationTest {

    @TestConfiguration
    static class Fake {
        @Bean
        @Primary
        FakeEmbeddingModel fakeEmbeddingModel() {
            return new FakeEmbeddingModel();
        }
    }

    @Autowired ChunkIndexer indexer;
    @Autowired Chunker chunker;
    @Autowired FakeEmbeddingModel fake;
    @Autowired JdbcClient jdbc;

    UUID colA = UUID.randomUUID();
    UUID colB = UUID.randomUUID();
    UUID docA = UUID.randomUUID();
    UUID docB = UUID.randomUUID();

    @BeforeEach
    void seed() {
        fake.fail = false;
        fake.dimensions = 768;
        jdbc.sql("DELETE FROM documents").update();
        jdbc.sql("DELETE FROM collections").update();
        for (UUID c : List.of(colA, colB)) {
            jdbc.sql("INSERT INTO collections (id, name) VALUES (:i, :n)").param("i", c).param("n", "c" + c).update();
        }
        doc(docA, colA, "refund-policy.pdf", "v1", "policy", "hr,finance");
        doc(docB, colB, "travel.txt", "v2", "guide", "travel");
    }

    private void doc(UUID id, UUID col, String name, String version, String type, String tags) {
        jdbc.sql("""
                INSERT INTO documents (id, collection_id, file_name, content_type, size_bytes, checksum_sha256,
                    version, document_type, tags, status, uploaded_by)
                VALUES (:id, :c, :n, 'text/plain', 1, :h, :v, :t, string_to_array(:tags, ','), 'INDEXED', 'x')
                """).param("id", id).param("c", col).param("n", name).param("h", id.toString().replace("-", "").repeat(2))
                .param("v", version).param("t", type).param("tags", tags).update();
    }

    private ExtractedDocument text(String t) {
        return new ExtractedDocument(List.of(new ExtractedSegment(t, 3, "Refunds", 0)));
    }

    private void index(UUID doc, String text) throws Exception {
        indexer.index(doc, chunker.chunk(doc, text(text)));
    }

    @Test
    void persistsVectorsLinkedToChunksAndSearchReturnsSourceMetadata() throws Exception {
        index(docA, "refunds are issued within thirty days of purchase");
        index(docB, "flights and hotels must be booked through the travel portal");

        List<SearchHit> hits = indexer.search("how long do refunds take", List.of(colA, colB), 5, 0.0, SearchFilter.NONE);
        assertThat(hits).isNotEmpty();
        SearchHit top = hits.get(0);
        assertThat(top.documentId()).isEqualTo(docA);
        assertThat(top.fileName()).isEqualTo("refund-policy.pdf");
        assertThat(top.pageNumber()).isEqualTo(3);
        assertThat(top.section()).isEqualTo("Refunds");
        assertThat(top.text()).contains("refunds");

        Integer linked = jdbc.sql("""
                SELECT count(*) FROM vector_store_ollama v JOIN document_chunks c ON c.id = v.id
                WHERE v.metadata->>'model' = 'nomic-embed-text' AND (v.metadata->>'dimensions')::int = 768
                  AND v.metadata->>'documentId' = c.document_id::text
                """).query(Integer.class).single();
        assertThat(linked).isEqualTo(2);
    }

    @Test
    void searchIsScopedToPermittedCollectionsAndFilters() throws Exception {
        index(docA, "refunds are issued within thirty days");
        index(docB, "refunds for flights follow airline rules");

        assertThat(indexer.search("refunds", List.of(colB), 5, 0.0, SearchFilter.NONE))
                .extracting(SearchHit::documentId).containsOnly(docB);
        assertThat(indexer.search("refunds", List.of(), 5, 0.0, SearchFilter.NONE)).isEmpty();
        assertThat(indexer.search("refunds", List.of(colA, colB), 5, 0.0, new SearchFilter("v2", null, null)))
                .extracting(SearchHit::documentId).containsOnly(docB);
        assertThat(indexer.search("refunds", List.of(colA, colB), 5, 0.0, new SearchFilter(null, "policy", null)))
                .extracting(SearchHit::documentId).containsOnly(docA);
        assertThat(indexer.search("refunds", List.of(colA, colB), 5, 0.0, new SearchFilter(null, null, List.of("travel"))))
                .extracting(SearchHit::documentId).containsOnly(docB);
    }

    @Test
    void softDeletedDocumentsAndLowSimilarityAreExcluded() throws Exception {
        index(docA, "refunds are issued within thirty days");
        assertThat(indexer.search("quantum chromodynamics", List.of(colA), 5, 0.7, SearchFilter.NONE)).isEmpty();
        jdbc.sql("UPDATE documents SET deleted_at = now() WHERE id = :d").param("d", docA).update();
        assertThat(indexer.search("refunds", List.of(colA), 5, 0.0, SearchFilter.NONE)).isEmpty();
    }

    @Test
    void reindexReplacesChunksWithoutDuplicates() throws Exception {
        index(docA, "refunds are issued within thirty days");
        index(docA, "refunds are issued within thirty days");
        assertThat(jdbc.sql("SELECT count(*) FROM document_chunks WHERE document_id = :d").param("d", docA)
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM vector_store_ollama").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void vectorsFromAnotherModelProfileAreNotSearched() throws Exception {
        index(docA, "refunds are issued within thirty days");
        jdbc.sql("UPDATE vector_store_ollama SET metadata = '{\"model\":\"other-model\"}'::json").update();
        assertThat(indexer.search("refunds", List.of(colA), 5, 0.0, SearchFilter.NONE)).isEmpty();
    }

    @Test
    void wrongDimensionIsRejectedAndNothingIsWritten() {
        fake.dimensions = 10;
        assertThatThrownBy(() -> index(docA, "refunds"))
                .isInstanceOfSatisfying(EmbeddingFailedException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("EMBEDDING_DIMENSION_MISMATCH"));
        assertThat(jdbc.sql("SELECT count(*) FROM document_chunks").query(Integer.class).single()).isZero();
    }

    @Test
    void providerFailureKeepsExistingIndexAndHidesProviderDetail() throws Exception {
        index(docA, "refunds are issued within thirty days");
        fake.fail = true;
        assertThatThrownBy(() -> index(docA, "completely different replacement text"))
                .isInstanceOfSatisfying(EmbeddingFailedException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("EMBEDDING_PROVIDER_FAILED");
                    assertThat(e.getMessage()).doesNotContain("secret-detail");
                });
        assertThat(jdbc.sql("SELECT content FROM document_chunks WHERE document_id = :d").param("d", docA)
                .query(String.class).single()).contains("refunds");
    }

    @Test
    void embeddingsAreRequestedInBoundedBatches() throws Exception {
        fake.calls.set(0);
        String words = String.join(" ", java.util.Collections.nCopies(400, "word"));
        indexer.index(docA, new Chunker(10, 0).chunk(docA, text(words)));
        // 40 chunks at batch size 32 -> 2 calls
        assertThat(fake.calls.get()).isEqualTo(2);
    }
}
