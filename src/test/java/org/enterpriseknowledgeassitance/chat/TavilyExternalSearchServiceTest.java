package org.enterpriseknowledgeassitance.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TavilyExternalSearchServiceTest {

    @Test
    void parsesValidTavilyResponseWithoutSpringHttpMessageConverters() throws Exception {
        var response = TavilyExternalSearchService.parseResponse("""
                {"results":[
                  {"title":"History of computing","url":"https://example.com/history","content":"A concise result."},
                  {"title":"Ignored","url":"http://example.com/insecure","content":"Not a valid citation."}
                ]}
                """);

        assertThat(TavilyExternalSearchService.results(response, 5))
                .containsExactly(new ExternalSearchResult("History of computing", "https://example.com/history",
                        "A concise result."));
    }

    @Test
    void rejectsMalformedProviderResponse() {
        assertThatThrownBy(() -> TavilyExternalSearchService.parseResponse("not-json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("External search returned an invalid JSON response");
    }
}
