package org.enterpriseknowledgeassitance.security;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.enterpriseknowledgeassitance.web.ApiException;
import org.enterpriseknowledgeassitance.web.CorrelationIdFilter;
import org.enterpriseknowledgeassitance.web.ErrorCodes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the internal auth API. Every failure is an authorization failure, never a denial
 * or an empty scope; the caller must fail closed.
 */
public final class AuthServiceClient {

    private static final String RESOLVE = "/internal/v1/users/resolve";
    private static final String CHECK = "/internal/v1/permissions/check";
    private static final String PAGE = "/internal/v1/scopes/page";
    private final RestClient client;
    private final int pageSize;
    private final int scopeLimit;

    AuthServiceClient(RestClient client, int pageSize, int scopeLimit) {
        if (client == null || pageSize < 1 || scopeLimit < 1) {
            throw new IllegalArgumentException("Invalid auth service client configuration");
        }
        this.client = client;
        this.pageSize = pageSize;
        this.scopeLimit = scopeLimit;
    }

    public void resolve(IdentityAssertion identity) {
        Map<String, Object> body = Map.of("identity", identity.toWire());
        ResolveResponse response = post(RESOLVE, body, ResolveResponse.class);
        if (response == null || !Set.of("CREATED", "EXISTING", "NOT_FOUND").contains(response.status())) {
            throw unavailable();
        }
        if ("NOT_FOUND".equals(response.status()) && identity.kind().equals("OIDC")) {
            throw unavailable();
        }
    }

    public void requirePermission(IdentityAssertion identity, UUID collectionId, Permission permission) {
        if (!check(identity, collectionId, permission)) {
            throw new org.enterpriseknowledgeassitance.web.NotFoundException();
        }
    }

    public boolean check(IdentityAssertion identity, UUID collectionId, Permission permission) {
        CheckResponse response = post(CHECK, Map.of(
                "identity", identity.toWire(),
                "collectionId", collectionId.toString(),
                "permission", permission.name()), CheckResponse.class);
        if (response == null || response.decision() == null
                || !Set.of("ALLOW", "DENY").contains(response.decision())) {
            throw unavailable();
        }
        return "ALLOW".equals(response.decision());
    }

    public PolicyScopes scopes(IdentityAssertion identity) {
        List<ScopeEntry> entries = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Set<String> seenCursors = new HashSet<>();
        String revision = null;
        String role = null;
        String cursor = null;
        int maxPages = Math.max(1, (scopeLimit + pageSize - 1) / pageSize);
        for (int page = 0; page < maxPages; page++) {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("identity", identity.toWire());
            body.put("pageSize", pageSize);
            if (cursor != null) {
                body.put("cursor", cursor);
            }
            ScopePage response = post(PAGE, body, ScopePage.class);
            validatePage(response);
            if (revision == null) {
                revision = response.revision();
            } else if (!revision.equals(response.revision())) {
                throw unavailable();
            }
            if (role == null) {
                role = response.role();
            } else if (!role.equals(response.role())) {
                throw unavailable();
            }
            for (ScopeEntryResponse entry : response.entries()) {
                UUID collectionId;
                try {
                    collectionId = UUID.fromString(entry.collectionId());
                } catch (RuntimeException e) {
                    throw unavailable();
                }
                if (!Set.of("READ", "MANAGE").contains(entry.permission()) || !seen.add(collectionId)) {
                    throw unavailable();
                }
                try {
                    entries.add(new ScopeEntry(collectionId, Permission.valueOf(entry.permission())));
                } catch (IllegalArgumentException e) {
                    throw unavailable();
                }
                if (entries.size() > scopeLimit) {
                    throw unavailable();
                }
            }
            cursor = response.nextCursor();
            if (cursor == null) {
                if ("NONE".equals(role) && !entries.isEmpty()) {
                    throw unavailable();
                }
                try {
                    return new PolicyScopes(Role.valueOf(role), List.copyOf(entries), revision);
                } catch (IllegalArgumentException e) {
                    throw unavailable();
                }
            }
            if (cursor.isBlank() || cursor.length() > 4096) {
                throw unavailable();
            }
            if (!seenCursors.add(cursor)) {
                throw unavailable();
            }
        }
        throw unavailable();
    }

    private void validatePage(ScopePage page) {
        if (page == null || page.role() == null || !Set.of("ADMIN", "MEMBER", "NONE").contains(page.role())
                || page.revision() == null || page.revision().isBlank()
                || page.entries() == null) {
            throw unavailable();
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            return client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current())
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.AUTHORIZATION_SERVICE_UNAVAILABLE,
                "Authorization service is temporarily unavailable");
    }

    record ResolveResponse(String status) {}

    record CheckResponse(String decision) {}

    record ScopePage(String role, String revision, List<ScopeEntryResponse> entries, String nextCursor) {}

    record ScopeEntryResponse(String collectionId, String permission) {}

    public record ScopeEntry(UUID collectionId, Permission permission) {}

    public record PolicyScopes(Role role, List<ScopeEntry> entries, String revision) {
        public PolicyScopes {
            entries = List.copyOf(entries);
        }
    }
}
