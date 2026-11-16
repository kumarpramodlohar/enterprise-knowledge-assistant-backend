package org.enterpriseknowledgeassitance.security;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Collection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AuthServiceProperties.class)
class AuthServiceClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "eka.auth-service", name = "enabled", havingValue = "true")
    AuthServiceClient authServiceClient(AuthServiceProperties properties) throws Exception {
        properties.validate();
        SSLContext sslContext = sslContext(properties);
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUri().toString())
                .requestFactory(requestFactory)
                .build();
        return new AuthServiceClient(restClient, properties.pageSize(), properties.scopeLimit());
    }

    private SSLContext sslContext(AuthServiceProperties properties) throws Exception {
        char[] password = properties.clientKeyStorePassword().toCharArray();
        KeyStore clientKeys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(properties.clientKeyStore()))) {
            clientKeys.load(input, password);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(clientKeys, password);

        CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> authorities;
        try (InputStream input = Files.newInputStream(Path.of(properties.trustedCaCertificate()))) {
            authorities = certificates.generateCertificates(input);
        }
        if (authorities.isEmpty()) {
            throw new IllegalStateException("Auth-service trust bundle is empty");
        }
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        int index = 0;
        for (Certificate authority : authorities) {
            trust.setCertificateEntry("auth-service-ca-" + index++, authority);
        }
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trust);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), new SecureRandom());
        return context;
    }
}
