package org.enterpriseknowledgeassitance.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.enterpriseknowledgeassitance.api.ChatFilters;
import org.enterpriseknowledgeassitance.api.ChatQueryRequest;
import org.enterpriseknowledgeassitance.chat.crag.ActionSelector;
import org.enterpriseknowledgeassitance.chat.crag.CragEvaluation;
import org.enterpriseknowledgeassitance.chat.crag.CragStageException;
import org.enterpriseknowledgeassitance.chat.crag.KnowledgeRefiner;
import org.enterpriseknowledgeassitance.chat.crag.QueryRewriter;
import org.enterpriseknowledgeassitance.chat.crag.RetrievalEvaluator;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.enterpriseknowledgeassitance.config.WebSearchProperties;
import org.enterpriseknowledgeassitance.ingestion.ChunkIndexer;
import org.enterpriseknowledgeassitance.ingestion.SearchFilter;
import org.enterpriseknowledgeassitance.ingestion.SearchHit;
import org.enterpriseknowledgeassitance.security.AccessService;
import org.springframework.ai.chat.client.ChatClient;
import org.junit.jupiter.api.Test;

class ChatServiceCragTest {
    private final UUID collection = UUID.randomUUID();
    private final UUID document = UUID.randomUUID();
    private final UUID chunk = UUID.randomUUID();
    private final UUID retryChunk = UUID.randomUUID();

    private EkaProperties properties() {
        return new EkaProperties(null, null, new EkaProperties.Retrieval(5, 4000, 10, 0.70),
                new EkaProperties.Crag(true, 5, 1, Duration.ofSeconds(60)),
                new EkaProperties.Ingestion(1, Duration.ofSeconds(60), 0, Duration.ofSeconds(1), Duration.ZERO,
                        Duration.ofMinutes(1), false), null, null, null);
    }

    private SearchHit hit(UUID id, String text, double score) {
        return new SearchHit(id, document, collection, "java.pdf", 23, "Java", text, score);
    }

    private ChatClient client(ChatClient.Builder builder) {
        ChatClient client = mock(ChatClient.class);
        when(builder.build()).thenReturn(client);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn("Java is a high-level object-oriented language.");
        return client;
    }

    private ChatService service(ChunkIndexer indexer, AccessService access, ChatClient.Builder builder,
            RetrievalEvaluator evaluator, ActionSelector selector, KnowledgeRefiner refiner, QueryRewriter rewriter) {
        return new ChatService(indexer, access, builder, evaluator, selector, refiner, rewriter, properties());
    }

