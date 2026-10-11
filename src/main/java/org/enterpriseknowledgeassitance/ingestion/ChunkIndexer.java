package org.enterpriseknowledgeassitance.ingestion;

import org.enterpriseknowledgeassitance.web.ErrorCodes;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Embeds chunks and persists them with vectors in the active profile's table. Each vector row shares
 * its id with {@code document_chunks.id}, so citations resolve through application-owned metadata.
 */
@Service
public class ChunkIndexer {

    private static final Pattern TABLE = Pattern.compile("vector_store_[a-z0-9_]+");

    private static final String INSERT_CHUNK = """
            INSERT INTO document_chunks (id, document_id, chunk_index, content, page_number, section, start_offset)
            VALUES (:id, :d, :i, :content, :page, :section, :off)""";

    // %s is the validated vector table name; every value is bound as a parameter.
    private static final String INSERT_VECTOR = """
            INSERT INTO %s (id, content, metadata, embedding)
            VALUES (:id, :content,
                    json_build_object('documentId', CAST(:d AS text), 'collectionId', CAST(:col AS text),
                                      'chunkId', CAST(:id AS text), 'model', CAST(:model AS text),
                                      'dimensions', CAST(:dim AS int)),
                    CAST(:v AS vector))""";

    // Optional filters are NULL-tolerant so the statement is static.
    private static final String SEARCH = """
            SELECT c.id, c.document_id, d.collection_id, d.file_name, c.page_number, c.section, c.content,
                   1 - (v.embedding <=> CAST(:q AS vector)) AS score
            FROM %s v
            JOIN document_chunks c ON c.id = v.id
            JOIN documents d ON d.id = c.document_id
            WHERE d.deleted_at IS NULL
              AND d.collection_id IN (:cols)
              AND v.metadata->>'model' = :model
              AND (CAST(:version AS text) IS NULL OR d.version = :version)
              AND (CAST(:dtype AS text) IS NULL OR d.document_type = :dtype)
              AND (CAST(:tags AS text) IS NULL OR d.tags && string_to_array(:tags, ','))
            ORDER BY v.embedding <=> CAST(:q AS vector)
            LIMIT :k""";

    private final JdbcClient jdbc;
    private final EmbeddingModel embeddings;
    private final TransactionTemplate tx;
    private final String insertVector;
    private final String searchSql;
    private final String model;
    private final int dimensions;
    private final int batchSize;

    public ChunkIndexer(JdbcClient jdbc, EmbeddingModel embeddings, TransactionTemplate tx, EkaProperties props) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.tx = tx;
        String table = props.ai().vectorTable();
        if (!TABLE.matcher(table).matches()) {
            throw new IllegalStateException("eka.ai.vector-table must match " + TABLE.pattern());
        }
        this.insertVector = INSERT_VECTOR.formatted(table);
        this.searchSql = SEARCH.formatted(table);
        this.model = props.ai().embeddingModel();
        this.dimensions = props.ai().embeddingDimensions();
        this.batchSize = props.ai().embeddingBatchSize();
    }

    /**
     * Replaces all chunks and vectors of the document. Embeddings are computed first, so a provider
     * failure leaves existing indexed data untouched. Safe to retry.
     */
    public void index(UUID documentId, List<Chunk> chunks) throws EmbeddingFailedException {
        List<float[]> vectors = embedAll(chunks.stream().map(Chunk::text).toList());
        UUID collectionId = jdbc.sql("SELECT collection_id FROM documents WHERE id = :d")
                .param("d", documentId).query(UUID.class).single();
        tx.executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM document_chunks WHERE document_id = :d").param("d", documentId).update();
            for (int i = 0; i < chunks.size(); i++) {
                Chunk c = chunks.get(i);
                jdbc.sql(INSERT_CHUNK)
                        .param("id", c.id()).param("d", documentId).param("i", c.index())
                        .param("content", c.text()).param("page", c.pageNumber())
                        .param("section", c.section()).param("off", c.startOffset()).update();
                jdbc.sql(insertVector)
                        .param("id", c.id()).param("content", c.text()).param("d", documentId)
                        .param("col", collectionId).param("model", model).param("dim", dimensions)
                        .param("v", literal(vectors.get(i))).update();
            }
        });
    }

    public List<SearchHit> search(String query, Collection<UUID> collectionIds, int topK, double minSimilarity,
            SearchFilter filter) throws EmbeddingFailedException {
        if (collectionIds.isEmpty() || topK < 1) {
            return List.of();
        }
        String q = literal(embedAll(List.of(query)).get(0));
        boolean hasTags = filter.tags() != null && !filter.tags().isEmpty();
        return jdbc.sql(searchSql)
                .param("q", q).param("cols", collectionIds).param("model", model).param("k", topK)
                .param("version", filter.version(), Types.VARCHAR)
                .param("dtype", filter.documentType(), Types.VARCHAR)
                .param("tags", hasTags ? String.join(",", filter.tags()) : null, Types.VARCHAR)
                .query((rs, n) -> new SearchHit(rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class), rs.getObject("collection_id", UUID.class),
                        rs.getString("file_name"), (Integer) rs.getObject("page_number"), rs.getString("section"),
                        rs.getString("content"), rs.getDouble("score")))
                .list().stream().filter(h -> h.score() >= minSimilarity).toList();
    }

    private List<float[]> embedAll(List<String> texts) throws EmbeddingFailedException {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            List<String> batch = texts.subList(from, Math.min(from + batchSize, texts.size()));
            List<float[]> result;
            try {
                result = embeddings.embed(batch);
            } catch (RuntimeException e) {
                throw new EmbeddingFailedException(ErrorCodes.EMBEDDING_PROVIDER_FAILED, e);
            }
            if (result == null || result.size() != batch.size()) {
                throw new EmbeddingFailedException(ErrorCodes.EMBEDDING_PROVIDER_FAILED, null);
            }
            for (float[] v : result) {
                if (v == null || v.length != dimensions) {
                    throw new EmbeddingFailedException(ErrorCodes.EMBEDDING_DIMENSION_MISMATCH, null);
                }
                for (float f : v) {
                    if (!Float.isFinite(f)) {
                        throw new EmbeddingFailedException(ErrorCodes.EMBEDDING_PROVIDER_FAILED, null);
                    }
                }
                out.add(v);
            }
        }
        return out;
    }

    private static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
