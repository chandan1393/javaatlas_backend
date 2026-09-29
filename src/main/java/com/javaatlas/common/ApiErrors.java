package com.javaatlas.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns errors into RFC 9457 Problem Details with a machine-readable "code". */
@RestControllerAdvice
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException ex) {
        return problem(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("Some details are missing or invalid.");
        return problem(HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid_request", "The request could not be read.");
    }

    /**
     * Everything else. Spring's own request errors (404, 405, 415, bad parameters) keep their status with a plain
     * message; anything unexpected is logged here and answered with a generic 500, never a stack trace or
     * exception message.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            if (status == null) status = HttpStatus.BAD_REQUEST;
            String message = switch (status) {
                case NOT_FOUND -> "Not found.";
                case METHOD_NOT_ALLOWED -> "This method isn’t allowed here.";
                case UNSUPPORTED_MEDIA_TYPE -> "Send the request as JSON.";
                default -> status.is5xxServerError() ? "Something went wrong on our side. Please try again." : "This request isn’t valid.";
            };
            return problem(status, "request_error", message);
        }
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "server_error", "Something went wrong on our side. Please try again.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setProperty("code", code);
        return ResponseEntity.status(status).body(pd);
    }
}
