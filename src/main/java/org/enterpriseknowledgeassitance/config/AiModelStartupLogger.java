package org.enterpriseknowledgeassitance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Logs effective, non-secret AI model configuration once the application is ready. */
@Component
class AiModelStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(AiModelStartupLogger.class);

    private final EkaProperties properties;
    private final Environment environment;

    AiModelStartupLogger(EkaProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logConfiguredModels() {
        EkaProperties.Ai ai = properties.ai();
        log.info("AI configuration: chat provider={}, chat model={}, embedding provider={}, embedding model={}, "
                        + "embedding dimensions={}, vector table={}",
                environment.getProperty("spring.ai.model.chat", "unknown"), ai.chatModel(),
                environment.getProperty("spring.ai.model.embedding", "unknown"), ai.embeddingModel(),
                ai.embeddingDimensions(), ai.vectorTable());
    }
}
