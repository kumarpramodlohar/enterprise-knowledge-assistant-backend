package org.enterpriseknowledgeassitance.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProfileGuardTest {

    @Test
    void rejectsLocalAndDeployedTogether() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local", "deployed");
        assertThatThrownBy(() -> new ProfileGuard(env)).isInstanceOf(IllegalStateException.class);
    }
}