    @Test
    void correctPassageBelowSimilarityThresholdIsRefinedAndAnsweredWithTrustedCitation() throws Exception {
        ChunkIndexer indexer = mock(ChunkIndexer.class);
        AccessService access = mock(AccessService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = client(builder);
        RetrievalEvaluator evaluator = mock(RetrievalEvaluator.class);
        ActionSelector selector = mock(ActionSelector.class);
        KnowledgeRefiner refiner = mock(KnowledgeRefiner.class);
        QueryRewriter rewriter = mock(QueryRewriter.class);
        SearchHit candidate = hit(chunk, "Java is a high-level object-oriented language. Unrelated passage.", 0.42);
        when(access.readableCollectionIds("member")).thenReturn(List.of(collection));
        when(indexer.searchCandidates("What is Java?", List.of(collection), 5, 0.70, SearchFilter.NONE))
                .thenReturn(List.of(candidate));
        List<CragEvaluation> evaluations = List.of(new CragEvaluation(candidate, CragEvaluation.Verdict.CORRECT));
        when(evaluator.evaluate(eq("What is Java?"), anyList(), anyLong())).thenReturn(evaluations);
        when(selector.select(evaluations)).thenReturn(ActionSelector.Action.CORRECT);
        when(refiner.refine(eq("What is Java?"), anyList(), anyLong())).thenReturn(List.of(
                new KnowledgeRefiner.RefinedPassage(candidate, "Java is a high-level object-oriented language.")));

        var response = service(indexer, access, builder, evaluator, selector, refiner, rewriter)
                .query("member", new ChatQueryRequest("What is Java?", null));

        assertThat(response.insufficientEvidence()).isFalse();
        assertThat(response.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.chunkId()).isEqualTo(chunk);
            assertThat(citation.documentId()).isEqualTo(document);
        });
        verify(indexer, never()).search(anyString(), eq(List.of(collection)), eq(5), eq(0.70), eq(SearchFilter.NONE));
        verify(client).prompt();
    }

    @Test
    void ambiguousEvidenceAbstainsWithoutGeneration() throws Exception {
        ChunkIndexer indexer = mock(ChunkIndexer.class);
        AccessService access = mock(AccessService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = client(builder);
        RetrievalEvaluator evaluator = mock(RetrievalEvaluator.class);
        ActionSelector selector = mock(ActionSelector.class);
        KnowledgeRefiner refiner = mock(KnowledgeRefiner.class);
        QueryRewriter rewriter = mock(QueryRewriter.class);
        SearchHit candidate = hit(chunk, "Possibly related Java passage.", 0.82);
        when(access.readableCollectionIds("member")).thenReturn(List.of(collection));
        when(indexer.searchCandidates(anyString(), eq(List.of(collection)), eq(5), eq(0.70), eq(SearchFilter.NONE)))
                .thenReturn(List.of(candidate));
        List<CragEvaluation> evaluations = List.of(new CragEvaluation(candidate, CragEvaluation.Verdict.AMBIGUOUS));
        when(evaluator.evaluate(anyString(), anyList(), anyLong())).thenReturn(evaluations);
        when(selector.select(evaluations)).thenReturn(ActionSelector.Action.AMBIGUOUS);

        var response = service(indexer, access, builder, evaluator, selector, refiner, rewriter)
                .query("member", new ChatQueryRequest("What is Java?", null));

        assertThat(response.insufficientEvidence()).isTrue();
        verify(client, never()).prompt();
        verify(rewriter, never()).rewrite(anyString(), anyLong());
    }

    @Test
    void incorrectEvidenceRewritesOnceAndRetrievesWithinTheSameScopeAndFilters() throws Exception {
        ChunkIndexer indexer = mock(ChunkIndexer.class);
        AccessService access = mock(AccessService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        client(builder);
        RetrievalEvaluator evaluator = mock(RetrievalEvaluator.class);
        ActionSelector selector = mock(ActionSelector.class);
        KnowledgeRefiner refiner = mock(KnowledgeRefiner.class);
        QueryRewriter rewriter = mock(QueryRewriter.class);
        SearchHit first = hit(chunk, "An unrelated Java passage.", 0.81);
        SearchHit retry = hit(retryChunk, "Java is a high-level object-oriented language.", 0.38);
        SearchFilter filter = new SearchFilter("5", "textbook", List.of("java"));
        ChatFilters filters = new ChatFilters(List.of(collection), "5", "textbook", List.of("java"));
        when(access.readableCollectionIds("member")).thenReturn(List.of(collection));
        when(indexer.searchCandidates("What is Java?", List.of(collection), 5, 0.70, filter))
                .thenReturn(List.of(first));
        when(indexer.searchCandidates("Java language definition", List.of(collection), 5, 0.70, filter))
                .thenReturn(List.of(retry));
        List<CragEvaluation> firstVerdicts = List.of(new CragEvaluation(first, CragEvaluation.Verdict.INCORRECT));
        List<CragEvaluation> retryVerdicts = List.of(new CragEvaluation(retry, CragEvaluation.Verdict.CORRECT));
        when(evaluator.evaluate(eq("What is Java?"), anyList(), anyLong())).thenReturn(firstVerdicts);
        when(evaluator.evaluate(eq("Java language definition"), anyList(), anyLong())).thenReturn(retryVerdicts);
        when(selector.select(firstVerdicts)).thenReturn(ActionSelector.Action.INCORRECT);
        when(selector.select(retryVerdicts)).thenReturn(ActionSelector.Action.CORRECT);
        when(rewriter.rewrite(eq("What is Java?"), anyLong())).thenReturn("Java language definition");
        when(refiner.refine(eq("Java language definition"), anyList(), anyLong())).thenReturn(List.of(
                new KnowledgeRefiner.RefinedPassage(retry, "Java is a high-level object-oriented language.")));

        var response = service(indexer, access, builder, evaluator, selector, refiner, rewriter)
                .query("member", new ChatQueryRequest("What is Java?", filters));

        assertThat(response.insufficientEvidence()).isFalse();
        assertThat(response.citations()).singleElement().satisfies(citation -> assertThat(citation.chunkId()).isEqualTo(retryChunk));
        verify(indexer).searchCandidates("Java language definition", List.of(collection), 5, 0.70, filter);
    }

    @Test
    void evaluatorFailureFallsBackToThresholdSelectedBaselineEvidence() throws Exception {
        ChunkIndexer indexer = mock(ChunkIndexer.class);
        AccessService access = mock(AccessService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = client(builder);
        RetrievalEvaluator evaluator = mock(RetrievalEvaluator.class);
        ActionSelector selector = mock(ActionSelector.class);
        KnowledgeRefiner refiner = mock(KnowledgeRefiner.class);
        QueryRewriter rewriter = mock(QueryRewriter.class);
        SearchHit candidate = hit(chunk, "Java source evidence.", 0.91);
        when(access.readableCollectionIds("member")).thenReturn(List.of(collection));
        when(indexer.searchCandidates(anyString(), eq(List.of(collection)), eq(5), eq(0.70), eq(SearchFilter.NONE)))
                .thenReturn(List.of(candidate));
        when(evaluator.evaluate(anyString(), anyList(), anyLong())).thenThrow(new CragStageException("evaluate"));

        var response = service(indexer, access, builder, evaluator, selector, refiner, rewriter)
                .query("member", new ChatQueryRequest("What is Java?", null));

        assertThat(response.insufficientEvidence()).isFalse();
        assertThat(response.citations()).singleElement().satisfies(citation -> assertThat(citation.chunkId()).isEqualTo(chunk));
        verify(client).prompt();
    }

    @Test
    void explicitOptInUsesExternalSearchOnlyAfterInternalRetryMiss() throws Exception {
        ChunkIndexer indexer = mock(ChunkIndexer.class);
        AccessService access = mock(AccessService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        client(builder);
        RetrievalEvaluator evaluator = mock(RetrievalEvaluator.class);
        ActionSelector selector = mock(ActionSelector.class);
        QueryRewriter rewriter = mock(QueryRewriter.class);
        ExternalSearchService externalSearch = mock(ExternalSearchService.class);
        SearchHit candidate = hit(chunk, "A distractor about Java.", 0.81);
        when(access.readableCollectionIds("member")).thenReturn(List.of(collection));
        when(indexer.searchCandidates("What is Java?", List.of(collection), 5, 0.70, SearchFilter.NONE))
                .thenReturn(List.of(candidate));
        when(indexer.searchCandidates("Java definition", List.of(collection), 5, 0.70, SearchFilter.NONE))
                .thenReturn(List.of());
        List<CragEvaluation> incorrect = List.of(new CragEvaluation(candidate, CragEvaluation.Verdict.INCORRECT));
        when(evaluator.evaluate(eq("What is Java?"), anyList(), anyLong())).thenReturn(incorrect);
        when(selector.select(incorrect)).thenReturn(ActionSelector.Action.INCORRECT);
        when(rewriter.rewrite(eq("What is Java?"), anyLong())).thenReturn("Java definition");
        when(externalSearch.search("What is Java?")).thenReturn(List.of(new ExternalSearchResult(
                "Java overview", "https://example.org/java", "Java is a programming language.")));
        ChatService service = service(indexer, access, builder, evaluator, selector, mock(KnowledgeRefiner.class), rewriter);
        service.configureExternalSearch(externalSearch,
                new WebSearchProperties(true, "test-key", Duration.ofSeconds(2), 5));

        var response = service.query("member", new ChatQueryRequest("What is Java?", null, true));

        assertThat(response.insufficientEvidence()).isFalse();
        assertThat(response.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.sourceType()).isEqualTo(org.enterpriseknowledgeassitance.api.Citation.SourceType.EXTERNAL);
            assertThat(citation.documentId()).isNull();
            assertThat(citation.url()).isEqualTo("https://example.org/java");
        });
        verify(externalSearch).search("What is Java?");
    }

    @Test
    void externalSearchRequiresPerRequestConsent() throws Exception {
        ChunkIndexer indexer = mock(ChunkIndexer.class);
        AccessService access = mock(AccessService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        client(builder);
        RetrievalEvaluator evaluator = mock(RetrievalEvaluator.class);
        ActionSelector selector = mock(ActionSelector.class);
        QueryRewriter rewriter = mock(QueryRewriter.class);
        ExternalSearchService externalSearch = mock(ExternalSearchService.class);
        SearchHit candidate = hit(chunk, "A distractor about Java.", 0.81);
        when(access.readableCollectionIds("member")).thenReturn(List.of(collection));
        when(indexer.searchCandidates(anyString(), eq(List.of(collection)), eq(5), eq(0.70), eq(SearchFilter.NONE)))
                .thenReturn(List.of(candidate));
        List<CragEvaluation> incorrect = List.of(new CragEvaluation(candidate, CragEvaluation.Verdict.INCORRECT));
        when(evaluator.evaluate(anyString(), anyList(), anyLong())).thenReturn(incorrect);
        when(selector.select(incorrect)).thenReturn(ActionSelector.Action.INCORRECT);
        when(rewriter.rewrite(anyString(), anyLong())).thenReturn("Java definition");
        ChatService service = service(indexer, access, builder, evaluator, selector, mock(KnowledgeRefiner.class), rewriter);
        service.configureExternalSearch(externalSearch,
                new WebSearchProperties(true, "test-key", Duration.ofSeconds(2), 5));

        var response = service.query("member", new ChatQueryRequest("What is Java?", null));

        assertThat(response.insufficientEvidence()).isTrue();
        verifyNoInteractions(externalSearch);
    }
}
