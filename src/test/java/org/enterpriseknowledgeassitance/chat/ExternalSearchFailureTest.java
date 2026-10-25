package org.enterpriseknowledgeassitance.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class ExternalSearchFailureTest {

    @Test
    void classifiesWrappedUnauthorizedProviderResponse() {
        HttpClientErrorException unauthorized = HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                "Unauthorized", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        ExternalSearchFailure failure = ExternalSearchFailure.from(new ExecutionException(unauthorized));

        assertThat(failure.reason()).isEqualTo("authentication_failed");
        assertThat(failure.httpStatus()).isEqualTo("401");
        assertThat(failure.exceptionType()).isEqualTo("Unauthorized");
    }

    @Test
    void classifiesTimeoutWithoutProviderDetails() {
        ExternalSearchFailure failure = ExternalSearchFailure.from(new TimeoutException());

        assertThat(failure.reason()).isEqualTo("timeout");
        assertThat(failure.httpStatus()).isEqualTo("none");
    }

    @Test
    void classifiesDnsFailureWithoutLoggingTheHost() {
        ExternalSearchFailure failure = ExternalSearchFailure.from(new UnknownHostException("api.tavily.com"));

        assertThat(failure.reason()).isEqualTo("dns_failure");
        assertThat(failure.httpStatus()).isEqualTo("none");
        assertThat(failure.exceptionType()).isEqualTo("UnknownHostException");
    }
}
