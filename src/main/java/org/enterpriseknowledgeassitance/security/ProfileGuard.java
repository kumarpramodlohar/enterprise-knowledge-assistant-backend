package org.enterpriseknowledgeassitance.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Fails startup if local development authentication could be combined with deployed configuration. */
@Component
class ProfileGuard {

    ProfileGuard(Environment env) {
        this(env, new AuthServiceProperties(false, null, null, null, null, null, null, 0, 0));
    }

    @Autowired
    ProfileGuard(Environment env, AuthServiceProperties authService) {
        if (env.acceptsProfiles(Profiles.of("local")) && env.acceptsProfiles(Profiles.of("deployed"))) {
            throw new IllegalStateException("Profiles 'local' and 'deployed' must not be active together");
        }
        if (env.acceptsProfiles(Profiles.of("local")) && authService.enabled()) {
            throw new IllegalStateException("Auth-service integration is not supported with the local profile");
        }
    }
}
