package org.enterpriseknowledgeassitance.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Fails startup if local development authentication could be combined with deployed configuration. */
@Component
class ProfileGuard {

    @Autowired
    ProfileGuard(Environment env) {
        if (env.acceptsProfiles(Profiles.of("local")) && env.acceptsProfiles(Profiles.of("deployed"))) {
            throw new IllegalStateException("Profiles 'local' and 'deployed' must not be active together");
        }
    }
}
