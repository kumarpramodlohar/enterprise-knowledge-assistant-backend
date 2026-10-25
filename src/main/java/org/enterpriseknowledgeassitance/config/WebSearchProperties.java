package org.enterpriseknowledgeassitance.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "eka.web-search")
public record WebSearchProperties(
        boolean enabled,
        String apiKey,
        @NotNull Duration timeout,
        @Min(1) @Max(5) int maxResults) {
    public WebSearchProperties {
        apiKey = apiKey == null ? "" : apiKey;
    }
}
