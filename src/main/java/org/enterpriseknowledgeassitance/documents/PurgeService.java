package org.enterpriseknowledgeassitance.documents;

import java.util.List;
import java.util.UUID;
import org.enterpriseknowledgeassitance.config.EkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Permanently removes soft-deleted documents once their persisted purge deadline passes. Deleting the
 * document row cascades to jobs, staged text, chunks and vectors. Failures are audited and retried
 * on the next run.
 */
@Service
public class PurgeService {

    private static final Logger log = LoggerFactory.getLogger(PurgeService.class);
    private static final String ACTOR = "system";

    private static final String DUE = """
            SELECT id, collection_id FROM documents
            WHERE deleted_at IS NOT NULL AND purge_after <= now()
            ORDER BY purge_after LIMIT :limit""";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final DocumentAudit audit;
    private final int batchSize;

    public PurgeService(JdbcClient jdbc, TransactionTemplate tx, DocumentAudit audit, EkaProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.audit = audit;
        this.batchSize = props.retention().purgeBatchSize();
    }

    /** @return number of documents purged in this run */
    public int purgeDue() {
        record Due(UUID id, UUID collectionId) {}
        List<Due> due = jdbc.sql(DUE).param("limit", batchSize)
                .query((rs, n) -> new Due(rs.getObject("id", UUID.class), rs.getObject("collection_id", UUID.class)))
                .list();
        int purged = 0;
        for (Due d : due) {
            try {
                tx.executeWithoutResult(s -> {
                    jdbc.sql("DELETE FROM documents WHERE id = :d AND deleted_at IS NOT NULL").param("d", d.id())
                            .update();
                    audit.record(d.id(), d.collectionId(), "PURGE", "SUCCESS", ACTOR);
                });
                purged++;
            } catch (RuntimeException e) {
                log.warn("Purge of document {} failed: {}", d.id(), e.getClass().getName());
                audit.record(d.id(), d.collectionId(), "PURGE", "FAILED", ACTOR);
            }
        }
        return purged;
    }

    @Component
    @ConditionalOnProperty(name = "eka.retention.purge-enabled", havingValue = "true")
    static class Scheduler {
        private final PurgeService service;

        Scheduler(PurgeService service) {
            this.service = service;
        }

        @Scheduled(fixedDelayString = "${eka.retention.purge-interval}")
        void run() {
            service.purgeDue();
        }
    }
}
