package org.enterpriseknowledgeassitance.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Development-only users. Passwords come from the environment; none are defined by default. */
@ConfigurationProperties(prefix = "eka.local-auth")
public record LocalAuthProperties(List<User> users) {

    public record User(String username, String password) {}

    public LocalAuthProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }
}
