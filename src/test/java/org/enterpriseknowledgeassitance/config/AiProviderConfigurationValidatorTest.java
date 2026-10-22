package org.enterpriseknowledgeassitance.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiProviderConfigurationValidatorTest {

    @Test
    void acceptsAllIndependentProviderCombinations() {
        assertThatCode(() -> AiProviderConfigurationValidator.validate(
                "ollama", "ollama", "", 768, "vector_store_ollama")).doesNotThrowAnyException();
        assertThatCode(() -> AiProviderConfigurationValidator.validate(
                "openai", "openai", "test-key", 1536, "vector_store_openai")).doesNotThrowAnyException();
        assertThatCode(() -> AiProviderConfigurationValidator.validate(
                "ollama", "openai", "test-key", 1536, "vector_store_openai")).doesNotThrowAnyException();
        assertThatCode(() -> AiProviderConfigurationValidator.validate(
                "openai", "ollama", "test-key", 768, "vector_store_ollama")).doesNotThrowAnyException();
    }

    @Test
    void requiresApiKeyOnlyWhenOpenAiIsSelected() {
        assertThatCode(() -> AiProviderConfigurationValidator.validate(
                "ollama", "ollama", "", 768, "vector_store_ollama")).doesNotThrowAnyException();
        assertThatThrownBy(() -> AiProviderConfigurationValidator.validate(
                "openai", "ollama", "", 768, "vector_store_ollama"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY")
                .hasMessageNotContaining("secret");
    }

    @Test
    void rejectsUnknownProviderAndMismatchedVectorConfiguration() {
        assertThatThrownBy(() -> AiProviderConfigurationValidator.validate(
                "azure", "ollama", "", 768, "vector_store_ollama"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("provider");
        assertThatThrownBy(() -> AiProviderConfigurationValidator.validate(
                "ollama", "openai", "test-key", 768, "vector_store_openai"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("embedding-dimensions");
    }
}
