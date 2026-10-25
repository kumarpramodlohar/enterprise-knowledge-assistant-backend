package org.enterpriseknowledgeassitance.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.enterpriseknowledgeassitance.config.WebSearchProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Component
public class TavilyExternalSearchService implements ExternalSearchService {
    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MAX_SNIPPET_LENGTH = 1200;
    private final RestClient client;
    private final WebSearchProperties properties;

    public TavilyExternalSearchService(WebSearchProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public List<ExternalSearchResult> search(String query) {
        String responseBody = client.post().uri("https://api.tavily.com/search")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.apiKey())
                .body(new SearchRequest(query, "basic", properties.maxResults(), false, false))
                .retrieve().body(String.class);
        JsonNode response = parseResponse(responseBody);
        return results(response, properties.maxResults());
    }

    static JsonNode parseResponse(String responseBody) {
        try {
            return new ObjectMapper().readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new ExternalSearchResponseException(e);
        }
    }

    static List<ExternalSearchResult> results(JsonNode response, int maxResults) {
        if (response == null || !response.path("results").isArray()) return List.of();
        List<ExternalSearchResult> results = new ArrayList<>();
        for (JsonNode item : response.path("results")) {
            String title = bounded(item.path("title").asText(""), MAX_TITLE_LENGTH);
            String snippet = bounded(item.path("content").asText(""), MAX_SNIPPET_LENGTH);
            String url = item.path("url").asText("");
            if (title.isBlank() || snippet.isBlank() || !isHttpsUrl(url)) continue;
            results.add(new ExternalSearchResult(title, url, snippet));
            if (results.size() >= maxResults) break;
        }
        return List.copyOf(results);
    }

    private static boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String bounded(String value, int max) {
        String compact = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max);
    }

    private record SearchRequest(String query, String search_depth, int max_results,
            boolean include_answer, boolean include_raw_content) {}

    private static final class ExternalSearchResponseException extends RuntimeException {
        ExternalSearchResponseException(JsonProcessingException cause) {
            super("External search returned an invalid JSON response", cause);
        }
    }
}
