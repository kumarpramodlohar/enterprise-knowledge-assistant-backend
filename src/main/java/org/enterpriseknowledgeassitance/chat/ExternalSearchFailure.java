package org.enterpriseknowledgeassitance.chat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import org.springframework.web.client.RestClientResponseException;

/** Safe diagnostic details for an external-search failure; provider response bodies are never logged. */
record ExternalSearchFailure(String reason, String httpStatus, String exceptionType) {

    static ExternalSearchFailure from(Throwable error) {
        Throwable cause = unwrap(error);
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException response) {
                int status = response.getStatusCode().value();
                return new ExternalSearchFailure(httpReason(status), Integer.toString(status),
                        response.getClass().getSimpleName());
            }
            if (current instanceof TimeoutException || current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return new ExternalSearchFailure("timeout", "none", current.getClass().getSimpleName());
            }
            if (current instanceof UnknownHostException) {
                return new ExternalSearchFailure("dns_failure", "none", current.getClass().getSimpleName());
            }
            if (current instanceof ConnectException) {
                return new ExternalSearchFailure("connection_failure", "none", current.getClass().getSimpleName());
            }
            if (current instanceof SSLException) {
                return new ExternalSearchFailure("tls_failure", "none", current.getClass().getSimpleName());
            }
        }
        return new ExternalSearchFailure("unexpected_failure", "none", cause.getClass().getSimpleName());
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof ExecutionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String httpReason(int status) {
        return switch (status) {
            case 401 -> "authentication_failed";
            case 403 -> "access_denied";
            case 408 -> "timeout";
            case 429 -> "rate_limited";
            default -> status >= 500 ? "provider_error" : "http_error";
        };
    }
}
