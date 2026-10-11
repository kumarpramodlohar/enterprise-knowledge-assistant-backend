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
        "eka.upload.max-file-name-length=255", "eka.upload.max-metadata-length=100", "eka.upload.max-tags=20",
        "eka.upload.max-tag-length=50", "eka.upload.default-content-type=application/octet-stream",
        "eka.chunking.size-tokens=800", "eka.chunking.overlap-tokens=100",
        "eka.retrieval.top-k=5", "eka.retrieval.context-token-budget=4000",
        "eka.retrieval.similarity-threshold=0.70",
        "eka.ingestion.concurrency=2", "eka.ingestion.provider-timeout=60s", "eka.ingestion.max-retries=3",
        "eka.ingestion.poll-interval=2s", "eka.ingestion.retry-backoff=5s", "eka.ingestion.stale-after=15m",
        "eka.ingestion.worker-enabled=false", "eka.retention.purge-interval=1h", "eka.retention.purge-batch-size=50",
        "eka.retention.purge-enabled=false",
        "eka.ai.chat-model=m", "eka.ai.embedding-model=e", "eka.ai.embedding-dimensions=768",
        "eka.ai.vector-table=vector_store_ollama", "eka.ai.embedding-batch-size=32", "eka.retention.purge-after-days=7",
        "eka.extraction.max-characters=2000000", "eka.extraction.max-pages=500",
        "eka.extraction.zip-min-inflate-ratio=0.01", "eka.extraction.zip-max-entry-size-bytes=209715200"
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
        bad[bad.length - 7] = "eka.ai.vector-table=";
        runner.withPropertyValues(bad).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void failsWhenOverlapIsNotSmallerThanChunkSize() {
        String[] bad = VALID.clone();
        for (int i = 0; i < bad.length; i++) {
            if (bad[i].startsWith("eka.chunking.overlap-tokens")) bad[i] = "eka.chunking.overlap-tokens=800";
        }
        runner.withPropertyValues(bad).run(ctx -> assertThat(ctx).hasFailed());
    }
}
