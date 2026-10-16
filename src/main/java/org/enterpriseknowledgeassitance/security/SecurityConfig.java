package org.enterpriseknowledgeassitance.security;

import org.enterpriseknowledgeassitance.web.ErrorCodes;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableConfigurationProperties(LocalAuthProperties.class)
class SecurityConfig {

    @Bean
    FilterRegistrationBean<OidcUserProvisioningFilter> oidcProvisioningFilterRegistration(
            OidcUserProvisioningFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @Profile("local & !oidc")
    SecurityFilterChain localChain(HttpSecurity http) throws Exception {
        common(http);
        http.httpBasic(b -> b.authenticationEntryPoint(entryPoint()));
        return http.build();
    }

    @Bean
    @Profile("local & !oidc")
    UserDetailsService localUsers(LocalAuthProperties props, PasswordEncoder encoder) {
        var users = props.users().stream()
                .map(u -> User.withUsername(u.username())
                        .password(encoder.encode(u.password()))
                        .authorities("ROLE_USER")
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    /** Active in deployed environments and whenever the `oidc` profile is added (for example `local,oidc`). */
    @Bean
    @Profile("!local | oidc")
    SecurityFilterChain oidcChain(HttpSecurity http, OidcUserProvisioningFilter provisioningFilter) throws Exception {
        common(http);
        http.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())
                .authenticationEntryPoint(entryPoint())
                .accessDeniedHandler(deniedHandler()))
                .addFilterAfter(provisioningFilter, BearerTokenAuthenticationFilter.class);
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
        return (req, res, ex) -> write(res, 401, ErrorCodes.UNAUTHENTICATED, "Authentication is required");
    }

    private AccessDeniedHandler deniedHandler() {
        return (req, res, ex) -> write(res, 403, ErrorCodes.FORBIDDEN, "Access denied");
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
