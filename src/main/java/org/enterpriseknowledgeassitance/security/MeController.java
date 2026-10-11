package org.enterpriseknowledgeassitance.security;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.enterpriseknowledgeassitance.api.ApiPaths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    record Me(String subject, String role, List<UUID> readableCollectionIds, List<UUID> manageableCollectionIds) {}

    private final AccessService access;

    MeController(AccessService access) {
        this.access = access;
    }

    @GetMapping(ApiPaths.ME)
    Me me(Principal principal) {
        String subject = principal.getName();
        return new Me(subject, access.roleOf(subject).map(Enum::name).orElse("NONE"),
                access.readableCollectionIds(subject), access.manageableCollectionIds(subject));
    }
}
