package org.enterpriseknowledgeassitance.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Validates independent Spring AI provider selection and embedding-index compatibility. */
@Component
class AiProviderConfigurationValidator {

    private final Environment environment;
    private final EkaProperties properties;

    AiProviderConfigurationValidator(Environment environment, EkaProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @PostConstruct
    void validateActiveConfiguration() {
        validate(
                environment.getProperty("spring.ai.model.chat", "ollama"),
                environment.getProperty("spring.ai.model.embedding", "ollama"),
                environment.getProperty("spring.ai.openai.api-key", ""),
                properties.ai().embeddingDimensions(),
                properties.ai().vectorTable());
    }

    static void validate(String chatProvider, String embeddingProvider, String openAiApiKey,
            int embeddingDimensions, String vectorTable) {
        requireSupported("chat", chatProvider);
        requireSupported("embedding", embeddingProvider);
        if (("openai".equals(chatProvider) || "openai".equals(embeddingProvider))
                && (openAiApiKey == null || openAiApiKey.isBlank())) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is required when OpenAI is selected for chat or embeddings");
        }

        int expectedDimensions = switch (vectorTable) {
            case "vector_store_ollama" -> 768;
            case "vector_store_openai" -> 1536;
            default -> throw new IllegalStateException(
                    "eka.ai.vector-table must select vector_store_ollama or vector_store_openai");
        };
        if (embeddingDimensions != expectedDimensions) {
            throw new IllegalStateException("eka.ai.embedding-dimensions must be " + expectedDimensions
                    + " for " + vectorTable);
        }
    }

    private static void requireSupported(String capability, String provider) {
        if (!"ollama".equals(provider) && !"openai".equals(provider)) {
            throw new IllegalStateException("AI " + capability + " provider must be 'ollama' or 'openai'");
        }
    }
}
