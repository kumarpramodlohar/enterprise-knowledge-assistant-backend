package org.enterpriseknowledgeassitance.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "eka")
public record EkaProperties(
        @Valid @NotNull Upload upload,
        @Valid @NotNull Chunking chunking,
        @Valid @NotNull Retrieval retrieval,
        @Valid @NotNull Ingestion ingestion,
        @Valid @NotNull Ai ai,
        @Valid @NotNull Retention retention,
        @Valid @NotNull Extraction extraction) {

    public record Upload(
            @Min(1) long maxFileSizeBytes,
            @NotNull Set<String> allowedExtensions,
            @Min(1) int maxFileNameLength,
            @Min(1) int maxMetadataLength,
            @Min(0) int maxTags,
            @Min(1) int maxTagLength,
            @NotBlank String defaultContentType) {}

    public record Chunking(@Min(1) int sizeTokens, @Min(0) int overlapTokens) {
        @AssertTrue(message = "overlapTokens must be smaller than sizeTokens")
        public boolean isOverlapValid() {
            return overlapTokens < sizeTokens;
        }
    }

    public record Retrieval(
            @Min(1) int topK,
            @Min(1) int contextTokenBudget,
            @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold) {}

    public record Ingestion(
            @Min(1) int concurrency,
            @NotNull Duration providerTimeout,
            @Min(0) int maxRetries) {}

    public record Ai(
            @NotBlank String chatModel,
            @NotBlank String embeddingModel,
            @Min(1) int embeddingDimensions,
            @NotBlank String vectorTable,
            @Min(1) int embeddingBatchSize) {}

    public record Retention(@Min(1) int purgeAfterDays) {}

    public record Extraction(
            @Min(1) long maxCharacters,
            @Min(1) int maxPages,
            @DecimalMin("0.0") @DecimalMax("1.0") double zipMinInflateRatio,
            @Min(1) long zipMaxEntrySizeBytes) {}
}
