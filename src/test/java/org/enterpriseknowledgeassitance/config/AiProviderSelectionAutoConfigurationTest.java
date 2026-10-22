package org.enterpriseknowledgeassitance.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiProviderSelectionAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ToolCallingAutoConfiguration.class,
                    OllamaApiAutoConfiguration.class,
                    OllamaChatAutoConfiguration.class,
                    OllamaEmbeddingAutoConfiguration.class,
                    OpenAiChatAutoConfiguration.class,
                    OpenAiEmbeddingAutoConfiguration.class));

    @ParameterizedTest
    @CsvSource({
        "ollama,ollama",
        "openai,openai",
        "ollama,openai",
        "openai,ollama"
    })
    void selectsChatAndEmbeddingAdaptersIndependently(String chatProvider, String embeddingProvider) {
        runner.withPropertyValues(
                "spring.ai.model.chat=" + chatProvider,
                "spring.ai.model.embedding=" + embeddingProvider,
                "spring.ai.openai.api-key=test-key",
                "spring.ai.openai.chat.options.model=gpt-4o-mini",
                "spring.ai.openai.embedding.options.model=text-embedding-3-small",
                "spring.ai.ollama.chat.options.model=llama3.2:latest",
                "spring.ai.ollama.embedding.options.model=nomic-embed-text")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ChatModel chat = context.getBean(ChatModel.class);
                    EmbeddingModel embedding = context.getBean(EmbeddingModel.class);
                    if ("openai".equals(chatProvider)) {
                        assertThat(chat).isInstanceOf(OpenAiChatModel.class);
                    } else {
                        assertThat(chat).isInstanceOf(OllamaChatModel.class);
                    }
                    if ("openai".equals(embeddingProvider)) {
                        assertThat(embedding).isInstanceOf(OpenAiEmbeddingModel.class);
                    } else {
                        assertThat(embedding).isInstanceOf(OllamaEmbeddingModel.class);
                    }
                });
    }
}
