package org.enterpriseknowledgeassitance.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid(Exception e) {
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request is invalid");
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return build(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return build(HttpStatus.CONTENT_TOO_LARGE, ErrorCodes.FILE_TOO_LARGE, "File exceeds the maximum upload size");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return build(status, status == HttpStatus.BAD_REQUEST ? ErrorCodes.VALIDATION_FAILED : ErrorCodes.REQUEST_REJECTED,
                    status == HttpStatus.BAD_REQUEST ? "Request is invalid" : "Request cannot be processed");
        }
        log.error("Unhandled error", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, CorrelationIdFilter.current()));
    }
}
