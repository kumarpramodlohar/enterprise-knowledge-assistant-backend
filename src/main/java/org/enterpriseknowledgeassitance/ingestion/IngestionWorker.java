package org.enterpriseknowledgeassitance.ingestion;

import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the durable job table and runs at most {@code eka.ingestion.concurrency} jobs at a time. */
@Component
@ConditionalOnProperty(name = "eka.ingestion.worker-enabled", havingValue = "true")
class IngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final IngestionService service;
    private final ExecutorService pool;
    private final Semaphore permits;

    IngestionWorker(IngestionService service, EkaProperties props) {
        this.service = service;
        int concurrency = props.ingestion().concurrency();
        this.pool = Executors.newFixedThreadPool(concurrency);
        this.permits = new Semaphore(concurrency);
    }

    @Scheduled(fixedDelayString = "${eka.ingestion.poll-interval}")
    void poll() {
        try {
            service.recoverStale();
            while (permits.tryAcquire()) {
                Optional<IngestionService.ClaimedJob> job = service.claimNext();
                if (job.isEmpty()) {
                    permits.release();
                    return;
                }
                pool.execute(() -> run(job.get()));
            }
        } catch (RuntimeException e) {
            log.warn("Ingestion poll failed: {}", e.getClass().getName());
        }
    }

    private void run(IngestionService.ClaimedJob job) {
        try {
            service.process(job);
        } catch (RuntimeException e) {
            log.warn("Ingestion job {} aborted: {}", job.jobId(), e.getClass().getName());
        } finally {
            permits.release();
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
    }
}
