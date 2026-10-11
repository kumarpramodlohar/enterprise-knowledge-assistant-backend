package org.enterpriseknowledgeassitance.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.enterpriseknowledgeassitance.web.NotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Server-side authorization. Roles and grants come only from the database, keyed by the
 * authenticated subject; nothing client-supplied is trusted. Denials surface as 404 so the
 * existence of inaccessible resources is not disclosed.
 */
@Service
public class AccessService {

    private final JdbcClient jdbc;

    public AccessService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Role> roleOf(String subject) {
        return jdbc.sql("SELECT role FROM app_users WHERE subject = :s").param("s", subject)
                .query(String.class).optional().map(Role::valueOf);
    }

    public boolean canRead(String subject, UUID collectionId) {
        return has(subject, collectionId, Permission.READ);
    }

    public boolean canManage(String subject, UUID collectionId) {
        return has(subject, collectionId, Permission.MANAGE);
    }

    public void requireRead(String subject, UUID collectionId) {
        if (!canRead(subject, collectionId)) {
            throw new NotFoundException();
        }
    }

    public void requireManage(String subject, UUID collectionId) {
        if (!canManage(subject, collectionId)) {
            throw new NotFoundException();
        }
    }

    /** Collections the subject may search or cite; retrieval must filter by this set. */
    public List<UUID> readableCollectionIds(String subject) {
        if (roleOf(subject).filter(r -> r == Role.ADMIN).isPresent()) {
            return jdbc.sql("SELECT id FROM collections").query(UUID.class).list();
        }
        return jdbc.sql("SELECT collection_id FROM collection_grants WHERE subject = :s").param("s", subject)
                .query(UUID.class).list();
    }

    /** Resolves the collection of a document only if the subject may read it (for citation resolution). */
    public UUID requireReadableDocument(String subject, UUID documentId) {
        UUID collectionId = jdbc.sql("SELECT collection_id FROM documents WHERE id = :d AND deleted_at IS NULL")
                .param("d", documentId).query(UUID.class).optional().orElseThrow(NotFoundException::new);
        requireRead(subject, collectionId);
        return collectionId;
    }

    private boolean has(String subject, UUID collectionId, Permission needed) {
        Optional<Role> role = roleOf(subject);
        if (role.isEmpty()) {
            return false;
        }
        if (role.get() == Role.ADMIN) {
            return true;
        }
        return jdbc.sql("SELECT permission FROM collection_grants WHERE subject = :s AND collection_id = :c")
                .param("s", subject).param("c", collectionId)
                .query(String.class).optional().map(Permission::valueOf)
                .map(p -> needed == Permission.READ || p == Permission.MANAGE)
                .orElse(false);
    }
}
