package org.enterpriseknowledgeassitance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatQueryResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void omitsDiagnosticsWhenUnavailable() throws Exception {
        ChatQueryResponse response = new ChatQueryResponse(null, true, List.of(), "corr", 1, 0);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.has("retrievalDiagnostics")).isFalse();
    }

    @Test
    void serializesOnlyCandidateIdRankAndScore() throws Exception {
        UUID chunkId = UUID.randomUUID();
        ChatQueryResponse response = new ChatQueryResponse(null, true, List.of(), "corr", 1, 0,
                new RetrievalDiagnostics(List.of(new RetrievalDiagnostic(chunkId, 1, 0.91))));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));
        JsonNode candidate = json.path("retrievalDiagnostics").path("candidates").get(0);

        assertThat(candidate.fieldNames()).toIterable().containsExactlyInAnyOrder("chunkId", "rank", "similarityScore");
        assertThat(candidate.path("chunkId").asText()).isEqualTo(chunkId.toString());
        assertThat(candidate.path("rank").asInt()).isEqualTo(1);
        assertThat(candidate.path("similarityScore").asDouble()).isEqualTo(0.91);
    }
}