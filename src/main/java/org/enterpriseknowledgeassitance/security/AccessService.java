package org.enterpriseknowledgeassitance.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final AuthServiceClient authService;
    private final boolean authServiceEnabled;

    @Autowired
    public AccessService(JdbcClient jdbc, AuthServiceProperties properties, ObjectProvider<AuthServiceClient> provider) {
        this.jdbc = jdbc;
        this.authServiceEnabled = properties.enabled();
        this.authService = provider.getIfAvailable();
        if (authServiceEnabled && authService == null) {
            throw new IllegalStateException("Auth service is enabled but its client is not configured");
        }
    }

    AccessService(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.authService = null;
        this.authServiceEnabled = false;
    }

    public Optional<Role> roleOf(String subject) {
        return jdbc.sql("SELECT role FROM app_users WHERE subject = :s").param("s", subject)
                .query(String.class).optional().map(Role::valueOf);
    }

    public Optional<Role> roleOf(IdentityAssertion identity) {
        if (authServiceEnabled) {
            Role role = authService.scopes(identity).role();
            return role == Role.NONE ? Optional.empty() : Optional.of(role);
        }
        return roleOf(identity.publicSubject());
    }

    public boolean canRead(String subject, UUID collectionId) {
        return has(subject, collectionId, Permission.READ);
    }

    public boolean canRead(IdentityAssertion identity, UUID collectionId) {
        return authServiceEnabled ? authService.check(identity, collectionId, Permission.READ)
                : canRead(identity.publicSubject(), collectionId);
    }

    public boolean canManage(String subject, UUID collectionId) {
        return has(subject, collectionId, Permission.MANAGE);
    }

    public boolean canManage(IdentityAssertion identity, UUID collectionId) {
        return authServiceEnabled ? authService.check(identity, collectionId, Permission.MANAGE)
                : canManage(identity.publicSubject(), collectionId);
    }

    public void requireRead(String subject, UUID collectionId) {
        if (!canRead(subject, collectionId)) {
            throw new NotFoundException();
        }
    }

    public void requireRead(IdentityAssertion identity, UUID collectionId) {
        requireExistingCollection(collectionId);
        if (authServiceEnabled) {
            authService.requirePermission(identity, collectionId, Permission.READ);
        } else {
            requireRead(identity.publicSubject(), collectionId);
        }
    }

    public void requireManage(String subject, UUID collectionId) {
        if (!canManage(subject, collectionId)) {
            throw new NotFoundException();
        }
    }

    public void requireManage(IdentityAssertion identity, UUID collectionId) {
        requireExistingCollection(collectionId);
        if (authServiceEnabled) {
            authService.requirePermission(identity, collectionId, Permission.MANAGE);
        } else {
            requireManage(identity.publicSubject(), collectionId);
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

    public List<UUID> readableCollectionIds(IdentityAssertion identity) {
        if (!authServiceEnabled) {
            return readableCollectionIds(identity.publicSubject());
        }
        return authService.scopes(identity).entries().stream().map(AuthServiceClient.ScopeEntry::collectionId).toList();
    }

    public List<UUID> manageableCollectionIds(String subject) {
        if (roleOf(subject).filter(r -> r == Role.ADMIN).isPresent()) {
            return jdbc.sql("SELECT id FROM collections").query(UUID.class).list();
        }
        return jdbc.sql("""
                SELECT collection_id FROM collection_grants
                WHERE subject = :s AND permission = 'MANAGE'
                """).param("s", subject).query(UUID.class).list();
    }

    public List<UUID> manageableCollectionIds(IdentityAssertion identity) {
        if (!authServiceEnabled) {
            return manageableCollectionIds(identity.publicSubject());
        }
        return authService.scopes(identity).entries().stream()
                .filter(entry -> entry.permission() == Permission.MANAGE)
                .map(AuthServiceClient.ScopeEntry::collectionId).toList();
    }

    public AuthServiceClient.PolicyScopes scopes(IdentityAssertion identity) {
        if (authServiceEnabled) {
            return authService.scopes(identity);
        }
        Optional<Role> role = roleOf(identity.publicSubject());
        List<UUID> readable = readableCollectionIds(identity.publicSubject());
        List<UUID> manageable = manageableCollectionIds(identity.publicSubject());
        List<AuthServiceClient.ScopeEntry> entries = readable.stream()
                .map(id -> new AuthServiceClient.ScopeEntry(id,
                        manageable.contains(id) ? Permission.MANAGE : Permission.READ))
                .toList();
        return new AuthServiceClient.PolicyScopes(role.orElse(Role.NONE), entries, "legacy");
    }

    /** Resolves the collection of a document only if the subject may read it (for citation resolution). */
    public UUID requireReadableDocument(String subject, UUID documentId) {
        UUID collectionId = jdbc.sql("SELECT collection_id FROM documents WHERE id = :d AND deleted_at IS NULL")
                .param("d", documentId).query(UUID.class).optional().orElseThrow(NotFoundException::new);
        requireRead(subject, collectionId);
        return collectionId;
    }

    public UUID requireReadableDocument(IdentityAssertion identity, UUID documentId) {
        UUID collectionId = jdbc.sql("SELECT collection_id FROM documents WHERE id = :d AND deleted_at IS NULL")
                .param("d", documentId).query(UUID.class).optional().orElseThrow(NotFoundException::new);
        requireRead(identity, collectionId);
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

    private void requireExistingCollection(UUID collectionId) {
        boolean exists = Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM collections WHERE id = :id)")
                .param("id", collectionId).query(Boolean.class).single());
        if (!exists) {
            throw new NotFoundException();
        }
    }
}
