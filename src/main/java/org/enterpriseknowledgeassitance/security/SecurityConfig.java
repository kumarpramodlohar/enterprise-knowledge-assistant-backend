package org.enterpriseknowledgeassitance.security;

import jakarta.servlet.http.HttpServletResponse;
import org.enterpriseknowledgeassitance.web.CorrelationIdFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableConfigurationProperties(LocalAuthProperties.class)
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @Profile("local")
    SecurityFilterChain localChain(HttpSecurity http) throws Exception {
        common(http);
        http.httpBasic(b -> b.authenticationEntryPoint(entryPoint()));
        return http.build();
    }

    @Bean
    @Profile("local")
    UserDetailsService localUsers(LocalAuthProperties props, PasswordEncoder encoder) {
        var users = props.users().stream()
                .map(u -> User.withUsername(u.username())
                        .password(encoder.encode(u.password()))
                        .authorities("ROLE_USER")
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    @Profile("!local")
    SecurityFilterChain oidcChain(HttpSecurity http) throws Exception {
        common(http);
        http.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())
                .authenticationEntryPoint(entryPoint())
                .accessDeniedHandler(deniedHandler()));
        return http.build();
    }

    private void common(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint()).accessDeniedHandler(deniedHandler()));
    }

    private AuthenticationEntryPoint entryPoint() {
        return (req, res, ex) -> write(res, 401, "UNAUTHENTICATED", "Authentication is required");
    }

    private AccessDeniedHandler deniedHandler() {
        return (req, res, ex) -> write(res, 403, "FORBIDDEN", "Access denied");
    }

    private void write(HttpServletResponse res, int status, String code, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Values are constants or validated by CorrelationIdFilter, so no JSON escaping is needed.
        String body = "{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"correlationId\":\"" + CorrelationIdFilter.current() + "\"}";
        res.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
