package org.enterpriseknowledgeassitance.security;

import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.UUID;
import org.enterpriseknowledgeassitance.api.ApiPaths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    record Me(String subject, String role, List<UUID> readableCollectionIds, List<UUID> manageableCollectionIds) {}

    private final AccessService access;
    private final AuthServiceProperties authServiceProperties;

    MeController(AccessService access, AuthServiceProperties authServiceProperties) {
        this.access = access;
        this.authServiceProperties = authServiceProperties;
    }

    @GetMapping(ApiPaths.ME)
    Me me(Authentication authentication) {
        if (!authServiceProperties.enabled()) {
            String subject = authentication.getName();
            return new Me(subject, access.roleOf(subject).map(Enum::name).orElse("NONE"),
                    access.readableCollectionIds(subject), access.manageableCollectionIds(subject));
        }
        IdentityAssertion identity = IdentityAssertion.from(authentication, authServiceProperties.localNamespace());
        AuthServiceClient.PolicyScopes scopes = access.scopes(identity);
        List<UUID> readable = scopes.entries().stream().map(AuthServiceClient.ScopeEntry::collectionId).toList();
        List<UUID> manageable = scopes.entries().stream()
                .filter(entry -> entry.permission() == Permission.MANAGE)
                .map(AuthServiceClient.ScopeEntry::collectionId).toList();
        return new Me(identity.publicSubject(), scopes.role().name(), readable, manageable);
    }
}
