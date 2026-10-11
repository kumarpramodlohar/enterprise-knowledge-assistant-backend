package org.enterpriseknowledgeassitance.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class EkaPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(EkaProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    private static final String[] VALID = {
        "eka.upload.max-file-size-bytes=10485760", "eka.upload.allowed-extensions=pdf,docx,txt,md",
        "eka.chunking.size-tokens=800", "eka.chunking.overlap-tokens=100",
        "eka.retrieval.top-k=5", "eka.retrieval.context-token-budget=4000",
        "eka.retrieval.similarity-threshold=0.70",
        "eka.ingestion.concurrency=2", "eka.ingestion.provider-timeout=60s", "eka.ingestion.max-retries=3",
        "eka.ai.chat-model=m", "eka.ai.embedding-model=e", "eka.ai.embedding-dimensions=768",
        "eka.ai.vector-table=vector_store_ollama", "eka.retention.purge-after-days=7",
        "eka.extraction.max-characters=2000000", "eka.extraction.max-pages=500"
    };

    @Test
    void bindsValidConfiguration() {
        runner.withPropertyValues(VALID).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            EkaProperties p = ctx.getBean(EkaProperties.class);
            assertThat(p.chunking().sizeTokens()).isEqualTo(800);
            assertThat(p.upload().allowedExtensions()).containsExactlyInAnyOrder("pdf", "docx", "txt", "md");
        });
    }

    @Test
    void failsWhenRequiredSettingMissing() {
        runner.withPropertyValues("eka.ai.chat-model=m").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void failsOnInvalidValue() {
        String[] bad = VALID.clone();
        bad[bad.length - 4] = "eka.ai.vector-table=";
        runner.withPropertyValues(bad).run(ctx -> assertThat(ctx).hasFailed());
    }
}
