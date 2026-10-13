package com.tmejs.springaijev.common;

import com.tmejs.springaijev.issue.IssueNotFoundException;
import com.tmejs.springaijev.triage.JevProtocolException;
import com.tmejs.springaijev.triage.JevUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {
    public record ApiError(String code, String message, boolean retryable) {}

    @ExceptionHandler(IssueNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiError("NOT_FOUND", "Resource not found", false));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid() {
        return ResponseEntity.badRequest()
            .body(new ApiError("INVALID_REQUEST", "Invalid request", false));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException error) {
        int code = error.getStatusCode().value();
        return ResponseEntity.status(error.getStatusCode())
            .body(new ApiError(code == 401 ? "UNAUTHORIZED" : "REQUEST_FAILED",
                code == 401 ? "Invalid credentials" : "Request failed", false));
    }

    @ExceptionHandler(JevProtocolException.class)
    ResponseEntity<ApiError> jevProtocol() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiError("JEV_INVALID_RESPONSE", "Jev returned an invalid response", false));
    }

    @ExceptionHandler(JevUnavailableException.class)
    ResponseEntity<ApiError> jevUnavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiError("JEV_UNAVAILABLE", "Jev is unavailable", true));
    }
}
